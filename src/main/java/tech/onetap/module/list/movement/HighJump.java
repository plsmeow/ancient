package tech.onetap.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.player.ElytraHelper;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.base.Instance;

@ModuleInformation(moduleName = "High Jump", moduleDesc = "Высокий прыжок", moduleCategory = ModuleCategory.MOVEMENT)
public class HighJump extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Funtime", "Funtime", "Grim Shulker", "Funsky Elytra");
    private final SliderSetting funtimeJumpY = new SliderSetting("Funtime JumpY", 0.42, 0.1, 1.0, 0.01).setVisible(() -> mode.is("Funtime"));
    private final SliderSetting shulkerJumpY = new SliderSetting("Shulker JumpY", 1.0, 0.5, 5.0, 0.1).setVisible(() -> mode.is("Grim Shulker"));
    private final SliderSetting funskyVelocityY = new SliderSetting("Funsky Скорость", 1.5, 0.5, 5.0, 0.1).setVisible(() -> mode.is("Funsky Elytra"));
    private final SliderSetting funskyVelocityForward = new SliderSetting("Funsky Вперёд", 0, 0, 5.0, 0.1).setVisible(() -> mode.is("Funsky Elytra"));
    private final SliderSetting funskyDelay = new SliderSetting("Funsky Задержка", 2, 1, 10, 1).setVisible(() -> mode.is("Funsky Elytra"));
    private final SliderSetting funskyWaitTicks = new SliderSetting("Funsky Ожидание", 10, 1, 60, 1).setVisible(() -> mode.is("Funsky Elytra"));

    private enum FunskyStage { IDLE, AFTER_COMMAND, AFTER_ABILITY, LAUNCHED }
    private FunskyStage funskyStage = FunskyStage.IDLE;
    private int funskyTickCounter = 0;
    private boolean funskySwappedToElytra = false;

    private static final int FUNSKY_SWAP_TIMEOUT_TICKS = 20;

    @EventHandler
    public void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Funtime")) {
            handleFuntimeSnow();
        } else if (mode.is("Grim Shulker")) {
            handleGrimShulker();
        } else if (mode.is("Funsky Elytra")) {
            handleFunskyElytra();
        }
    }

    private void handleFuntimeSnow() {
        if (mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, funtimeJumpY.getFloatValue(), mc.player.getVelocity().z);
        }
    }

    private void handleGrimShulker() {
        BlockPos origin = mc.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterate(origin.add(-1, -1, -1), origin.add(1, 1, 1))) {
            BlockEntity be = mc.world.getBlockEntity(pos);
            if (!(be instanceof ShulkerBoxBlockEntity shulker)) continue;

            ShulkerBoxBlockEntity.AnimationStage stage = shulker.getAnimationStage();
            if (stage != ShulkerBoxBlockEntity.AnimationStage.OPENING
                    && stage != ShulkerBoxBlockEntity.AnimationStage.OPENED) continue;

            double dx = mc.player.getX() - (pos.getX() + 0.5);
            double dz = mc.player.getZ() - (pos.getZ() + 0.5);
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDistance > 1.0) continue;

            mc.player.setVelocity(mc.player.getVelocity().x, shulkerJumpY.getFloatValue(), mc.player.getVelocity().z);
            break;
        }
    }

    private void handleFunskyElytra() {
        switch (funskyStage) {
            case IDLE -> {
                if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                    if (funskyTickCounter == 0) {
                        Instance.get(ElytraHelper.class).swap(false);
                        funskySwappedToElytra = true;
                    }

                    funskyTickCounter++;
                    if (funskyTickCounter >= FUNSKY_SWAP_TIMEOUT_TICKS) {
                        setEnabled(false);
                    }
                    return;
                }

                mc.player.networkHandler.sendChatCommand("fly");

                funskyStage = FunskyStage.AFTER_COMMAND;
                funskyTickCounter = 0;
            }
            case AFTER_COMMAND -> {
                funskyTickCounter++;
                if (funskyTickCounter >= funskyDelay.getValue()) {
                    PlayerAbilities abilities = mc.player.getAbilities();
                    abilities.flying = true;
                    mc.player.networkHandler.sendPacket(new UpdatePlayerAbilitiesC2SPacket(abilities));

                    funskyStage = FunskyStage.AFTER_ABILITY;
                    funskyTickCounter = 0;
                }
            }
            case AFTER_ABILITY -> {
                funskyTickCounter++;
                if (funskyTickCounter >= funskyDelay.getValue()) {
                    double vx = mc.player.getVelocity().x;
                    double vz = mc.player.getVelocity().z;

                    float forward = funskyVelocityForward.getFloatValue();
                    if (forward > 0) {
                        Vec3d direction = Vec3d.fromPolar(0, mc.player.getYaw());
                        vx = direction.x * forward;
                        vz = direction.z * forward;
                    }

                    mc.player.setVelocity(vx, funskyVelocityY.getFloatValue(), vz);

                    funskyStage = FunskyStage.LAUNCHED;
                    funskyTickCounter = 0;
                }
            }
            case LAUNCHED -> {
                funskyTickCounter++;
                if (funskyTickCounter >= funskyWaitTicks.getValue()) {
                    if (funskySwappedToElytra) {
                        Instance.get(ElytraHelper.class).swap(true);
                    }

                    funskyStage = FunskyStage.IDLE;
                    funskyTickCounter = 0;
                    funskySwappedToElytra = false;
                    setEnabled(false);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        funskyStage = FunskyStage.IDLE;
        funskyTickCounter = 0;
        funskySwappedToElytra = false;
    }
}
