package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.MathHelper;
import tech.onetap.Onetap;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "GrimStrafe", moduleDesc = "Стрейф на земле через поворот yaw", moduleCategory = ModuleCategory.MOVEMENT)
public class GrimStrafe extends Module {

    @Subscribe
    private void onMoveInput(MoveInputEvent e) {
        if (mc.player == null) return;

        KillAura aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        if (aura != null && aura.isEnabled() && (aura.getTarget() != null || aura.isSnapActive())) return;

        float forward = e.forward;
        float strafe = e.strafe;
        if (forward == 0 && strafe == 0) return;

        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float moveYaw = cameraYaw;

        if (forward != 0) {
            if (strafe > 0) moveYaw += (forward > 0) ? -45 : 45;
            else if (strafe < 0) moveYaw += (forward > 0) ? 45 : -45;
        } else {
            if (strafe > 0) moveYaw -= 90;
            else if (strafe < 0) moveYaw += 90;
        }
        if (forward < 0) moveYaw += 180;

        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0) {
            moveYaw = mc.player.getYaw() + Math.round((moveYaw - mc.player.getYaw()) / gcd) * gcd;
        }

        mc.player.setYaw(moveYaw);
        mc.player.headYaw = moveYaw;
        mc.player.bodyYaw = moveYaw;

        RotationComponent.update(
                new Rotation(moveYaw, 0),
                360, 360, 360, 360,
                0, 0, false
        );

        RotationComponent.fixMovement(e, moveYaw);

        e.forward = 1f;
        e.strafe = 0f;
    }
}
