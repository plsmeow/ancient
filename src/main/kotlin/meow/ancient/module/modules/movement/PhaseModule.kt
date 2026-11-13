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
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.event.events.PlayerMovementSentEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.render.FreeCameraModule
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

object PhaseModule : Module("Phase", MOVEMENT, "Automatically aims and throws a pearl to enter a nearby wall") {
    private val searchRange by number("Search Range", 1.0, 0.5, 2.0, 0.1, 1)
    private val onlyOnGround by boolean("Only On Ground", true)
    private val silentSlots by boolean("Silent Slots", true)
    private val render by boolean("Render", true)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { 16.0 })

    private data class Prepared(val player: LocalPlayer, val level: ClientLevel,
        val launch: PearlPhasePlanner.Launch, val shot: PearlPhasePlanner.Shot,
        val hand: InteractionHand, val slot: Int, val tick: Int)
    private var prepared: Prepared? = null
    private var playerRef: LocalPlayer? = null
    private var levelRef: ClientLevel? = null
    private var sentFeet = Vec3.ZERO
    private var tick = 0
    private var throwAccepted = false
    private var blockPushUntil = 0

    fun isPreparingThrow() = isEnabled() && prepared != null

    fun shouldCancelBlockPush(): Boolean {
        val player = mc.player ?: return false
        return player === playerRef && mc.level === levelRef && insideBlocks(player)
            && (isEnabled() || player.tickCount < blockPushUntil)
    }

    private fun insideBlocks(player: LocalPlayer) =
        mc.level?.getBlockCollisions(player, player.boundingBox.deflate(0.002))?.iterator()?.hasNext() == true

    private fun reset() {
        prepared = null
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
        if (!player.offhandItem.`is`(Items.ENDER_PEARL) && InventoryUtil.findHotbarSlot(Items.ENDER_PEARL) < 0) {
            ChatUtil.error("Phase: no ender pearl in the hotbar or offhand.")
            setEnabled(false)
        }
    }

    override fun onDisable() {
        prepared = null
        visual.reset()
        SilentSwapUtil.releasePreparedSlot(this)
        if (!throwAccepted) reset()
    }
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

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
        if (insideBlocks(player)) {
            setEnabled(false)
            return
        }
        if (throwAccepted || onlyOnGround && !player.onGround() || player.isInWater) return
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
        prepared = Prepared(player, level, launch, shot, hand, slot, tick)
        visual.show(AABB(shot.wall.pos), AuraTargetRenderer.Kind.PLACE)
        RotationManager.setRotation(shot.rotation, moveFix = MoveFixMode.SILENT, gcd = false, keepTicks = 0, returnSpeed = 180f)
        val selected = if (silentSlots || hand == InteractionHand.OFF_HAND) SilentSwapUtil.prepareSlot(slot, owner = this)
            else { InventoryUtil.swapToSlot(slot); player.inventory.selectedSlot == slot }
        if (!selected) prepared = null
    }

    private fun launch(player: LocalPlayer) = PearlPhasePlanner.Launch(sentFeet, player.eyeHeight.toDouble(),
        player.knownMovement, player.onGround(), player.bbWidth.toDouble(), player.bbHeight.toDouble())

    private fun nearbyWalls(player: LocalPlayer, level: ClientLevel, launch: PearlPhasePlanner.Launch): List<PearlPhasePlanner.Wall> {
        val origin = BlockPos.containing(launch.feet)
        val radius = ceil(searchRange + 0.5).toInt()
        val walls = ArrayList<PearlPhasePlanner.Wall>()
        for (mutable in BlockPos.betweenClosed(origin.x - radius, origin.y, origin.z - radius,
            origin.x + radius, origin.y + 1, origin.z + radius)) {
            val pos = mutable.immutable()
            if (!level.hasChunk(pos.x shr 4, pos.z shr 4) || !level.worldBorder.isWithinBounds(pos)) continue
            val state = level.getBlockState(pos)
            val shape = state.getCollisionShape(level, pos)
            if (shape.isEmpty || !state.fluidState.isEmpty) continue
            val boxes = shape.toAabbs().map { it.move(pos) }.filter {
                it.maxY > launch.feet.y + 0.2 && it.minY < launch.feet.y + 0.4
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
            || current.player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)
            || SilentSwapUtil.serverSlot != current.slot || current.player.position().distanceToSqr(sentFeet) > 0.36
            || !PearlPhasePlanner.validate(current.launch, current.shot,
                { from, to -> collision(current.player, current.level, from, to) })) {
            prepared = null
            return
        }
        val pearl = if (current.hand == InteractionHand.OFF_HAND) current.player.offhandItem else current.player.inventory.getItem(current.slot)
        if (!pearl.`is`(Items.ENDER_PEARL)) { prepared = null; return }
        val gameMode = mc.gameMode ?: return
        gameMode.startPrediction(current.level) { sequence ->
            ServerboundUseItemPacket(current.hand, sequence, current.shot.rotation.yaw, current.shot.rotation.pitch)
        }
        current.player.swing(current.hand, SwingAnimation.DEFAULT, false)
        prepared = null
    }

    @Commando(priority = -128)
    fun onPacket(event: PacketSentEvent) {
        if (!mc.isSameThread || playerRef !== mc.player || levelRef !== mc.level) return
        when (val packet = event.packet) {
            is ServerboundMovePlayerPacket -> sentFeet = Vec3(packet.getX(sentFeet.x), packet.getY(sentFeet.y), packet.getZ(sentFeet.z))
            is ServerboundUseItemPacket -> {
                val current = prepared ?: return
                if (ActionCoordinator.resolveOwner() != ActionCoordinator.resolveOwner(this) || packet.hand() != current.hand) return
                val ping = current.player.connection.getPlayerInfo(current.player.uuid)?.latency ?: 150
                throwAccepted = true
                blockPushUntil = current.player.tickCount + current.shot.flightTicks + ping.coerceAtLeast(0) / 50 + 20
                visual.pulse()
            }
        }
    }

    @Commando
    fun onMovementSent(event: PlayerMovementSentEvent) {
        // Finish after vanilla sends this tick's aimed movement; no second pearl is scheduled.
        if (throwAccepted) setEnabled(false)
    }
}
