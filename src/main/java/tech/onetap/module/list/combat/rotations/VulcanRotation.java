package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

public class VulcanRotation extends RotationMode {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null || target == null) return;

        boolean isAttackReady = mc.player.getAttackCooldownProgress(0.5f) >= 0.9f;

        if (!isAttackReady) {
            RotationComponent.update(new Rotation(mc.player.getYaw(), mc.player.getPitch()), 360, 360, 360, 360, 0, 1, false, ka.getMoveFixMode(), "KillAura");
            return;
        }

        Vec3d targetPos = target.getEyePos();
        Vec3d eyePos = mc.player.getEyePos();

        double deltaX = targetPos.x - eyePos.x;
        double deltaY = targetPos.y - eyePos.y;
        double deltaZ = targetPos.z - eyePos.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(deltaY, distance)));

        targetPitch = MathHelper.clamp(targetPitch, -89.9f, 89.9f);

        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0.0f) {
            targetYaw = mc.player.getYaw() + (float) Math.round((targetYaw - mc.player.getYaw()) / gcd) * gcd;
            targetPitch = mc.player.getPitch() + (float) Math.round((targetPitch - mc.player.getPitch()) / gcd) * gcd;
        }

        var VulcanRot = new Rotation(targetYaw, targetPitch);
        RotationComponent.update(VulcanRot, 360, 360, 360, 360, 0, 1, false, ka.getMoveFixMode(), "KillAura");
    }
}
