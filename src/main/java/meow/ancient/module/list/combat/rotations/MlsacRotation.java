package meow.ancient.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.util.IMinecraft;
import meow.ancient.util.math.RotationUtil;
import meow.ancient.util.player.combat.PredictUtils;
import meow.ancient.util.player.combat.RaytraceUtil;
import meow.ancient.util.render.math.GCDFixer;
import meow.ancient.util.rotation.Rotation;
import meow.ancient.util.rotation.RotationHelper;

import java.util.Random;

public class MlsacRotation extends RotationMode implements IMinecraft {

    private final Random rng = new Random();

    private LivingEntity tracked;
    private boolean initialized;

    private float velYaw;
    private float velPitch;

    private float tremorYaw;
    private float tremorPitch;

    private float residualYaw;
    private float residualPitch;

    public static Vec3d getAimPoint(KillAura ka, LivingEntity target) {
        if (target == null || mc.player == null) {
            return target != null ? target.getBoundingBox().getCenter() : Vec3d.ZERO;
        }
        if (target.isGliding() && ka != null && ka.isElytraPredictActive() && !ka.isTurnaroundActive) {
            return PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }
        if (ka != null && (ka.extrapolation.getValue() || ka.backtrack.getValue())) {
            return ka.getTargetCenter(target);
        }
        Box box = target.getBoundingBox();
        double chestY = box.minY + (box.maxY - box.minY) * 0.65;
        return new Vec3d((box.minX + box.maxX) / 2.0, chestY, (box.minZ + box.maxZ) / 2.0);
    }

    public boolean isReadyForAttack(KillAura ka, LivingEntity target) {
        if (mc.player == null || target == null) return false;
        Box box = (ka.extrapolation.getValue() || ka.backtrack.getValue()) ? ka.getTargetBox(target) : target.getBoundingBox();
        if (box == null) box = target.getBoundingBox();
        Vec3d lookVec = new Rotation(ka.lastYaw, ka.lastPitch).toVector();
        return RaytraceUtil.rayTrace(lookVec, ka.distance.getValue() + 0.2, box);
    }

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (mc.player == null || target == null) return;

        Vec3d aimPoint = getAimPoint(ka, target);
        Rotation aim = new Rotation(RotationUtil.calculate(aimPoint));
        float aimYaw = aim.getYaw();
        float aimPitch = aim.getPitch();

        if (tracked != target || !initialized) {
            tracked = target;
            initialized = true;
            velYaw = 0.0f;
            velPitch = 0.0f;
            tremorYaw = 0.0f;
            tremorPitch = 0.0f;
            residualYaw = 0.0f;
            residualPitch = 0.0f;
            ka.lastYaw = mc.player.getYaw();
            ka.lastPitch = mc.player.getPitch();
        }

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float errYaw = RotationHelper.angleDelta(curYaw, aimYaw);
        float errPitch = aimPitch - curPitch;
        float dist = (float) Math.hypot(errYaw, errPitch);

        float gcd = GCDFixer.getGCDValue();
        if (gcd <= 0.001f) {
            gcd = 0.015f;
        }

        Box box = (ka.extrapolation.getValue() || ka.backtrack.getValue()) ? ka.getTargetBox(target) : target.getBoundingBox();
        if (box == null) box = target.getBoundingBox();

        Vec3d lookVec = new Rotation(curYaw, curPitch).toVector();
        boolean onTarget = RaytraceUtil.rayTrace(lookVec, ka.distance.getValue() + 0.3, box);

        float rho = 0.70f;
        float rhoScale = (float) Math.sqrt(1.0f - rho * rho);
        tremorYaw = rho * tremorYaw + rhoScale * (float) (rng.nextGaussian() * gcd * 0.40f);
        tremorPitch = rho * tremorPitch + rhoScale * (float) (rng.nextGaussian() * gcd * 0.30f);

        float planYaw;
        float planPitch;

        if (onTarget) {
            velYaw *= 0.35f;
            velPitch *= 0.35f;
            planYaw = velYaw + tremorYaw;
            planPitch = velPitch + tremorPitch;
        } else {
            float desiredSpeed = Math.min(65.0f, Math.max(12.0f, dist * 0.75f));

            float dirYaw = errYaw / Math.max(dist, 0.001f);
            float dirPitch = errPitch / Math.max(dist, 0.001f);

            float targetVelY = dirYaw * desiredSpeed;
            float targetVelP = dirPitch * desiredSpeed;

            velYaw += (targetVelY - velYaw) * 0.65f;
            velPitch += (targetVelP - velPitch) * 0.65f;

            planYaw = velYaw + tremorYaw;
            planPitch = velPitch + tremorPitch;
        }

        residualYaw += planYaw;
        residualPitch += planPitch;

        int stepsYaw = Math.round(residualYaw / gcd);
        if (stepsYaw != 0) {
            residualYaw -= stepsYaw * gcd;
            curYaw += stepsYaw * gcd;
        }

        int stepsPitch = Math.round(residualPitch / gcd);
        if (stepsPitch != 0) {
            residualPitch -= stepsPitch * gcd;
            curPitch = RotationHelper.clampPitch(curPitch + stepsPitch * gcd);
        }

        Rotation out = new Rotation(curYaw, curPitch);
        RotationHelper.apply(out, ka);

        ka.lastYaw = curYaw;
        ka.lastPitch = curPitch;
    }

    @Override
    public void reset(KillAura ka) {
        tracked = null;
        initialized = false;
        velYaw = 0.0f;
        velPitch = 0.0f;
        tremorYaw = 0.0f;
        tremorPitch = 0.0f;
        residualYaw = 0.0f;
        residualPitch = 0.0f;
    }
}
