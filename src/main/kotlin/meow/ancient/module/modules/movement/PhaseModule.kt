package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.PearlPhasePlanner
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerMovementSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.render.FreeCameraModule
import meow.ancient.network.SilentPacketQueue
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.ceil

object PhaseModule : Module("Phase", MOVEMENT, "Phase through blocks using Pearls or Wind Charge") {
    val modeSetting = mode("Mode", "Pearl", listOf("Pearl", "WindCharge Vanilla"))
    val mode by modeSetting

    private val searchRange by number("Search Range", 1.0, 0.5, 2.0, 0.1, 1, isVisible = { mode == "Pearl" })
    private val maxWallThickness by number("Max Thickness", 8.5, 1.0, 9.0, 0.5, 1, isVisible = { mode == "WindCharge Vanilla" })
    private val impulseDelay by number("Impulse Delay", 2.0, 1.0, 5.0, 1.0, 0, isVisible = { mode == "WindCharge Vanilla" })
    private val autoDisable by boolean("Auto Disable", true)
    private val onlyOnGround by boolean("Only On Ground", true)
    private val silentSlots by boolean("Silent Slots", true)
    private val render by boolean("Render", true)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { 16.0 })

    private data class Prepared(
        val player: LocalPlayer,
        val level: ClientLevel,
        val target: Vec3,
        val wallBox: AABB,
        val hand: InteractionHand,
        val slot: Int,
        val tick: Int,
        val isWindCharge: Boolean,
        val rotation: Rotation,
        val shot: PearlPhasePlanner.Shot? = null
    )

    private var prepared: Prepared? = null
    private var activeWindCharge: Prepared? = null
    private var windChargeTicks = 0
    private var playerRef: LocalPlayer? = null
    private var levelRef: ClientLevel? = null
    private var sentFeet = Vec3.ZERO
    private var tick = 0
    private var throwAccepted = false
    private var blockPushUntil = 0

    fun isPreparingThrow() = isEnabled() && prepared != null

    fun shouldCancelBlockPush(): Boolean {
        val player = mc.player ?: return false
        if (!insideBlocks(player)) return false
        if (isEnabled()) return true
        return player === playerRef && mc.level === levelRef && player.tickCount < blockPushUntil
    }

    private fun insideBlocks(player: LocalPlayer) =
        mc.level?.getBlockCollisions(player, player.boundingBox.deflate(0.002))?.iterator()?.hasNext() == true

    private fun reset() {
        prepared = null
        activeWindCharge = null
        windChargeTicks = 0
        playerRef = null
        levelRef = null
        sentFeet = Vec3.ZERO
        tick = 0
        throwAccepted = false
        blockPushUntil = 0
        visual.reset()
        SilentSwapUtil.releasePreparedSlot(this)
    }

    override fun onEnable() {
        reset()
        val player = mc.player ?: return
        val expectedItem = if (mode == "WindCharge Vanilla") Items.WIND_CHARGE else Items.ENDER_PEARL
        val name = if (mode == "WindCharge Vanilla") "wind charge" else "ender pearl"
        if (!player.offhandItem.`is`(expectedItem) && InventoryUtil.findHotbarSlot(expectedItem) < 0) {
            ChatUtil.error("Phase: no $name in the hotbar or offhand.")
            setEnabled(false)
        }
    }

    override fun onDisable() {
        prepared = null
        activeWindCharge = null
        windChargeTicks = 0
        visual.reset()
        SilentSwapUtil.releasePreparedSlot(this)
        if (!throwAccepted) reset()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando(priority = 127)
    fun onUpdate(event: PlayerUpdateEvent) {
        prepared = null
        visual.beginTick()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (playerRef !== player || levelRef !== level) {
            reset()
            playerRef = player
            levelRef = level
            sentFeet = player.position()
        }
        tick++
        if (!player.connection.connection.isConnected || !player.isAlive || player.isSpectator || player.isPassenger
            || FreeCameraModule.isEnabled() || mc.gui.screen() != null || player.isUsingItem
            || player.containerMenu !== player.inventoryMenu || !player.containerMenu.carried.isEmpty
            || InventoryMoveModule.isInventoryActionSlowed()) return

        val wc = activeWindCharge
        if (wc != null) {
            windChargeTicks++
            val shouldExecute = player.isInPostImpulseGraceTime || windChargeTicks >= impulseDelay.toInt()
            if (shouldExecute) {
                val conn = player.connection.connection
                SilentPacketQueue.send(
                    conn,
                    ServerboundMovePlayerPacket.Pos(wc.target.x, wc.target.y, wc.target.z, false, false)
                )
                player.setPos(wc.target.x, wc.target.y, wc.target.z)
                player.deltaMovement = Vec3.ZERO
                blockPushUntil = player.tickCount + 20
                visual.pulse()
                activeWindCharge = null
                windChargeTicks = 0
                throwAccepted = true
                if (autoDisable) {
                    setEnabled(false)
                }
            }
            return
        }

        val grounded = player.onGround() || player.verticalCollision
        if (throwAccepted || (onlyOnGround && !grounded) || player.isInWater) return

        if (mode == "WindCharge Vanilla") {
            handleWindCharge(player, level)
        } else {
            handlePearl(player, level)
        }
    }

    private fun handlePearl(player: LocalPlayer, level: ClientLevel) {
        if (player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)) return
        val hand = if (player.offhandItem.`is`(Items.ENDER_PEARL)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val slot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else InventoryUtil.findHotbarSlot(Items.ENDER_PEARL)
        if (slot !in 0..8) {
            ChatUtil.error("Phase: no ender pearl in the hotbar or offhand.")
            setEnabled(false)
            return
        }
        if (!ActionCoordinator.canAct(this)) return
        val launch = launch(player)
        val walls = nearbyWalls(player, level, launch)
        if (walls.isEmpty()) return
        val base = RotationManager.getServerRotation()
        val shot = PearlPhasePlanner.find(launch, walls, RotationManager.clientYaw,
            { GCDUtil.applyGCD(it.normalizeTo(base), base) }, { from, to -> collision(player, level, from, to) }) ?: return
        prepared = Prepared(
            player, level, shot.teleport, AABB(shot.wall.pos), hand, slot, tick,
            isWindCharge = false, rotation = shot.rotation, shot = shot
        )
        visual.show(AABB(shot.wall.pos), AuraTargetRenderer.Kind.PLACE)
        RotationManager.setRotation(shot.rotation, moveFix = MoveFixMode.SILENT, gcd = false, keepTicks = 0, returnSpeed = 180f)
        val selected = if (silentSlots || hand == InteractionHand.OFF_HAND) SilentSwapUtil.prepareSlot(slot, owner = this)
            else { InventoryUtil.swapToSlot(slot); player.inventory.selectedSlot == slot }
        if (!selected) prepared = null
    }

    private fun handleWindCharge(player: LocalPlayer, level: ClientLevel) {
        val hand = if (player.offhandItem.`is`(Items.WIND_CHARGE)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val slot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else InventoryUtil.findHotbarSlot(Items.WIND_CHARGE)
        if (slot !in 0..8) {
            ChatUtil.error("Phase: no wind charge in the hotbar or offhand.")
            setEnabled(false)
            return
        }
        if (!ActionCoordinator.canAct(this)) return

        val exit = findHorizontalExit(player, level, maxWallThickness) ?: return
        val targetPos = exit.first
        val wallBox = exit.second

        val base = RotationManager.getServerRotation()
        val rotation = GCDUtil.applyGCD(Rotation(player.yRot, 90.0f).normalizeTo(base), base)

        prepared = Prepared(
            player, level, targetPos, wallBox, hand, slot, tick,
            isWindCharge = true, rotation = rotation
        )
        visual.show(wallBox, AuraTargetRenderer.Kind.PLACE)
        RotationManager.setRotation(rotation, moveFix = MoveFixMode.SILENT, gcd = false, keepTicks = 2, returnSpeed = 180f)
        val selected = if (silentSlots || hand == InteractionHand.OFF_HAND) SilentSwapUtil.prepareSlot(slot, holdTicks = 3, owner = this)
            else { InventoryUtil.swapToSlot(slot); player.inventory.selectedSlot == slot }
        if (!selected) prepared = null
    }

    private fun findHorizontalExit(player: LocalPlayer, level: ClientLevel, maxDist: Double): Pair<Vec3, AABB>? {
        val lookVec = player.lookAngle
        val len = Math.hypot(lookVec.x, lookVec.z)
        if (len < 0.001) return null
        val normX = lookVec.x / len
        val normZ = lookVec.z / len

        var firstWallBox: AABB? = null
        var foundWall = false
        var step = 0.3
        val maxStep = maxDist.coerceAtMost(9.0)
        while (step <= maxStep + 0.8) {
            val testPos = Vec3(player.x + normX * step, player.y, player.z + normZ * step)
            if (testPos.distanceTo(player.position()) > 9.75) break

            val testBox = player.boundingBox.move(testPos.x - player.x, testPos.y - player.y, testPos.z - player.z)
            val collides = level.getBlockCollisions(player, testBox).iterator().hasNext()

            if (collides) {
                foundWall = true
                if (firstWallBox == null) {
                    firstWallBox = testBox
                }
            } else if (foundWall) {
                if (level.noCollision(player, testBox)) {
                    return Pair(testPos, firstWallBox ?: testBox)
                }
            }
            step += 0.2
        }
        return null
    }

    private fun launch(player: LocalPlayer) = PearlPhasePlanner.Launch(
        sentFeet,
        player.eyeHeight.toDouble(),
        player.knownMovement,
        player.onGround() || player.verticalCollision,
        player.bbWidth.toDouble(),
        player.bbHeight.toDouble()
    )

    private fun nearbyWalls(player: LocalPlayer, level: ClientLevel, launch: PearlPhasePlanner.Launch): List<PearlPhasePlanner.Wall> {
        val origin = BlockPos.containing(launch.feet)
        val radius = ceil(searchRange + 0.5).toInt()
        val walls = ArrayList<PearlPhasePlanner.Wall>()
        for (mutable in BlockPos.betweenClosed(
            origin.x - radius, origin.y - 1, origin.z - radius,
            origin.x + radius, origin.y + 2, origin.z + radius
        )) {
            val pos = mutable.immutable()
            if (!level.hasChunk(pos.x shr 4, pos.z shr 4) || !level.worldBorder.isWithinBounds(pos)) continue
            val state = level.getBlockState(pos)
            val shape = state.getCollisionShape(level, pos)
            if (shape.isEmpty || !state.fluidState.isEmpty) continue
            val boxes = shape.toAabbs().map { it.move(pos) }.filter {
                it.maxY > launch.feet.y + 0.1 && it.minY < launch.feet.y + player.bbHeight - 0.1
                    && !it.intersects(player.boundingBox.deflate(0.002))
                    && horizontalDistance(launch.feet, it) <= searchRange * searchRange
            }
            if (boxes.isNotEmpty()) walls.add(PearlPhasePlanner.Wall(pos, boxes))
        }
        return walls.sortedBy { wall -> wall.boxes.minOf { horizontalDistance(launch.feet, it) } }.take(8)
    }

    private fun horizontalDistance(point: Vec3, box: AABB): Double {
        val dx = point.x - point.x.coerceIn(box.minX, box.maxX)
        val dz = point.z - point.z.coerceIn(box.minZ, box.maxZ)
        return dx * dx + dz * dz
    }

    private fun collision(player: LocalPlayer, level: ClientLevel, from: Vec3, to: Vec3): HitResult? {
        val block = level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        var best: HitResult? = block.takeIf { it.type == HitResult.Type.BLOCK }
        var distance = best?.location?.distanceToSqr(from) ?: Double.POSITIVE_INFINITY
        for (entity in level.getEntities(player, AABB(from, to).inflate(0.3))) {
            if (!entity.isAlive || !entity.isPickable || entity.isSpectator) continue
            val intercept = entity.boundingBox.inflate(0.3).clip(from, to).orElse(null) ?: continue
            val candidateDistance = intercept.distanceToSqr(from)
            if (candidateDistance < distance) { best = EntityHitResult(entity, intercept); distance = candidateDistance }
        }
        return best
    }

    @Commando(priority = 127)
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val current = prepared ?: return
        if (mc.player !== current.player || mc.level !== current.level || tick != current.tick
            || !ActionCoordinator.canAct(this) || current.player.isUsingItem
            || SilentSwapUtil.serverSlot != current.slot || current.player.position().distanceToSqr(sentFeet) > 0.36) {
            prepared = null
            return
        }

        val expectedItem = if (current.isWindCharge) Items.WIND_CHARGE else Items.ENDER_PEARL
        val item = if (current.hand == InteractionHand.OFF_HAND) current.player.offhandItem else current.player.inventory.getItem(current.slot)
        if (!item.`is`(expectedItem)) {
            prepared = null
            return
        }

        if (!current.isWindCharge) {
            val shot = current.shot
            if (shot == null || current.player.cooldowns.isOnCooldown(expectedItem.defaultInstance)
                || !PearlPhasePlanner.validate(launch(current.player), shot, { from, to -> collision(current.player, current.level, from, to) })) {
                prepared = null
                return
            }
        }

        val gameMode = mc.gameMode ?: return
        gameMode.startPrediction(current.level) { sequence ->
            ServerboundUseItemPacket(current.hand, sequence, current.rotation.yaw, current.rotation.pitch)
        }
        current.player.swing(current.hand, SwingAnimation.DEFAULT, false)

        if (current.isWindCharge) {
            activeWindCharge = current
            windChargeTicks = 0
        }
        prepared = null
    }

    @Commando(priority = -128)
    fun onPacket(event: PacketSentEvent) {
        if (!mc.isSameThread || playerRef !== mc.player || levelRef !== mc.level) return
        when (val packet = event.packet) {
            is ServerboundMovePlayerPacket -> {
                sentFeet = Vec3(packet.getX(sentFeet.x), packet.getY(sentFeet.y), packet.getZ(sentFeet.z))
            }
            is ServerboundUseItemPacket -> {
                val current = prepared ?: return
                if (ActionCoordinator.resolveOwner() != ActionCoordinator.resolveOwner(this) || packet.hand() != current.hand) return
                val ping = current.player.connection.getPlayerInfo(current.player.uuid)?.latency ?: 150
                throwAccepted = true
                val flightTicks = current.shot?.flightTicks ?: 2
                blockPushUntil = current.player.tickCount + flightTicks + ping.coerceAtLeast(0) / 50 + 20
                visual.pulse()
            }
        }
    }

    @Commando
    fun onMovementSent(event: PlayerMovementSentEvent) {
        val player = playerRef ?: return
        if (throwAccepted) {
            if (insideBlocks(player) || player.position().distanceToSqr(sentFeet) > 0.36) {
                throwAccepted = false
                if (autoDisable) {
                    setEnabled(false)
                }
            }
        }
    }
}
