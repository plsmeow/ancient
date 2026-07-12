package tech.onetap.util.neuro.rotation;

import com.google.common.eventbus.Subscribe;
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
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.rotation.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.reflect.Field;

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

    private static Rotation previousRotation = null;
    private static Rotation currentRotation = null;
    private static LivingEntity slimeTarget = null;

    @Subscribe
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;

        if (mode == Mode.SLIMES && (slimeTarget == null || !slimeTarget.isAlive() || slimeTarget.isRemoved())) {
            slimeTarget = spawnSlime();
            previousRotation = null;
            currentRotation = null;
            return;
        }

        LivingEntity target = resolveTarget();
        if (target == null || !target.isAlive()) return;

        KillAura killAura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        double distance = (killAura != null && killAura.isEnabled())
                ? killAura.distance.getValue()
                : 3.0;

        Rotation nextRotation = new Rotation(
                MathHelper.wrapDegrees(mc.player.getYaw()),
                mc.player.getPitch()
        );

        if (currentRotation == null) {
            currentRotation = nextRotation;
            System.out.println("AI RECORDER: Initialized with rotation " +
                    String.format("%.2f, %.2f", nextRotation.getYaw(), nextRotation.getPitch()));
            return;
        }

        if (previousRotation == null) {
            previousRotation = currentRotation;
            currentRotation = nextRotation;
            return;
        }

        float actualDeltaYaw = MathHelper.wrapDegrees(nextRotation.getYaw() - currentRotation.getYaw());
        float actualDeltaPitch = nextRotation.getPitch() - currentRotation.getPitch();

        Vec3d targetPoint = BestPoint.getMultipoint(target, distance);
        Rotation targetRotation = new Rotation(RotationUtil.calculate(targetPoint));

        float[] input = AIRotationFeatures.buildInput(
                mc.player,
                target,
                currentRotation,
                targetRotation,
                previousRotation
        );

        float[] output = new float[]{actualDeltaYaw, actualDeltaPitch};

        if (!isValidSample(input, output)) {
            previousRotation = currentRotation;
            currentRotation = nextRotation;
            return;
        }

        samples.add(new TrainingSample(input, output));

        if (samples.size() % 20 == 0) {
            System.out.println("AI RECORDER: Sample " + samples.size() +
                    " | Input: [" + format(input) + "] | Output: [" +
                    String.format("%.2f, %.2f", output[0], output[1]) + "]");
        }

        previousRotation = currentRotation;
        currentRotation = nextRotation;
    }

    @Subscribe
    public void onPacket(EventPacket event) {
        if (!recording || mode != Mode.SLIMES || event.getType() != EventPacket.Type.SEND || mc.world == null) return;
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) || slimeTarget == null) return;

        if (packetEntityId(packet) != slimeTarget.getId()) return;

        mc.world.removeEntity(slimeTarget.getId(), Entity.RemovalReason.DISCARDED);
        slimeTarget = null;
        event.setCancelled(true);
        System.out.println("AI RECORDER: Recorded " + samples.size() + " samples, spawning next slime");
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

    private static boolean isValidSample(float[] input, float[] output) {
        if (!AIRotationFeatures.isValidInput(input) || !AIRotationFeatures.isValidOutput(output)) return false;

        // Отсекаем почти нулевое движение мыши (шум), оставляем реальные повороты
        if (Math.abs(output[0]) < 0.01f && Math.abs(output[1]) < 0.01f) return false;

        return true;
    }

    private static String format(float[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.2f", values[i]));
        }
        return sb.toString();
    }

    public static void startRecording() {
        startRecording(Mode.KILLAURA);
    }

    public static void startRecording(Mode recordMode) {
        recording = true;
        mode = recordMode;
        samples.clear();
        previousRotation = null;
        currentRotation = null;
        slimeTarget = null;
        System.out.println("AI RECORDER: Started recording (" + recordMode.name().toLowerCase() + ")");
    }

    public static int stopRecording() {
        recording = false;
        int count = samples.size();
        removeSlimeTarget();
        previousRotation = null;
        currentRotation = null;
        System.out.println("AI RECORDER: Stopped recording, collected " + count + " samples");
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

    private static int packetEntityId(PlayerInteractEntityC2SPacket packet) {
        for (Field field : packet.getClass().getDeclaredFields()) {
            if (field.getType() != int.class) continue;
            try {
                field.setAccessible(true);
                return field.getInt(packet);
            } catch (IllegalAccessException ignored) {
            }
        }

        return -1;
    }
}
