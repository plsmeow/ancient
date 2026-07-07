package tech.onetap.util.rotation;

import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.util.render.math.GCDFixer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Режимы коррекции движения по модулям. Ключ — имя модуля-владельца.
    // При обработке MoveInputEvent выбирается режим с наивысшим приоритетом (FREE > CORRECT).
    private final Map<String, MoveFixMode> moveFixModes = new ConcurrentHashMap<>();
    private String currentOwner;

    public void currentOwner(String owner) {
        this.currentOwner = owner;
    }

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
     * Текущий эффективный режим коррекции — наивысший приоритет среди всех активных модулей.
     */
    public MoveFixMode getEffectiveMoveFixMode() {
        MoveFixMode result = null;
        for (MoveFixMode mode : moveFixModes.values()) {
            result = MoveFixMode.highest(result, mode);
        }
        return result;
    }

    /**
     * Регистрирует режим коррекции для модуля-владельца.
     */
    public void setMoveFixMode(String owner, MoveFixMode mode) {
        if (mode == null) {
            moveFixModes.remove(owner);
        } else {
            moveFixModes.put(owner, mode);
        }
    }

    /**
     * Снимает регистрацию режима коррекции для модуля-владельца.
     */
    public void clearMoveFixMode(String owner) {
        moveFixModes.remove(owner);
    }

    @Subscribe
    public void onEvent(MoveInputEvent event) {
        if (!isRotating() || mc.player == null) return;

        MoveFixMode effective = getEffectiveMoveFixMode();
        if (effective == null) return;

        final float forward = event.getForward();
        final float strafe = event.getStrafe();
        if (forward == 0 && strafe == 0) return;

        final float viewYaw = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw());
        final float serverYaw = MathHelper.wrapDegrees(mc.player.getYaw());

        if (effective == MoveFixMode.FREE) {
            fixMovement(event, viewYaw, serverYaw);
        } else {
            fixMovement(event, viewYaw);
        }
    }

    private void resetRotation() {
        Rotation targetRotation = new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch());
        if (updateRotation(targetRotation, currentYawReturnSpeed(), currentPitchReturnSpeed())) {
            currentTask(RotationTask.IDLE);
            currentPriority(0);
            currentOwner = null;
            FreeLookComponent.setActive(false);
        }
    }

    @Subscribe
    public void onEvent(EventTick event) {
        if (currentTask().equals(RotationTask.AIM) && idleTicks() > currentTimeout()) {
            currentTask(RotationTask.RESET);
        }

        if (currentTask().equals(RotationTask.RESET)) {
            resetRotation();
        }
        idleTicks++;
    }

    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        update(target, yawSpeed, pitchSpeed, yawReturnSpeed, pitchReturnSpeed, timeout, priority, clientRotation, null, null);
    }

    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout, int priority, boolean clientRotation, MoveFixMode moveFixMode, String owner) {
        final RotationComponent instance = RotationComponent.getInstance();

        if (instance.currentPriority() > priority) {
            return;
        }

        if (instance.currentTask().equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookComponent.setActive(true);
        }

        instance.currentYawSpeed(yawSpeed);
        instance.currentPitchSpeed(pitchSpeed);
        instance.currentYawReturnSpeed(yawReturnSpeed);
        instance.currentPitchReturnSpeed(pitchReturnSpeed);
        instance.currentTimeout(timeout);
        instance.currentPriority(priority);
        instance.currentTask(RotationTask.AIM);
        instance.targetRotation(target);

        if (moveFixMode != null && owner != null) {
            instance.setMoveFixMode(owner, moveFixMode);
            instance.currentOwner(owner);
        }

        instance.updateRotation(target, yawSpeed, pitchSpeed);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false, null, null);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority, MoveFixMode moveFixMode, String owner) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false, moveFixMode, owner);
    }

    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false, null, null);
    }

    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed, int timeout, int priority, MoveFixMode moveFixMode, String owner) {
        update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false, moveFixMode, owner);
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
        // Возвращаем игрока к камере плавно через RESET-задачу, а не обрываем резко.
        if (FreeLookComponent.isActive() && mc.player != null) {
            if (currentYawReturnSpeed() <= 0) currentYawReturnSpeed(180);
            if (currentPitchReturnSpeed() <= 0) currentPitchReturnSpeed(180);
            currentTask(RotationTask.RESET);
        } else {
            currentTask(RotationTask.IDLE);
            currentPriority(0);
            currentOwner = null;
            FreeLookComponent.setActive(false);
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
