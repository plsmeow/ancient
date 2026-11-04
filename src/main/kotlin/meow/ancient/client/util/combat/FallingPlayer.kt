package meow.ancient.client.util.combat

import it.unimi.dsi.fastutil.floats.FloatArraySet
import it.unimi.dsi.fastutil.floats.FloatArrays
import meow.ancient.client.extensions.mc
import meow.ancient.mixin.EntityCollisionContextAccessor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.BlockCollisions
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.sqrt

class FallingPlayer(
    private val player: LocalPlayer,
    var x: Double,
    var y: Double,
    var z: Double,
    private var motionX: Double,
    private var motionY: Double,
    private var motionZ: Double,
    private val yRot: Float
) {
    companion object {
        private const val SUPPORT_EPSILON = 1.0E-6

        fun fromPlayer(player: LocalPlayer, movementYaw: Float = player.yRot): FallingPlayer = FallingPlayer(
            player,
            player.x,
            player.y,
            player.z,
            player.deltaMovement.x,
            player.deltaMovement.y,
            player.deltaMovement.z,
            movementYaw
        )
    }

    class CollisionResult(val pos: BlockPos?, val tick: Int, val positionBeforeMovement: Vec3)

    private var simulatedTicks: Int = 0
    private var boundingBox = player.getDimensions(player.pose).makeBoundingBox(x, y, z)
    private var lastResolvedMovement = Vec3.ZERO

    fun findCollision(ticks: Int): CollisionResult? {
        val rotationVec = player.lookAngle

        for (i in 0 until ticks) {
            val positionBeforeMovement = Vec3(x, y, z)
            if (advanceSimulation(rotationVec, forceDescending = false)) {
                return CollisionResult(findSupportingBlock(boundingBox, lastResolvedMovement), i, positionBeforeMovement)
            }
        }
        return null
    }

    fun willStartTickInBlockBeforeCollision(targetPos: BlockPos, ticks: Int, forceDescending: Boolean = false): Boolean {
        val rotationVec = player.lookAngle

        repeat(ticks) {
            if (BlockPos.containing(x, y, z) == targetPos) return true
            if (advanceSimulation(rotationVec, forceDescending)) return false
        }
        return false
    }

    private fun advanceSimulation(rotationVec: Vec3, forceDescending: Boolean): Boolean {
        val intendedMovement = calculateMovementForTick(rotationVec)
        val resolvedMovement = if (forceDescending) {
            val collisionContext = EntityCollisionContextAccessor.`ancient$create`(
                true,
                false,
                y,
                player.mainHandItem,
                false,
                player
            )
            Entity.collideBoundingBox(collisionContext, intendedMovement, boundingBox, mc.level!!, emptyList())
        } else {
            collidePlayer(intendedMovement)
        }
        val collidedDownwards = intendedMovement.y < 0.0 && resolvedMovement.y != intendedMovement.y

        lastResolvedMovement = resolvedMovement
        x += resolvedMovement.x
        y += resolvedMovement.y
        z += resolvedMovement.z
        boundingBox = boundingBox.move(resolvedMovement)

        if (collidedDownwards) return true

        applyCollisionResponse(intendedMovement, resolvedMovement)
        if (!player.isFallFlying) applyFreeFallForces()

        simulatedTicks++
        return false
    }

    private fun collidePlayer(movement: Vec3): Vec3 {
        val world = mc.level!!
        val entityColliders = world.getEntityCollisions(player, boundingBox.expandTowards(movement))
        val directMovement = if (movement.lengthSqr() == 0.0) {
            movement
        } else {
            Entity.collideBoundingBox(player, movement, boundingBox, world, entityColliders)
        }
        val collidedHorizontally = movement.x != directMovement.x || movement.z != directMovement.z
        val landed = movement.y < 0.0 && movement.y != directMovement.y

        if (player.maxUpStep() <= 0f || (!landed && !player.onGround()) || !collidedHorizontally) {
            return directMovement
        }

        val groundedBox = if (landed) boundingBox.move(0.0, directMovement.y, 0.0) else boundingBox
        var stepSearchBox = groundedBox.expandTowards(movement.x, player.maxUpStep().toDouble(), movement.z)
        if (!landed) stepSearchBox = stepSearchBox.expandTowards(0.0, -1.0E-5, 0.0)

        val colliders = buildList {
            addAll(entityColliders)
            world.worldBorder.takeIf { it.isInsideCloseToBorder(player, stepSearchBox) }
                ?.let { add(it.collisionShape) }
            addAll(world.getBlockCollisions(player, stepSearchBox))
        }

        return resolveStepUpMovement(movement, directMovement, boundingBox, groundedBox, player.maxUpStep(), colliders)
    }

    private fun findSupportingBlock(playerBox: AABB, movement: Vec3): BlockPos? {
        val testArea = AABB(
            playerBox.minX,
            playerBox.minY - SUPPORT_EPSILON,
            playerBox.minZ,
            playerBox.maxX,
            playerBox.minY,
            playerBox.maxZ
        )
        return findSupportingBlock(testArea)
            ?: findSupportingBlock(testArea.move(-movement.x, 0.0, -movement.z))
    }

    private fun findSupportingBlock(testArea: AABB): BlockPos? {
        val candidates = BlockCollisions(mc.level!!, player, testArea, false) { pos, _ -> pos }
        return selectSupportingBlock(candidates, Vec3(x, y, z))
    }

    private fun applyCollisionResponse(intendedMovement: Vec3, resolvedMovement: Vec3) {
        motionX = if (resolvedMovement.x != intendedMovement.x) 0.0 else motionX
        motionY = if (resolvedMovement.y != intendedMovement.y) 0.0 else motionY
        motionZ = if (resolvedMovement.z != intendedMovement.z) 0.0 else motionZ
    }

    private fun calculateMovementForTick(rotationVec: Vec3): Vec3 {
        if (player.isFallFlying) {
            calculateElytraTick(rotationVec)
        } else {
            applyAirInput()
        }
        return Vec3(motionX, motionY, motionZ)
    }

    private fun applyAirInput() {
        val speed = player.speed * 0.1f
        if (speed > 0f) {
            val inputVec = getInputVector(playerMovementInput(), speed, yRot)
            motionX += inputVec.x
            motionZ += inputVec.z
        }
    }

    private fun applyFreeFallForces() {
        motionY -= effectiveGravity()
        motionX *= LivingEntity.BASE_HORIZONTAL_AIR_DRAG.toDouble()
        motionY *= LivingEntity.BASE_VERTICAL_AIR_DRAG.toDouble()
        motionZ *= LivingEntity.BASE_HORIZONTAL_AIR_DRAG.toDouble()
    }

    private fun calculateElytraTick(rotationVec: Vec3) {
        val pitchRad = player.xRot.toDouble() * Mth.DEG_TO_RAD

        val lookHorLength = sqrt(rotationVec.x * rotationVec.x + rotationVec.z * rotationVec.z)
        val moveHorLength = sqrt(motionX * motionX + motionZ * motionZ)
        val gravity = effectiveGravity()

        val m = rotationVec.length()
        var n = Mth.cos(pitchRad)
        n = (n.toDouble() * n.toDouble() * 1.0.coerceAtMost(m / 0.4)).toFloat()

        var vec3d5 = Vec3(motionX, motionY + gravity * (-1.0 + n.toDouble() * 0.75), motionZ)

        var q: Double
        if (vec3d5.y < 0.0 && lookHorLength > 0.0) {
            q = vec3d5.y * -0.1 * n.toDouble()
            vec3d5 = vec3d5.add(rotationVec.x * q / lookHorLength, q, rotationVec.z * q / lookHorLength)
        }

        if (pitchRad < 0.0 && lookHorLength > 0.0) {
            q = moveHorLength * (-Mth.sin(pitchRad)).toDouble() * 0.04
            vec3d5 = vec3d5.add(-rotationVec.x * q / lookHorLength, q * 3.2, -rotationVec.z * q / lookHorLength)
        }

        if (lookHorLength > 0.0) {
            vec3d5 = vec3d5.add(
                (rotationVec.x / lookHorLength * moveHorLength - vec3d5.x) * 0.1,
                0.0,
                (rotationVec.z / lookHorLength * moveHorLength - vec3d5.z) * 0.1
            )
        }

        motionX = vec3d5.x * LivingEntity.ELYTRA_HORIZONTAL_AIR_DRAG.toDouble()
        motionY = vec3d5.y * LivingEntity.ELYTRA_VERTICAL_AIR_DRAG.toDouble()
        motionZ = vec3d5.z * LivingEntity.ELYTRA_HORIZONTAL_AIR_DRAG.toDouble()
    }

    private fun effectiveGravity(): Double {
        val rawGravity = player.gravity
        return if (motionY <= 0.0 && hasStatusEffect(MobEffects.SLOW_FALLING)) minOf(rawGravity, 0.01) else rawGravity
    }

    private fun hasStatusEffect(effect: Holder<MobEffect>): Boolean {
        val instance = player.getEffect(effect) ?: return false
        return instance.duration >= simulatedTicks
    }

    private fun playerMovementInput(): Vec3 {
        val moveVector = player.input.getMoveVector()
        return Vec3(moveVector.x.toDouble() * 0.98, 0.0, moveVector.y.toDouble() * 0.98)
    }

    private fun getInputVector(input: Vec3, speed: Float, yRot: Float): Vec3 {
        val lengthSquared = input.lengthSqr()
        if (lengthSquared < 1.0E-7) return Vec3.ZERO
        val normalized = if (lengthSquared > 1.0) input.normalize() else input
        val scaled = normalized.scale(speed.toDouble())
        val sin = Mth.sin(yRot.toDouble() * Mth.DEG_TO_RAD)
        val cos = Mth.cos(yRot.toDouble() * Mth.DEG_TO_RAD)
        return Vec3(scaled.x * cos - scaled.z * sin, scaled.y, scaled.z * cos + scaled.x * sin)
    }
}

