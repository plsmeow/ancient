package tech.onetap.util.rotation;

import meteordevelopment.orbit.EventHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.list.EventPlayerSync;
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

    // Рампа плавного входа/возврата: пока она идёт, шаг ограничен растущим
    // капом RAMP_MAX_SPEED * smoothstep, поэтому наведение на цель и отвод
    // обратно к взгляду игрока идут плавно, а не одним кадром. По завершении
    // рампы скорость снова полностью определяется запросом модуля.
    private static final int RAMP_TICKS = 8;
    private static final float RAMP_MAX_SPEED = 45.0F;
    private int rampTicks;

    // Бюджет доворота на текущий тик (в градусах). Ротация больше не шагает
    // раз в тик: каждый кадр (EventWorldRender) из бюджета тратится доля,
    // пропорциональная времени кадра, поэтому наведение обновляется с частотой
    // кадров (сотни Гц), а не 20 Гц. Непотраченный остаток досыпается перед
    // отправкой пакетов (EventPlayerSync), чтобы суммарная скорость за тик
    // осталась ровно той, что запросил модуль.
    private float yawBudget;
    private float pitchBudget;
    private long lastFrameNanos = System.nanoTime();

    // Текущий режим коррекции движения. Ставится модулем через update(),
    // сбрасывается при остановке/завершении ротации.
    private MoveFixMode moveFixMode;

    // Плавная отводка (RESET): включается только тем, кто явно её запросил
    // (ротации KillAura: Universal, Sloth, Wellmine old, LonyGrief, SpookyTime,
    // Neuro при включённой настройке «Отводка»). Все остальные — мгновенный возврат.
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

    private void resetRotation() {
        float factor = advanceRamp();
        yawBudget = rampedSpeed(currentYawReturnSpeed(), factor);
        pitchBudget = rampedSpeed(currentPitchReturnSpeed(), factor);
    }

    /** Мгновенный сброс: серверный поворот снапится на взгляд камеры. */
    private void instantReset() {
        if (mc.player != null) {
            mc.player.setYaw(FreeLookComponent.getFreeYaw());
            mc.player.setPitch(FreeLookComponent.getFreePitch());
        }
        yawBudget = 0;
        pitchBudget = 0;
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
            if (smoothReturn) {
                currentTask(RotationTask.RESET);
            } else {
                instantReset();
            }
        }

        if (currentTask().equals(RotationTask.RESET)) {
            resetRotation();
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
        }
        instance.currentTask(RotationTask.AIM);
        instance.targetRotation(target);
        instance.idleTicks(0);

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
     * Захват цели на новый тик: бюджет доворота = скорость (с учётом рампы
     * входа — первые RAMP_TICKS тиков она наращивается плавно). Если цель
     * достижима за один тик (мгновенные режимы со скоростью 360), доворачиваем
     * сразу; иначе остаток распределяется по кадрам в onWorldRender.
     */
    private void beginAim(Rotation target, float yawSpeed, float pitchSpeed) {
        float factor = advanceRamp();
        yawBudget = rampedSpeed(yawSpeed, factor);
        pitchBudget = rampedSpeed(pitchSpeed, factor);

        if (mc.player == null) return;

        Rotation currentRotation = new Rotation(mc.player);
        float yawDelta = Math.abs(MathHelper.wrapDegrees(target.getYaw() - currentRotation.getYaw()));
        float pitchDelta = Math.abs(target.getPitch() - currentRotation.getPitch());

        if (yawDelta <= yawBudget && pitchDelta <= pitchBudget) {
            yawBudget = 0;
            pitchBudget = 0;
            updateRotation(target, yawDelta, pitchDelta);
        }
    }

    @EventHandler
    public void onWorldRender(EventWorldRender event) {
        long now = System.nanoTime();
        float frameTicks = Math.min((now - lastFrameNanos) / 50_000_000.0F, 1.0F);
        lastFrameNanos = now;

        if (!isRotating() || mc.player == null) return;

        boolean aim = currentTask().equals(RotationTask.AIM);
        float yawSpeed = aim ? currentYawSpeed() : currentYawReturnSpeed();
        float pitchSpeed = aim ? currentPitchSpeed() : currentPitchReturnSpeed();
        float yawStep = Math.min(yawSpeed * frameTicks, Math.max(yawBudget, 0.0F));
        float pitchStep = Math.min(pitchSpeed * frameTicks, Math.max(pitchBudget, 0.0F));
        if (yawStep <= 0 && pitchStep <= 0) return;

        yawBudget -= yawStep;
        pitchBudget -= pitchStep;

        Rotation target = aim ? targetRotation() : new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch());
        finishResetIfDone(updateRotation(target, yawStep, pitchStep));
    }

    @EventHandler
    public void onEvent(EventPlayerSync event) {
        // Страховка: перед отправкой пакетов досыпаем непотраченный остаток
        // бюджета, чтобы за тик суммарный доворот совпадал со старым поведением.
        if (!isRotating() || mc.player == null) return;
        if (yawBudget <= 0 && pitchBudget <= 0) return;

        boolean aim = currentTask().equals(RotationTask.AIM);
        float yawStep = Math.max(yawBudget, 0.0F);
        float pitchStep = Math.max(pitchBudget, 0.0F);
        yawBudget = 0;
        pitchBudget = 0;

        Rotation target = aim ? targetRotation() : new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch());
        finishResetIfDone(updateRotation(target, yawStep, pitchStep));
    }

    private void finishResetIfDone(boolean done) {
        if (done && currentTask().equals(RotationTask.RESET)) {
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
        moveFixMode = null;
        // Плавная отводка — только для opt-in (KillAura с включённой «Отводкой»),
        // остальные модули возвращают взгляд мгновенно.
        if (smoothReturn && FreeLookComponent.isActive() && mc.player != null) {
            if (currentYawReturnSpeed() <= 0) currentYawReturnSpeed(180);
            if (currentPitchReturnSpeed() <= 0) currentPitchReturnSpeed(180);
            rampTicks = 0;
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
