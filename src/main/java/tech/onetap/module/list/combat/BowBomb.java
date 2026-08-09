package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.packet.NetworkUtils;

import java.util.Random;

@ModuleInformation(moduleName = "BowBomb", moduleCategory = ModuleCategory.COMBAT)
public class BowBomb extends Module {

    private final BooleanSetting rotation = new BooleanSetting("Ротация", false);
    private final ModeSetting mode = new ModeSetting("Режим", "Maximum", "Normal", "Maximum", "Factorised");
    private final SliderSetting factor = new SliderSetting("Factor", 1, 1, 20, 1);
    private final ModeSetting exploit = new ModeSetting("Exploit", "Strong", "Strong", "Fast", "Strict", "Phobos", "WB");
    private final BooleanSetting minimize = new BooleanSetting("Minimize", false);
    private final SliderSetting delay = new SliderSetting("Delay", 5, 0, 10, 0.5);
    private final ModeListSetting selection = new ModeListSetting("Selection",
            new BooleanSetting("Bows", true),
            new BooleanSetting("EPearls", true),
            new BooleanSetting("XP", true),
            new BooleanSetting("Eggs", true),
            new BooleanSetting("SplashPotions", true),
            new BooleanSetting("Snowballs", true));

    private final StopWatch delayTimer = new StopWatch();
    private final Random rnd = new Random();

    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.SEND) return;
        if (mc.player == null || mc.world == null) return;
        if (!delayTimer.isReached((long) (delay.getValue() * 1000))) return;

        Packet<?> packet = event.getPacket();

        boolean bowRelease = packet instanceof PlayerActionC2SPacket action
                && action.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
                && mc.player.getActiveItem().getItem() == Items.BOW
                && selection.isEnabled("Bows");

        boolean interact = packet instanceof PlayerInteractItemC2SPacket interactPacket
                && interactPacket.getHand() == Hand.MAIN_HAND
                && ((mc.player.getMainHandStack().isOf(Items.ENDER_PEARL) && selection.isEnabled("EPearls"))
                || (mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE) && selection.isEnabled("XP"))
                || (mc.player.getMainHandStack().isOf(Items.EGG) && selection.isEnabled("Eggs"))
                || (mc.player.getMainHandStack().isOf(Items.SPLASH_POTION) && selection.isEnabled("SplashPotions"))
                || (mc.player.getMainHandStack().isOf(Items.SNOWBALL) && selection.isEnabled("Snowballs")));

        if (!bowRelease && !interact) return;

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));

        double[] strictDirection = new double[]{
                100f * -Math.sin(Math.toRadians(mc.player.getYaw())),
                100f * Math.cos(Math.toRadians(mc.player.getYaw()))};

        switch (exploit.getValue()) {
            case "Fast" -> {
                for (int i = 0; i < getRuns(); i++) {
                    spoof(mc.player.getX(), minimize.getValue() ? mc.player.getY() : mc.player.getY() - 1e-10, mc.player.getZ(), true);
                    spoof(mc.player.getX(), mc.player.getY() + 1e-10, mc.player.getZ(), false);
                }
            }
            case "Strong" -> {
                for (int i = 0; i < getRuns(); i++) {
                    spoof(mc.player.getX(), mc.player.getY() + 1e-10, mc.player.getZ(), false);
                    spoof(mc.player.getX(), minimize.getValue() ? mc.player.getY() : mc.player.getY() - 1e-10, mc.player.getZ(), true);
                }
            }
            case "Phobos" -> {
                for (int i = 0; i < getRuns(); i++) {
                    spoof(mc.player.getX(), mc.player.getY() + 0.00000000000013, mc.player.getZ(), true);
                    spoof(mc.player.getX(), mc.player.getY() + 0.00000000000027, mc.player.getZ(), false);
                }
            }
            case "Strict" -> {
                for (int i = 0; i < getRuns(); i++) {
                    if (rnd.nextBoolean()) {
                        spoof(mc.player.getX() - strictDirection[0], mc.player.getY(), mc.player.getZ() - strictDirection[1], false);
                    } else {
                        spoof(mc.player.getX() + strictDirection[0], mc.player.getY(), mc.player.getZ() + strictDirection[1], true);
                    }
                }
            }
            case "WB" -> {
                for (int i = 0; i < getRuns(); i++) {
                    spoof(mc.player.getX() + getWorldBorderRnd(), mc.player.getY(), mc.player.getZ() + getWorldBorderRnd(), false);
                }
            }
        }
        delayTimer.reset();
    }

    private void spoof(double x, double y, double z, boolean ground) {
        if (rotation.getValue()) {
            NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.Full(x, y, z, mc.player.getYaw(), mc.player.getPitch(), ground, mc.player.horizontalCollision));
        } else {
            NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, ground, mc.player.horizontalCollision));
        }
    }

    private int getRuns() {
        return switch (mode.getValue()) {
            case "Normal" -> (int) Math.floor(factor.getValue());
            case "Factorised" -> 10 + (int) (factor.getValue() - 1);
            default -> (int) (30f * factor.getValue());
        };
    }

    private int getWorldBorderRnd() {
        if (mc.isInSingleplayer()) return 1;

        int n = rnd.nextInt(29000000);
        if (rnd.nextBoolean()) return n;
        return -n;
    }
}
