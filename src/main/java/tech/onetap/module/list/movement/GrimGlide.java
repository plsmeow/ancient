package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

import tech.onetap.util.render.math.MathUtil;


@ModuleInformation(moduleName = "GrimGlide", moduleCategory = ModuleCategory.MOVEMENT, moduleDesc = ":/")
public class GrimGlide extends Module {
    private int tickCounter = 0;
    private int ticksTwo = 0;

    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null || !mc.player.isGliding()) {
            return;
        }
        ticksTwo++;
        Vec3d pos = mc.player.getPos();

        float yaw = mc.player.getYaw();
        double forward = 0.087;
        double motion = MathUtil.getBps(mc.player);

            float valuePidor = 52;
        if (motion >= valuePidor) {
            forward = 0.0;
            motion = 0.0;
        }

        double dx = -Math.sin(Math.toRadians(yaw)) * forward;
        double dz = Math.cos(Math.toRadians(yaw)) * forward;
        mc.player.setVelocity(dx * MathUtil.random(1.1f, 1.21f), mc.player.getVelocity().y - 0.02f, dz * MathUtil.random(1.1f, 1.21f));

        if (ticksTwo >= 50) {
            mc.player.setPosition(pos.x + dx, pos.y, pos.z + dz);
            ticksTwo = 0;
        }
        mc.player.setVelocity(dx * MathUtil.random(1.1f, 1.21f), mc.player.getVelocity().y + 0.016f, dz * MathUtil.random(1.1f, 1.21f));
    }
}
