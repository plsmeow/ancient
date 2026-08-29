package tech.onetap.util.commands.defaults;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventTick;
import tech.onetap.util.QuickLogger;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Утилита для телепортации с разными типами байпаса.
 * Типы pos, bypass, vault взяты из модуля TPLoot; fs — телепорт с полётом
 * как в High Jump (режим Funsky Elytra).
 */
public final class ClipBypass {

    public static final List<String> BYPASS_TYPES = List.of("pos", "bypass", "vault", "fs");
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
     * @param bypass   тип байпаса: "pos", "bypass", "vault", "fs" или null/пусто для дефолтной логики
     * @return true, если телепортация выполнена; false — для "fs" при невыполненных условиях
     */
    public static boolean teleport(double targetX, double targetY, double targetZ, String bypass) {
        return teleport(null, targetX, targetY, targetZ, bypass, DEFAULT_PACKETS);
    }

    /**
     * Выполняет телепортацию на заданную позицию используя указанный тип байпаса.
     *
     * @param logger   лог для сообщений об ошибках режима "fs" (может быть null)
     * @param targetX  целевая координата X
     * @param targetY  целевая координата Y
     * @param targetZ  целевая координата Z
     * @param bypass   тип байпаса: "pos", "bypass", "vault", "fs" или null/пусто для дефолтной логики
     * @return true, если телепортация выполнена; false — для "fs" при невыполненных условиях
     */
    public static boolean teleport(QuickLogger logger, double targetX, double targetY, double targetZ, String bypass) {
        return teleport(logger, targetX, targetY, targetZ, bypass, DEFAULT_PACKETS);
    }

    /**
     * Выполняет телепортацию на заданную позицию используя указанный тип байпаса.
     *
     * @param targetX  целевая координата X
     * @param targetY  целевая координата Y
     * @param targetZ  целевая координата Z
     * @param bypass   тип байпаса: "pos", "bypass", "vault", "fs" или null/пусто для дефолтной логики
     * @param packets  количество пакетов позиции для режимов "bypass" и "fs"
     * @return true, если телепортация выполнена; false — для "fs" при невыполненных условиях
     */
    public static boolean teleport(double targetX, double targetY, double targetZ, String bypass, int packets) {
        return teleport(null, targetX, targetY, targetZ, bypass, packets);
    }

    /**
     * Выполняет телепортацию на заданную позицию используя указанный тип байпаса.
     *
     * @param logger   лог для сообщений об ошибках режима "fs" (может быть null)
     * @param targetX  целевая координата X
     * @param targetY  целевая координата Y
     * @param targetZ  целевая координата Z
     * @param bypass   тип байпаса: "pos", "bypass", "vault", "fs" или null/пусто для дефолтной логики
     * @param packets  количество пакетов позиции для режимов "bypass" и "fs"
     * @return true, если телепортация выполнена; false — для "fs" при невыполненных условиях
     */
    public static boolean teleport(QuickLogger logger, double targetX, double targetY, double targetZ, String bypass, int packets) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return false;

        String mode = bypass == null ? "" : bypass.toLowerCase(Locale.ROOT);

        switch (mode) {
            case "fs" -> {
                return fsMode(logger, player, targetX, targetY, targetZ, packets);
            }
            case "bypass" -> bypassMode(player, targetX, targetY, targetZ, packets);
            case "vault" -> vaultMode(player, targetX, targetY, targetZ);
            default -> posMode(player, targetX, targetY, targetZ);
        }
        return true;
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
     * Задержка (в тиках) между шагами fs-последовательности /fly → ability → телепорт.
     * Сервер должен успеть обработать /fly до ability и пакетов телепорта,
     * иначе первый телепорт откатывается (срабатывает только со второго раза).
     */
    public static final int FS_STAGE_DELAY_TICKS = 2;

    /**
     * FS (Funsky) — телепорт с полётом как в High Jump (режим Funsky Elytra):
     * проверяет элитру и что игрок не на земле, затем пошагово
     * /fly → ability → телепорт как в режиме bypass, с задержкой между шагами.
     */
    private static boolean fsMode(QuickLogger logger, ClientPlayerEntity player, double x, double y, double z, int packets) {
        String failReason = funskyFailReason(player);
        if (failReason != null) {
            if (logger != null) {
                logger.logDirect(Formatting.RED + failReason);
            }
            return false;
        }

        enqueueFsSequence(new FsSequence(player, true, x, y, z, packets));
        return true;
    }

