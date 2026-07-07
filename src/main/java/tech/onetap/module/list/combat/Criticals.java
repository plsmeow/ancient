package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.PlayerInput;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.other.WorldUtils;

@ModuleInformation(moduleName = "Criticals", moduleCategory = ModuleCategory.COMBAT)
public class Criticals extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "Grim", "Grim", "Packet", "UpdatedNCP");

    public static boolean killAuraTriggered;

    @Subscribe
    private void onAttack(EventAttack e) {
        if (killAuraTriggered) return;
        if (mc.player == null || mc.world == null) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        doCrit();
    }

    public void doCrit() {
        if (mc.player == null || mc.world == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(
                new PlayerInput(
                        mc.player.input.playerInput.forward(),
                        mc.player.input.playerInput.backward(),
                        mc.player.input.playerInput.left(),
                        mc.player.input.playerInput.right(),
                        mc.player.input.playerInput.jump(),
                        mc.player.input.playerInput.sneak(),
                        false
                )
        ));

        switch (mode.getValue()) {
            case "Grim" -> {
                if (mc.player.fallDistance != 0 || mc.player.isOnGround()) return;
                if (!WorldUtils.isInWeb()) return;

                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();

                NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 0.0625, z, false, false
                ));
                NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y + 0.0015, z, false, false
                ));
                NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, y, z, false, false
                ));
            }
            case "Packet" -> {
                if (!canPacketCrit()) return;

                NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY() + 0.0625, mc.player.getZ(), false, false
                ));
                NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false
                ));
            }
            case "UpdatedNCP" -> {
                if (!canPacketCrit()) return;

                NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY() + 0.0000008, mc.player.getZ(), false, false
                ));
                NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false
                ));
            }
        }
    }

    private boolean canPacketCrit() {
        return mc.player.isOnGround()
                && !mc.player.getAbilities().flying
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.isInLava()
                && !mc.player.isSubmergedInWater()
                && mc.world.getBlockState(mc.player.getBlockPos()).getBlock() != Blocks.LADDER;
    }
}
