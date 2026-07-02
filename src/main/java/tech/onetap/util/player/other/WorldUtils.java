package tech.onetap.util.player.other;

import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.IMinecraft;

import java.util.List;
import java.util.function.Predicate;

@UtilityClass
public class WorldUtils implements IMinecraft {
    public boolean isInWeb() {
        Box box = mc.player.getBoundingBox();

        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);

        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (state.isOf(Blocks.COBWEB)) {
                        if (state.getOutlineShape(mc.world, pos)
                                .getBoundingBoxes()
                                .stream()
                                .anyMatch(shape -> shape.offset(pos).intersects(box))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isBoxInBlock(Box box, Block block) {
        return isBox(box, pos -> mc.world.getBlockState(pos).getBlock().equals(block));
    }

    public boolean isBoxInBlocks(Box box, List<Block> blocks) {
        return isBox(box, pos -> blocks.contains(mc.world.getBlockState(pos).getBlock()));
    }

    public boolean isBox(Box box, Predicate<BlockPos> pos) {
        return BlockPos.stream(box).anyMatch(pos);
    }

    public static BlockPos findNearestPlaceableBlock() {
        Vec3d playerPos = mc.player.getPos();
        BlockPos feetBlock = mc.player.getBlockPos();

        int placementY = feetBlock.getY() - 1;

        BlockPos[] candidates = new BlockPos[] {
                new BlockPos(feetBlock.getX() - 1, placementY, feetBlock.getZ()),
                new BlockPos(feetBlock.getX() + 1, placementY, feetBlock.getZ()),
                new BlockPos(feetBlock.getX(), placementY, feetBlock.getZ() - 1),
                new BlockPos(feetBlock.getX(), placementY, feetBlock.getZ() + 1)
        };

        Box playerBox = mc.player.getBoundingBox();

        BlockPos nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos candidate : candidates) {

            Box blockBox = new Box(candidate);

            if (playerBox.expand(0.15f).intersects(blockBox)) {
                continue;
            }

            Vec3d blockCenter = Vec3d.ofCenter(candidate.up());
            double distance = playerPos.distanceTo(blockCenter);

            if (distance < minDistance) {
                minDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }
}