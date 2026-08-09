package tech.onetap.util.neuro.rotation;

import meteordevelopment.orbit.EventHandler;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.rotation.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Пишет датасет для Neuro Rotation.
 *
 * Ключевое отличие от старой версии: фичи собираются из состояния тика t,
 * а метка — дельта поворота между t и t+1. Строка фич держится один тик,
 * поэтому фичи и метка описывают один и тот же интервал.
 */
public class AIRotationRecorder implements IMinecraft {

    public enum Mode {
        KILLAURA,
        SLIMES
    }

    @Getter
    private static boolean recording = false;
    @Getter
    private static Mode mode = Mode.KILLAURA;

    private static final List<TrainingSample> samples = new ArrayList<>();
    @Getter
    private static final DatasetBalance balance = new DatasetBalance();

    private static LivingEntity slimeTarget = null;
    private static int tickCounter = 0;

    // Отложенный сэмпл: фичи тика t ждут метку, которая станет известна на t+1
    private static float[] pendingFeatures = null;
    private static Rotation pendingRotation = null;
    private static SampleQuality pendingQuality = SampleQuality.CLEAN;
    private static int pendingTick = 0;

    private static LivingEntity lastTarget = null;
    private static float prevDeltaYaw = 0.0f;
    private static float prevDeltaPitch = 0.0f;

    private final NeuroFeatureCollector collector = new NeuroFeatureCollector();
    private final AimPointController aimController = new AimPointController();

    @EventHandler
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;

        tickCounter++;

        if (mode == Mode.SLIMES && (slimeTarget == null || !slimeTarget.isAlive() || slimeTarget.isRemoved())) {
            slimeTarget = spawnSlime();
            discardPending();
            return;
        }

        LivingEntity target = resolveTarget();
        if (target == null || !target.isAlive()) {
            discardPending();
            return;
        }

        Rotation nextRotation = new Rotation(
                MathHelper.wrapDegrees(mc.player.getYaw()),
                mc.player.getPitch()
        );

        boolean targetChanged = target != lastTarget;
        lastTarget = target;

        // Сначала закрываем отложенный сэмпл: метка = дельта между тиком t и t+1
        if (pendingFeatures != null && pendingRotation != null) {
            float labelYaw = MathHelper.wrapDegrees(nextRotation.getYaw() - pendingRotation.getYaw());
            float labelPitch = nextRotation.getPitch() - pendingRotation.getPitch();

            SampleQuality quality = targetChanged ? SampleQuality.TARGET_SWITCH : pendingQuality;
            commitSample(pendingFeatures, labelYaw, labelPitch, quality, pendingTick);

            prevDeltaYaw = labelYaw;
            prevDeltaPitch = labelPitch;
        }

        // Теперь собираем фичи текущего тика — они станут pending
        KillAura killAura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        Vec3d aimPoint = resolveAimPoint(killAura, target, targetChanged);

        float[] features = new float[NeuroFeatureSchema.FEATURE_COUNT];
        collector.collect(features, 0, mc.player, target, nextRotation, aimPoint, targetChanged);

        // Дельты предыдущего шага заполняет рекордер, а не коллектор
        features[NeuroFeatureSchema.PREV_DELTA_YAW] = prevDeltaYaw;
        features[NeuroFeatureSchema.PREV_DELTA_PITCH] = prevDeltaPitch;

