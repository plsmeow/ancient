package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventNoSlow;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.simulate.SimulatedPlayer;

@ModuleInformation(moduleName = "No Slow", moduleCategory = ModuleCategory.MOVEMENT)
public class NoSlow extends Module {

    private final ModeSetting mode = new ModeSetting("Mode", "Vanilla", "Vanilla", "Grim", "Grim2");

    @Subscribe
    private void onNoSlow(EventNoSlow e) {
        switch (mode.getValue()) {
            case "Vanilla" -> {
                if (!shouldKeepAuraFallSlowdown()) mc.player.setSprinting(true);
                e.cancelEvent();
            }
            case "Grim" -> {
                mc.player.setSprinting(mc.player.getItemUseTime() > 4
                        && !shouldKeepAuraFallSlowdown()
                        && Onetap.getInstance().getServerManager().getSprintingChangeTicks() > 0);
                if (mc.player.getItemUseTime() % 2 == 0) e.cancelEvent();
            }
            case "Grim2" -> {
                if (mc.player == null || mc.world == null || mc.interactionManager == null || !mc.player.isUsingItem()) return;

                if ((mc.player.getMainHandStack().getUseAction() != UseAction.BLOCK
                        && mc.player.getOffHandStack().getUseAction() != UseAction.EAT
                        || mc.player.getActiveHand() != Hand.MAIN_HAND)
                        && mc.player.isUsingItem()) {
                    mc.player.setSprinting(true);

                    if (mc.player.getActiveHand() == Hand.MAIN_HAND) {
                        mc.interactionManager.sendSequencedPacket(mc.world, sequence ->
                                new PlayerInteractItemC2SPacket(Hand.OFF_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
                        e.cancelEvent();
                    } else {
                        mc.interactionManager.sendSequencedPacket(mc.world, sequence ->
                                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
                        e.cancelEvent();
                    }
                }
            }
        }
    }

    private boolean shouldKeepAuraFallSlowdown() {
        return Onetap.getInstance().getModuleStorage().get(KillAura.class).getTarget() != null
                && SimulatedPlayer.simulateLocalPlayer(1).fallDistance > 0;
    }
}
