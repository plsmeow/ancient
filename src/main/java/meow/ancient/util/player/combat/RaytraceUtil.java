package meow.ancient.util.player.combat;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import meow.ancient.util.IMinecraft;
import meow.ancient.util.rotation.Rotation;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import java.util.Objects;
import java.util.function.Predicate;

@UtilityClass
public class RaytraceUtil implements IMinecraft {
    public BlockHitResult raycast(double range, Rotation angle, boolean includeFluids) {
        return raycast(Objects.requireNonNull(mc.player).getCameraPosVec(1.0F),range,angle,includeFluids);
    }

    public BlockHitResult raycast(Vec3d vec, double range, Rotation angle, boolean includeFluids) {
        Entity entity = mc.cameraEntity;

        if (entity == null) {
            return null;
        }

        Vec3d rotationVec = angle.toVector();
        Vec3d end = vec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);

        World world = mc.world;
        if (world == null) {
            return null;
        }

        RaycastContext.FluidHandling fluidHandling = includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE;
        RaycastContext context = new RaycastContext(vec, end, RaycastContext.ShapeType.OUTLINE, fluidHandling, entity);

        return world.raycast(context);
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType) {
        return raycast(start, end, shapeType, mc.player);
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return raycast(start, end, shapeType, entity, null);
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity, Predicate<BlockPos> ignoreBlock) {
        if (mc.world == null) return null;
        if (ignoreBlock == null) {
            return mc.world.raycast(new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity));
        }
        RaycastContext context = new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity);
        return BlockView.raycast(start, end, context, (ctx, pos) -> {
            if (ignoreBlock.test(pos)) {
                return null;
            }
            BlockState blockState = mc.world.getBlockState(pos);
            VoxelShape voxelShape = ctx.getBlockShape(blockState, mc.world, pos);
            return mc.world.raycastBlock(start, end, pos, voxelShape, blockState);
        }, ctx -> {
            Vec3d dir = ctx.getStart().subtract(ctx.getEnd());
            return BlockHitResult.createMissed(ctx.getEnd(), Direction.getFacing(dir.x, dir.y, dir.z), BlockPos.ofFloored(ctx.getEnd()));
        });
    }

    public EntityHitResult raytraceEntity(double range, Rotation angle, Predicate<Entity> filter) {
        Entity entity = mc.cameraEntity;
        if (entity == null) return null;

        Vec3d cameraVec = entity.getCameraPosVec(1.0F);
        Vec3d rotationVec = angle.toVector();

        Vec3d vec3d3 = cameraVec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);
        Box box = entity.getBoundingBox().stretch(rotationVec.multiply(range)).expand(1.0, 1.0, 1.0);

        return ProjectileUtil.raycast(entity, cameraVec, vec3d3, box, (e) -> !e.isSpectator() && filter.test(e), range * range);
    }

    public boolean rayTrace(Vec3d clientVec, double range, Box box) {
        Vec3d cameraVec = Objects.requireNonNull(mc.player).getEyePos();
        return box.contains(cameraVec) || box.raycast(cameraVec,cameraVec.add(clientVec.multiply(range))).isPresent();
    }
}