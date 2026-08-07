package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.neuro.rotation.*;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

/**
 * AI-ротация на ONNX-модели.
 *
 * Ключевое: канонический такт — игровой тик (20 Гц). Модель опрашивается ровно
 * раз в тик, результат кладётся в бюджет дельты. Вызовы из EventGameUpdate
 * (до 240 за кадр) только выплачивают долю бюджета — без inference,
 * без сбора фич и без аллокаций.
 *
 * При отсутствии/несовместимости модели, низком confidence, исключении или
 * невалидном предсказании управление уходит в NoRotRotation (§23).
 */
public class NeuroRotation extends RotationMode {

    private static final float MAX_DELTA_YAW = 75.0f;
    private static final float MAX_DELTA_PITCH = 55.0f;
    private static final float MIN_CONFIDENCE = 0.15f;

    /** Причина отката в fallback — для debug-панели. */
    public enum FallbackReason {
        NONE,
        NO_MODEL,
        AIM_POINT,
        INFERENCE,
        INVALID_OUTPUT,
        LOW_CONFIDENCE
    }

    private final NeuroFeatureCollector collector = new NeuroFeatureCollector();
    private final AimPointController aimController = new AimPointController();
    private final RotationHistory history = new RotationHistory(
            NeuroFeatureSchema.SEQ_LEN, NeuroFeatureSchema.FEATURE_COUNT);
    private final NeuroRotationController controller = new NeuroRotationController();
    private final NoRotRotation noRotFallback = new NoRotRotation();

    /** Преаллоцированные буферы — переиспользуются каждый тик. */
    private final float[] featureRow = new float[NeuroFeatureSchema.FEATURE_COUNT];
    private final float[] flatInput =
            new float[NeuroFeatureSchema.SEQ_LEN * NeuroFeatureSchema.FEATURE_COUNT];

    private LivingEntity lastTarget = null;
    private long lastTickHandled = -1;
    private float prevDeltaYaw = 0.0f;
    private float prevDeltaPitch = 0.0f;
    private boolean fallbackActive = false;

    /** Диагностика для debug-рендера. */
    private Vec3d debugAimPoint = null;
    private float debugConfidence = 0.0f;
    private long debugInferenceNanos = 0;
    private FallbackReason fallbackReason = FallbackReason.NONE;
    private int fallbackTicks = 0;
    private float debugPredYaw = 0.0f;
    private float debugPredPitch = 0.0f;
    private float debugGeoYaw = 0.0f;
    private float debugGeoPitch = 0.0f;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (target == null || mc.player == null) return;

        // Читаем активную модель ОДИН раз — дальше работаем только с локальной ссылкой
        ActiveModel model = AIRotationManager.getActive();
        if (model == null) {
            fallbackActive = true;
            fallbackReason = FallbackReason.NO_MODEL;
            noRotFallback.update(ka, target);
            return;
        }

        boolean targetChanged = target != lastTarget;
        if (targetChanged) {
            onTargetSwitch(target);
        }

        // Новый тик? Тогда собираем фичи и делаем одно предсказание.
        long currentTick = mc.world != null ? mc.world.getTime() : lastTickHandled;
        if (currentTick != lastTickHandled) {
            lastTickHandled = currentTick;
            boolean ok = runInference(ka, target, model, targetChanged);
            if (!ok) {
                fallbackActive = true;
                fallbackTicks++;
                holdOrFallback(ka, target);
                return;
            }
            fallbackActive = false;
            fallbackReason = FallbackReason.NONE;
            fallbackTicks = 0;
        }

        if (fallbackActive) {
            holdOrFallback(ka, target);
            return;
        }

