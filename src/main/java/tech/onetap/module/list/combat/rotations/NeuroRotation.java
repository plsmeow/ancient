package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.neuro.rotation.NeuroModel;
import tech.onetap.util.neuro.rotation.NeuroRecorder;
import tech.onetap.util.neuro.rotation.NeuroRotationTracker;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Нейросетевая ротация на основе GRU+MDN архитектуры из ROKSTAR.
 * Инференс выполняется на чистом Java без ONNX Runtime.
 *
 * Математика шага модели синхронизирована строго с 20 TPS (1 вызов GRU на игровой тик),
 * а между тиками применяется плавная покадровая субтиковая интерполяция (lerp по tickDelta),
 * обеспечивая плавность на любой герцовке монитора.
 */
public class NeuroRotation extends RotationMode {
    private static final float AIM_OFFSET_SCALE = 0.45f;
    private static final float TEMPERATURE = 0.7f;
    private static final int CANDIDATES = 4;
    private static final float DEFAULT_SPEED = 1.1f;
    private static final float WATER_SPEED = 0.8f;
    private static final int MAX_FREEZE = 12;
    private static final int MAX_RETURN_TICKS = 30;
    private static final float MIN_RETURN_DELTA = 3.0f;

    private final NeuroRotationTracker combatTracker = new NeuroRotationTracker();
    private final NeuroRotationTracker returnTracker = new NeuroRotationTracker();
    private final NeuroRecorder recorder = new NeuroRecorder();
    private final float[] deltaOut = new float[2];

    private int lastTickAge = -1;
    private int lastTargetId = -1;
    private int returnTicks;
    private float currentAimOffset = 0.0f;
    private boolean attacked;
    private boolean modelWarned;

    // Субтиковая интерполяция (20 Hz step -> кадровая интерполяция)
    private float tickStartYaw;
    private float tickStartPitch;
    private float tickTargetYaw;
    private float tickTargetPitch;

    // Переменные для debug панели
    private Vec3d debugAimPoint;
    private long lastInferenceNanos;
    private float lastPredDeltaYaw;
    private float lastPredDeltaPitch;

    /**
     * Целевая точка наведения.
     * Нейросеть обучалась целиться в центр хитбокса (getCenter()), а физиологический
     * разброс и естественные микросмещения обеспечивает встроенный MDN (sampleAimError).
     * Фиксированный центр исключает джиттер и скачки между гранями хитбокса.
     */
    public static Vec3d getAimPoint(KillAura ka, LivingEntity target) {
        if (target == null || mc.player == null) {
            return target != null ? target.getBoundingBox().getCenter() : Vec3d.ZERO;
        }
        if (target.isGliding() && ka != null && ka.isElytraPredictActive() && !ka.isTurnaroundActive) {
            return PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }
        return target.getBoundingBox().getCenter();
    }

    public static Vec3d getAimPoint(LivingEntity target) {
        return getAimPoint(null, target);
    }

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (mc.player == null || target == null) {
            return;
        }

