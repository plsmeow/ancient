package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes

/** Uses exposed hitbox faces and the same projected-blocker search as block placement. */
internal object HitboxPointResolver {
    // An entity face is not a world block. Keep its ray identifier outside world coordinates.
    private val hitboxKey = BlockPos(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

    fun find(eyes: Vec3, box: AABB, reach: Double, preferred: Vec3? = null, secondaryEyes: Vec3? = null,
             trace: (Vec3, Vec3) -> BlockHitResult?, shapes: (BlockPos) -> List<AABB>,
             accepts: (Vec3) -> Boolean = { true }): Vec3? {
        if (!reach.isFinite() || reach <= 0.0) return null
        if (box.contains(eyes)) return eyes.takeIf {
            (secondaryEyes == null || secondaryEyes.distanceToSqr(eyes) <= reach * reach) && accepts(it)
        }
        val shape = Shapes.create(box)
        fun hit(start: Vec3, end: Vec3): BlockHitResult? {
            val entity = shape.clip(start, end, BlockPos.ZERO) ?: return trace(start, end)
            val block = trace(start, entity.location)
            if (block != null && start.distanceToSqr(block.location) + 1.0E-7 < start.distanceToSqr(entity.location)) return block
            return BlockHitResult(entity.location, entity.direction, hitboxKey, false)
        }
        // Retain a preferred aim only when its ray actually reaches an exposed part of the box.
        if (preferred != null && preferred.distanceToSqr(eyes) > 1.0E-12) {
            val end = eyes.add(preferred.subtract(eyes).normalize().scale(reach + 0.002))
            val candidate = hit(eyes, end)
            if (candidate?.blockPos == hitboxKey && eyes.distanceToSqr(candidate.location) <= reach * reach
                && (secondaryEyes == null || secondaryEyes.distanceToSqr(candidate.location) <= reach * reach)
                && accepts(preferred)) return preferred
        }
        val result = BlockFaceResolver.find(eyes, hitboxKey, listOf(box), reach, preferred = preferred,
            secondaryEyes = secondaryEyes, trace = ::hit, shapes = shapes,
            accepts = { accepts(BlockFaceResolver.aimPoint(eyes, it)) }) ?: return null
        return BlockFaceResolver.aimPoint(eyes, result)
    }
}
