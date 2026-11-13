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
import net.minecraft.tags.BlockTags
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CandleCakeBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
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
              exactFace: Boolean = false, wallCheck: Boolean = true, action: (BlockHitResult) -> Unit): Boolean {
        if (!prepareBlock(hit, range, rotations, moveFix, exactFace, wallCheck)) return false
        pending = { blockHit(hit, range, exactFace, wallCheck)?.let(action) }
        return true
    }

    fun prepareBlock(hit: BlockHitResult, range: Double, rotations: Boolean, moveFix: MoveFixMode,
                     exactFace: Boolean = false, wallCheck: Boolean = true): Boolean {
        val current = player ?: return false
        val reach = minOf(range, current.blockInteractionRange())
        val accepts: (Rotation) -> Boolean = { rotation ->
            val actual = trace(serverEyes, rotation, reach, hit.blockPos.takeUnless { wallCheck })
            actual != null && actual.blockPos == hit.blockPos && (!wallCheck || !exactFace || actual.direction == hit.direction)
                && current.eyePosition.distanceToSqr(actual.location) <= reach * reach
        }
        if (!rotations) return true
        var rotation: Rotation? = null
        val selected = visibleHit(current, hit.blockPos, range, hit.direction.takeIf { exactFace }, wallCheck,
            eyes = serverEyes, preferred = hit.location, secondaryEyes = current.eyePosition) { candidate ->
            rotation = aimingRotation(blockAimPoint(candidate), accepts)
            rotation != null
        } ?: return false
        if (!wallCheck) rotation = aimingRotation(blockAimPoint(selected), accepts)
        applyAim(rotation ?: return false, moveFix)
        return true
    }

    /** Refresh supports from the same eye position that will be used to send the interaction. */
    fun placementHit(pos: BlockPos, range: Double, wallCheck: Boolean = true, rotations: Boolean = false): BlockHitResult? {
        val current = player ?: return null
        val reach = minOf(range, current.blockInteractionRange())
        return Companion.placementHit(current, pos, range, wallCheck, eyes = serverEyes,
            secondaryEyes = current.eyePosition) { hit ->
            !rotations || !wallCheck || aimingRotation(blockAimPoint(hit)) { rotation ->
                val actual = trace(serverEyes, rotation, reach)
                actual != null && actual.blockPos == hit.blockPos && actual.direction == hit.direction
                    && current.eyePosition.distanceToSqr(actual.location) <= reach * reach
            } != null
        }
    }

    fun visibleHit(pos: BlockPos, range: Double, wallCheck: Boolean = true, preferred: Vec3? = null): BlockHitResult? {
        val current = player ?: return null
        return Companion.visibleHit(current, pos, range, wallCheck = wallCheck, eyes = serverEyes,
            preferred = preferred, secondaryEyes = current.eyePosition)
    }

    fun blockHit(hit: BlockHitResult, range: Double, exactFace: Boolean = false, wallCheck: Boolean = true): BlockHitResult? {
        val current = player ?: return null
        if (!hasMovement || current !== mc.player || level !== mc.level) return null
        val reach = minOf(range, current.blockInteractionRange())
        if (!wallCheck) {
            val world = level ?: return null
            if (world.getBlockState(hit.blockPos).getShape(world, hit.blockPos).isEmpty) return null
            return hit.takeIf { serverEyes.distanceToSqr(it.location) <= reach * reach
                && current.eyePosition.distanceToSqr(it.location) <= reach * reach }
        }
        val actual = trace(serverEyes, sentRotation, reach) ?: return null
        // Use one position for the ray and its visible face, even while walking locally.
        return actual.takeIf { it.blockPos == hit.blockPos && (!exactFace || it.direction == hit.direction)
            && current.eyePosition.distanceToSqr(it.location) <= reach * reach }
    }

    fun crystal(crystal: EndCrystal, range: Double, rotations: Boolean, moveFix: MoveFixMode,
                wallCheck: Boolean = true, action: () -> Unit): Boolean {
        if (!prepareCrystal(crystal, range, rotations, moveFix, wallCheck)) return false
        pending = { if (crystalReady(crystal, range, wallCheck)) action() }
        return true
    }

    fun prepareCrystal(crystal: EndCrystal, range: Double, rotations: Boolean, moveFix: MoveFixMode,
                       wallCheck: Boolean = true): Boolean {
        val current = player ?: return false
        val reach = minOf(range, current.entityInteractionRange())
        val point = visibleCrystalPoint(current, crystal, reach, wallCheck) ?: return false
        if (rotations && !aim(point, moveFix) { hitsCrystal(serverEyes, it, crystal, reach, wallCheck) }) return false
        return true
    }

    fun crystalReady(crystal: EndCrystal, range: Double, wallCheck: Boolean = true): Boolean {
        val current = player ?: return false
        val reach = minOf(range, current.entityInteractionRange())
        return hasMovement && current === mc.player && level === mc.level && crystal.isAlive
            && hitsCrystal(serverEyes, sentRotation, crystal, reach, wallCheck)
            && withinReach(current.eyePosition, crystal.boundingBox, reach)
    }

    private fun blockAimPoint(hit: BlockHitResult): Vec3 =
        BlockFaceResolver.aimPoint(serverEyes, hit)

    private fun withinReach(eyes: Vec3, box: AABB, reach: Double) =
        eyes.distanceToSqr(RotationUtil.getNearestPoint(eyes, box)) <= reach * reach

    private fun aimingRotation(point: Vec3, accepts: (Rotation) -> Boolean): Rotation? {
        if (player == null) return null
        val desired = RotationUtil.getRotationToVec(point, serverEyes).normalizeTo(sentRotation)
        val pitchOnly = Rotation(sentRotation.yaw, desired.pitch)
        val base = RotationManager.getServerRotation()
        val quantized = GCDUtil.applyGCD(desired.normalizeTo(base), base)
        // Check the rounded ray too: near an exposed edge, rounding can move it into a wall.
        var next = sequenceOf(sentRotation, pitchOnly, quantized)
            .map { GCDUtil.applyGCD(it.normalizeTo(base), base) }.firstOrNull(accepts)
            ?: quantizedNeighbors(quantized, GCDUtil.getGCD()).firstOrNull(accepts) ?: return null
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
        return next
    }

    private fun aim(point: Vec3, moveFix: MoveFixMode, accepts: (Rotation) -> Boolean): Boolean {
        val rotation = aimingRotation(point, accepts) ?: return false
        applyAim(rotation, moveFix)
        return true
    }

    private fun applyAim(rotation: Rotation, moveFix: MoveFixMode) {
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(owner)) {
            RotationManager.setRotation(rotation, moveFix = moveFix, gcd = false, keepTicks = 3)
        }
    }

    private fun hitsCrystal(eyes: Vec3, rotation: Rotation, crystal: EndCrystal, reach: Double, wallCheck: Boolean): Boolean {
        if (crystal.boundingBox.contains(eyes)) return true
        val end = eyes.add(rotation.toDirectionVec().scale(reach))
        val hit = crystal.boundingBox.clip(eyes, end).orElse(null) ?: return crystal.boundingBox.contains(eyes)
        if (!wallCheck) return true
        val block = trace(eyes, rotation, reach)
        return block == null || eyes.distanceToSqr(hit) < eyes.distanceToSqr(block.location)
    }

    private fun trace(eyes: Vec3, rotation: Rotation, reach: Double, targetOnly: BlockPos? = null): BlockHitResult? {
        val world = level ?: return null
        val end = eyes.add(rotation.toDirectionVec().scale(reach))
        if (targetOnly != null) return world.getBlockState(targetOnly).getShape(world, targetOnly).clip(eyes, end, targetOnly)
        return world.clip(ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player!!))
            .takeIf { it.type == HitResult.Type.BLOCK }
    }

    companion object {
        fun visibleCrystalPoint(player: LocalPlayer, crystal: EndCrystal, range: Double, wallCheck: Boolean = true): Vec3? {
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
                val hit = if (wallCheck) level.clip(ClipContext(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)) else null
                crystal.boundingBox.contains(eyes) || intercept != null && eyes.distanceToSqr(intercept) <= reach * reach
                    && (hit == null || hit.type != HitResult.Type.BLOCK || eyes.distanceToSqr(intercept) < eyes.distanceToSqr(hit.location))
            }
        }

        fun visibleHit(player: LocalPlayer, pos: BlockPos, range: Double, face: Direction? = null,
                       wallCheck: Boolean = true, overrides: Map<BlockPos, BlockState> = emptyMap(),
                       eyes: Vec3 = player.eyePosition, preferred: Vec3? = null, secondaryEyes: Vec3? = null,
                       accepts: (BlockHitResult) -> Boolean = { true }): BlockHitResult? {
            val level = mc.level ?: return null
            val reach = minOf(range, player.blockInteractionRange())
            val shapes = HashMap<BlockPos, VoxelShape>()
            val shapeCache = HashMap<BlockPos, List<AABB>>()
            fun shape(block: BlockPos) = shapes.getOrPut(block.immutable()) {
                (overrides[block] ?: level.getBlockState(block)).getShape(level, block)
            }
            fun boxes(block: BlockPos) = shapeCache.getOrPut(block.immutable()) {
                shape(block).toAabbs().map { it.move(block) }
            }
            val targetBoxes = boxes(pos)
            if (!wallCheck) return BlockFaceResolver.direct(eyes, pos, targetBoxes, reach, face, secondaryEyes)
            return BlockFaceResolver.find(eyes, pos, targetBoxes, reach, face, preferred, secondaryEyes,
                trace = { start, end ->
                    if (overrides.isEmpty()) {
                        level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
                            .takeIf { it.type == HitResult.Type.BLOCK }
                    } else MeteorDamageHelper.raycast(start, end, ExplosionDamageUtil.ExposureRaycastContext(start, end),
                        ExplosionDamageUtil.RaycastFactory { ray, block ->
                            shape(block).clip(ray.start, ray.end, block)
                        })
                }, shapes = ::boxes, accepts = accepts)
        }

        private fun quantizedNeighbors(rotation: Rotation, step: Float): Sequence<Rotation> = sequence {
            for (radius in 1..2) for (yaw in -radius..radius) for (pitch in -radius..radius) {
                if (maxOf(abs(yaw), abs(pitch)) != radius) continue
                val y = rotation.pitch + pitch * step
                if (y in -90f..90f) yield(Rotation(rotation.yaw + yaw * step, y))
            }
        }

        fun placementHit(player: LocalPlayer, pos: BlockPos, range: Double, wallCheck: Boolean = true,
                         overrides: Map<BlockPos, BlockState> = emptyMap(), eyes: Vec3 = player.eyePosition,
                         secondaryEyes: Vec3? = null, accepts: (BlockHitResult) -> Boolean = { true }): BlockHitResult? {
            val level = mc.level ?: return null
            val hits = ArrayList<BlockHitResult>()
            for (side in Direction.entries) {
                val neighbor = pos.relative(side.opposite)
                val state = overrides[neighbor] ?: level.getBlockState(neighbor)
                if (state.canBeReplaced()) continue
                // A right click must place the held block instead of opening/toggling the support.
                if (!player.isSecondaryUseActive && (state.hasBlockEntity() || state.getMenuProvider(level, neighbor) != null
                        || state.`is`(BlockTags.DOORS) || state.`is`(BlockTags.TRAPDOORS) || state.`is`(BlockTags.BUTTONS)
                        || state.`is`(BlockTags.FENCE_GATES) || state.`is`(BlockTags.BEDS) || state.`is`(Blocks.LEVER)
                        || state.`is`(Blocks.NOTE_BLOCK) || state.`is`(Blocks.RESPAWN_ANCHOR) || state.`is`(Blocks.CAKE)
                        || state.block is CandleCakeBlock || state.block is SweetBerryBushBlock)) continue
                val hit = visibleHit(player, neighbor, range, side, wallCheck, overrides, eyes = eyes,
                    secondaryEyes = secondaryEyes, accepts = accepts) ?: continue
                if (hit.blockPos.relative(hit.direction) == pos) hits.add(hit)
            }
            return hits.minByOrNull { eyes.distanceToSqr(it.location) }
        }
    }
}
