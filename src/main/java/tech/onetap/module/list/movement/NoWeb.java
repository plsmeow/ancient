package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventCobweb;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "No Web", moduleCategory = ModuleCategory.MOVEMENT)
public class NoWeb extends Module {

    private final SliderSetting strength = new SliderSetting("Strength", 0.8, 0.3, 1.05, 0.05);
    private final SliderSetting upSpeed = new SliderSetting("Up Speed", 0.35, 0.05, 2.0, 0.05);
    private final SliderSetting downSpeed = new SliderSetting("Down Speed", 0.35, 0.05, 2.0, 0.05);

    @Subscribe
    private void onCobweb(EventCobweb e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isSpectator() || mc.player.getAbilities().flying) return;
        if (!isInsideCobweb()) return;

        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        boolean up = mc.options.jumpKey.isPressed();
        boolean down = mc.options.sneakKey.isPressed();
        if (forward == 0.0f && strafe == 0.0f && !up && !down) return;

        Vec3d dir = inputDirection(forward, strafe);

        Vec3d v = mc.player.getVelocity();
        double targetSpeed = 0.2873 * strength.getValue();
        double y = up ? upSpeed.getValue() : down ? -downSpeed.getValue() : 0.0;
        mc.player.setVelocity(dir.x * targetSpeed, y, dir.z * targetSpeed);
        mc.player.fallDistance = 0;
    }

    private Vec3d inputDirection(float forward, float strafe) {
        float f = MathHelper.clamp(forward, -1.0f, 1.0f);
        float s = MathHelper.clamp(strafe, -1.0f, 1.0f);
        double rad = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = s * cos - f * sin;
        double z = s * sin + f * cos;
        double len = Math.sqrt(x * x + z * z);

        if (len < 1.0E-6) return Vec3d.ZERO;
        return new Vec3d(x / len, 0.0, z / len);
    }

    private boolean isInsideCobweb() {
        Box bb = mc.player.getBoundingBox();

        int minX = MathHelper.floor(bb.minX);
        int maxX = MathHelper.floor(bb.maxX);
        int minY = MathHelper.floor(bb.minY);
        int maxY = MathHelper.floor(bb.maxY);
        int minZ = MathHelper.floor(bb.minZ);
        int maxZ = MathHelper.floor(bb.maxZ);

        BlockPos.Mutable p = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    p.set(x, y, z);

                    if (mc.world.getBlockState(p).getBlock() == Blocks.COBWEB) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