    /**
     * Причина, по которой Funsky-телепорт сейчас невозможен
     * (нет элитры или игрок на земле), либо null, если условия выполнены.
     */
    public static String funskyFailReason(ClientPlayerEntity player) {
        if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            return "Для режима fs нужна надетая элитра.";
        }
        if (player.isOnGround()) {
            return "Режим fs работает только в воздухе.";
        }
        return null;
    }

    /**
     * Полёт fs уже активен (ability flying включён) — телепорт можно делать сразу.
     */
    public static boolean isFsFlyReady(ClientPlayerEntity player) {
        return player.getAbilities().flying;
    }

    /**
     * Запускает (однократно) отложенную последовательность /fly → ability,
     * чтобы следующий fs-телепорт сработал с первого раза.
     */
    public static void startFsFlyWarmup(ClientPlayerEntity player) {
        for (FsSequence sequence : pendingFsSequences) {
            if (sequence.player == player) return;
        }
        enqueueFsSequence(new FsSequence(player, false, 0, 0, 0, 0));
    }

    private static final class FsSequence {
        final ClientPlayerEntity player;
        final boolean teleport;
        final double x, y, z;
        final int packets;
        int stage;
        int ticksLeft = FS_STAGE_DELAY_TICKS;

        FsSequence(ClientPlayerEntity player, boolean teleport, double x, double y, double z, int packets) {
            this.player = player;
            this.teleport = teleport;
            this.x = x;
            this.y = y;
            this.z = z;
            this.packets = packets;
        }
    }

    private static final List<FsSequence> pendingFsSequences = new ArrayList<>();
    private static boolean fsTickHooked = false;

    private static void enqueueFsSequence(FsSequence sequence) {
        if (!fsTickHooked) {
            fsTickHooked = true;
            Onetap.getInstance().getEventBus().subscribe(new FsTickListener());
        }
        pendingFsSequences.add(sequence);
    }

    private static final class FsTickListener {
        @EventHandler
        private void onTick(EventTick event) {
            tickFsSequences();
        }
    }

    /**
     * Пошагово выполняет fs-последовательности: /fly → ability → телепорт,
     * с задержкой в тики между шагами (аналог стадий High Jump Funsky Elytra).
     */
    private static void tickFsSequences() {
        if (pendingFsSequences.isEmpty()) return;

        ClientPlayerEntity currentPlayer = MinecraftClient.getInstance().player;
        if (currentPlayer == null) {
            pendingFsSequences.clear();
            return;
        }

        Iterator<FsSequence> iterator = pendingFsSequences.iterator();
        while (iterator.hasNext()) {
            FsSequence sequence = iterator.next();
            if (sequence.player != currentPlayer) {
                iterator.remove();
                continue;
            }

            // Полёт уже активен — /fly и ability не нужны, телепортируем сразу
            if (sequence.player.getAbilities().flying) {
                finishFsSequence(iterator, sequence);
                continue;
            }

            if (--sequence.ticksLeft > 0) continue;

            switch (sequence.stage) {
                case 0 -> {
                    sequence.player.networkHandler.sendChatCommand("fly");
                    sequence.stage = 1;
                    sequence.ticksLeft = FS_STAGE_DELAY_TICKS;
                }
                case 1 -> {
                    PlayerAbilities abilities = sequence.player.getAbilities();
                    abilities.flying = true;
                    sequence.player.networkHandler.sendPacket(new UpdatePlayerAbilitiesC2SPacket(abilities));
                    if (sequence.teleport) {
                        // Даём серверу обработать ability до пакетов телепорта
                        sequence.stage = 2;
                        sequence.ticksLeft = FS_STAGE_DELAY_TICKS;
                    } else {
                        iterator.remove();
                    }
                }
                case 2 -> finishFsSequence(iterator, sequence);
            }
        }
    }

    private static void finishFsSequence(Iterator<FsSequence> iterator, FsSequence sequence) {
        iterator.remove();
        if (sequence.teleport) {
            bypassMode(sequence.player, sequence.x, sequence.y, sequence.z, sequence.packets);
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
