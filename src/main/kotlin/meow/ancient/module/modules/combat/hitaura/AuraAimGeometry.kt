package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.module.modules.combat.HitAuraModule.AimPointMode
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal object AuraAimGeometry {
    fun point(eyes: Vec3, box: AABB, mode: AimPointMode): Vec3 = when (mode) {
        AimPointMode.NEAREST -> RotationUtil.getNearestPoint(eyes, box, 0.10)
        AimPointMode.MULTIPOINT -> RotationUtil.getMultipoint(eyes, box, 0.78)
        AimPointMode.HEAD -> Vec3(box.center.x,
            (box.minY + (box.maxY - box.minY) * 0.88).coerceAtMost(box.maxY - 0.05), box.center.z)
        AimPointMode.CHEST -> Vec3(box.center.x, box.minY + (box.maxY - box.minY) * 0.55, box.center.z)
    }
}
