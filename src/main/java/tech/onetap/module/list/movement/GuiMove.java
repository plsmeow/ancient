package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.other.SlownessManager;

@ModuleInformation(moduleName = "Gui Move", moduleCategory = ModuleCategory.MOVEMENT)
public class GuiMove extends Module {

    private final BooleanSetting slowness = new BooleanSetting("Замедление", false);
    // Добавляем режимы свапа, аналогичные другим боевым модулям
    private final ModeSetting mode = modeCreate();

    private boolean bool;

    @Subscribe
    private void onGameUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;
        if (!SlownessManager.slowTasksIsEmpty()) return;
        if (mc.currentScreen == null) bool = false;

        if (!(mc.currentScreen instanceof InventoryScreen) && slowness.getValue()) return;

        if (mc.currentScreen instanceof InventoryScreen && (!mc.player.currentScreenHandler.getSlot(1).getStack().isEmpty() || !mc.player.currentScreenHandler.getSlot(2).getStack().isEmpty() || !mc.player.currentScreenHandler.getSlot(3).getStack().isEmpty() || !mc.player.currentScreenHandler.getSlot(4).getStack().isEmpty())) return;

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty() && slowness.getValue() || bool) return;

        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) {
            for (KeyBinding k : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey}) {
                k.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(),
                        InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));
            }
        }
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null) return;
        if (!slowness.getValue() || mode.getValue().equals("Vanilla")) return;

        if (e.getPacket() instanceof ClickSlotC2SPacket click && mc.currentScreen instanceof InventoryScreen) {
            if (click.getActionType() == SlotActionType.PICKUP) bool = true;

            // Настройка задержки в зависимости от выбранного режима
            int delay = switch (mode.getValue()) {
                case "Grim" -> 50;
                case "Polar" -> 100;
                default -> 60;
            };

            SlownessManager.addTask(new SlownessManager.SlowTask(delay, 0, () -> {
                NetworkUtils.sendSilentPacket(click);
                if ((click.getActionType() == SlotActionType.SWAP && click.getSlot() != 1 && click.getSlot() != 2 && click.getSlot() != 3 && click.getSlot() != 4) || click.getActionType() == SlotActionType.QUICK_MOVE)
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