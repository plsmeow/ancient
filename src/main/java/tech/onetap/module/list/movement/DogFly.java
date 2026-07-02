package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.PlayerInput;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "Dog Fly", moduleDesc = "Позволяет летать на нагруднике", moduleCategory = ModuleCategory.MOVEMENT)
public class DogFly extends Module {

    StopWatch stopWatch = new StopWatch();

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (InventoryUtil.searchItem(Items.ELYTRA) == -1) {
            logDirect(Text.literal("Нема элитр падла").formatted(Formatting.RED));
            setEnabled(false);
            return;
        }

        if (InventoryUtil.searchItem(Items.FIREWORK_ROCKET) == -1) {
            logDirect(Text.literal("Нема феерверков падла").formatted(Formatting.RED));
            setEnabled(false);
            return;
        }

        if (mc.player.isOnGround() && !mc.player.isInLava() && !mc.player.isSwimming() && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
            mc.player.jump();
        }

        if (stopWatch.isReached(550)) {
            if (!mc.player.isOnGround() && !mc.player.isSwimming() && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
                int slot = InventoryUtil.searchItem(Items.ELYTRA);
                int fireworkSlot = InventoryUtil.searchItem(Items.FIREWORK_ROCKET);
                if (slot != -1 && fireworkSlot != -1) {
                    if (slot >= 0 && slot <= 8) {
                        InventoryUtil.swapWithBypassGrim(() -> {
                            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
                            NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            mc.player.startGliding();
                            NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, true, false, false)));
                            NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                            InventoryUtil.swapAndUseHvH(Items.FIREWORK_ROCKET);
                            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
                        });
                    } else {
                        InventoryUtil.swapWithBypassGrim(() -> {
                            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                            mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
                            NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            mc.player.startGliding();
                            NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, true, false, false)));
                            NetworkUtils.sendSilentPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                            InventoryUtil.swapAndUseHvH(Items.FIREWORK_ROCKET);
                            mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
                            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                        });
                    }
                    stopWatch.reset();
                }
            }
        }
    }
}