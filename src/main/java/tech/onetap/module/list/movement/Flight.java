package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.ArmorStandItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventPacket;
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

    public final ModeSetting mode = new ModeSetting("Режим", "Vanilla", "Vanilla", "Vulcan", "Vulcan XZ", "FunSky");
    public SliderSetting speed = new SliderSetting("Скорость", 1.0, 0.1, 40.0, 0.1).setVisible(() -> mode.is("Vanilla"));
    public SliderSetting vulcanXzSpeed = new SliderSetting("Скорость", 1.0, 0.1, 40.0, 0.1).setVisible(() -> mode.is("Vulcan XZ"));
    public final SliderSetting vulcanXzBlockInterval = new SliderSetting("Блок каждые N тиков", 20.0, 10.0, 80.0, 5.0).setVisible(() -> mode.is("Vulcan XZ"));

    public final SliderSetting airJumpRiseSpeed = new SliderSetting("Скорость подъёма", 0.4, 0.05, 1.5, 0.05).setVisible(() -> mode.is("FunSky"));
    public final SliderSetting airJumpSpeed = new SliderSetting("Скорость", 1.0, 0.1, 40.0, 0.1).setVisible(() -> mode.is("FunSky"));
    public final SliderSetting airJumpDescendSpeed = new SliderSetting("Скорость спуска", 0.04, 0.01, 1.0, 0.01).setVisible(() -> mode.is("FunSky"));

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

    private double vulcanXzLockedY;
    private int vulcanXzBlockTickCounter;

    @Override
    public void onEnable() {
        super.onEnable();
        resetVulcanState();
        resetAntiKickState();
        resetVulcanXzState();
        if (mc.player != null) {
            vulcanStartHeight = mc.player.getY();
            vulcanXzLockedY = mc.player.getY();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetVulcanState();
        resetAntiKickState();
        resetVulcanXzState();
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
    public void onUpdateVulcanXz(EventTick event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!mode.is("Vulcan XZ")) return;

        handleVulcanXzMode();
    }

    @Subscribe
    public void onUpdateAirJump(EventTick event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!mode.is("FunSky")) return;

        handleAirJumpMode();
    }

    @Subscribe
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!mode.is("FunSky")) return;
        if (event.getType() != EventPacket.Type.SEND) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.isOnGround()) return;

            event.cancelEvent();

            double x = packet.getX(mc.player.getX());
            double y = packet.getY(mc.player.getY());
            double z = packet.getZ(mc.player.getZ());
            float yaw = packet.getYaw(mc.player.getYaw());
            float pitch = packet.getPitch(mc.player.getPitch());
            boolean collision = mc.player.horizontalCollision;

            PlayerMoveC2SPacket modifiedPacket;

            if (packet.changesPosition() && packet.changesLook()) {
                modifiedPacket = new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, true, collision);
            } else if (packet.changesPosition()) {
                modifiedPacket = new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true, collision);
            } else if (packet.changesLook()) {
                modifiedPacket = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, true, collision);
            } else {
                modifiedPacket = new PlayerMoveC2SPacket.OnGroundOnly(true, collision);
            }

            mc.getNetworkHandler().sendPacket(modifiedPacket);
        }
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

    private void resetVulcanXzState() {
        vulcanXzLockedY = 0.0;
        vulcanXzBlockTickCounter = 0;
    }

    private void handleAirJumpMode() {
        if (mc.options.jumpKey.isPressed()) {
            // Плавный подъём: небольшой шаг вверх каждый тик, пока зажат пробел
            double targetY = mc.player.getY() + airJumpRiseSpeed.getValue();

            NetworkUtils.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));

            NetworkUtils.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), targetY, mc.player.getZ(), false, mc.player.horizontalCollision));

            mc.player.setPos(mc.player.getX(), targetY, mc.player.getZ());
            mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
            mc.player.setOnGround(false);
        } else {
            // Пассивный спуск с настраиваемой скоростью
            if (!mc.player.isOnGround()) {
                mc.player.setVelocity(mc.player.getVelocity().x, -airJumpDescendSpeed.getValue(), mc.player.getVelocity().z);
            }
            // Быстрый спуск по шифту
            if (mc.options.sneakKey.isPressed()) {
                mc.player.setVelocity(mc.player.getVelocity().x, -airJumpSpeed.getValue(), mc.player.getVelocity().z);
            }
        }

        // Горизонтальное движение
        if (MoveUtil.hasPlayerMovement()) {
            double[] dir = MoveUtil.calculateDirection(airJumpSpeed.getValue());
            mc.player.setVelocity(dir[0], mc.player.getVelocity().y, dir[1]);
        }

        mc.player.fallDistance = 0;
    }

    private void handleVulcanXzMode() {
        // Лочим вертикаль на высоте включения
        mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        mc.player.setPos(mc.player.getX(), vulcanXzLockedY, mc.player.getZ());

        // Горизонтальное движение как в vanilla fly
        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        if (forward != 0 || strafe != 0) {
            double angle = Math.atan2(-strafe, forward);
            double finalYaw = Math.toRadians(yaw) + angle;
            double speedVal = vulcanXzSpeed.getValue();

            mc.player.setVelocity(
                    -Math.sin(finalYaw) * speedVal,
                    0,
                    Math.cos(finalYaw) * speedVal
            );
        }

        mc.player.setOnGround(false);
        mc.player.fallDistance = 0;

        // Ставим блок под себя по логике airplace каждые N тиков
        vulcanXzBlockTickCounter++;
        if (vulcanXzBlockTickCounter >= vulcanXzBlockInterval.getIntValue()) {
            vulcanXzBlockTickCounter = 0;
            placeBlockUnderSelf();
        }
    }

    private void placeBlockUnderSelf() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Hand hand = getPlaceableHand();
        if (hand == null) return;

        BlockPos pos = BlockPos.ofFloored(mc.player.getX(), vulcanXzLockedY - 1.0, mc.player.getZ());
        if (!mc.world.getBlockState(pos).isReplaceable()) return;

        Vec3d hitVec = Vec3d.ofCenter(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);

        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        if (result.isAccepted()) {
            mc.player.swingHand(hand);
        }
    }

    private Hand getPlaceableHand() {
        if (isPlaceable(mc.player.getMainHandStack())) return Hand.MAIN_HAND;
        if (isPlaceable(mc.player.getOffHandStack())) return Hand.OFF_HAND;
        return null;
    }

    private boolean isPlaceable(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BlockItem || item instanceof SpawnEggItem || item instanceof FireworkRocketItem || item instanceof ArmorStandItem;
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
