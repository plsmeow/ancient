package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Утилита для телепортации с разными типами байпаса.
 * Типы взяты из модуля TPLoot: pos, bypass, vault.
 */
public final class ClipBypass {

    public static final List<String> BYPASS_TYPES = List.of("pos", "bypass", "vault");

    private ClipBypass() {}

    /**
     * Выполняет телепортацию на заданную позицию используя указанный тип байпаса.
     *
     * @param targetX  целевая координата X
     * @param targetY  целевая координата Y
     * @param targetZ  целевая координата Z
     * @param bypass   тип байпаса: "pos", "bypass", "vault" или null/пусто для дефолтной логики
     */
    public static void teleport(double targetX, double targetY, double targetZ, String bypass) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return;

        double startX = player.getX();
        double startY = player.getY();
        double startZ = player.getZ();

        String mode = bypass == null ? "" : bypass.toLowerCase();

        switch (mode) {
            case "bypass" -> bypassMode(player, targetX, targetY, targetZ);
            case "vault" -> vaultMode(player, targetX, targetY, targetZ);
            default -> posMode(player, targetX, targetY, targetZ);
        }
    }

    /**
     * Pos — простая телепортация: setPosition + один пакет позиции.
     * Это дефолтная логика, которая использовалась в vclip/hclip ранее.
     */
    private static void posMode(ClientPlayerEntity player, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(
                    player.isOnGround(), player.horizontalCollision));
        }
        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                x, y, z, false, player.horizontalCollision));
        player.setPosition(x, y, z);
    }

    /**
     * Bypass — setPosition + спам пакетов позиции (10 штук).
     */
    private static void bypassMode(ClientPlayerEntity player, double x, double y, double z) {
        player.setPosition(x, y, z);
        for (int i = 0; i < 10; i++) {
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    x, y, z, true, player.horizontalCollision));
        }
    }

    /**
     * Vault — телепорт вверх, к цели, вниз по аналогии с TPLoot Vault режимом.
     */
    private static void vaultMode(ClientPlayerEntity player, double x, double y, double z) {
        Entity entity = player.hasVehicle() ? player.getVehicle() : player;
        if (entity == null) return;

        Vec3d currentPos = entity.getPos();
        Vec3d upPos = currentPos.add(0, 129.0, 0);
        Vec3d aboveTarget = new Vec3d(x, upPos.y, z);
        Vec3d downPos = new Vec3d(x, y, z);
        Vec3d finalPos = downPos.add(0, 0.01, 0);

        for (int i = 0; i < 13; i++) {
            if (player.hasVehicle() && player.getVehicle() != null) {
                player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(player.getVehicle()));
            } else {
                player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(
                        false, player.horizontalCollision));
            }
        }

        sendVaultMove(player, entity, upPos);
        sendVaultMove(player, entity, aboveTarget);
        sendVaultMove(player, entity, downPos);
        sendVaultMove(player, entity, finalPos);

        entity.setPosition(finalPos.x, finalPos.y, finalPos.z);
        if (entity != player) {
            player.setPosition(finalPos.x, finalPos.y, finalPos.z);
        }
    }

    private static void sendVaultMove(ClientPlayerEntity player, Entity entity, Vec3d pos) {
        if (player.networkHandler == null) return;

        if (entity == player) {
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    pos.x, pos.y, pos.z, false, player.horizontalCollision));
        } else if (player.getVehicle() != null) {
            player.networkHandler.sendPacket(new VehicleMoveC2SPacket(
                    pos, player.getVehicle().getYaw(), player.getVehicle().getPitch(), false));
        }
    }
}
