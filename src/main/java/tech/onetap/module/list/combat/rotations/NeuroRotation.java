package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.neuro.rotation.ActiveModel;
import tech.onetap.util.neuro.rotation.AimPointController;
import tech.onetap.util.neuro.rotation.NeuroFeatureCollector;
import tech.onetap.util.neuro.rotation.NeuroFeatureSchema;
import tech.onetap.util.neuro.rotation.NeuroMdn;
import tech.onetap.util.neuro.rotation.NeuroRotationController;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

/**
 * Нейро-ротация: прицел ведёт TCN+MDN модель (train_neuro.py).
 *
 * Канонический такт — игровой тик (20 Гц). Раз в тик собирается RAW-строка
 * (тот же формат, что пишется в датасет), из окна 16 тиков выводятся фичи,
 * модель выдаёт распределение дельт, и СРЕДНЕЕ смеси становится бюджетом
 * поворота. Никакого сведения к геометрии: предикт модели применяется как есть
 * (кламп только в диапазоне обученных меток ±25°), поэтому траектория
 * повторяет человеческую, а не идеальную прямую на цель.
 *
 * При отсутствии модели, ошибке inference или невалидном выходе — откат
 * в NoRotRotation/геометрию (§23). Прогрев (первые 16 тиков окна) —
 * геометрическое наведение, модель на неполном окне выдаёт мусор.
 */
public class NeuroRotation extends RotationMode {

    private static final int SUBSTEPS_PER_TICK = 12;

    /** Причина отката в fallback — для debug-панели. */
    public enum FallbackReason {
        NONE,
        NO_MODEL,
        AIM_POINT,
        INFERENCE,
        INVALID_OUTPUT
    }

    private final NeuroFeatureCollector collector = new NeuroFeatureCollector();
    private final AimPointController aimController = new AimPointController();
    private final NeuroRotationController controller = new NeuroRotationController();
    private final NoRotRotation noRotFallback = new NoRotRotation();

    /** Преаллоцированный вход модели — переиспользуется каждый тик. */
    private final float[] flatInput =
            new float[NeuroFeatureSchema.SEQ_LEN * NeuroFeatureSchema.FEATURE_COUNT];

    private LivingEntity lastTarget = null;
    private long lastTickHandled = -1;
    private long tickCounter = 0;
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
            runInference(ka, target, model, targetChanged);
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
     * В полёте делегируем NoRot (он там реально крутит). На земле держим
     * текущий поворот keep-alive пингом, пока модель не восстановится,
     * чтобы RotationComponent не снапнул поворот на взгляд игрока.
     */
    private void holdOrFallback(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player.isGliding() && target.isGliding()) {
            noRotFallback.update(ka, target);
            return;
        }

