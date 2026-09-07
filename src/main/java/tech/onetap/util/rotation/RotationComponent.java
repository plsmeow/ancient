package tech.onetap.util.rotation;

import meteordevelopment.orbit.EventHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.event.list.EventPlayerSyncEnd;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.util.render.math.GCDFixer;

@Getter
@Setter
@Accessors(fluent = true)
public class RotationComponent extends Component {
    public static RotationComponent getInstance() {
        return tech.onetap.util.base.Instance.getComponent(RotationComponent.class);
    }

    private RotationTask currentTask = RotationTask.IDLE;
    private float currentYawSpeed;
    private float currentPitchSpeed;
    private float currentYawReturnSpeed;
    private float currentPitchReturnSpeed;
    private int currentPriority;
    private int currentTimeout;
    private int idleTicks;
    private Rotation targetRotation;

    // ─── Кадровое распределение доворота ───────────────────────────────────
    //
    // Бюджет доворота на тик выдаётся строго ПОСЛЕ отправки пакетов
    // (EventPlayerSyncEnd). Кадры между отправками расходуют его долями,
    // пропорциональными прошедшему времени (advanceSmoothRotation), поэтому
    // и наведение, и отводка движутся с частотой кадров, а не раз в тик.
    //
    // Продвижение вызывается в начале кадра (EventGameUpdate — до тика и его
    // движения) и на рендере; повторный вызов с тем же временем ничего не
    // добавляет. EventPlayerUpdate досыпает непотраченный «хвост» тика
    // (доли миллисекунды с последнего кадра), поэтому суммарный доворот за
    // тик остаётся ровно тем, что запросил модуль, а ротация в пакете
    // совпадает с ротацией, по которой считалось движение.
    private float yawBudget;
    private float pitchBudget;
    private float yawBudgetTotal;
    private float pitchBudgetTotal;
    private float consumedFraction;
    private long grantNanos;

    // Модуль запрашивал наведение с прошлой выдачи бюджета. Пока запросы
    // идут — бюджет продлевается; прекратились — ротация замирает и ждёт
    // таймаута/отводки.
    private boolean aimRequested;

    // Рампа входа отводки: первые RAMP_TICKS тиков шаг ограничен растущим
    // капом RAMP_MAX_SPEED * smoothstep — возврат начинается без рывка.
    private static final int RAMP_TICKS = 5;
    private static final float RAMP_MAX_SPEED = 45.0F;

    // Форма торможения отводки: шаг за тик не больше этой доли оставшегося
    // угла — хвост экспоненциально затухает, посадка во взгляд игрока
    // плавная, без резкой остановки клампом.
    private static final float RETURN_EASE = 0.75F;
    private int rampTicks;

    // Текущий режим коррекции движения. Ставится модулем через update(),
    // сбрасывается при завершении ротации.
    private MoveFixMode moveFixMode;

    // Плавная отводка (RESET): включается только тем, кто явно её запросил
    // (ротации KillAura: Universal, SpookyTime, Test, Test2, Sloth 07.09.26,
    // Wellmine old, LonyGrief, Neuro при включённой настройке «Плавная
    // отводка»). Все остальные — мгновенный возврат.
    private boolean smoothReturn;

    public static double direction(float rotationYaw, final float moveForward, final float moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }

    public static void fixMovement(final MoveInputEvent event, final float yaw) {
        fixMovement(event, yaw, yaw);
    }