private fun resolveStepUpMovement(
    movement: Vec3,
    directMovement: Vec3,
    boundingBox: AABB,
    groundedBox: AABB,
    maxUpStep: Float,
    colliders: List<VoxelShape>
): Vec3 {
    val candidates = FloatArraySet(4)
    for (collider in colliders) {
        val coords = collider.getCoords(Direction.Axis.Y)
        for (i in coords.indices) {
            val coordinate = coords.getDouble(i)
            val relativeCoordinate = (coordinate - groundedBox.minY).toFloat()
            if (relativeCoordinate >= 0f && relativeCoordinate != directMovement.y.toFloat()) {
                if (relativeCoordinate > maxUpStep) break
                candidates.add(relativeCoordinate)
            }
        }
    }

    val sortedCandidates = candidates.toFloatArray()
    FloatArrays.unstableSort(sortedCandidates)
    for (candidate in sortedCandidates) {
        val steppedMovement = collideWithShapes(Vec3(movement.x, candidate.toDouble(), movement.z), groundedBox, colliders)
        if (steppedMovement.horizontalDistanceSqr() > directMovement.horizontalDistanceSqr()) {
            return steppedMovement.subtract(0.0, boundingBox.minY - groundedBox.minY, 0.0)
        }
    }

    return directMovement
}

private fun collideWithShapes(movement: Vec3, boundingBox: AABB, shapes: List<VoxelShape>): Vec3 {
    if (shapes.isEmpty()) return movement

    var resolvedMovement = Vec3.ZERO
    for (axis in Direction.axisStepOrder(movement)) {
        val axisMovement = movement.get(axis)
        if (axisMovement != 0.0) {
            val collision = Shapes.collide(axis, boundingBox.move(resolvedMovement), shapes, axisMovement)
            resolvedMovement = resolvedMovement.with(axis, collision)
        }
    }
    return resolvedMovement
}

private fun selectSupportingBlock(candidates: Iterator<BlockPos>, position: Vec3): BlockPos? {
    val support = BlockPos.MutableBlockPos()
    var supportDistance = Double.POSITIVE_INFINITY

    for (candidate in candidates) {
        val distance = candidate.distToCenterSqr(position)
        if (distance < supportDistance || distance == supportDistance && support.compareTo(candidate) < 0) {
            support.set(candidate)
            supportDistance = distance
        }
    }

    return support.takeIf { supportDistance.isFinite() }
}
