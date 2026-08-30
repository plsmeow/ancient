package tech.onetap.module.list.player;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.commands.defaults.ClipBypass;
import tech.onetap.util.math.RotationUtil;

import java.util.Locale;

@ModuleInformation(moduleName = "Player TP", moduleDesc = "Телепорт к ближайшему к прицелу игроку (по FOV)", moduleCategory = ModuleCategory.PLAYER)
public class PlayerTP extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Pos", "Pos", "Bypass", "Vault", "FS");

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            setEnabled(false);
            return;
        }

        AbstractClientPlayerEntity target = findTarget();
        if (target == null) {
            logDirect(Formatting.RED + "Игрок рядом не найден.");
            setEnabled(false);
            return;
        }

        if (ClipBypass.teleport(this, target.getX(), target.getY(), target.getZ(),
                mode.getValue().toLowerCase(Locale.ROOT))) {
            logDirect("Телепортировано к игроку " + target.getName().getString()
                    + " [" + mode.getValue() + "]");
        }
        setEnabled(false);
    }

    private AbstractClientPlayerEntity findTarget() {
        AbstractClientPlayerEntity best = null;
        float bestFov = Float.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;

        Vec3d eyes = mc.player.getEyePos();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            float[] angles = RotationUtil.calculateAngle(eyes, player.getBoundingBox().getCenter());
            float fov = RotationUtil.calculateFov(yaw, pitch, angles[0], angles[1]);
            double distance = player.squaredDistanceTo(mc.player);

            if (fov < bestFov || (fov == bestFov && distance < bestDistance)) {
                bestFov = fov;
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }
}