        pendingFeatures = features;
        pendingRotation = nextRotation;
        pendingTick = tickCounter;
        pendingQuality = classify(features, target, aimPoint, targetChanged);
    }

    /**
     * Определяет качество сэмпла по §26.
     */
    private SampleQuality classify(float[] features, LivingEntity target, Vec3d aimPoint, boolean targetChanged) {
        if (!isFinite(features)) {
            return SampleQuality.INVALID;
        }
        if (targetChanged) {
            return SampleQuality.TARGET_SWITCH;
        }
        if (aimPoint != null && isOccluded(aimPoint)) {
            return SampleQuality.OCCLUDED;
        }
        if (features[NeuroFeatureSchema.LINE_OF_SIGHT] < 0.5f) {
            return SampleQuality.TRANSITION;
        }
        return SampleQuality.CLEAN;
    }

    /**
     * Проверяет перекрытие блоками — старая реализация этого не делала вообще.
     */
    private boolean isOccluded(Vec3d point) {
        if (mc.player == null || mc.world == null) return false;
        Vec3d eyePos = mc.player.getEyePos();
        var hit = RaytraceUtil.raycast(eyePos, point, RaycastContext.ShapeType.COLLIDER, mc.player);
        if (hit.getType() == HitResult.Type.MISS) return false;
        return eyePos.squaredDistanceTo(hit.getPos()) < eyePos.squaredDistanceTo(point) - 1e-4;
    }

    private Vec3d resolveAimPoint(KillAura killAura, LivingEntity target, boolean targetChanged) {
        if (killAura != null && killAura.isEnabled()) {
            return aimController.update(killAura, target, targetChanged);
        }
        // KillAura выключена — берём центр верхней части хитбокса
        return target.getPos().add(0, target.getHeight() * 0.65, 0);
    }

    private void commitSample(float[] features, float labelYaw, float labelPitch,
                              SampleQuality quality, int tick) {
        float[] output = new float[]{labelYaw, labelPitch};

        if (quality == SampleQuality.INVALID || !isValidLabel(output)) {
            return;
        }

        // Отсекаем почти нулевое движение мыши — шум не должен
        // размывать распределение меток.
        if (Math.abs(labelYaw) < 0.01f && Math.abs(labelPitch) < 0.01f) {
            return;
        }

        samples.add(new TrainingSample(features, output, quality, SampleSource.HUMAN, tick));
        balance.record(features);
    }

    private void discardPending() {
        pendingFeatures = null;
        pendingRotation = null;
        pendingQuality = SampleQuality.CLEAN;
        lastTarget = null;
        prevDeltaYaw = 0.0f;
        prevDeltaPitch = 0.0f;
    }

    private static boolean isFinite(float[] values) {
        for (float value : values) {
            if (!Float.isFinite(value)) return false;
        }
        return true;
    }

    private static boolean isValidLabel(float[] output) {
        if (output == null || output.length != NeuroFeatureSchema.OUTPUT_SIZE) return false;
        for (float value : output) {
            if (!Float.isFinite(value)) return false;
        }
        return Math.abs(output[0]) <= 180.0f && Math.abs(output[1]) <= 90.0f;
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (!recording || mode != Mode.SLIMES || event.getType() != EventPacket.Type.SEND || mc.world == null) return;
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) || slimeTarget == null) return;

        if (packet.entityId != slimeTarget.getId()) return;

        mc.world.removeEntity(slimeTarget.getId(), Entity.RemovalReason.DISCARDED);
        slimeTarget = null;
        event.setCancelled(true);
    }

    private static LivingEntity resolveTarget() {
        if (mode == Mode.SLIMES) {
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof SlimeEntity slime) || !slime.isAlive()) continue;
                double d = mc.player.getEyePos().distanceTo(slime.getEyePos());
                if (d < bestDist && d <= 8.0) {
                    bestDist = d;
                    best = slime;
                }
            }
            return best;
        }

        KillAura killAura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return null;
        return KillAura.lastTarget;
    }

    public static void startRecording() {
        startRecording(Mode.KILLAURA);
    }

    public static void startRecording(Mode recordMode) {
        recording = true;
        mode = recordMode;
        samples.clear();
        balance.reset();
        slimeTarget = null;
        tickCounter = 0;
        pendingFeatures = null;
        pendingRotation = null;
        pendingQuality = SampleQuality.CLEAN;
        lastTarget = null;
        prevDeltaYaw = 0.0f;
        prevDeltaPitch = 0.0f;
    }

    public static int stopRecording() {
        recording = false;
        int count = samples.size();
        removeSlimeTarget();
        pendingFeatures = null;
        pendingRotation = null;
        lastTarget = null;
        return count;
    }

    public static List<TrainingSample> getSamples() {
        return new ArrayList<>(samples);
    }

    public static int getSampleCount() {
        return samples.size();
    }

    public static void clearSamples() {
        samples.clear();
        balance.reset();
    }

    private static LivingEntity spawnSlime() {
        if (mc.player == null || mc.world == null) return null;

        SlimeEntity slime = new SlimeEntity(EntityType.SLIME, mc.world);
        slime.setUuid(UUID.randomUUID());

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double distance = random.nextDouble(2.0, 2.9);
        float yaw = mc.player.getYaw() + (float) random.nextDouble(-65.0, 65.0);
        float pitch = (float) random.nextDouble(-20.0, 10.0);
        Vec3d direction = directionVector(yaw, pitch).multiply(distance);
        Vec3d position = mc.player.getEyePos().add(direction);

        slime.setPosition(position);
        mc.world.addEntity(slime);
        mc.world.playSound(position.x, position.y, position.z,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.NEUTRAL,
                1.0f,
                1.0f,
                false);

        return slime;
    }

    private static void removeSlimeTarget() {
        if (slimeTarget != null && mc.world != null) {
            mc.world.removeEntity(slimeTarget.getId(), Entity.RemovalReason.DISCARDED);
            slimeTarget = null;
        }
    }

    private static Vec3d directionVector(float yaw, float pitch) {
        float yawRad = -yaw * MathHelper.RADIANS_PER_DEGREE - MathHelper.PI;
        float pitchRad = -pitch * MathHelper.RADIANS_PER_DEGREE;
        float pitchCos = MathHelper.cos(pitchRad);
        return new Vec3d(
                MathHelper.sin(yawRad) * pitchCos,
                MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * pitchCos
        );
    }
}
