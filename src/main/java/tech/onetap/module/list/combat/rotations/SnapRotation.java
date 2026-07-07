package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

public class SnapRotation extends RotationMode {

    private float snapSavedYaw = 0;
    private float snapSavedPitch = 0;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null || target == null) {
            ka.snapActive = false;
            ka.snapTimer = 0;
            return;
        }

        boolean cooldownReady = mc.player.getAttackCooldownProgress(0.5f) >= 0.98f && ka.ticksToAttack <= 0;

        if (!cooldownReady) {
            ka.snapActive = false;
            ka.snapTimer = 0;
            return;
        }

        if (!ka.snapActive) {
            snapSavedYaw = mc.player.getYaw();
            snapSavedPitch = mc.player.getPitch();
            ka.snapActive = true;
            ka.snapTimer = 0;
        }

        Vec3d targetPoint = ka.resolveMultipoint(target, BestPoint.getNearestPoint(target), ka.distance.getValue());
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            targetPoint = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        var aimRotation = new Rotation(RotationUtil.calculate(targetPoint));
        RotationComponent.update(aimRotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        ka.snapTimer++;
    }

    @Override
    public void reset(KillAura ka) {
        snapSavedYaw = 0;
        snapSavedPitch = 0;
    }
}
