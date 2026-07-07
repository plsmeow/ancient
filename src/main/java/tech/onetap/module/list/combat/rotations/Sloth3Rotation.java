package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Sloth3Rotation extends RotationMode {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (target == null) return;

        List<Vec3d> multiPoints = new ArrayList<>();
        Box bb = target.getBoundingBox();
        double x = target.getX();
        double z = target.getZ();

        multiPoints.add(new Vec3d(x, bb.minY + target.getStandingEyeHeight(), z));
        multiPoints.add(new Vec3d(x, bb.minY + (target.getHeight() * 0.75), z));
        multiPoints.add(new Vec3d(x, bb.minY + (target.getHeight() * 0.5), z));
        multiPoints.add(new Vec3d(x, bb.minY + (target.getHeight() * 0.25), z));

        double width = bb.maxX - bb.minX;
        for (int i = 0; i < 5; i++) {
            double randomX = bb.minX + (Math.random() * width);
            double randomZ = bb.minZ + (Math.random() * width);
            multiPoints.add(new Vec3d(randomX, bb.minY + (target.getHeight() * 0.8), randomZ));
            multiPoints.add(new Vec3d(randomX, bb.minY + (target.getHeight() * 0.4), randomZ));
        }

        Vec3d optimalPoint = multiPoints.get(ThreadLocalRandom.current().nextInt(multiPoints.size()));
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            optimalPoint = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        var aimRotation = new Rotation(RotationUtil.calculate(optimalPoint));

        RotationComponent.update(aimRotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        ka.lastYaw = aimRotation.getYaw();
        ka.lastPitch = aimRotation.getPitch();
    }
}
