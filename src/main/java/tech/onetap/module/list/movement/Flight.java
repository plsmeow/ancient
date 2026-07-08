package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.move.MoveUtil;

@ModuleInformation(moduleName = "Flight", moduleCategory = ModuleCategory.MOVEMENT)
public class Flight extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "Vanilla", "Vanilla", "Vulcan");
    public SliderSetting speed = new SliderSetting("Скорость", 1.0, 0.1, 10.0, 0.1).setVisible(() -> mode.is("Vanilla"));

    public final BooleanSetting antiKick = new BooleanSetting("Анти-кик", true);

    private int vulcanStep;
    private int vulcanResetCount;
    private boolean vulcanSwitch;
    private boolean vulcanDownwards;
    private double vulcanStartHeight;
    private double lastMotionX;
    private double lastMotionZ;

    private int antiKickDelayLeft;
    private int antiKickOffLeft;

    @Override
    public void onEnable() {
        super.onEnable();
        resetVulcanState();
        resetAntiKickState();
        if (mc.player != null) {
            vulcanStartHeight = mc.player.getY();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetVulcanState();
        resetAntiKickState();
    }

    @Subscribe
    public void onUpdate(EventTick event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (!mode.is("Vanilla")) return;

        boolean antiKicking = tickAntiKick();

        if (antiKicking) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.0313, mc.player.getVelocity().z);
        } else if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, speed.getValue() * 0.5, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -speed.getValue() * 0.5, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        }

        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        if (forward != 0 || strafe != 0) {
            double angle = Math.atan2(-strafe, forward);
            double finalYaw = Math.toRadians(yaw) + angle;
            double speedVal = speed.getValue();

            mc.player.setVelocity(
                    -Math.sin(finalYaw) * speedVal,
                    mc.player.getVelocity().y,
                    Math.cos(finalYaw) * speedVal
            );
        }

        mc.player.setOnGround(false);
        mc.player.fallDistance = 0;
    }

    @Subscribe
    public void onPlayerUpdate(EventPlayerUpdate event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!mode.is("Vulcan")) return;

        handleVulcanMode();
    }

    private void handleVulcanMode() {
        boolean downwards = mc.options.sneakKey.isPressed();
        boolean upwards = mc.options.jumpKey.isPressed();

        if (mc.player.isOnGround()) {
            vulcanStartHeight = mc.player.getY();
            vulcanStep = 0;
            vulcanDownwards = false;
            vulcanResetCount = 0;
            vulcanSwitch = false;
            mc.player.fallDistance = 0;
            return;
        }

        double currentY = mc.player.getY();
        if (vulcanDownwards && !downwards) {
            vulcanStartHeight = snapToStep(currentY, 0.015625);
            vulcanDownwards = false;
            vulcanResetCount++;
            vulcanStep--;
        }

        double motionX = mc.player.getVelocity().x;
        double motionY = 0.0;
        double motionZ = mc.player.getVelocity().z;
        double deltaY = currentY - vulcanStartHeight;

        vulcanStep++;

        switch (vulcanStep) {
            case 1 -> {
                if (deltaY < 0.073) {
                    if (deltaY > 0.0 || vulcanResetCount > 1) {
                        motionY = -deltaY;
                    }
                    vulcanSwitch = true;
                } else {
                    return;
                }
            }
            case 2 -> NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
            case 3 -> {
                lastMotionX = motionX;
                lastMotionZ = motionZ;

                motionY = -deltaY + (vulcanSwitch ? 0.015625 : (upwards ? 0.5 : 0.0625));
                if (upwards) {
                    vulcanStartHeight = currentY + motionY;
                }
            }
            case 4 -> {
                vulcanSwitch = !vulcanSwitch;
                mc.player.setOnGround(!vulcanDownwards || !downwards);

                motionX = lastMotionX * 0.88;
                motionY = downwards ? (vulcanSwitch ? -0.097000002 : -0.147000003) : -0.097000002;
                motionZ = lastMotionZ * 0.88;

                vulcanDownwards = downwards;
                if (downwards) {
                    vulcanStep--;
                } else {
                    vulcanStep = 1;
                }
            }
            default -> {
                if (MoveUtil.hasPlayerMovement()) {
                    double[] direction = MoveUtil.calculateDirection(0.28);
                    motionX = direction[0];
                    motionZ = direction[1];
                }
            }
        }

        if (vulcanStep != 4 && MoveUtil.hasPlayerMovement()) {
            double[] direction = MoveUtil.calculateDirection(0.28);
            motionX = direction[0];
            motionZ = direction[1];
        }

        mc.player.setVelocity(motionX, motionY, motionZ);
        mc.player.fallDistance = 0;
    }

    private void resetVulcanState() {
        vulcanStep = 0;
        vulcanResetCount = 0;
        vulcanSwitch = false;
        vulcanDownwards = false;
        lastMotionX = 0.0;
        lastMotionZ = 0.0;
    }

    private void resetAntiKickState() {
        antiKickDelayLeft = 0;
        antiKickOffLeft = 0;
    }

    private boolean tickAntiKick() {
        if (!antiKick.getValue()) return false;
        if (!mode.is("Vanilla")) return false;

        if (antiKickDelayLeft > 0) antiKickDelayLeft--;

        if (antiKickDelayLeft <= 0 && antiKickOffLeft <= 0) {
            antiKickDelayLeft = 20;
            antiKickOffLeft = 1;
            return false;
        }

        if (antiKickDelayLeft <= 0 && antiKickOffLeft > 0) {
            antiKickOffLeft--;
            return true;
        }

        return false;
    }

    private double snapToStep(double value, double step) {
        return Math.floor(value / step) * step;
    }
}
