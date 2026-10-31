package meow.ancient.module.modules.render

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.lighting.LightEngine
import net.minecraft.world.phys.shapes.Shapes
import sweetie.evaware.flora.api.Commando
import java.util.concurrent.Executors

object LightEspModule : Module(
    name = "LightESP",
    category = RENDER,
    description = "Highlights illuminated blocks through walls"
) {
    val color = color("Color", Color4b(255, 200, 0, 255))
    val fill = boolean("Fill", true)
    val lineWidth = number("Line Width", 1.5, 0.5, 5.0, 0.5, 1)
    val allSources = boolean("All Light Sources", false)

    private const val TORCH_REACH = 14
    private val NO_SURFACES = LongArray(0)
    private val NO_CHUNKS = emptyArray<CachedChunk>()
    private val DIRECTIONS = Direction.entries.toTypedArray()

    private val cacheLock = Any()
    private val chunks = Long2ObjectOpenHashMap<CachedChunk>()
    private val sourceDirty = LongLinkedOpenHashSet()
    private val lightDirty = LongLinkedOpenHashSet()
    private val replacements = ArrayList<ChunkAccess>()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Ancient-LightESP").apply { isDaemon = true }
    }
    private var scanSources = Long2ByteOpenHashMap()
    private val lightLevels = Long2ByteOpenHashMap()
    private val queue = LongArrayFIFOQueue()
    private val surfaces = LongOpenHashSet()
    private val neighbors = arrayOfNulls<CachedChunk>(9)
    private val neighborRevisions = LongArray(9)
    private val blockPos = BlockPos.MutableBlockPos()
    private val neighborPos = BlockPos.MutableBlockPos()

    private var currentLevel: ClientLevel? = null
    private var generation = 0L
    private var scheduled = false
    private var renderDirty = false
    private var renderChunks = NO_CHUNKS
    private var registered = false

    private fun isLightSource(state: BlockState): Boolean {
        if (allSources.value) {
            return state.lightEmission > 0
        }
        return state.`is`(Blocks.TORCH) || state.`is`(Blocks.WALL_TORCH) ||
            state.`is`(Blocks.SOUL_TORCH) || state.`is`(Blocks.SOUL_WALL_TORCH)
    }

    override fun onEnable() {
        reset(mc.level, true)
        if (!registered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (!isEnabled() || !inGame) return@register
                val world = mc.level ?: return@register
                val snapshot: Array<CachedChunk>
                synchronized(cacheLock) {
                    if (currentLevel !== world) return@register
                    if (renderDirty) {
                        renderChunks = if (chunks.isEmpty()) NO_CHUNKS else chunks.values.toTypedArray()
                        renderDirty = false
                    }
                    snapshot = renderChunks
                }

                val strokeColor = color.value
                val fillColor = strokeColor.alpha(30)
                val style = if (fill.value) {
                    GizmoStyle.strokeAndFill(strokeColor.argb, lineWidth.value.toFloat(), fillColor.argb)
                } else {
                    GizmoStyle.stroke(strokeColor.argb, lineWidth.value.toFloat())
                }

                for (chunk in snapshot) {
                    val positions = chunk.surfaces
                    if (positions.isEmpty()) continue
                    if (world.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false) !== chunk.chunk) continue
                    for (packed in positions) {
                        Gizmos.cuboid(BlockPos.of(packed), style)
                    }
                }
            }
            registered = true
        }
    }

    override fun onDisable() {
        reset(null, false)
    }

    private fun reset(world: ClientLevel?, active: Boolean) {
        synchronized(cacheLock) {
            generation++
            currentLevel = world
            chunks.clear()
            sourceDirty.clear()
            lightDirty.clear()
            renderChunks = NO_CHUNKS
            renderDirty = false
            if (active && world != null) {
                val player = mc.player
                if (player != null) {
                    val px = player.blockX shr 4
                    val pz = player.blockZ shr 4
                    val radius = mc.options.renderDistance().get().coerceAtMost(16)
                    for (dx in -radius..radius) {
                        for (dz in -radius..radius) {
                            val chunk = world.getChunk(px + dx, pz + dz, ChunkStatus.FULL, false) ?: continue
                            addChunk(chunk)
                        }
                    }
                }
                schedule()
            }
        }
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        reset(null, isEnabled())
    }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        if (!isEnabled()) return
        when (val packet = event.packet) {
            is ClientboundBlockUpdatePacket -> {
                handleBlockUpdate(packet.pos, packet.blockState)
            }
            is ClientboundSectionBlocksUpdatePacket -> {
                packet.runUpdates { pos, state ->
                    handleBlockUpdate(pos, state)
                }
            }
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!isEnabled()) return
        val world = mc.level ?: return
        if (currentLevel != world) reset(world, true)
        val player = mc.player ?: return
        synchronized(cacheLock) {
            val lvl = currentLevel ?: return
            replacements.clear()
            val iterator = chunks.values.iterator()
            while (iterator.hasNext()) {
                val chunk = iterator.next()
                val loaded = lvl.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false)
                if (loaded === chunk.chunk) continue
                iterator.remove()
                sourceDirty.remove(chunk.key)
                lightDirty.remove(chunk.key)
                invalidateChunks(chunk.x - 1, chunk.z - 1, chunk.x + 1, chunk.z + 1)
                renderDirty = true
                if (loaded != null) replacements.add(loaded)
            }
            for (chunk in replacements) addChunk(chunk)
            replacements.clear()

            val px = player.blockX shr 4
            val pz = player.blockZ shr 4
            val radius = mc.options.renderDistance().get().coerceAtMost(16)
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val loaded = lvl.getChunk(px + dx, pz + dz, ChunkStatus.FULL, false) ?: continue
                    val key = ChunkPos.pack(loaded.pos.x, loaded.pos.z)
                    if (!chunks.containsKey(key)) {
                        addChunk(loaded)
                    }
                }
            }
            schedule()
        }
    }

    private fun handleBlockUpdate(pos: BlockPos, state: BlockState) {
        val world = mc.level ?: return
        if (currentLevel != world) reset(world, true)
        val packed = pos.asLong()
        val x = pos.x
        val z = pos.z
        synchronized(cacheLock) {
            val lvl = currentLevel ?: return
            val chunkX = x shr 4
            val chunkZ = z shr 4
            val key = ChunkPos.pack(chunkX, chunkZ)
            val loaded = lvl.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return
            var chunk = chunks.get(key)
            if (chunk == null || chunk.chunk !== loaded) {
                addChunk(loaded)
                chunk = chunks.get(key) ?: return
            }
            chunk.revision++
            if (isLightSource(state)) {
                chunk.sources.put(packed, state.lightEmission.toByte())
            } else {
                chunk.sources.remove(packed)
            }
            if (!chunk.sourcesReady) {
                sourceDirty.add(key)
            }
            invalidateChunks(
                (x - TORCH_REACH) shr 4,
                (z - TORCH_REACH) shr 4,
                (x + TORCH_REACH) shr 4,
                (z + TORCH_REACH) shr 4
            )
            schedule()
        }
    }

    private fun addChunk(chunk: ChunkAccess) {
        val key = chunk.pos.pack()
        chunks.put(key, CachedChunk(chunk))
        sourceDirty.add(key)
        invalidateChunks(chunk.pos.x - 1, chunk.pos.z - 1, chunk.pos.x + 1, chunk.pos.z + 1)
        renderDirty = true
    }

    private fun invalidateChunks(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        for (z in minZ..maxZ) {
            for (x in minX..maxX) {
                val key = ChunkPos.pack(x, z)
                val chunk = chunks.get(key) ?: continue
                chunk.lightRevision++
                lightDirty.add(key)
            }
        }
    }

    private fun schedule() {
        if (scheduled || !isEnabled() || currentLevel == null || (sourceDirty.isEmpty() && lightDirty.isEmpty())) return
        scheduled = true
        worker.execute(::drain)
    }

    private fun drain() {
        try {
            while (true) {
                var chunk: CachedChunk? = null
                var world: ClientLevel? = null
                var epoch = 0L
                var revision = 0L
                var scan = false
                synchronized(cacheLock) {
                    if (!isEnabled() || currentLevel == null || (sourceDirty.isEmpty() && lightDirty.isEmpty())) return
                    scan = !sourceDirty.isEmpty()
                    val key = if (scan) sourceDirty.removeFirstLong() else lightDirty.removeFirstLong()
                    val found = chunks.get(key)
                    if (found != null) {
                        chunk = found
                        world = currentLevel
                        epoch = generation
                        revision = if (scan) found.revision else found.lightRevision
                    }
                }
                val target = chunk ?: continue
                val lvl = world ?: continue
                if (scan) {
                    scanChunk(target, lvl, epoch, revision)
                } else {
                    illuminateChunk(target, lvl, epoch, revision)
                }
            }
        } finally {
            synchronized(cacheLock) {
                scheduled = false
                schedule()
            }
        }
    }

    private fun current(world: ClientLevel, epoch: Long): Boolean {
        return isEnabled() && generation == epoch && currentLevel === world && mc.level === world
    }

    private fun scanChunk(chunk: CachedChunk, world: ClientLevel, epoch: Long, revision: Long) {
        if (!current(world, epoch) || world.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false) !== chunk.chunk) return
        scanSources.clear()
        chunk.chunk.findBlocks({ isLightSource(it) }) { pos, state ->
            scanSources.put(pos.asLong(), state.lightEmission.toByte())
        }
        synchronized(cacheLock) {
            if (!current(world, epoch) || chunks.get(chunk.key) !== chunk || chunk.revision != revision
                || world.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false) !== chunk.chunk) return
            val previous = chunk.sources
            chunk.sources = scanSources
            scanSources = previous
            chunk.sourcesReady = true
        }
    }

    private fun illuminateChunk(target: CachedChunk, world: ClientLevel, epoch: Long, revision: Long) {
        lightLevels.clear()
        queue.clear()
        surfaces.clear()
        val minX = target.x shl 4
        val minZ = target.z shl 4
        val maxX = minX + 15
        val maxZ = minZ + 15
        synchronized(cacheLock) {
            if (!current(world, epoch) || chunks.get(target.key) !== target || target.lightRevision != revision) return
            for (dz in -1..1) {
                for (dx in -1..1) {
                    val index = (dz + 1) * 3 + dx + 1
                    val chunk = chunks.get(ChunkPos.pack(target.x + dx, target.z + dz))
                    neighbors[index] = chunk
                    neighborRevisions[index] = chunk?.revision ?: 0L
                    if (world.getChunk(target.x + dx, target.z + dz, ChunkStatus.FULL, false) !== chunk?.chunk) return
                    if (chunk != null && !chunk.sourcesReady) {
                        sourceDirty.add(chunk.key)
                        lightDirty.add(target.key)
                        return
                    }
                }
            }
            for (chunk in neighbors) {
                if (chunk == null) continue
                val iterator = chunk.sources.long2ByteEntrySet().fastIterator()
                while (iterator.hasNext()) {
                    val source = iterator.next()
                    queue.enqueue(source.longKey)
                    queue.enqueue(source.byteValue.toLong())
                }
            }
        }

        var count = queue.size() / 2
        while (count > 0) {
            val packed = queue.dequeueLong()
            val light = queue.dequeueLong().toInt()
            val x = BlockPos.getX(packed)
            val z = BlockPos.getZ(packed)
            val distance = maxOf(minX - x, maxOf(0, x - maxX)) + maxOf(minZ - z, maxOf(0, z - maxZ))
            if (distance <= light) {
                lightLevels.put(packed, light.toByte())
                queue.enqueue(packed)
                queue.enqueue(light.toLong())
            }
            count--
        }

        var steps = 0
        while (queue.size() > 0) {
            if ((steps++ and 255) == 0 && !current(world, epoch)) return
            val packed = queue.dequeueLong()
            val light = queue.dequeueLong().toInt()
            if (light != lightLevels.get(packed).toInt()) continue
            blockPos.set(packed)
            val chunk = neighborChunk(target, blockPos.x, blockPos.z) ?: continue
            val state = chunk.chunk.getBlockState(blockPos)
            for (direction in DIRECTIONS) {
                neighborPos.setWithOffset(blockPos, direction)
                val x = neighborPos.x
                val z = neighborPos.z
                if (x < minX - TORCH_REACH || x > maxX + TORCH_REACH || z < minZ - TORCH_REACH || z > maxZ + TORCH_REACH
                    || world.isOutsideBuildHeight(neighborPos)) continue
                val neighborChunk = neighborChunk(target, x, z) ?: continue
                val neighbor = neighborChunk.chunk.getBlockState(neighborPos)
                val neighborPacked = neighborPos.asLong()
                if (neighborChunk === target && !neighbor.isAir && !surfaces.contains(neighborPacked)
                    && !neighbor.getCollisionShape(world, neighborPos).isEmpty
                    && !Shapes.faceShapeOccludes(LightEngine.getOcclusionShape(state, direction), Shapes.empty())) {
                    surfaces.add(neighborPacked)
                }
                val dampening = LightEngine.getLightDampeningInto(state, neighbor, direction, maxOf(1, neighbor.lightDampening))
                val nextLight = light - dampening
                if (nextLight <= lightLevels.get(neighborPacked).toInt()) continue
                lightLevels.put(neighborPacked, nextLight.toByte())
                queue.enqueue(neighborPacked)
                queue.enqueue(nextLight.toLong())
            }
        }

        val result = if (surfaces.isEmpty()) NO_SURFACES else surfaces.toLongArray()
        synchronized(cacheLock) {
            if (!current(world, epoch) || chunks.get(target.key) !== target || target.lightRevision != revision) return
            for (dz in -1..1) {
                for (dx in -1..1) {
                    val index = (dz + 1) * 3 + dx + 1
                    val chunk = neighbors[index]
                    if (world.getChunk(target.x + dx, target.z + dz, ChunkStatus.FULL, false) !== chunk?.chunk) return
                    if (chunks.get(ChunkPos.pack(target.x + dx, target.z + dz)) !== chunk || (chunk != null && chunk.revision != neighborRevisions[index])) {
                        lightDirty.add(target.key)
                        return
                    }
                }
            }
            target.surfaces = result
        }
    }

    private fun neighborChunk(target: CachedChunk, x: Int, z: Int): CachedChunk? {
        val dx = (x shr 4) - target.x
        val dz = (z shr 4) - target.z
        return if (dx < -1 || dx > 1 || dz < -1 || dz > 1) null else neighbors[(dz + 1) * 3 + dx + 1]
    }

    private class CachedChunk(val chunk: ChunkAccess) {
        val key: Long = chunk.pos.pack()
        val x: Int = chunk.pos.x
        val z: Int = chunk.pos.z
        var sources: Long2ByteOpenHashMap = Long2ByteOpenHashMap()
        @Volatile
        var surfaces: LongArray = NO_SURFACES
        var revision: Long = 0L
        var lightRevision: Long = 0L
        var sourcesReady: Boolean = false
    }
}
