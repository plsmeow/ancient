package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.EventManager
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import net.minecraft.util.Mth
import kotlin.math.abs

/** Before flying, interactions use the position and look the server already knows. */
class AuraActionQueue(private val owner: Any) {
    private var player: LocalPlayer? = null
    private var level: ClientLevel? = null
    private var sentRotation = Rotation(0f, 0f)
    private var sentPosition = Vec3.ZERO
    private var hasMovement = false
    private var sentYawDelta = 0f
    private var lastPlaceYawDelta = Float.NaN
    private var rotated = false
    private var awaitingPlaceLook = false
    private var pending: (() -> Unit)? = null
    private val serverEyes get() = sentPosition.add(0.0, player?.eyeHeight?.toDouble() ?: 0.0, 0.0)

    init { EventManager.register(this) }

    fun beginTick() {
        pending = null
        syncContext()
    }

    private fun syncContext() {
        if (player !== mc.player || level !== mc.level) {
            reset()
            player = mc.player
            level = mc.level
            sentPosition = player?.position() ?: Vec3.ZERO
            sentRotation = Rotation(RotationManager.clientYaw, RotationManager.clientPitch)
        }
    }

    fun reset() {
        pending = null
        player = null
        level = null
        hasMovement = false
        sentYawDelta = 0f
        lastPlaceYawDelta = Float.NaN
        rotated = false
        awaitingPlaceLook = false
    }

    @Commando(priority = -128)
    fun onPacket(event: PacketSentEvent) {
        if (!mc.isSameThread) return
        if (event.packet is ServerboundUseItemOnPacket
            && ActionCoordinator.resolveOwner() == ActionCoordinator.resolveOwner(owner)) {
            awaitingPlaceLook = true
            return
        }
        if (event.packet is ServerboundClientTickEndPacket) {
            finishPlaceLook()
            return
        }
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return
        syncContext()
        if (player == null || level == null) return
        if (packet.hasRotation()) {
            sentYawDelta = abs(Mth.wrapDegrees(packet.getYRot(sentRotation.yaw) - sentRotation.yaw))
            rotated = true
        }
        sentRotation = Rotation(packet.getYRot(sentRotation.yaw), packet.getXRot(sentRotation.pitch))
        sentPosition = Vec3(packet.getX(sentPosition.x), packet.getY(sentPosition.y), packet.getZ(sentPosition.z))
        hasMovement = true
        finishPlaceLook()
    }

    private fun finishPlaceLook() {
        if (!awaitingPlaceLook) return
        if (rotated) lastPlaceYawDelta = sentYawDelta
        rotated = false
        awaitingPlaceLook = false
    }