        NeuroModel model = NeuroModel.getActive();
        if (model == null) {
            warnModelNotLoaded();
            Vec3d targetPoint = getAimPoint(ka, target);
            this.debugAimPoint = targetPoint;
            Rotation rotation = new Rotation(RotationUtil.calculate(targetPoint));
            RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), ka.otvodkaActive());
            return;
        }

        int currentAge = mc.player.age;
        boolean isNewTick = (currentAge != this.lastTickAge);

        if (isNewTick) {
            this.lastTickAge = currentAge;

            Box box = target.getBoundingBox();
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d aimPoint = getAimPoint(ka, target);
            this.debugAimPoint = aimPoint;

            Vec3d diff = aimPoint.subtract(eyePos);
            double d = Math.max(Math.hypot(diff.x, diff.z), 0.05);

            float targetYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
            float targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, d)));
            float fovX = Math.max((float) Math.toDegrees(Math.atan2(box.getLengthX() / 2.0, d)), 0.5f);
            float fovY = Math.max((float) Math.toDegrees(Math.atan2(box.getLengthY() / 2.0, d)), 0.5f);
            double dist = NeuroRecorder.distanceToBox(eyePos, box);

            // Начальные углы для нового тика
            float currentBaseYaw = (this.lastTargetId == target.getId() && this.combatTracker.isInitialized())
                    ? this.tickTargetYaw
                    : ((ka.lastYaw != 0 && RotationComponent.getInstance().isRotating()) ? ka.lastYaw : mc.player.getYaw());
            float currentBasePitch = (this.lastTargetId == target.getId() && this.combatTracker.isInitialized())
                    ? this.tickTargetPitch
                    : ((ka.lastPitch != 0 && RotationComponent.getInstance().isRotating()) ? ka.lastPitch : mc.player.getPitch());

            if (target.getId() != this.lastTargetId || !this.combatTracker.isInitialized()) {
                this.combatTracker.init(model, currentBaseYaw, currentBasePitch, targetYaw, targetPitch);
                this.lastTargetId = target.getId();
                this.currentAimOffset = AIM_OFFSET_SCALE * model.sampleAimError(ThreadLocalRandom.current().nextFloat());
                this.tickStartYaw = currentBaseYaw;
                this.tickStartPitch = currentBasePitch;
                this.tickTargetYaw = currentBaseYaw;
                this.tickTargetPitch = currentBasePitch;
            } else {
                this.tickStartYaw = this.tickTargetYaw;
                this.tickStartPitch = this.tickTargetPitch;
            }

            this.returnTicks = 0;
            float aimOffset = this.currentAimOffset;
            float speedMul = getSpeedMultiplier();

            long t0 = System.nanoTime();
            boolean stepped = this.combatTracker.step(
                    model, this.tickStartYaw, this.tickStartPitch, targetYaw, targetPitch,
                    fovX, fovY, dist, aimOffset, MAX_FREEZE, TEMPERATURE, CANDIDATES, speedMul, this.deltaOut);
            this.lastInferenceNanos = System.nanoTime() - t0;

            if (stepped) {
                this.lastPredDeltaYaw = this.deltaOut[0];
                this.lastPredDeltaPitch = this.deltaOut[1];

                this.tickTargetYaw = this.tickStartYaw + this.deltaOut[0];
                this.tickTargetPitch = MathHelper.clamp(this.tickStartPitch + this.deltaOut[1], -90.0f, 90.0f);

                if (this.attacked) {
                    this.attacked = false;
                    this.combatTracker.onAttack();
                    this.currentAimOffset = AIM_OFFSET_SCALE * model.sampleAimError(ThreadLocalRandom.current().nextFloat());
                } else {
                    this.combatTracker.tick();
                }
            }
        }

        // Субтиковая интерполяция: между тиками плавно ведём прицел к tickTarget
        float tickDelta = 1.0f;
        if (mc.getRenderTickCounter() != null) {
            tickDelta = MathHelper.clamp(mc.getRenderTickCounter().getTickDelta(true), 0.0f, 1.0f);
        }

        float interpolatedYaw = MathHelper.lerpAngleDegrees(tickDelta, this.tickStartYaw, this.tickTargetYaw);
        float interpolatedPitch = MathHelper.lerp(tickDelta, this.tickStartPitch, this.tickTargetPitch);

        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0.001f) {
            interpolatedYaw = this.tickStartYaw + Math.round(MathHelper.wrapDegrees(interpolatedYaw - this.tickStartYaw) / gcd) * gcd;
            interpolatedPitch = this.tickStartPitch + Math.round((interpolatedPitch - this.tickStartPitch) / gcd) * gcd;
        }
        interpolatedPitch = MathHelper.clamp(interpolatedPitch, -90.0f, 90.0f);

        Rotation nextRotation = new Rotation(interpolatedYaw, interpolatedPitch);
        RotationComponent.update(nextRotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), ka.otvodkaActive());
        ka.lastYaw = this.tickTargetYaw;
        ka.lastPitch = this.tickTargetPitch;
    }

    public Rotation stepReturn(Rotation current, Rotation target) {
        NeuroModel model = NeuroModel.getActive();
        if (model == null || mc.player == null) {
            return null;
        }
        float gcd = Math.max(MIN_RETURN_DELTA, GCDFixer.getGCDValue());
        if (Math.abs(MathHelper.wrapDegrees(target.getYaw() - current.getYaw())) <= gcd
                && Math.abs(target.getPitch() - current.getPitch()) <= gcd) {
            this.returnTracker.reset();
            this.returnTicks = 0;
            return null;
        }
        if (++this.returnTicks > MAX_RETURN_TICKS) {
            this.returnTracker.reset();
            return null;
        }
        if (!this.returnTracker.isInitialized()) {
            this.returnTracker.init(model, current.getYaw(), current.getPitch(), target.getYaw(), target.getPitch());
        }
        if (!this.returnTracker.step(
                model, current.getYaw(), current.getPitch(), target.getYaw(), target.getPitch(),
                5.0f, 15.0f, 3.0, 0.0f, MAX_FREEZE, TEMPERATURE, CANDIDATES, DEFAULT_SPEED, this.deltaOut)) {
            return null;
        }
        this.returnTracker.tick();
        return new Rotation(
                current.getYaw() + this.deltaOut[0],
                MathHelper.clamp(current.getPitch() + this.deltaOut[1], -90.0f, 90.0f)
        );
    }

    private float getSpeedMultiplier() {
        return mc.player != null && mc.player.isSubmergedInWater() ? WATER_SPEED : DEFAULT_SPEED;
    }

    public void attack() {
        this.attacked = true;
    }

    @Override
    public void reset(KillAura ka) {
        super.reset(ka);
        this.combatTracker.reset();
        this.returnTracker.reset();
        this.lastTickAge = -1;
        this.lastTargetId = -1;
        this.currentAimOffset = 0.0f;
        this.attacked = false;
        this.modelWarned = false;
        this.debugAimPoint = null;
    }

    private void warnModelNotLoaded() {
        if (this.modelWarned) {
            return;
        }
        this.modelWarned = true;
        ChatUtil.send("§cМодель " + NeuroModel.getActiveName() + " не загрузилась — обучи через .neuro train или выбери другую: .neuro list");
    }

    public NeuroRecorder getRecorder() {
        return this.recorder;
    }

    public boolean isModelLoaded() {
        return NeuroModel.getActive() != null;
    }

    public Vec3d getDebugAimPoint() {
        return this.debugAimPoint;
    }

    public long getDebugInferenceNanos() {
        return this.lastInferenceNanos;
    }

    public float getDebugPredYaw() {
        return this.lastPredDeltaYaw;
    }

    public float getDebugPredPitch() {
        return this.lastPredDeltaPitch;
    }

    public float getDebugAimOffset() {
        return this.currentAimOffset;
    }

    public int getDebugFrozenTicks() {
        return (int) this.combatTracker.getFrozenTicks();
    }

    public float getDebugYawError() {
        return this.combatTracker.getLastYawError();
    }

    public float getDebugPitchError() {
        return this.combatTracker.getLastPitchError();
    }

    public NeuroRotationTracker getCombatTracker() {
        return this.combatTracker;
    }
}
