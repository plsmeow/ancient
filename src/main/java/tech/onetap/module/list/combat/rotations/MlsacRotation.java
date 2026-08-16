package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.Random;

public class MlsacRotation extends RotationMode implements IMinecraft {

    private static final float RESP_Y = 0.46f;
    private static final float INERTIA_Y = 0.66f;
    private static final float K_P = 0.11f;
    private static final float DAMP_P = 0.05f;
    private static final float RHO_Y = 0.52f;
    private static final float RHO_P = 0.66f;
    private static final float SIG_Y = 3.4f;
    private static final float SIG_P = 0.95f;
    private static final float SETTLE_NOISE = 0.9f;
    private static final float EXC_INERTIA = 0.86f;
    private static final float EXC_SIG = 1.5f;
    private static final float EXC_PULL = 0.018f;
    private static final float EXC_CAP = 42f;
    private static final float JUKE_MIN = 10f;
    private static final float JUKE_MAX = 40f;
    private static final float EXC_SIG_P = 0.32f;
    private static final float EXC_PULL_P = 0.05f;
    private static final float EXC_CAP_P = 9f;
    private static final float CONVERGE_START = 0.80f;
    private static final float CONVERGE_SPAN = 0.18f;

    private final Random rng = new Random();

    private LivingEntity tracked;
    private boolean initialized;
    private float velYaw;
    private float velPitch;
    private float noiseYaw;
    private float noisePitch;
    private float excYaw;
    private float excPitch;
    private float excVelYaw;
    private int jukeInYaw;
    private int jukeInPitch;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (mc.player == null || target == null) return;

        Vec3d point = target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive
                ? PredictUtils.getPredicted(target, ka.predictValue.getValue())
                : ka.resolveMultipoint(target, BestPoint.getPoint(target), 6);

        Rotation aim = new Rotation(RotationUtil.calculate(point));
        float aimYaw = aim.getYaw();
        float aimPitch = aim.getPitch();

        if (tracked != target || !initialized) {
            tracked = target;
            initialized = true;
            velYaw = velPitch = 0f;
            noiseYaw = noisePitch = 0f;
            excYaw = excPitch = excVelYaw = 0f;
            jukeInYaw = 20 + rng.nextInt(26);
            jukeInPitch = 24 + rng.nextInt(35);
            ka.lastYaw = mc.player.getYaw();
            ka.lastPitch = mc.player.getPitch();
        }

        float curYaw = ka.lastYaw;
        float curPitch = ka.lastPitch;

        boolean inRange = mc.player.getEyePos().distanceTo(BestPoint.getNearestPoint(target)) <= ka.distance.getValue();
        float cd = MathHelper.clamp(mc.player.getAttackCooldownProgress(0.5f), 0f, 1f);
        float converge = inRange ? MathHelper.clamp((cd - CONVERGE_START) / CONVERGE_SPAN, 0f, 1f) : 0f;

        excVelYaw = EXC_INERTIA * excVelYaw + (float) rng.nextGaussian() * EXC_SIG;
        excYaw += excVelYaw - excYaw * EXC_PULL;
        if (--jukeInYaw <= 0 && converge < 0.4f) {
            excYaw += (rng.nextBoolean() ? 1f : -1f) * (JUKE_MIN + rng.nextFloat() * (JUKE_MAX - JUKE_MIN));
            jukeInYaw = 20 + rng.nextInt(26);
        }
        excYaw = MathHelper.clamp(excYaw, -EXC_CAP, EXC_CAP);

        excPitch += (float) rng.nextGaussian() * EXC_SIG_P - excPitch * EXC_PULL_P;
        if (--jukeInPitch <= 0 && converge < 0.4f) {
            excPitch += (rng.nextBoolean() ? 1f : -1f) * (4f + rng.nextFloat() * 7f);
            jukeInPitch = 24 + rng.nextInt(35);
        }
        excPitch = MathHelper.clamp(excPitch, -EXC_CAP_P, EXC_CAP_P);

        float tgtYaw = MathHelper.wrapDegrees(aimYaw + excYaw * (1f - converge));
        float tgtPitch = MathHelper.clamp(aimPitch + excPitch * (1f - converge), -90f, 90f);

        float errYaw = MathHelper.wrapDegrees(tgtYaw - curYaw);
        float errPitch = tgtPitch - curPitch;

        velYaw += (errYaw * RESP_Y - velYaw) * (1f - INERTIA_Y);
        velPitch += errPitch * K_P - velPitch * DAMP_P;

        float noiseAmp = 1f - converge * SETTLE_NOISE;
        noiseYaw = RHO_Y * noiseYaw + (float) rng.nextGaussian() * SIG_Y * noiseAmp;
        noisePitch = RHO_P * noisePitch + (float) rng.nextGaussian() * SIG_P * noiseAmp;

        float emitYaw = velYaw + noiseYaw;
        float emitPitch = velPitch + noisePitch;

        if (converge > 0f) {
            float directYaw = MathHelper.wrapDegrees(aimYaw - curYaw);
            float directPitch = aimPitch - curPitch;
            emitYaw = emitYaw * (1f - converge) + directYaw * converge;
            emitPitch = emitPitch * (1f - converge) + directPitch * converge;
        }

        emitYaw = MathHelper.clamp(emitYaw, -90f, 90f);
        emitPitch = MathHelper.clamp(emitPitch, -30f, 30f);

        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0f) {
            emitYaw = Math.round(emitYaw / gcd) * gcd;
            emitPitch = Math.round(emitPitch / gcd) * gcd;
        }

        float newYaw = curYaw + emitYaw;
        float newPitch = MathHelper.clamp(curPitch + emitPitch, -90f, 90f);

        Rotation out = new Rotation(newYaw, newPitch);
        RotationComponent.update(out, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        ka.lastYaw = out.getYaw();
        ka.lastPitch = out.getPitch();
    }

    @Override
    public void reset(KillAura ka) {
        tracked = null;
        initialized = false;
        velYaw = velPitch = 0f;
        noiseYaw = noisePitch = 0f;
        excYaw = excPitch = excVelYaw = 0f;
    }
}
