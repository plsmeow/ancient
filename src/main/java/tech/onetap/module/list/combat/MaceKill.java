package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "MaceKill", moduleDesc = "Усиливает булаву через packet criticals", moduleCategory = ModuleCategory.COMBAT)
public final class MaceKill extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Default", "Default", "Custom", "Calculate", "Matrix", "NCP", "Sample");
    private final SliderSetting fallHeight = new SliderSetting("Fall Height", 20, 5, 150, 1);
    private final SliderSetting packetCount = new SliderSetting("Кол-во пакетов", 2, 1, 15, 1)
            .setVisible(() -> mode.is("Custom"));
    private final SliderSetting heightSeparator = new SliderSetting("Height Separator", 8.9, 0.1, 20, 0.1)
            .setVisible(() -> mode.is("NCP"));

    public static boolean cancelCrit;
    public static boolean killAuraTriggered;

    @Subscribe
    public void onAttack(EventAttack event) {
        if (killAuraTriggered) return;
        if (mc.player == null || mc.world == null) return;
        if (event.getEntity() instanceof EndCrystalEntity || cancelCrit) return;

        KillAura aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        boolean autoMaceActive = aura != null && aura.isEnabled() && aura.autoMace.getValue();
        if (!autoMaceActive) {
            ItemStack mainHand = mc.player.getMainHandStack();
            boolean isMace = mainHand.isOf(Items.MACE);
            boolean isCustomMace = mainHand.getName().getString().contains("1.21 Mace");
            if (!isMace && !isCustomMace) return;
        }

        doCrit();
    }

    public void doCrit() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return;

        int height = determineHeight();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        switch (mode.getValue()) {
            case "Default" -> {
                sendTpPacket(x, y + height, z, false);
                sendTpPacket(x, y, z, false);
            }
            case "Custom" -> runIterationMode(x, y, z, height, packetCount.getIntValue(), 0);
            case "Calculate" -> runIterationMode(x, y, z, height, calcIterations(height), 0);
            case "Sample" -> runIterationMode(x, y, z, height, calcIterations(height), 1);
            case "Matrix" -> {
                if (height > 10) {
                    int times = (int) Math.ceil(Math.abs(height / 10.0));
                    for (int i = 0; i < times; i++) {
                        sendTpPacket(x, y, z, false);
                    }
                } else {
                    for (int i = 0; i < 2; i++) {
                        sendTpPacket(x, y, z, mc.player.isOnGround());
                    }
                }
                sendTpPacket(x, y + height, z, false);
                sendTpPacket(x, y, z, false);
            }
            case "NCP" -> {
                double targetY = y + height;
                double distance = Math.abs(y - targetY);
                int packets = (int) (distance / heightSeparator.getValue());
                for (int i = 0; i < packets; i++) {
                    sendTpPacket(x, y, z, false);
                }
                sendTpPacket(x, targetY, z, false);
                for (int i = 0; i < packets; i++) {
                    sendTpPacket(x, y, z, false);
                }
            }
        }
    }

    private int calcIterations(int height) {
        return height < 11 ? 1 : height < 31 ? 2 : (int) Math.ceil(height / 10.0f);
    }

    private void runIterationMode(double x, double y, double z, int height, int iterations, int decrement) {
        int count = iterations - decrement;
        if (!mc.player.isOnGround()) {
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y, z, false);
            }
            sendTpPacket(x, y + height, z, false);
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y, z, false);
            }
        } else {
            float rise = 0.5f;
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y + rise, z, false);
                mc.player.setPosition(x, y + rise, z);
            }
            sendTpPacket(x, y + height, z, false);
            for (int i = 0; i < count; i++) {
                sendTpPacket(x, y + rise, z, false);
                mc.player.setPosition(x, y + rise, z);
            }
        }
    }

    private int determineHeight() {
        Box boundingBox = mc.player.getBoundingBox();
        for (int i = fallHeight.getIntValue(); i >= 1; i--) {
            Box newBB = boundingBox.offset(0, i, 0);
            boolean noCollision = true;
            for (VoxelShape shape : mc.world.getCollisions(mc.player, newBB)) {
                if (shape.isEmpty()) continue;
                noCollision = false;
                break;
            }
            if (noCollision) return i;
        }
        return 0;
    }

    private void sendTpPacket(double x, double y, double z, boolean onGround) {
        NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision));
    }
}
