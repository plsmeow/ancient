package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.player.combat.PredictUtils;

import java.util.Comparator;

@ModuleInformation(moduleName = "Troll", moduleCategory = ModuleCategory.MOVEMENT)
public class Troll extends Module {
    private final SliderSetting range = new SliderSetting("Радиус", 30.0, 1.0, 100.0, 1.0);
    private final SliderSetting predict = new SliderSetting("Предикт", 3.0, 0.0, 10.0, 0.1);
    private final SliderSetting distance = new SliderSetting("Дистанция", 0.8, 0.1, 3.0, 0.1);

    @Subscribe
    private void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        PlayerEntity target = getNearestPlayer();
        if (target == null) return;

        Vec3d predictedHeadPos = PredictUtils.getPredicted(target, predict.getValue());
        Vec3d forward = Vec3d.fromPolar(0.0f, target.getYaw()).normalize();
        Vec3d teleportPos = predictedHeadPos
                .subtract(0.0, target.getHeight(), 0.0)
                .add(forward.multiply(distance.getValue()));

        teleport(teleportPos);
    }

    private PlayerEntity getNearestPlayer() {
        double maxDistanceSq = range.getValue() * range.getValue();

        return mc.world.getPlayers().stream()
                .filter(this::isValidTarget)
                .filter(player -> mc.player.squaredDistanceTo(player) <= maxDistanceSq)
                .min(Comparator.comparingDouble(player -> mc.player.squaredDistanceTo(player)))
                .orElse(null);
    }

    private boolean isValidTarget(PlayerEntity player) {
        if (player == mc.player) return false;
        if (player instanceof ClientPlayerEntity) return false;
        return player.isAlive() && !player.isSpectator();
    }

    private void teleport(Vec3d pos) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                pos.x,
                pos.y,
                pos.z,
                mc.player.isOnGround(),
                mc.player.horizontalCollision
        ));
        mc.player.setPosition(pos.x, pos.y, pos.z);
    }
}
