package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.QuickLogger;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.List;
import java.util.Locale;

/**
 * Утилита для телепортации с разными типами байпаса.
 * Типы взяты из модуля TPLoot: pos, bypass, vault.
 */
public final class ClipBypass {

    public static final List<String> BYPASS_TYPES = List.of("pos", "bypass", "vault");
    public static final int DEFAULT_PACKETS = 10;
    public static final int MAX_PACKETS = 1000;

    /**
     * Результат парсинга аргументов байпаса: режим (null — не указан) и количество пакетов.
     */
    public record BypassArgs(String mode, int packets) {}

    /** Сентинел ошибки парсинга (сообщение пользователю уже выведено). */
    public static final BypassArgs INVALID = new BypassArgs(null, -1);

    private ClipBypass() {}

    /**
     * Парсит необязательные аргументы байпаса: [режим] [пакеты].
     * Пакеты можно указать только после режима, по умолчанию {@link #DEFAULT_PACKETS}.
     *
     * @return распарсенные аргументы или {@link #INVALID} при ошибке (сообщение уже выведено)
     */
    public static BypassArgs parseArgs(QuickLogger logger, IArgConsumer args) throws CommandException {
        String mode = null;
        int packets = DEFAULT_PACKETS;

        if (args.hasAny()) {
            mode = args.getString().toLowerCase(Locale.ROOT);
            if (!BYPASS_TYPES.contains(mode)) {
                logger.logDirect(Formatting.RED + "Неизвестный тип байпаса: " + mode);
                logger.logDirect(Formatting.GRAY + "Доступные: " + String.join(", ", BYPASS_TYPES));
                return INVALID;
            }
        }

        if (mode != null && args.hasAny()) {
            String input = args.getString();
            try {
                packets = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                logger.logDirect(Formatting.RED + input + " не является числом.");
                return INVALID;
            }
            if (packets < 1 || packets > MAX_PACKETS) {
                logger.logDirect(Formatting.RED + "Количество пакетов должно быть от 1 до " + MAX_PACKETS + ".");
                return INVALID;
            }
        }

        return new BypassArgs(mode, packets);
    }

    /**
     * Выполняет телепортацию на заданную позицию используя указанный тип байпаса.
     *
     * @param targetX  целевая координата X
     * @param targetY  целевая координата Y
     * @param targetZ  целевая координата Z
     * @param bypass   тип байпаса: "pos", "bypass", "vault" или null/пусто для дефолтной логики
     */
    public static void teleport(double targetX, double targetY, double targetZ, String bypass) {
        teleport(targetX, targetY, targetZ, bypass, DEFAULT_PACKETS);
    }

    /**
     * Выполняет телепортацию на заданную позицию используя указанный тип байпаса.
     *
     * @param targetX  целевая координата X
     * @param targetY  целевая координата Y
     * @param targetZ  целевая координата Z
     * @param bypass   тип байпаса: "pos", "bypass", "vault" или null/пусто для дефолтной логики
     * @param packets  количество пакетов позиции для режима "bypass"
     */
    public static void teleport(double targetX, double targetY, double targetZ, String bypass, int packets) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return;

        String mode = bypass == null ? "" : bypass.toLowerCase(Locale.ROOT);

        switch (mode) {
            case "bypass" -> bypassMode(player, targetX, targetY, targetZ, packets);
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
     * Bypass — setPosition + спам пакетов позиции.
     */
    private static void bypassMode(ClientPlayerEntity player, double x, double y, double z, int packets) {
        player.setPosition(x, y, z);
        for (int i = 0; i < packets; i++) {
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
