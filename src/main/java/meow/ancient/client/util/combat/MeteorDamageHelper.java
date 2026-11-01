package meow.ancient.client.util.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class MeteorDamageHelper {
    private MeteorDamageHelper() {}

    public static BlockHitResult raycast(Vec3 start, Vec3 end, ExplosionDamageUtil.ExposureRaycastContext context, ExplosionDamageUtil.RaycastFactory factory) {
        return BlockGetter.traverseBlocks(start, end, context, factory::apply, ignored -> null);
    }
}
