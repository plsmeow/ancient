package tech.onetap.util.rotation;

import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.list.movement.MoveFix;
import tech.onetap.util.base.Instance;
import tech.onetap.util.render.math.GCDFixer;

@Getter
@Setter
@Accessors(fluent = true)
public class RotationComponent extends Component {
    public static RotationComponent getInstance() {
        return Instance.getComponent(RotationComponent.class);
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
    // Принудительная свободная коррекция движения (выставляется потребителем, напр. Scaffold),
    // работает независимо от модуля MoveFix и его режима.
    private boolean forceFreeMovement;

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
        // Сфокусированная коррекция — направление движения считается относительно того же угла,
        // что использует игра (взгляд игрока), поэтому игрок продолжает идти на цель.
        fixMovement(event, yaw, yaw);
    }

    /**
     * Коррекция движения.
     *
     * @param desiredYaw угол, в сторону которого игрок ХОЧЕТ двигаться (для свободной — взгляд/камера)
     * @param serverYaw  угол, который реально использует игра при расчёте перемещения (mc.player.getYaw())
     */
    public static void fixMovement(final MoveInputEvent event, final float desiredYaw, final float serverYaw) {
        final float forward = event.getForward();
        final float strafe = event.getStrafe();

        // Early exit if there's no movement
        if (forward == 0 && strafe == 0) {
            return;
        }

        // Желаемое направление движения в мире (по взгляду игрока и нажатым клавишам)
        final double targetAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(desiredYaw, forward, strafe)));

        float bestForward = 0, bestStrafe = 0;
        float smallestDifference = Float.MAX_VALUE;

        // Iterate over all valid combinations of movement inputs
        for (float testForward = -1F; testForward <= 1F; testForward++) {
            for (float testStrafe = -1F; testStrafe <= 1F; testStrafe++) {
                if (testForward == 0 && testStrafe == 0) continue;

                // Направление этого варианта ввода при угле, который применит игра
                final double testAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(serverYaw, testForward, testStrafe)));
                final float difference = Math.abs(MathHelper.wrapDegrees((float)(targetAngle - testAngle)));

                // Update best match
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    bestForward = testForward;
                    bestStrafe = testStrafe;
                }
            }
        }

        // Apply corrected input
        event.forward = (bestForward);
        event.strafe = (bestStrafe);
    }

    @Subscribe
    public void onEvent(MoveInputEvent event) {
        if (!isRotating() || mc.player == null) return;

        final float forward = event.getForward();
        final float strafe = event.getStrafe();
        if (forward == 0 && strafe == 0) return;

        final float viewYaw = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw());
        final float serverYaw = MathHelper.wrapDegrees(mc.player.getYaw());

        // Принудительная свободная коррекция — независимо от модуля MoveFix и его режима.
        if (forceFreeMovement) {
            fixMovement(event, viewYaw, serverYaw);
            return;
        }

        final MoveFix moveFix = Instance.get(MoveFix.class);

        if (moveFix == null || !moveFix.isEnabled()) {
            return;
        }

        if (moveFix.isFree()) {
            fixMovement(event, viewYaw, serverYaw);
        } else {
            fixMovement(event, viewYaw);
        }
    }

    private void resetRotation() {
        Rotation targetRotation = new Rotation(FreeLookComponent.getFreeYaw(), FreeLookComponent.getFreePitch());
        if (updateRotation(targetRotation, currentYawReturnSpeed(), currentPitchReturnSpeed())) {
            stopRotation();
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

        instance.updateRotation(target, yawSpeed, pitchSpeed);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }

    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false);
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
        currentTask(RotationTask.IDLE);
        currentPriority(0);
        FreeLookComponent.setActive(false);
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