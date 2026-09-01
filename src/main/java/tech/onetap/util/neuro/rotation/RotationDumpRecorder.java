package tech.onetap.util.neuro.rotation;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventTick;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;
import net.minecraft.util.math.Vec3d;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Дамп чужих ротаций (.ai dump start ник1|ник2 / .ai dump stop).
 *
 * Наблюдает за указанными игроками и пишет RAW-строки тика в том же формате,
 * что и AIRotationRecorder: yaw/pitch наблюдаемого, дельта его поворота,
 * ближайшая живая цель в радиусе боя от его глаз. Источник меток — только
 * человеческое движение.
 *
 * Несколько игроков пишутся в раздельные треки и склеиваются при сохранении:
 * построитель эпизодов требует непрерывности тиков, перемешанные ряды двух
 * игроков дали бы битые окна. У каждого трека свой счётчик t, поэтому на
 * стыке треков последовательность t рвётся — эпизоды не склеиваются.
 */
public class RotationDumpRecorder implements IMinecraft {

    private static final DateTimeFormatter DATASET_TIME = DateTimeFormatter.ofPattern("MMdd_HHmm");
    private static final double TARGET_RANGE = 6.0;

    private static RotationDumpRecorder instance = null;
    private static boolean recording = false;
    /** Ники для наблюдения (lowercase), в порядке ввода. */
    private static final Set<String> names = new LinkedHashSet<>();
    /** Отображение ников как ввёл пользователь (для сообщений). */
    private static final Map<String, String> displayNames = new LinkedHashMap<>();
    private static final Map<String, Track> tracks = new LinkedHashMap<>();

    /** RAW-строки одного наблюдаемого игрока. */
    private static final class Track {
        final List<float[]> rows = new ArrayList<>();
        final NeuroFeatureCollector collector = new NeuroFeatureCollector();
        LivingEntity lastTarget = null;
        long tickCounter = 0;
        int lastSeenTick = -1;
    }

    private int wallTick = 0;

    @EventHandler
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;

        wallTick++;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            String key = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
            if (!names.contains(key)) continue;

            Track track = tracks.computeIfAbsent(key, k -> new Track());
            track.lastSeenTick = wallTick;
            recordTrack(track, player);
        }

        // Игрок пропал из мира — трек не пишется, последовательность t рвётся
        for (Track track : tracks.values()) {
            if (track.lastSeenTick != wallTick) {
                track.lastTarget = null;
            }
        }
    }

    private void recordTrack(Track track, AbstractClientPlayerEntity player) {
        LivingEntity target = resolveTarget(player);
        boolean targetChanged = target != track.lastTarget;
        track.lastTarget = target;
        track.tickCounter++;

        boolean clean = target != null && !targetChanged
                && Float.isFinite(player.getYaw()) && Float.isFinite(player.getPitch());

        track.rows.add(track.collector.pushFrame(
                player, target,
                target != null ? target.getBoundingBox().getCenter() : Vec3d.ZERO,
                clean, track.tickCounter - 1));
    }

    /**
     * Цель наблюдаемого игрока: ближайшая живая сущность в радиусе боя.
     * Может быть и наш игрок, если дампимый бьётся с нами.
     */
    private static LivingEntity resolveTarget(AbstractClientPlayerEntity player) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == player || !living.isAlive()) continue;
            double d = player.getEyePos().distanceTo(living.getEyePos());
            if (d < bestDist && d <= TARGET_RANGE) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Управление (вызывается из .ai dump)
    // ------------------------------------------------------------------

    public static void start(String nicksToken) {
        if (recording) {
            ChatUtil.send("§cДамп уже идёт: §f" + getNamesLine() + " §7— остановка: .ai dump stop");
            return;
        }
        if (mc.player == null || mc.world == null) {
            ChatUtil.send("§cЗайдите в мир перед запуском дампа");
            return;
        }

        Set<String> parsed = new LinkedHashSet<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (String raw : nicksToken.split("\\|")) {
            String nick = raw.trim();
            if (nick.isEmpty()) continue;
            String key = nick.toLowerCase(Locale.ROOT);
            parsed.add(key);
            display.putIfAbsent(key, nick);
        }

        if (parsed.isEmpty()) {
            ChatUtil.send("§cИспользование: §f.ai dump start <ник|ник2>");
            return;
        }

        if (mc.player != null
                && parsed.contains(mc.player.getGameProfile().getName().toLowerCase(Locale.ROOT))) {
            ChatUtil.send("§7Свой ник в дампе игнорируется — для себя есть модуль Ai Record");
        }

        names.clear();
        names.addAll(parsed);
        displayNames.clear();
        displayNames.putAll(display);
        tracks.clear();

        instance = new RotationDumpRecorder();
        Onetap.getInstance().getEventBus().subscribe(instance);
        recording = true;

        ChatUtil.send("§aДамп ротаций начат: §f" + getNamesLine());
        ChatUtil.send("§7Игроки должны быть в прогруженном мире. Остановка: §f.ai dump stop");
    }

    public static void stopAndSave() {
        if (!recording) {
            ChatUtil.send("§7Дамп не запущен");
            return;
        }
        recording = false;

        if (instance != null) {
            Onetap.getInstance().getEventBus().unsubscribe(instance);
            instance = null;
        }

        // Склеиваем треки: сначала все строки одного игрока, потом следующего —
        // каждый трек непрерывен внутри себя, на стыке t рвётся
        List<float[]> all = new ArrayList<>();
        for (String key : names) {
            String display = displayNames.getOrDefault(key, key);
            Track track = tracks.get(key);
            if (track == null || track.rows.isEmpty()) {
                ChatUtil.send("§7" + display + ": §cнет строк §7(не найден в мире или без цели)");
                continue;
            }
            ChatUtil.send("§7" + display + ": §f" + track.rows.size() + " §7тиков");
            all.addAll(track.rows);
        }

        if (all.isEmpty()) {
            ChatUtil.send("§cДамп пуст — датасет не сохранён");
            return;
        }

        String name = "dump" + LocalDateTime.now().format(DATASET_TIME);
        AIRotationManager.saveDumpDataset(name, all);
        if (all.size() < 256) {
            ChatUtil.send("§eСтрок мало (" + all.size() + ") — для обучения пишите дольше");
        }
    }

    // ------------------------------------------------------------------
    // Диагностика для debug-панели
    // ------------------------------------------------------------------

    public static boolean isRecording() {
        return recording;
    }

    public static String getNamesLine() {
        return String.join(", ", displayNames.values());
    }

    public static int getTotalRows() {
        int total = 0;
        for (Track track : tracks.values()) {
            total += track.rows.size();
        }
        return total;
    }
}
