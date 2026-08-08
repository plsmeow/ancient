package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "Fast Crossbow", moduleDesc = "Превращает арбалет в пулемёт", moduleCategory = ModuleCategory.PLAYER)
public class FastCrossbow extends Module {

    /** Сдвиг хотбара в контейнере инвентаря игрока (слоты 36-44). */
    private static final int HOTBAR_OFFSET = 36;
    /** Значение кнопки SWAP, означающее вторую руку. */
    private static final int OFFHAND_BUTTON = 40;

    private final ModeSetting mode = new ModeSetting("Мод", "Пакеты", "Пакеты", "Свап");
    private final SliderSetting delay = new SliderSetting("Задержка", ValueUnit.countable("тик", "тика", "тиков"), 0, 0, 20, 1);
    private final SliderSetting packets = new SliderSetting("Пакетов за тик", 1, 1, 20, 1).setVisible(() -> mode.is("Пакеты"));
    private final BooleanSetting correctSequence = new BooleanSetting("Верная последовательность", true).setVisible(() -> mode.is("Пакеты"));
    private final BooleanSetting onlyWhenHold = new BooleanSetting("Только при зажатой ПКМ", true);
    private final BooleanSetting noUseCooldown = new BooleanSetting("Без задержки использования", true);

    private int timer;

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        if (noUseCooldown.getValue()) mc.itemUseCooldown = 0;

        var hand = getCrossbowHand();
        if (hand == null) {
            timer = 0;
            return;
        }

        if (onlyWhenHold.getValue() && !mc.options.useKey.isPressed()) {
            timer = 0;
            return;
        }

        var ticks = delay.getIntValue();
        if (ticks > 0) {
            if (timer++ < ticks) return;
            timer = 0;
        }

        if (mode.is("Пакеты")) {
            shootPackets(hand);
        } else {
            shootSwap();
        }
    }

    /**
     * Спамит пакетами использования предмета — сервер обрабатывает каждый как выстрел.
     */
    private void shootPackets(Hand hand) {
        for (var i = 0; i < packets.getIntValue(); i++) {
            if (correctSequence.getValue()) {
                mc.interactionManager.sendSequencedPacket(mc.world, sequence ->
                        new PlayerInteractItemC2SPacket(hand, sequence, mc.player.getYaw(), mc.player.getPitch()));
            } else {
                NetworkUtils.sendPacket(new PlayerInteractItemC2SPacket(hand, 0, mc.player.getYaw(), mc.player.getPitch()));
            }
        }
    }

    /**
     * Свапает арбалет во вторую руку и обратно — каждый свап сбрасывает зарядку,
     * позволяя стрелять без ожидания перезарядки.
     */
    private void shootSwap() {
        if (!mc.player.isUsingItem()) return;
        if (!mc.player.getActiveItem().isOf(Items.CROSSBOW)) return;

        var hand = mc.player.getActiveHand();
        var slot = mc.player.getInventory().selectedSlot + HOTBAR_OFFSET;

        mc.interactionManager.clickSlot(0, slot, OFFHAND_BUTTON, SlotActionType.SWAP, mc.player);
        mc.interactionManager.interactItem(mc.player, opposite(hand));

        mc.interactionManager.clickSlot(0, slot, OFFHAND_BUTTON, SlotActionType.SWAP, mc.player);
        mc.interactionManager.interactItem(mc.player, hand);
    }

    private Hand getCrossbowHand() {
        if (mc.player.getMainHandStack().isOf(Items.CROSSBOW)) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().isOf(Items.CROSSBOW)) return Hand.OFF_HAND;
        return null;
    }

    private Hand opposite(Hand hand) {
        return hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        timer = 0;
    }
}
