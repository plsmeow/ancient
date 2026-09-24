package meow.ancient.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.util.math.BestPoint;
import meow.ancient.util.math.RotationUtil;
import meow.ancient.util.player.combat.PredictUtils;
import meow.ancient.util.rotation.MoveFixMode;
import meow.ancient.util.rotation.Rotation;
import meow.ancient.util.rotation.RotationComponent;

public class GrimFunRotation extends RotationMode {

    private boolean pitchDown;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null || target == null) return;

        Vec3d targetPoint = ka.resolveMultipoint(target, BestPoint.getNearestPoint(target), ka.distance.getValue());
        if (target.isGliding() && ka.isElytraPredictActive() && !ka.isTurnaroundActive) {
            targetPoint = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        Rotation aim = new Rotation(RotationUtil.calculate(targetPoint));
        boolean cooldownReady = mc.player.getAttackCooldownProgress(0.5f) >= 0.98f && ka.ticksToAttack <= 0;

        float pitch;
        if (cooldownReady) {
            pitch = aim.getPitch();
        } else {
            pitch = pitchDown ? -80.0f : 90.0f;
            pitchDown = !pitchDown;
        }

        Rotation rotation = new Rotation(aim.getYaw(), pitch);
        RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode());
        ka.lastYaw = rotation.getYaw();
        ka.lastPitch = rotation.getPitch();
    }

    @Override
    public void reset(KillAura ka) {
        pitchDown = false;
    }
}