        // Не морозим взгляд: пока модель не восстановится, ведём прицел к точке
        // геометрически — иначе фолбэк выглядит как «застрял на одном месте»
        Vec3d aimPoint = aimController.update(ka, target, false);
        Rotation targetRotation = new Rotation(aimPoint);
        float dYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - mc.player.getYaw());
        float dPitch = targetRotation.getPitch() - mc.player.getPitch();

        if (Math.abs(dYaw) < 1.0f && Math.abs(dPitch) < 1.0f) {
            keepAlive(ka);
            return;
        }

        float stepYaw = MathHelper.clamp(dYaw * 0.35f, -20.0f, 20.0f);
        float stepPitch = MathHelper.clamp(dPitch * 0.35f, -15.0f, 15.0f);
        setBudget(ka, stepYaw, stepPitch);
        applySubStep(ka);
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
                ka.clientLook.getValue(), ka.getMoveFixMode(), ka.otvodkaActive());
    }

    /**
     * Раз в тик: RAW-строка -> окно фич -> предсказание модели -> бюджет.
     * Модель непригодна только при реальной ошибке (нет точки, сбой inference,
     * мусорный выход) — предикт по направлению НЕ корректируется, иначе
     * ротация вырождается в ванильную.
     */
    private void runInference(KillAura ka, LivingEntity target, ActiveModel model, boolean targetChanged) {
        var mc = ka.mc;

        Vec3d aimPoint = aimController.update(ka, target, targetChanged);
        if (aimPoint == null || aimPoint.equals(Vec3d.ZERO)) {
            fail(FallbackReason.AIM_POINT);
            return;
        }
        debugAimPoint = aimPoint;

        // RAW-строка текущего тика: dyaw/dpitch коллектор берёт из своей истории —
        // это ровно дельта, применённая на прошлом тике, как и в датасете
        collector.pushFrame(mc.player, target, aimPoint, !targetChanged, tickCounter++);

        // Пока окно не набрано, ведём цель геометрически — модель на неполном
        // окне выдаёт мусор, а padding нулями сместил бы распределение
        if (!collector.isWarm()) {
            warmUpBudget(ka, target);
            debugConfidence = 1.0f;
            debugPredYaw = 0.0f;
            debugPredPitch = 0.0f;
            return;
        }

        collector.collect(flatInput);
        int last = (NeuroFeatureSchema.SEQ_LEN - 1) * NeuroFeatureSchema.FEATURE_COUNT;
        debugGeoYaw = flatInput[last + NeuroFeatureSchema.F_ERR_YAW];
        debugGeoPitch = flatInput[last + NeuroFeatureSchema.F_ERR_PITCH];

        float[] output;
        long start = System.nanoTime();
        try {
            output = model.getEngine().predict(flatInput);
        } catch (Throwable t) {
            fail(FallbackReason.INFERENCE);
            return;
        }
        debugInferenceNanos = System.nanoTime() - start;

        NeuroMdn.Prediction pred = NeuroMdn.decode(output);
        if (pred == null) {
            fail(FallbackReason.INVALID_OUTPUT);
            return;
        }

        // Кламп только в диапазоне обученных меток — форму движения задаёт модель
        float deltaYaw = MathHelper.clamp(pred.deltaYaw(), -NeuroFeatureSchema.LABEL_CLIP_DEG,
                NeuroFeatureSchema.LABEL_CLIP_DEG);
        float deltaPitch = MathHelper.clamp(pred.deltaPitch(), -NeuroFeatureSchema.LABEL_CLIP_DEG,
                NeuroFeatureSchema.LABEL_CLIP_DEG);

        debugPredYaw = deltaYaw;
        debugPredPitch = deltaPitch;
        debugConfidence = NeuroMdn.confidence(pred.sigma());

        if (fallbackActive) {
            // В окне истории за время простоя образовалась дыра — сбрасываем,
            // следующие SEQ_LEN тиков цель ведётся геометрически (warm-up)
            collector.reset();
        }
        fallbackActive = false;
        fallbackReason = FallbackReason.NONE;
        fallbackTicks = 0;

        setBudget(ka, deltaYaw, deltaPitch);
    }

    /**
     * Прогрев: геометрическое наведение, пока окно истории не набрано.
     */
    private void warmUpBudget(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        Vec3d aimPoint = debugAimPoint;
        if (aimPoint == null) return;
        Rotation targetRotation = new Rotation(aimPoint);
        float dYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - mc.player.getYaw());
        float dPitch = targetRotation.getPitch() - mc.player.getPitch();
        setBudget(ka,
                MathHelper.clamp(dYaw * 0.5f, -20.0f, 20.0f),
                MathHelper.clamp(dPitch * 0.5f, -15.0f, 15.0f));
    }

    private void fail(FallbackReason reason) {
        if (!fallbackActive) {
            collector.reset();
        }
        fallbackActive = true;
        fallbackReason = reason;
        fallbackTicks++;
        debugConfidence = 0.0f;
    }

    private void setBudget(KillAura ka, float deltaYaw, float deltaPitch) {
        float yawMul = (float) ka.neuroYawMultiplier.getValue();
        float pitchMul = (float) ka.neuroPitchMultiplier.getValue();
        controller.setBudget(deltaYaw * yawMul, deltaPitch * pitchMul);
    }

    /**
     * Каждый субшаг: выплатить долю бюджета. Без inference и аллокаций.
     */
    private void applySubStep(KillAura ka) {
        var mc = ka.mc;
        if (!controller.hasBudget()) return;

        float[] step = controller.getStepDelta(SUBSTEPS_PER_TICK, true);
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
                ka.clientLook.getValue(), ka.getMoveFixMode(), ka.otvodkaActive());

        ka.lastYaw = rotation.getYaw();
        ka.lastPitch = rotation.getPitch();
    }

    /**
     * §15: при смене цели история должна быть очищена, иначе модель продолжит
     * траекторию старой цели.
     */
    private void onTargetSwitch(LivingEntity newTarget) {
        lastTarget = newTarget;
        collector.reset();
        controller.reset();
        aimController.reset();
    }

    @Override
    public void reset(KillAura ka) {
        collector.reset();
        controller.reset();
        aimController.reset();
        noRotFallback.reset(ka);
        lastTarget = null;
        lastTickHandled = -1;
        tickCounter = 0;
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

    /** Предсказание модели (после клампа, до множителей). */
    public float getDebugPredYaw() {
        return debugPredYaw;
    }

    public float getDebugPredPitch() {
        return debugPredPitch;
    }

    /** Геометрическая ошибка до точки прицеливания (последний тик окна). */
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
        return collector.isWarm();
    }
}
