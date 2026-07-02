package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

public class GrimFunRotation extends RotationMode {

    private boolean pitchDown;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null || target == null) return;

        Vec3d targetPoint = ka.resolveMultipoint(target, BestPoint.getNearestPoint(target), ka.distance.getValue());
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            targetPoint = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        Rotation aim = new Rotation(RotationUtil.calculate(targetPoint));
        boolean cooldownReady = mc.player.getAttackCooldownProgress(0.5f) >= 0.98f && ka.ticksToAttack <= 0;

        float pitch;
        if (cooldownReady) {
            pitch = aim.getPitch();
        } else {
            pitch = pitchDown ? -90.0f : 90.0f;
            pitchDown = !pitchDown;
        }

        Rotation rotation = new Rotation(aim.getYaw(), pitch);
        RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue());
        ka.lastYaw = rotation.getYaw();
        ka.lastPitch = rotation.getPitch();
    }

    @Override
    public void reset(KillAura ka) {
        pitchDown = false;
    }
}
