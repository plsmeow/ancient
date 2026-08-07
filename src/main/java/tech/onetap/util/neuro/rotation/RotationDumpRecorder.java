package tech.onetap.util.neuro.rotation;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventTick;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.rotation.Rotation;

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
 * Наблюдает за указанными игроками и пишет датасет в том же формате, что и
 * AIRotationRecorder: фичи тика t по состоянию наблюдаемого игрока, метка —
 * фактическая дельта его поворота между t и t+1. Источник всегда HUMAN —
 * обучение на нем человеческих метках запрещено.
 *
 * Несколько игроков пишутся в раздельные треки и склеиваются при сохранении:
 * построитель окон требует непрерывности тиков, перемешанные ряды двух
 * игроков дали бы битые окна. Общий счётчик тиков сохраняет разрывы внутри
 * трека (нет цели — тик пропущен), чтобы окна не пересекали паузы.
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
    private static final DatasetBalance balance = new DatasetBalance();
    private static int tickCounter = 0;

    /** Состояние одного наблюдаемого игрока. */
    private static final class Track {
        final List<TrainingSample> samples = new ArrayList<>();
        final NeuroFeatureCollector collector = new NeuroFeatureCollector();
        float[] pendingFeatures = null;
        Rotation pendingRotation = null;
        SampleQuality pendingQuality = SampleQuality.CLEAN;
        int pendingTick = 0;
        LivingEntity lastTarget = null;
        float prevDeltaYaw = 0.0f;
        float prevDeltaPitch = 0.0f;
        int lastSeenTick = -1;
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;

        tickCounter++;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            String key = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
            if (!names.contains(key)) continue;

            Track track = tracks.computeIfAbsent(key, k -> new Track());
            track.lastSeenTick = tickCounter;
            recordTrack(track, player);
        }

        // Игрок пропал из мира — отложенный сэмпл закрывать нельзя:
        // метка посчиталась бы через разрыв записи.
        for (Track track : tracks.values()) {
            if (track.lastSeenTick != tickCounter) {
                discardPending(track);
            }
        }
    }

    private void recordTrack(Track track, AbstractClientPlayerEntity player) {
        LivingEntity target = resolveTarget(player);
        if (target == null || !target.isAlive()) {
            discardPending(track);
            return;
        }

        Rotation nextRotation = new Rotation(
                MathHelper.wrapDegrees(player.getYaw()),
                player.getPitch()
        );

        boolean targetChanged = target != track.lastTarget;
        track.lastTarget = target;

        // Закрываем отложенный сэмпл: метка — дельта поворота наблюдаемого между тиками
        if (track.pendingFeatures != null && track.pendingRotation != null) {
            float labelYaw = MathHelper.wrapDegrees(nextRotation.getYaw() - track.pendingRotation.getYaw());
            float labelPitch = nextRotation.getPitch() - track.pendingRotation.getPitch();

            SampleQuality quality = targetChanged ? SampleQuality.TARGET_SWITCH : track.pendingQuality;
            commitSample(track, track.pendingFeatures, labelYaw, labelPitch, quality, track.pendingTick);

            track.prevDeltaYaw = labelYaw;
            track.prevDeltaPitch = labelPitch;
        }

        // Референсная точка прицеливания — верхняя часть хитбокса цели.
        // Чужой AimPointController нам недоступен, геометрии достаточно.
        Vec3d aimPoint = target.getPos().add(0, target.getHeight() * 0.65, 0);

        float[] features = new float[NeuroFeatureSchema.FEATURE_COUNT];
        track.collector.collect(features, 0, player, target, nextRotation, aimPoint, targetChanged);

        features[NeuroFeatureSchema.PREV_DELTA_YAW] = track.prevDeltaYaw;
        features[NeuroFeatureSchema.PREV_DELTA_PITCH] = track.prevDeltaPitch;

        track.pendingFeatures = features;
        track.pendingRotation = nextRotation;
        track.pendingTick = tickCounter;
        track.pendingQuality = classify(player, features, aimPoint, targetChanged);
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

    private SampleQuality classify(AbstractClientPlayerEntity player, float[] features,
                                   Vec3d aimPoint, boolean targetChanged) {
        for (float value : features) {
            if (!Float.isFinite(value)) return SampleQuality.INVALID;
        }
        if (targetChanged) {
            return SampleQuality.TARGET_SWITCH;
        }
        if (aimPoint != null && isOccluded(player, aimPoint)) {
            return SampleQuality.OCCLUDED;
        }
        if (features[NeuroFeatureSchema.LINE_OF_SIGHT] < 0.5f) {
            return SampleQuality.TRANSITION;
        }
        return SampleQuality.CLEAN;
    }

    /** Перекрытие блоками — от глаз наблюдаемого игрока. */
    private boolean isOccluded(AbstractClientPlayerEntity player, Vec3d point) {
        if (mc.world == null) return false;
        Vec3d eyePos = player.getEyePos();
        var hit = RaytraceUtil.raycast(eyePos, point, RaycastContext.ShapeType.COLLIDER, player);
        if (hit == null || hit.getType() == HitResult.Type.MISS) return false;
        return eyePos.squaredDistanceTo(hit.getPos()) < eyePos.squaredDistanceTo(point) - 1e-4;
    }

    private void commitSample(Track track, float[] features, float labelYaw, float labelPitch,
                              SampleQuality quality, int tick) {
        if (quality == SampleQuality.INVALID) return;
        if (!Float.isFinite(labelYaw) || !Float.isFinite(labelPitch)) return;
        if (Math.abs(labelYaw) > 180.0f || Math.abs(labelPitch) > 90.0f) return;

        // Тот же шум-фильтр, что и у AIRotationRecorder
        if (Math.abs(labelYaw) < 0.01f && Math.abs(labelPitch) < 0.01f) return;

        track.samples.add(new TrainingSample(
                features, new float[]{labelYaw, labelPitch}, quality, SampleSource.HUMAN, tick));
        balance.record(features);
    }

    private static void discardPending(Track track) {
        track.pendingFeatures = null;
        track.pendingRotation = null;
        track.pendingQuality = SampleQuality.CLEAN;
        track.lastTarget = null;
        track.prevDeltaYaw = 0.0f;
        track.prevDeltaPitch = 0.0f;
        track.collector.reset();
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
        balance.reset();
        tickCounter = 0;

        instance = new RotationDumpRecorder();
        Onetap.getInstance().getEventBus().register(instance);
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
            Onetap.getInstance().getEventBus().unregister(instance);
            instance = null;
        }

        // Склеиваем треки: сначала все сэмплы одного игрока, потом следующего —
        // непрерывность тиков внутри серии сохраняется, окна строятся корректно.
        List<TrainingSample> all = new ArrayList<>();
        for (String key : names) {
            String display = displayNames.getOrDefault(key, key);
            Track track = tracks.get(key);
            if (track == null || track.samples.isEmpty()) {
                ChatUtil.send("§7" + display + ": §cнет сэмплов §7(не найден в мире или без цели)");
                continue;
            }
            ChatUtil.send("§7" + display + ": §f" + track.samples.size() + " §7сэмплов");
            all.addAll(track.samples);
        }

        if (all.isEmpty()) {
            ChatUtil.send("§cДамп пуст — датасет не сохранён");
            return;
        }

        String name = "dump" + LocalDateTime.now().format(DATASET_TIME);
        AIRotationManager.saveDumpDataset(name, all, balance);
        if (all.size() < 64) {
            ChatUtil.send("§eСэмплов мало (" + all.size() + ") — для обучения пишите дольше");
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

    public static int getTotalSamples() {
        int total = 0;
        for (Track track : tracks.values()) {
            total += track.samples.size();
        }
        return total;
    }
}