    public static void fixMovement(final MoveInputEvent event, final float desiredYaw, final float serverYaw) {
        final float forward = event.getForward();
        final float strafe = event.getStrafe();

        if (forward == 0 && strafe == 0) return;

        final double targetAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(desiredYaw, forward, strafe)));

        float bestForward = 0, bestStrafe = 0;
        float smallestDifference = Float.MAX_VALUE;

        for (float testForward = -1F; testForward <= 1F; testForward++) {
            for (float testStrafe = -1F; testStrafe <= 1F; testStrafe++) {
                if (testForward == 0 && testStrafe == 0) continue;

                final double testAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(serverYaw, testForward, testStrafe)));
                final float difference = Math.abs(MathHelper.wrapDegrees((float)(targetAngle - testAngle)));

                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    bestForward = testForward;
                    bestStrafe = testStrafe;
                }
            }
        }

        event.forward = bestForward;
        event.strafe = bestStrafe;
    }

    /**
     * Снимает текущий режим коррекции движения.
     */
    public void clearMoveFixMode() {
        moveFixMode = null;
    }

    @EventHandler
    public void onEvent(MoveInputEvent event) {
        if (!isRotating() || mc.player == null) return;

        MoveFixMode effective = moveFixMode;
        if (effective == null) return;

        final float forward = event.getForward();
        final float strafe = event.getStrafe();
        if (forward == 0 && strafe == 0) return;

        final float viewYaw = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw());
        final float serverYaw = MathHelper.wrapDegrees(mc.player.getYaw());

        if (effective == MoveFixMode.FREE) {
            fixMovement(event, viewYaw, serverYaw);
        } else if (effective == MoveFixMode.CORRECT) {
            fixMovement(event, viewYaw);
        }
    }

    /**
     * NONE-режим коррекции активен: вход не трогаем, а yaw для расчёта скорости
     * подменяем на взгляд FreeLook (миксин на Entity.updateVelocity).
     */
    public boolean isFreelookMovement() {
        return isRotating()
                && moveFixMode == MoveFixMode.NONE
                && FreeLookComponent.interactionActive();
    }

    /** Мгновенный сброс: серверный поворот снапится на взгляд камеры. */
    private void instantReset() {
        if (mc.player != null) {
            mc.player.setYaw(FreeLookComponent.getFreeYaw());
            mc.player.setPitch(FreeLookComponent.getFreePitch());
        }
        clearBudgets();
        currentTask(RotationTask.IDLE);
        currentPriority(0);
        moveFixMode = null;
        FreeLookComponent.setActive(false);
    }

    @EventHandler
    public void onEvent(EventTick event) {
        if (currentTask().equals(RotationTask.AIM) && idleTicks() > currentTimeout()) {
            // Цель пропала — возвращаем взгляд: плавно (отводка) или мгновенно.
            rampTicks = 0;
            clearBudgets();
            if (smoothReturn) {
                currentTask(RotationTask.RESET);
            } else {
                instantReset();
            }
        }

        idleTicks++;
    }

    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        update(target, yawSpeed, pitchSpeed, yawReturnSpeed, pitchReturnSpeed, timeout, priority, clientRotation, null);
    }
    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout, int priority, boolean clientRotation, MoveFixMode moveFixMode) {
        final RotationComponent instance = RotationComponent.getInstance();

        if (instance.currentPriority() > priority) {
            return;
        }

        // По умолчанию каждый запрос ротации сбрасывает плавную отводку:
        // её включает только явный opt-in через перегрузку с smoothReturn.
        instance.smoothReturn(false);

        if (instance.currentTask().equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookComponent.setActive(true);
        }

        instance.currentYawSpeed(yawSpeed);
        instance.currentPitchSpeed(pitchSpeed);
        instance.currentYawReturnSpeed(yawReturnSpeed);
        instance.currentPitchReturnSpeed(pitchReturnSpeed);
        instance.currentTimeout(timeout);
        instance.currentPriority(priority);
        // Рампу обнуляем только при свежем захвате: пока наведение держится,
        // update() приходит каждый тик и рампа должна продолжать набираться.
        if (!instance.currentTask().equals(RotationTask.AIM)) {
            instance.rampTicks = 0;
            // Свежий захват посреди тика: бюджет выдаём сразу, чтобы первый
            // тик наведения не потерял движение до ближайшего EventPlayerSyncEnd
            instance.grantBudgets(yawSpeed, pitchSpeed);
        }
        instance.currentTask(RotationTask.AIM);
        instance.targetRotation(target);
        instance.idleTicks(0);
        instance.aimRequested(true);

        if (moveFixMode != null) {
            instance.moveFixMode(moveFixMode);
        }

        instance.beginAim(target, yawSpeed, pitchSpeed);
    }

    /** Opt-in плавной отводки: после успешного захвата цели RESET идёт по рампе. */
    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout, int priority, boolean clientRotation, MoveFixMode moveFixMode, boolean smoothReturn) {
        final RotationComponent instance = RotationComponent.getInstance();
        if (instance.currentPriority() > priority) {
            return;
        }
        update(target, yawSpeed, pitchSpeed, yawReturnSpeed, pitchReturnSpeed, timeout, priority, clientRotation, moveFixMode);
        instance.smoothReturn(smoothReturn);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false, null);
    }

    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false, null);
    }

    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed, int timeout, int priority, MoveFixMode moveFixMode) {
        update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false, moveFixMode);
    }

    /**
     * Захват цели на новый тик. Если цель достижима за один тик (мгновенные
     * режимы со скоростью 360), доворачиваем сразу — к моменту тика и удара
     * ротация уже на цели. Иначе движение идёт по кадровому бюджету из
     * grantBudgets: модуль запросил скорость — столько за тик и выйдет.
     */
    private void beginAim(Rotation target, float yawSpeed, float pitchSpeed) {
        if (mc.player == null) return;

        float factor = advanceRamp();
        float yawAllowance = rampedSpeed(yawSpeed, factor);
        float pitchAllowance = rampedSpeed(pitchSpeed, factor);

        Rotation currentRotation = new Rotation(mc.player);
        float yawDelta = Math.abs(MathHelper.wrapDegrees(target.getYaw() - currentRotation.getYaw()));
        float pitchDelta = Math.abs(target.getPitch() - currentRotation.getPitch());

        if (yawDelta <= yawAllowance && pitchDelta <= pitchAllowance) {
            clearBudgets();
            updateRotation(target, yawDelta, pitchDelta);
        }
    }

    /**
     * Выдача бюджета доворота на следующий тик — сразу после отправки
     * пакетов: до следующей отправки его целиком съедят кадры.
     */
    @EventHandler
    public void onEvent(EventPlayerSyncEnd event) {
        if (!isRotating() || mc.player == null) return;

        if (currentTask().equals(RotationTask.AIM)) {
            if (aimRequested) {
                grantBudgets(currentYawSpeed(), currentPitchSpeed());
            } else {
                // Модуль перестал запрашивать наведение — замираем до отводки
                clearBudgets();
            }
            aimRequested(false);
        } else {
            // Отводка: рампа разгона + торможение у цели — за тик не больше
            // RETURN_EASE-доли оставшегося угла.
            Rotation free = new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch());
            Rotation current = new Rotation(mc.player);
            if (current.getDelta(free) < 1.0F) {
                finishResetIfDone(true);
                return;
            }

            float factor = advanceRamp();
            float yawRemain = Math.abs(MathHelper.wrapDegrees(free.getYaw() - current.getYaw()));
            float pitchRemain = Math.abs(free.getPitch() - current.getPitch());
            grantBudgets(
                    Math.min(rampedSpeed(currentYawReturnSpeed(), factor), yawRemain * RETURN_EASE),
                    Math.min(rampedSpeed(currentPitchReturnSpeed(), factor), pitchRemain * RETURN_EASE));
        }
    }

    /**
     * Продвигает доворот до текущего момента: между отправками пакетов бюджет
     * расходуется пропорционально прошедшему времени. Вызывается и в начале
     * кадра (EventGameUpdate — до тика и его движения), и на рендере.
     */
    private void advanceSmoothRotation() {
        if (!isRotating() || mc.player == null) return;
        if (yawBudget <= 0 && pitchBudget <= 0) return;

        float fraction = Math.min((System.nanoTime() - grantNanos) / 50_000_000.0F, 1.0F);
        if (fraction <= consumedFraction) return;

        float share = fraction - consumedFraction;
        consumedFraction = fraction;

        float yawStep = Math.min(yawBudgetTotal * share, Math.max(yawBudget, 0.0F));
        float pitchStep = Math.min(pitchBudgetTotal * share, Math.max(pitchBudget, 0.0F));
        yawBudget = Math.max(yawBudget - yawStep, 0.0F);
        pitchBudget = Math.max(pitchBudget - pitchStep, 0.0F);
        if (yawStep <= 0 && pitchStep <= 0) return;

        Rotation target = currentTask().equals(RotationTask.AIM)
                ? targetRotation()
                : new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch());
        finishResetIfDone(updateRotation(target, yawStep, pitchStep));
    }

    @EventHandler
    public void onEvent(EventGameUpdate event) {
        advanceSmoothRotation();
    }

    @EventHandler
    public void onWorldRender(EventWorldRender event) {
        advanceSmoothRotation();
    }

    @EventHandler
    public void onEvent(EventPlayerUpdate event) {
        // Страховка: в начале тика игрока досыпаем непотраченный «хвост»
        // бюджета — доли миллисекунды с последнего кадра. Именно здесь, а не
        // перед отправкой пакетов: к этому моменту ещё не считались ни ввод
        // (MoveFix), ни движение тика, поэтому коррекция, физика движения и
        // пакет видят один и тот же yaw — рассинхрона для симуляции античита
        // нет. Суммарный доворот за тик по-прежнему равен запросу модуля.
        if (!isRotating() || mc.player == null) return;
        if (yawBudget <= 0 && pitchBudget <= 0) return;

        float yawStep = Math.max(yawBudget, 0.0F);
        float pitchStep = Math.max(pitchBudget, 0.0F);
        clearBudgets();

        Rotation target = currentTask().equals(RotationTask.AIM)
                ? targetRotation()
                : new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch());
        finishResetIfDone(updateRotation(target, yawStep, pitchStep));
    }

    private void grantBudgets(float yaw, float pitch) {
        yawBudget = Math.max(yaw, 0.0F);
        pitchBudget = Math.max(pitch, 0.0F);
        yawBudgetTotal = yawBudget;
        pitchBudgetTotal = pitchBudget;
        consumedFraction = 0.0F;
        grantNanos = System.nanoTime();
    }

    private void clearBudgets() {
        yawBudget = 0;
        pitchBudget = 0;
        yawBudgetTotal = 0;
        pitchBudgetTotal = 0;
    }

    private void finishResetIfDone(boolean done) {
        if (done && currentTask().equals(RotationTask.RESET)) {
            // Дотягиваем остаток до свободного взгляда: после выключения
            // FreeLook камера не должна прыгать на недолетевшие доли градуса.
            updateRotation(new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch()), 360.0F, 360.0F);
            clearBudgets();
            currentTask(RotationTask.IDLE);
            currentPriority(0);
            moveFixMode = null;
            FreeLookComponent.setActive(false);
        }
    }

    /** Smoothstep-рампа 0..1 за RAMP_TICKS тиков; после — всегда 1. */
    private float advanceRamp() {
        if (rampTicks >= RAMP_TICKS) return 1.0F;
        float t = (float) ++rampTicks / RAMP_TICKS;
        return t * t * (3.0F - 2.0F * t);
    }

    /** Во время рампы шаг ограничен растущим капом, после — запрос модуля. */
    private static float rampedSpeed(float requested, float factor) {
        if (factor >= 1.0F) return requested;
        return Math.min(requested, RAMP_MAX_SPEED * factor);
    }

    private boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
        if (mc.player == null) return false;

        Rotation currentRotation = new Rotation(mc.player);
        float yawDelta = MathHelper.wrapDegrees(targetRotation.getYaw() - currentRotation.getYaw());
        float pitchDelta = targetRotation.getPitch() - currentRotation.getPitch();

        float clampedYaw = Math.min(Math.abs(yawDelta), yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);

        float yaw = mc.player.getYaw();
        yaw += GCDFixer.getFixRotate(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw));
        mc.player.setYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + GCDFixer.getFixRotate(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)), -90F, 90F));

        idleTicks(0);
        return new Rotation(mc.player).getDelta(targetRotation) < 1F;
    }

    public void stopRotation() {
        // Плавная отводка — только для opt-in (KillAura с включённой «Отводкой»),
        // остальные модули возвращают взгляд мгновенно.
        if (smoothReturn && FreeLookComponent.isActive() && mc.player != null) {
            if (currentYawReturnSpeed() <= 0) currentYawReturnSpeed(180);
            if (currentPitchReturnSpeed() <= 0) currentPitchReturnSpeed(180);
            rampTicks = 0;
            clearBudgets();
            // MoveFix живёт всю отводку и гасится только при её завершении:
            // траектория держится по взгляду игрока, пока тело доворачивается.
            currentTask(RotationTask.RESET);
        } else {
            instantReset();
        }
    }

    public boolean isRotating() {
        return !currentTask.equals(RotationTask.IDLE);
    }

    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }
}