    @Commando(priority = -128)
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val action = pending ?: return
        pending = null
        if (player !== mc.player || level !== mc.level || !hasMovement || !ActionCoordinator.canAct(owner)) return
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(owner)) { action() }
    }

    fun block(hit: BlockHitResult, range: Double, rotations: Boolean, moveFix: MoveFixMode,
              exactFace: Boolean = false, action: (BlockHitResult) -> Unit): Boolean {
        if (!prepareBlock(hit, range, rotations, moveFix, exactFace)) return false
        pending = { blockHit(hit, range, exactFace)?.let(action) }
        return true
    }

    fun prepareBlock(hit: BlockHitResult, range: Double, rotations: Boolean, moveFix: MoveFixMode,
                     exactFace: Boolean = false): Boolean {
        val current = player ?: return false
        val reach = minOf(range, current.blockInteractionRange())
        val accepts: (BlockHitResult?) -> Boolean = {
            it != null && it.blockPos == hit.blockPos && (!exactFace || it.direction == hit.direction)
        }
        if (rotations) {
            aim(blockAimPoint(hit), moveFix) { accepts(trace(serverEyes, it, reach)) }
        }
        return true
    }

    fun blockHit(hit: BlockHitResult, range: Double, exactFace: Boolean = false): BlockHitResult? {
        val current = player ?: return null
        if (!hasMovement || current !== mc.player || level !== mc.level) return null
        val reach = minOf(range, current.blockInteractionRange())
        val actual = trace(serverEyes, sentRotation, reach) ?: return null
        // Use one position for the ray and its visible face, even while walking locally.
        return actual.takeIf { it.blockPos == hit.blockPos && (!exactFace || it.direction == hit.direction)
            && withinReach(current.eyePosition, AABB(hit.blockPos), reach) }
    }

    fun crystal(crystal: EndCrystal, range: Double, rotations: Boolean, moveFix: MoveFixMode,
                action: () -> Unit): Boolean {
        if (!prepareCrystal(crystal, range, rotations, moveFix)) return false
        pending = { if (crystalReady(crystal, range)) action() }
        return true
    }

    fun prepareCrystal(crystal: EndCrystal, range: Double, rotations: Boolean, moveFix: MoveFixMode): Boolean {
        val current = player ?: return false
        val reach = minOf(range, current.entityInteractionRange())
        val point = visibleCrystalPoint(current, crystal, reach) ?: return false
        if (rotations) aim(point, moveFix) { hitsCrystal(serverEyes, it, crystal, reach) }
        return true
    }

    fun crystalReady(crystal: EndCrystal, range: Double): Boolean {
        val current = player ?: return false
        val reach = minOf(range, current.entityInteractionRange())
        return hasMovement && current === mc.player && level === mc.level && crystal.isAlive
            && hitsCrystal(serverEyes, sentRotation, crystal, reach)
            && withinReach(current.eyePosition, crystal.boundingBox, reach)
    }

    private fun blockAimPoint(hit: BlockHitResult): Vec3 =
        // A buried center aims through neighboring floor blocks. Retain the exposed face.
        hit.location.add(-hit.direction.stepX * 0.001,
            -hit.direction.stepY * 0.001, -hit.direction.stepZ * 0.001)

    private fun withinReach(eyes: Vec3, box: AABB, reach: Double) =
        eyes.distanceToSqr(RotationUtil.getNearestPoint(eyes, box)) <= reach * reach

    private fun aim(point: Vec3, moveFix: MoveFixMode, accepts: (Rotation) -> Boolean) {
        if (player == null) return
        val desired = RotationUtil.getRotationToVec(point, serverEyes).normalizeTo(sentRotation)
        val pitchOnly = Rotation(sentRotation.yaw, desired.pitch)
        // Keep a ray that already hits. Base/crystal transitions usually only need pitch.
        var next = when {
            accepts(sentRotation) -> sentRotation
            accepts(pitchOnly) -> pitchOnly
            else -> desired
        }
        val base = RotationManager.getServerRotation()
        next = GCDUtil.applyGCD(next.normalizeTo(base), base)
        val delta = abs(Mth.wrapDegrees(next.yaw - sentRotation.yaw))
        // Resolve duplicate turns with another valid ray, never by indefinitely rejecting a site.
        if (lastPlaceYawDelta > 2f && (abs(delta - lastPlaceYawDelta) < 0.0001f
                || delta < 0.0001f && rotated && abs(sentYawDelta - lastPlaceYawDelta) < 0.0001f)) {
            val step = GCDUtil.getGCD().coerceAtLeast(0.01f)
            val alternative = (1..4).asSequence().flatMap { n -> sequenceOf(n, -n) }.map { n ->
                GCDUtil.applyGCD(Rotation(next.yaw + step * n, next.pitch), base)
            }.firstOrNull { candidate ->
                val turn = abs(Mth.wrapDegrees(candidate.yaw - sentRotation.yaw))
                turn > 0.0001f && abs(turn - lastPlaceYawDelta) >= 0.0001f && accepts(candidate)
            }
            if (alternative != null) next = alternative
        }
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(owner)) {
            RotationManager.setRotation(next, moveFix = moveFix, gcd = false, keepTicks = 3)
        }
    }

    private fun hitsCrystal(eyes: Vec3, rotation: Rotation, crystal: EndCrystal, reach: Double): Boolean {
        if (crystal.boundingBox.contains(eyes)) return true
        val end = eyes.add(rotation.toDirectionVec().scale(reach))
        val hit = crystal.boundingBox.clip(eyes, end).orElse(null) ?: return crystal.boundingBox.contains(eyes)
        val block = trace(eyes, rotation, reach)
        return block == null || eyes.distanceToSqr(hit) < eyes.distanceToSqr(block.location)
    }

    private fun trace(eyes: Vec3, rotation: Rotation, reach: Double): BlockHitResult? =
        level?.clip(ClipContext(eyes, eyes.add(rotation.toDirectionVec().scale(reach)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player!!))
            ?.takeIf { it.type == HitResult.Type.BLOCK }

    companion object {
        fun visibleCrystalPoint(player: LocalPlayer, crystal: EndCrystal, range: Double): Vec3? {
            val level = mc.level ?: return null
            val eyes = player.eyePosition
            val box = crystal.boundingBox.deflate(0.05)
            val center = box.center
            val reach = minOf(range, player.entityInteractionRange())
            val points = listOf(RotationUtil.getNearestPoint(eyes, box), center,
                Vec3(box.minX, center.y, center.z), Vec3(box.maxX, center.y, center.z),
                Vec3(center.x, box.minY, center.z), Vec3(center.x, box.maxY, center.z),
                Vec3(center.x, center.y, box.minZ), Vec3(center.x, center.y, box.maxZ))
            return points.firstOrNull { point ->
                val intercept = crystal.boundingBox.clip(eyes, point).orElse(null)
                val hit = level.clip(ClipContext(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
                crystal.boundingBox.contains(eyes) || intercept != null && eyes.distanceToSqr(intercept) <= reach * reach
                    && (hit.type != HitResult.Type.BLOCK || eyes.distanceToSqr(intercept) < eyes.distanceToSqr(hit.location))
            }
        }

        fun visibleHit(player: LocalPlayer, pos: BlockPos, range: Double, face: Direction? = null): BlockHitResult? {
            val level = mc.level ?: return null
            val shape = level.getBlockState(pos).getShape(level, pos)
            if (shape.isEmpty) return null
            val eyes = player.eyePosition
            val reach = minOf(range, player.blockInteractionRange())
            val boxes = shape.toAabbs().map { it.move(pos) }
            for (box in boxes) {
                val center = box.center
                val points = listOf(center,
                    Vec3(box.minX + 0.001, center.y, center.z), Vec3(box.maxX - 0.001, center.y, center.z),
                    Vec3(center.x, box.minY + 0.001, center.z), Vec3(center.x, box.maxY - 0.001, center.z),
                    Vec3(center.x, center.y, box.minZ + 0.001), Vec3(center.x, center.y, box.maxZ - 0.001))
                for (point in points) {
                    val hit = level.clip(ClipContext(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
                    if (hit.type == HitResult.Type.BLOCK && hit.blockPos == pos && (face == null || face == hit.direction)
                        && eyes.distanceToSqr(hit.location) <= reach * reach && visibleFace(eyes, hit, box)) return hit
                }
            }
            return null
        }

        private fun visibleFace(eyes: Vec3, hit: BlockHitResult, box: AABB): Boolean = when (hit.direction) {
            Direction.UP -> eyes.y >= box.maxY
            Direction.DOWN -> eyes.y <= box.minY
            Direction.NORTH -> eyes.z <= box.minZ
            Direction.SOUTH -> eyes.z >= box.maxZ
            Direction.WEST -> eyes.x <= box.minX
            Direction.EAST -> eyes.x >= box.maxX
        }

        fun placementHit(player: LocalPlayer, pos: BlockPos, range: Double): BlockHitResult? {
            val level = mc.level ?: return null
            for (side in Direction.entries) {
                val neighbor = pos.relative(side.opposite)
                val state = level.getBlockState(neighbor)
                if (state.canBeReplaced() || state.hasBlockEntity()) continue
                val hit = visibleHit(player, neighbor, range, side) ?: continue
                if (hit.blockPos.relative(hit.direction) == pos) return hit
            }
            return null
        }
    }
}
