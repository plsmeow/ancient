package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.combat.AuraEntityOccupancy
import meow.ancient.client.util.combat.ExplosionDamageUtil
import meow.ancient.client.util.combat.SurroundGeometry
import meow.ancient.client.util.combat.SurroundExplosion
import meow.ancient.client.util.combat.SurroundProtection
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.floor

object SurroundModule : Module("Surround", COMBAT, "Surrounds your feet with obsidian and repairs broken walls") {
    val mode by mode("Mode", "Default", listOf("Default", "Smart"))
    private val safeDamage by number("Safe Damage", 2.0, 0.0, 20.0, 0.5, 1,
        description = "Smart protects against explosions above this damage", isVisible = { mode == "Smart" })
    private val minDamageGain by number("Min Damage Gain", 0.5, 0.1, 5.0, 0.1, 1,
        description = "Minimum damage reduction from a Smart cover plan", isVisible = { mode == "Smart" })
    private val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1)
    private val delay by number("Delay", 0.0, 0.0, 10.0, 1.0, 0)
    private val rotations by boolean("Rotations", true)
    private val silentSlots by boolean("Silent Slots", true)
    private val wallCheck by boolean("WallCheck", true)
    private val onlyGround by boolean("Only Ground", true)
    private val center by boolean("Center", false, "Moves to the center of the current block once on enable")
    private val supports by boolean("Supports", true, "Places support blocks below exposed surround positions")
    private val render by boolean("Render", true)
    private val actions = AuraActionQueue(this)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { 14.0 })
    private data class Site(val pos: BlockPos, val root: BlockPos, val hit: BlockHitResult,
                            val smart: Boolean = false, val cover: Set<BlockPos> = setOf(root))
    private data class ProtectionRequest(val explosion: SurroundExplosion, val expires: Int)
    private val protection = SurroundProtection()
    private val occupancy = AuraEntityOccupancy()
    private val requests = LinkedHashMap<String, ProtectionRequest>()
    private val anchors = HashSet<BlockPos>()
    private var anchorScanTick = -100
    private var anchorScanOrigin: BlockPos? = null
    private val pending = HashMap<BlockPos, Int>()
    private var queued = false
    private var tick = 0
    private var lastPlace = -100
    private var centerRequested = false
    private var playerContext: LocalPlayer? = null
    @Volatile private var levelContext: ClientLevel? = null

    fun hasPendingAction() = isEnabled() && (queued || pending.isNotEmpty())

    private fun reset() {
        queued = false
        pending.clear()
        requests.clear(); anchors.clear(); protection.clear(); occupancy.clear()
        anchorScanTick = -100; anchorScanOrigin = null
        tick = 0
        lastPlace = -100
        centerRequested = false
        playerContext = null
        levelContext = null
        actions.reset()
        visual.reset()
        SilentSwapUtil.releasePreparedSlot(this)
    }

    override fun onEnable() { reset(); centerRequested = center }
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

    internal fun clearProtectionRequest(owner: String) { requests.remove(owner) }

    /** Auras request cover before spending a crystal/charge or sending the detonation. */
    internal fun requestProtection(owner: String, explosion: SurroundExplosion): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        if (!isEnabled() || mode != "Smart" || playerContext !== player || levelContext !== level
            || !canProtect(player) || !hasObsidian(player) && pending.isEmpty()) { requests.remove(owner); return false }
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        requests[owner] = ProtectionRequest(explosion, tick + (ping.coerceAtLeast(0) / 50 + 12).coerceIn(12, 100))
        updateProtection(player, level)
        val waiting = protection.gain(pending.keys.toSet())?.damageReduction?.let { it >= minDamageGain } == true
            || findSmartSite(player, level) != null
        if (!waiting) requests.remove(owner)
        return waiting
    }

    private fun hasObsidian(player: LocalPlayer) = player.offhandItem.`is`(Items.OBSIDIAN)
        || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0

    private fun obsidianCount(player: LocalPlayer): Int = (0..8).sumOf {
        player.inventory.getItem(it).let { stack -> if (stack.`is`(Items.OBSIDIAN)) stack.count else 0 }
    } + if (player.offhandItem.`is`(Items.OBSIDIAN)) player.offhandItem.count else 0

    private fun canProtect(player: LocalPlayer) = player.isAlive && !player.isSpectator && !player.isUsingItem
        && mc.gui.screen() == null && player.containerMenu === player.inventoryMenu
        && player.containerMenu.carried.isEmpty && !InventoryMoveModule.isInventoryActionSlowed()
        && (!onlyGround || player.onGround())

    @Commando
    fun onPacket(event: PacketReceiveEvent) {
        val level = levelContext ?: return
        val changes = ArrayList<Pair<BlockPos, BlockState>>()
        when (val packet = event.packet) {
            is ClientboundBlockUpdatePacket -> changes.add(packet.pos.immutable() to packet.blockState)
            is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, state -> changes.add(pos.immutable() to state) }
            else -> return
        }
        mc.execute {
            if (!isEnabled() || mode != "Smart" || mc.level !== level) return@execute
            val origin = mc.player?.position() ?: return@execute
            for ((pos, state) in changes) {
                if (state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0
                    && origin.distanceToSqr(Vec3.atCenterOf(pos)) <= 100.0) anchors.add(pos)
                else anchors.remove(pos)
            }
        }
    }

    @Commando(priority = 120)
    fun onUpdate(event: PlayerUpdateEvent) {
        queued = false
        actions.beginTick()
        visual.beginTick()
        val player = mc.player
        val level = mc.level
        if (player == null || level == null || !player.connection.connection.isConnected) { reset(); return }
        if (playerContext !== player || levelContext !== level) {
            reset()
            playerContext = player
            levelContext = level
            centerRequested = center
            actions.beginTick()
        }
        tick++
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        val timeout = (ping.coerceAtLeast(0) / 50 + 5).coerceIn(5, 80)
        pending.entries.removeIf { level.getBlockState(it.key).`is`(Blocks.OBSIDIAN) || tick - it.value >= timeout }
        requests.entries.removeIf { tick >= it.value.expires || it.value.explosion.targetId?.let { id ->
            (level.getEntity(id) as? Player)?.let { !it.isAlive || FriendRepository.isFriend(it) } != false
        } == true }
        if (mode != "Smart") { requests.clear(); anchors.clear(); anchorScanOrigin = null }
        if (!ActionCoordinator.canAct(this) || !player.isAlive || player.isSpectator || player.isUsingItem
            || mc.gui.screen() != null || player.containerMenu !== player.inventoryMenu
            || !player.containerMenu.carried.isEmpty || InventoryMoveModule.isInventoryActionSlowed()
            || onlyGround && !player.onGround()) {
            SilentSwapUtil.releasePreparedSlot(this)
            return
        }
        if (centerRequested && player.onGround()) {
            val point = Vec3(floor(player.x) + 0.5, player.y, floor(player.z) + 0.5)
            if (level.noCollision(player, player.boundingBox.move(point.subtract(player.position())))) player.setPos(point)
            centerRequested = false
        }
        if (tick - lastPlace < delay.toInt()) return
        val hand = if (player.offhandItem.`is`(Items.OBSIDIAN)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val slot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else InventoryUtil.findHotbarSlot(Items.OBSIDIAN)
        if (slot !in 0..8) { SilentSwapUtil.releasePreparedSlot(this); return }
        if (mode == "Smart") updateProtection(player, level)
        val site = (if (mode == "Smart") findSmartSite(player, level) else findSite(player, level))
            ?: run { SilentSwapUtil.releasePreparedSlot(this); return }
        if (silentSlots || hand == InteractionHand.OFF_HAND) {
            if (!SilentSwapUtil.prepareSlot(slot, 2, this)) return
        } else {
            InventoryUtil.swapToSlot(slot)
            if (player.inventory.selectedSlot != slot || SilentSwapUtil.serverSlot != slot) return
        }
        visual.show(AABB(site.pos), AuraTargetRenderer.Kind.BASE)
        queued = true
        val preparedTick = tick
        val aimed = actions.block(site.hit, range, rotations, MoveFixMode.SILENT, exactFace = true, wallCheck = wallCheck) { hit ->
            queued = false
            if (preparedTick != tick || mc.player !== player || mc.level !== level || !ActionCoordinator.canAct(this)
                || player.isUsingItem || mc.gui.screen() != null || player.containerMenu !== player.inventoryMenu
                || !player.containerMenu.carried.isEmpty || InventoryMoveModule.isInventoryActionSlowed()
                || onlyGround && !player.onGround() || SilentSwapUtil.serverSlot != slot
                || !validSite(player, level, site, hit, hand, slot)) return@block
            if (InventoryUtil.useItemOnPacket(hand, hit)) {
                pending[site.pos] = tick
                lastPlace = tick
                visual.pulse()
            }
        }
        if (!aimed) queued = false
    }

    private fun replaceable(player: LocalPlayer, level: ClientLevel, pos: BlockPos): Boolean {
        val gameMode = mc.gameMode ?: return false
        return !PacketMineModule.targetsPosition(pos) && level.worldBorder.isWithinBounds(pos) && level.getBlockState(pos).canBeReplaced()
            && !level.isOutsideBuildHeight(pos)
            && !player.blockActionRestricted(level, pos, gameMode.playerMode)
            && !AABB(pos).intersects(player.boundingBox) && level.getEntities(null, AABB(pos)).none { !it.isRemoved && it.blocksBuilding }
    }

    private fun updateProtection(player: LocalPlayer, level: ClientLevel) {
        val explosions = collectExplosions(player, level).toMutableList()
        // Keep the offensive target/reservations when the same crystal or anchor is also seen in the world.
        for (request in requests.values) {
            explosions.removeAll { it.center == request.explosion.center && it.diameter == request.explosion.diameter }
            explosions.add(request.explosion)
        }
        val selected = explosions.sortedWith(compareByDescending<SurroundExplosion> { it.targetId != null }
            .thenByDescending { it.immediate }.thenBy { player.position().distanceToSqr(it.center) }).take(64)
        protection.update(level, player, selected, safeDamage.toFloat())
    }

    private fun collectExplosions(player: LocalPlayer, level: ClientLevel): List<SurroundExplosion> {
        val explosions = ArrayList<SurroundExplosion>()
        val enemies = ArrayList<Player>()
        val entities = level.entitiesForRendering().toList()
        occupancy.update(entities, player.boundingBox.inflate(8.0))
        for (entity in entities) {
            if (entity is EndCrystal && entity.isAlive && player.distanceToSqr(entity) <= 144.0)
                explosions.add(SurroundExplosion(entity.position(), ExplosionDamageUtil.CRYSTAL_DIAMETER))
            if (entity is Player && entity !== player && entity.isAlive && !entity.isSpectator
                && player.distanceToSqr(entity) <= 144.0 && !FriendRepository.isFriend(entity)) enemies.add(entity)
        }
        val origin = player.blockPosition()
        // Packets discover new charged anchors immediately; occasional scans cover loaded chunks/movement.
        if (anchorScanOrigin != origin || tick - anchorScanTick >= 5) {
            anchors.clear(); anchorScanOrigin = origin; anchorScanTick = tick
            for (mutable in BlockPos.betweenClosed(origin.x - 10, maxOf(level.minY, origin.y - 10), origin.z - 10,
                origin.x + 10, minOf(level.maxY - 1, origin.y + 10), origin.z + 10)) {
                if (player.position().distanceToSqr(Vec3.atCenterOf(mutable)) > 100.0) continue
                val state = level.getBlockState(mutable)
                if (state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0) anchors.add(mutable.immutable())
            }
        }
        anchors.removeIf { pos ->
            val state = level.getBlockState(pos)
            player.position().distanceToSqr(Vec3.atCenterOf(pos)) > 100.0
                || !state.`is`(Blocks.RESPAWN_ANCHOR) || state.getValue(RespawnAnchorBlock.CHARGE) == 0
        }
        for (pos in anchors) if (ExplosionDamageUtil.anchorExplodes(level, pos))
            explosions.add(SurroundExplosion(Vec3.atCenterOf(pos), ExplosionDamageUtil.ANCHOR_DIAMETER, pos, anchor = true))
        // A reachable empty crystal base is a threat even before the opponent sends its placement.
        if (enemies.isNotEmpty()) for (mutable in BlockPos.betweenClosed(origin.x - 4, maxOf(level.minY, origin.y - 2), origin.z - 4,
            origin.x + 4, minOf(level.maxY - 1, origin.y + 3), origin.z + 4)) {
            val state = level.getBlockState(mutable)
            if (!state.`is`(Blocks.OBSIDIAN) && !state.`is`(Blocks.BEDROCK)) continue
            val pos = mutable.immutable()
            if (!level.getBlockState(pos.above()).isAir || occupancy.crystalBlocked(pos)) continue
            if (enemies.none { enemy ->
                val box = AABB(pos)
                val eyes = enemy.eyePosition
                val nearest = Vec3(eyes.x.coerceIn(box.minX, box.maxX), eyes.y.coerceIn(box.minY, box.maxY), eyes.z.coerceIn(box.minZ, box.maxZ))
                eyes.distanceToSqr(nearest) <= enemy.blockInteractionRange() * enemy.blockInteractionRange()
            }) continue
            explosions.add(SurroundExplosion(Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5),
                ExplosionDamageUtil.CRYSTAL_DIAMETER, prospective = true, reserved = setOf(pos.above()), immediate = false))
        }
        return explosions
    }

    private fun findSmartSite(player: LocalPlayer, level: ClientLevel): Site? {
        if (!protection.hasThreats) return null
        val available = obsidianCount(player)
        if (available == 0) return null
        var best: Site? = null
        var bestScore = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        val placementHits = HashMap<BlockPos, BlockHitResult?>()
        val canPlace = HashMap<BlockPos, Boolean>()
        fun placeable(pos: BlockPos) = canPlace.getOrPut(pos) { pos !in pending && replaceable(player, level, pos) }
        fun hit(pos: BlockPos): BlockHitResult? {
            if (!placementHits.containsKey(pos)) placementHits[pos] = actions.placementHit(pos, range, wallCheck, rotations)
            return placementHits[pos]
        }
        fun consider(site: Site) {
            val gain = protection.gain(site.cover) ?: return
            if (gain.damageReduction < minDamageGain.toFloat()) return
            val score = gain.riskReduction / site.cover.size
            val distance = player.eyePosition.distanceToSqr(site.hit.location)
            if (score > bestScore || score == bestScore && distance < bestDistance) {
                best = site; bestScore = score; bestDistance = distance
            }
        }
        for (root in SurroundGeometry.protectionShell(player.boundingBox)) {
            if (!placeable(root)) continue
            val box = AABB(root)
            val eyes = player.eyePosition
            val nearest = Vec3(eyes.x.coerceIn(box.minX, box.maxX), eyes.y.coerceIn(box.minY, box.maxY), eyes.z.coerceIn(box.minZ, box.maxZ))
            val reach = minOf(range, player.blockInteractionRange())
            if (eyes.distanceToSqr(nearest) > reach * reach) continue
            val direct = hit(root)
            if (direct != null) {
                consider(Site(root, root, direct, smart = true))
            } else if (supports) {
                for (path in SurroundGeometry.supportPaths(root)) {
                    if (path.size > available || path.any { !placeable(it) }) continue
                    val support = path.last()
                    val supportHit = hit(support) ?: continue
                    consider(Site(support, root, supportHit, smart = true, cover = path.toSet()))
                }
            }
        }
        return best
    }

    private fun findSite(player: LocalPlayer, level: ClientLevel): Site? {
        val ring = SurroundGeometry.ring(player.boundingBox).sortedBy { player.eyePosition.distanceToSqr(Vec3.atCenterOf(it)) }
        for (root in ring) {
            if (root in pending || !replaceable(player, level, root)) continue
            val hit = actions.placementHit(root, range, wallCheck, rotations) ?: continue
            return Site(root, root, hit)
        }
        if (supports) for (root in ring) {
            if (root in pending || !replaceable(player, level, root)) continue
            for (depth in 1..2) {
                val pos = root.below(depth)
                if (pos in pending || !replaceable(player, level, pos)) break
                val hit = actions.placementHit(pos, range, wallCheck, rotations) ?: continue
                return Site(pos, root, hit)
            }
        }
        return null
    }

    private fun validSite(player: LocalPlayer, level: ClientLevel, site: Site, hit: BlockHitResult,
                          hand: InteractionHand, slot: Int): Boolean {
        if (site.smart != (mode == "Smart")) return false
        val roots = if (site.smart) SurroundGeometry.protectionShell(player.boundingBox) else SurroundGeometry.ring(player.boundingBox)
        if (site.root !in roots || !level.getBlockState(site.root).canBeReplaced()
            || !replaceable(player, level, site.pos) || hit.blockPos.relative(hit.direction) != site.pos) return false
        if (site.smart) {
            if (site.cover.any { AABB(it).intersects(player.boundingBox) || PacketMineModule.targetsPosition(it) }) return false
            updateProtection(player, level)
            if (protection.gain(site.cover)?.damageReduction?.let { it >= minDamageGain } != true) return false
        }
        val stack = if (hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(slot)
        if (!stack.`is`(Items.OBSIDIAN)) return false
        val context = BlockPlaceContext(player, hand, stack, hit)
        return context.clickedPos == site.pos && context.canPlace()
    }
}
