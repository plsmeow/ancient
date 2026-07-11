package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.ui.ClickGuiFrame;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.other.SlownessManager;

@ModuleInformation(moduleName = "Gui Move", moduleCategory = ModuleCategory.MOVEMENT)
public class GuiMove extends Module {

    private final BooleanSetting slowness = new BooleanSetting("Замедление", false);
    // Добавляем режимы свапа, аналогичные другим боевым модулям
    private final ModeSetting mode = modeCreate();

    @Subscribe
    private void onGameUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;

        if (!(mc.currentScreen instanceof ChatScreen) && mc.currentScreen != null && shouldAllowMovement()) {
            for (KeyBinding k : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey}) {
                k.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(),
                        InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));
            }
        }
    }

    private boolean shouldAllowMovement() {
        if (mc.currentScreen instanceof ClickGuiFrame) {
            return true;
        }

        if (!(mc.currentScreen instanceof HandledScreen)) {
            return !slowness.getValue() || SlownessManager.slowTasksIsEmpty();
        }

        if (slowness.getValue()) {
            if (!SlownessManager.slowTasksIsEmpty()) return false;
            if (mc.currentScreen instanceof InventoryScreen && hasArmorInSlots()) return false;
        }

        return true;
    }

    private boolean hasArmorInSlots() {
        return !mc.player.currentScreenHandler.getSlot(1).getStack().isEmpty()
                || !mc.player.currentScreenHandler.getSlot(2).getStack().isEmpty()
                || !mc.player.currentScreenHandler.getSlot(3).getStack().isEmpty()
                || !mc.player.currentScreenHandler.getSlot(4).getStack().isEmpty();
    }

    private boolean shouldCloseAfterClick(ClickSlotC2SPacket click) {
        if (mode.is("Grim") && !(mc.currentScreen instanceof InventoryScreen)) return false;

        return (click.getActionType() == SlotActionType.SWAP && click.getSlot() != 1 && click.getSlot() != 2 && click.getSlot() != 3 && click.getSlot() != 4)
                || click.getActionType() == SlotActionType.QUICK_MOVE;
    }

    @Subscribe
    private void onMoveInput(MoveInputEvent e) {
        if (!slowness.getValue() || !mode.is("Grim") || SlownessManager.slowTasksIsEmpty()) return;

        e.forward *= 0.2f;
        e.strafe *= 0.2f;
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null) return;
        if (!slowness.getValue() || mode.getValue().equals("Vanilla")) return;

        if (e.getPacket() instanceof ClickSlotC2SPacket click && mc.currentScreen instanceof HandledScreen) {
            // Настройка задержки в зависимости от выбранного режима
            int delay = switch (mode.getValue()) {
                case "Grim" -> 50;
                case "Polar" -> 100;
                default -> 60;
            };

            boolean closeAfterClick = shouldCloseAfterClick(click);

            if (mode.is("Grim")) {
                SlownessManager.addTask(new SlownessManager.SlowTask(delay, 0, () -> {
                    NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                    NetworkUtils.sendSilentPacket(click);
                    if (closeAfterClick)
                        NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                    NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
                }));
                e.cancelEvent();
                return;
            }

            SlownessManager.addTask(new SlownessManager.SlowTask(delay, 0, () -> {
                NetworkUtils.sendSilentPacket(click);
                if (closeAfterClick)
                    NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            }));
            e.cancelEvent();
        }

        if (e.getPacket() instanceof CloseHandledScreenC2SPacket) {
            SlownessManager.addTask(new SlownessManager.SlowTask(60, 0, () -> {
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            }));
            e.cancelEvent();
        }
    }
}