        applySubStep(ka);
    }

    /**
     * Обработка тика, когда модель есть, но предсказание непригодно.
     *
     * В полёте делегируем NoRot (он там реально крутит). На земле NoRot не
     * вызывает RotationComponent.update вообще — и раньше такой «пустой» тик
     * заканчивался RESET-задачей RotationComponent, которая мгновенно
     * (return speed 360) снапила поворот на взгляд игрока, после чего Neuro
     * заново доворачивался на цель. Вместо этого держим текущий поворот
     * keep-alive пингом, пока модель не восстановится.
     */
    private void holdOrFallback(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player.isGliding() && target.isGliding()) {
            noRotFallback.update(ka, target);
        } else {
            keepAlive(ka);
        }
    }

    /**
     * Пинг RotationComponent текущим поворотом: видимого движения нет,
     * но AIM-задача остаётся живой и idleTicks сбрасывается — иначе
     * по таймауту 0 сработает RESET со снапом на FreeLook (камеру).
     */
    private void keepAlive(KillAura ka) {
        var mc = ka.mc;
        if (mc.player == null) return;
        Rotation current = new Rotation(
                MathHelper.wrapDegrees(mc.player.getYaw()),
                mc.player.getPitch()
        );
        RotationComponent.update(current, 360, 360, 360, 360, 0, 1,
                ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");
    }

    /**
     * Раз в тик: собрать фичи, нормализовать, спросить модель, положить бюджет.
     * @return false если предсказание невалидно и нужен fallback
     */
    private boolean runInference(KillAura ka, LivingEntity target, ActiveModel model, boolean targetChanged) {
        var mc = ka.mc;

        float currentYaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float currentPitch = mc.player.getPitch();
        Rotation currentRotation = new Rotation(currentYaw, currentPitch);

        Vec3d aimPoint = aimController.update(ka, target, targetChanged);
        if (aimPoint == null || aimPoint.equals(Vec3d.ZERO)) {
            fallbackReason = FallbackReason.AIM_POINT;
            return false;
        }
        debugAimPoint = aimPoint;

        collector.collect(featureRow, 0, mc.player, target, currentRotation, aimPoint, targetChanged);
        featureRow[NeuroFeatureSchema.PREV_DELTA_YAW] = prevDeltaYaw;
        featureRow[NeuroFeatureSchema.PREV_DELTA_PITCH] = prevDeltaPitch;

        debugGeoYaw = featureRow[NeuroFeatureSchema.TARGET_DELTA_YAW];
        debugGeoPitch = featureRow[NeuroFeatureSchema.TARGET_DELTA_PITCH];

        history.push(featureRow);

        // Пока история не набрана, ведём цель геометрически — модель на неполном
        // окне выдаёт мусор, а padding нулями сместил бы распределение.
        if (!history.isWarm()) {
            setBudget(ka, debugGeoYaw * 0.5f, debugGeoPitch * 0.5f);
            debugPredYaw = debugGeoYaw * 0.5f;
            debugPredPitch = debugGeoPitch * 0.5f;
            debugConfidence = 1.0f;
            return true;
        }

        history.fillFlat(flatInput);
        model.getNormalizer().normalize(flatInput);

        float[] output;
        long start = System.nanoTime();
        try {
            output = model.getEngine().predict(flatInput);
        } catch (Throwable t) {
            fallbackReason = FallbackReason.INFERENCE;
            return false;
        }
        debugInferenceNanos = System.nanoTime() - start;

        if (output == null || output.length < 2
                || !Float.isFinite(output[0]) || !Float.isFinite(output[1])) {
            fallbackReason = FallbackReason.INVALID_OUTPUT;
            return false;
        }

        float deltaYaw = MathHelper.clamp(output[0], -MAX_DELTA_YAW, MAX_DELTA_YAW);
        float deltaPitch = MathHelper.clamp(output[1], -MAX_DELTA_PITCH, MAX_DELTA_PITCH);
        debugPredYaw = deltaYaw;
        debugPredPitch = deltaPitch;

        // Confidence выводится из согласия с геометрической дельтой (§12):
        // метки confidence в датасете нет, поэтому оцениваем на inference.
        debugConfidence = estimateConfidence(deltaYaw, deltaPitch, debugGeoYaw, debugGeoPitch);

        if (debugConfidence < MIN_CONFIDENCE) {
            fallbackReason = FallbackReason.LOW_CONFIDENCE;
            return false;
        }

        setBudget(ka, deltaYaw, deltaPitch);
        return true;
    }

    private void setBudget(KillAura ka, float deltaYaw, float deltaPitch) {
        float yawMul = (float) ka.neuroYawMultiplier.getValue();
        float pitchMul = (float) ka.neuroPitchMultiplier.getValue();
        controller.setBudget(deltaYaw * yawMul, deltaPitch * pitchMul);
    }

    /**
     * Оценка уверенности: насколько предсказание согласуется с геометрией.
     * Модель, тянущая прицел прочь от цели, доверия не заслуживает.
     */
    private float estimateConfidence(float predYaw, float predPitch, float geoYaw, float geoPitch) {
        float geoMagnitude = (float) Math.hypot(geoYaw, geoPitch);

        // Цель почти под прицелом — любое малое движение приемлемо
        if (geoMagnitude < 1.0f) {
            return 1.0f;
        }

        float predMagnitude = (float) Math.hypot(predYaw, predPitch);
        if (predMagnitude < 1e-4f) {
            // Модель стоит на месте при заметной ошибке — это и есть stall
            return 0.0f;
        }

        // Косинус между предсказанным и геометрическим направлением
        float dot = predYaw * geoYaw + predPitch * geoPitch;
        float cosine = dot / (predMagnitude * geoMagnitude);

        return MathHelper.clamp((cosine + 1.0f) * 0.5f, 0.0f, 1.0f);
    }

    /**
     * Каждый субшаг: выплатить долю бюджета. Без inference и аллокаций.
     */
    private void applySubStep(KillAura ka) {
        var mc = ka.mc;
        if (!controller.hasBudget()) return;

        // EventGameUpdate идёт ~240 Гц против 20 Гц тика — примерно 12 субшагов на тик
        float[] step = controller.getStepDelta(12, true);
        float stepYaw = step[0];
        float stepPitch = step[1];

        if (stepYaw == 0.0f && stepPitch == 0.0f) {
            // Нулевой шаг (бюджет выплачен или GCD округлил в ноль) —
            // без keep-alive тик без updateRotation завершится RESET-ом
            // со снапом поворота на взгляд игрока.
            keepAlive(ka);
            return;
        }

        float currentYaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float currentPitch = mc.player.getPitch();

        float nextYaw = currentYaw + stepYaw;
        float nextPitch = MathHelper.clamp(currentPitch + stepPitch, -89.0f, 90.0f);

        Rotation rotation = new Rotation(nextYaw, nextPitch);
        RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1,
                ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        ka.lastYaw = rotation.getYaw();
        ka.lastPitch = rotation.getPitch();

        prevDeltaYaw = MathHelper.wrapDegrees(rotation.getYaw() - currentYaw);
        prevDeltaPitch = rotation.getPitch() - currentPitch;
    }

    /**
     * §15: при смене цели история должна быть очищена, иначе модель продолжит
     * траекторию старой цели.
     */
    private void onTargetSwitch(LivingEntity newTarget) {
        lastTarget = newTarget;
        history.reset();
        collector.reset();
        controller.reset();
        aimController.reset();
        prevDeltaYaw = 0.0f;
        prevDeltaPitch = 0.0f;
    }

    @Override
    public void reset(KillAura ka) {
        history.reset();
        collector.reset();
        controller.reset();
        aimController.reset();
        noRotFallback.reset(ka);
        lastTarget = null;
        lastTickHandled = -1;
        prevDeltaYaw = 0.0f;
        prevDeltaPitch = 0.0f;
        fallbackActive = false;
        fallbackReason = FallbackReason.NONE;
        fallbackTicks = 0;
        debugAimPoint = null;
        debugConfidence = 0.0f;
        debugInferenceNanos = 0;
        debugPredYaw = 0.0f;
        debugPredPitch = 0.0f;
        debugGeoYaw = 0.0f;
        debugGeoPitch = 0.0f;
    }

    // ------------------------------------------------------------------
    // Диагностика для debug-рендера
    // ------------------------------------------------------------------

    public Vec3d getDebugAimPoint() {
        return debugAimPoint;
    }

    public float getDebugConfidence() {
        return debugConfidence;
    }

    public long getDebugInferenceNanos() {
        return debugInferenceNanos;
    }

    public boolean isFallbackActive() {
        return fallbackActive;
    }

    public FallbackReason getFallbackReason() {
        return fallbackReason;
    }

    /** Сколько тиков подряд модель в fallback (0 — модель работает). */
    public int getFallbackTicks() {
        return fallbackTicks;
    }

    /** Сырое предсказание модели (после clamp, до множителей). */
    public float getDebugPredYaw() {
        return debugPredYaw;
    }

    public float getDebugPredPitch() {
        return debugPredPitch;
    }

    /** Геометрическая ошибка до точки прицеливания. */
    public float getDebugGeoYaw() {
        return debugGeoYaw;
    }

    public float getDebugGeoPitch() {
        return debugGeoPitch;
    }

    public NeuroRotationController getController() {
        return controller;
    }

    public boolean isHistoryWarm() {
        return history.isWarm();
    }
}
