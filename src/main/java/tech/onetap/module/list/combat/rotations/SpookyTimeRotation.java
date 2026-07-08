package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.concurrent.ThreadLocalRandom;

public class SpookyTimeRotation extends RotationMode {

    private static final int HEAD = 0;
    private static final int TORSO = 1;
    private static final int LEGS = 2;

    private LivingEntity trackedTarget;
    private int tick;

    private int currentZone = TORSO;
    private int targetZone = HEAD;
    private float zoneProgress;
    private int zoneTimer;
    private Vec3d dynamicHeadPoint;
    private Vec3d dynamicTorsoPoint;
    private Vec3d dynamicLegsPoint;

    private float noisePhase;
    private float noiseYaw;
    private float noisePitch;

    private int overshootTicks;
    private float overshootYaw;
    private float overshootPitch;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null || target == null) {
            return;
        }

        if (mc.player.isBlocking()) {
            ka.lastYaw = mc.player.getYaw();
            ka.lastPitch = mc.player.getPitch();
            return;
        }

        if (trackedTarget != target) {
            trackedTarget = target;
            tick = 0;
            currentZone = ThreadLocalRandom.current().nextInt(3);
            do {
                targetZone = ThreadLocalRandom.current().nextInt(3);
            } while (targetZone == currentZone);
            zoneProgress = 0.0F;
            zoneTimer = 20 + ThreadLocalRandom.current().nextInt(30);
            dynamicHeadPoint = null;
            dynamicTorsoPoint = null;
            dynamicLegsPoint = null;
            noisePhase = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0);
            noiseYaw = 0.0F;
            noisePitch = 0.0F;
            overshootTicks = 0;
            overshootYaw = 0.0F;
            overshootPitch = 0.0F;
            ka.lastYaw = mc.player.getYaw();
            ka.lastPitch = mc.player.getPitch();
        }

        tick++;
        Box box = target.getBoundingBox();
        double dist = mc.player.getEyePos().distanceTo(box.getCenter());

        refreshZonePoints(box);

        zoneTimer--;
        if (zoneTimer <= 0) {
            currentZone = targetZone;
            do {
                targetZone = ThreadLocalRandom.current().nextInt(3);
            } while (targetZone == currentZone);
            zoneProgress = 0.0F;
            zoneTimer = 25 + ThreadLocalRandom.current().nextInt(35);
        }

        float transSpeed = 0.025F + ThreadLocalRandom.current().nextFloat() * 0.02F;
        zoneProgress = Math.min(1.0F, zoneProgress + transSpeed);
        float ease = zoneProgress * zoneProgress * (3.0F - 2.0F * zoneProgress);

        Vec3d srcPoint = getZonePoint(currentZone);
        Vec3d dstPoint = getZonePoint(targetZone);
        Vec3d aimPoint;

        if (zoneProgress < 1.0F) {
            aimPoint = new Vec3d(
                    MathHelper.lerp(ease, srcPoint.x, dstPoint.x),
                    MathHelper.lerp(ease, srcPoint.y, dstPoint.y),
                    MathHelper.lerp(ease, srcPoint.z, dstPoint.z)
            );
        } else {
            aimPoint = dstPoint;
        }

        noisePhase += 0.1F + ThreadLocalRandom.current().nextFloat() * 0.04F;
        noiseYaw += ((float) (Math.sin(noisePhase) * 0.35
                + Math.sin(noisePhase * 2.4F) * 0.15
                + Math.cos(noisePhase * 0.6F) * 0.1) - noiseYaw) * 0.1F;
        noisePitch += ((float) (Math.cos(noisePhase * 1.3F) * 0.25
                + Math.cos(noisePhase * 3.7F) * 0.1
                + Math.sin(noisePhase * 0.8F) * 0.08) - noisePitch) * 0.1F;

        aimPoint = aimPoint.add(noiseYaw, noisePitch, 0.0);
        aimPoint = getPredictedPoint(ka, target, aimPoint);

        var angle = RotationUtil.calculate(aimPoint);
        float targetYaw = angle.x;
        float targetPitch = angle.y;

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - ka.lastYaw));
        boolean ready = mc.player.getAttackCooldownProgress(1.0F) > 0.9F && ka.ticksToAttack <= 1;

        float aggression = (float) MathHelper.clamp(1.0 - (dist - 1.0) / 6.0, 0.2, 1.0);
        float baseSpeed = 0.025F + aggression * 0.06F;

        if (yawDiff > 60.0F) {
            baseSpeed = Math.max(baseSpeed, 0.2F);
        } else if (yawDiff > 30.0F) {
            baseSpeed = Math.max(baseSpeed, 0.12F);
        } else if (yawDiff > 10.0F) {
            baseSpeed = Math.max(baseSpeed, 0.06F);
        }

        if (ready) {
            baseSpeed *= 1.2F;
        }

        if (ThreadLocalRandom.current().nextFloat() < 0.03F) {
            baseSpeed *= 0.3F + ThreadLocalRandom.current().nextFloat() * 0.4F;
        }

        float speed = MathHelper.clamp(baseSpeed, 0.008F, 0.3F);

        if (overshootTicks > 0) {
            overshootTicks--;
            overshootYaw *= 0.75F;
            overshootPitch *= 0.75F;
        } else if (ready && ThreadLocalRandom.current().nextFloat() < 0.025F) {
            overshootTicks = 2 + ThreadLocalRandom.current().nextInt(3);
            overshootYaw = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.6F;
            overshootPitch = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.25F;
        }

        float microYaw = (float) (Math.sin(tick * 0.19F) * 0.015
                + Math.sin(tick * 0.47F) * 0.008
                + Math.cos(tick * 0.31F) * 0.012);
        float microPitch = (float) (Math.cos(tick * 0.23F) * 0.012
                + Math.cos(tick * 0.53F) * 0.006
                + Math.sin(tick * 0.37F) * 0.01);

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - ka.lastYaw) * speed
                + microYaw + overshootYaw + noiseYaw * 0.02F;
        float deltaPitch = (targetPitch - ka.lastPitch) * speed
                + microPitch + overshootPitch + noisePitch * 0.02F;

        float maxYawDelta = ready ? 32.0F : 18.0F;
        float maxPitchDelta = ready ? 4.5F : 2.5F;
        deltaYaw = MathHelper.clamp(deltaYaw, -maxYawDelta, maxYawDelta);
        deltaPitch = MathHelper.clamp(deltaPitch, -maxPitchDelta, maxPitchDelta);

        float newYaw = ka.lastYaw + deltaYaw;
        float newPitch = ka.lastPitch + deltaPitch;

        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0.0F) {
            newYaw = ka.lastYaw + Math.round((newYaw - ka.lastYaw) / gcd) * gcd;
            newPitch = ka.lastPitch + Math.round((newPitch - ka.lastPitch) / gcd) * gcd;
        }

        newPitch = MathHelper.clamp(newPitch, -89.0F, 89.0F);

        var rot = new Rotation(newYaw, newPitch);
        float rotSpeed = mc.player.isGliding() && target.isGliding() ? 360.0F : 35.0F;
        RotationComponent.update(rot, rotSpeed, rotSpeed, rotSpeed, rotSpeed, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        ka.lastYaw = rot.getYaw();
        ka.lastPitch = rot.getPitch();
    }

    @Override
    public void reset(KillAura ka) {
        trackedTarget = null;
        tick = 0;
        currentZone = TORSO;
        targetZone = HEAD;
        zoneProgress = 0.0F;
        zoneTimer = 0;
        dynamicHeadPoint = null;
        dynamicTorsoPoint = null;
        dynamicLegsPoint = null;
        noisePhase = 0.0F;
        noiseYaw = 0.0F;
        noisePitch = 0.0F;
        overshootTicks = 0;
        overshootYaw = 0.0F;
        overshootPitch = 0.0F;
        if (ka.mc.player != null) {
            ka.lastYaw = ka.mc.player.getYaw();
            ka.lastPitch = ka.mc.player.getPitch();
        }
    }

    private Vec3d getPredictedPoint(KillAura ka, LivingEntity target, Vec3d point) {
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            Vec3d predictedCenter = PredictUtils.getPredicted(target, ka.predictValue.getValue());
            Vec3d currentCenter = target.getPos().add(0.0, target.getHeight(), 0.0);
            return point.add(predictedCenter.subtract(currentCenter));
        }
        return ka.resolveMultipoint(target, point, 6.0);
    }

    private Vec3d getZonePoint(int zone) {
        return switch (zone) {
            case HEAD -> dynamicHeadPoint;
            case TORSO -> dynamicTorsoPoint;
            default -> dynamicLegsPoint;
        };
    }

    private void refreshZonePoints(Box box) {
        dynamicHeadPoint = sampleZonePoint(box, HEAD);
        dynamicTorsoPoint = sampleZonePoint(box, TORSO);
        dynamicLegsPoint = sampleZonePoint(box, LEGS);
    }

    private Vec3d sampleZonePoint(Box box, int zone) {
        double h = box.maxY - box.minY;
        double w = box.maxX - box.minX;
        double d = box.maxZ - box.minZ;

        double minY;
        double maxY;
        switch (zone) {
            case HEAD -> {
                minY = box.minY + h * 0.7;
                maxY = box.maxY;
            }
            case TORSO -> {
                minY = box.minY + h * 0.3;
                maxY = box.minY + h * 0.75;
            }
            case LEGS -> {
                minY = box.minY;
                maxY = box.minY + h * 0.35;
            }
            default -> {
                minY = box.minY;
                maxY = box.maxY;
            }
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            double px = box.minX + ThreadLocalRandom.current().nextDouble() * w;
            double py = minY + ThreadLocalRandom.current().nextDouble() * (maxY - minY);
            double pz = box.minZ + ThreadLocalRandom.current().nextDouble() * d;
            Vec3d point = new Vec3d(px, py, pz);
            Vec3d direction = point.subtract(kaEye()).normalize();
            double range = kaEye().distanceTo(point) + 0.2;
            if (RaytraceUtil.rayTrace(direction, range, box)) {
                return point;
            }
        }

        return new Vec3d(
                box.minX + w / 2.0,
                box.minY + h * switch (zone) {
                    case HEAD -> 0.85;
                    case TORSO -> 0.5;
                    default -> 0.18;
                },
                box.minZ + d / 2.0
        );
    }

    private Vec3d kaEye() {
        return tech.onetap.util.IMinecraft.mc.player.getEyePos();
    }
}
