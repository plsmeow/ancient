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
import net.minecraft.util.math.Vec3d;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Пишет датасет для Neuro Rotation — RAW-состояние тика, по строке на тик
 * (формат train_neuro.py: t,gcd,clean,yaw,...,sprint). Фичи из него выводит
 * тренер, поэтому формат датасета не зависит от схемы фич.
 *
 * Строка пишется каждый тик записи, даже без цели (has=0, clean=0) —
 * разрыв последовательности t и флаг clean режут её на эпизоды у тренера.
 * Метка (dyaw/dpitch) — фактическое движение мыши человека между тиками.
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

    private static final List<float[]> rows = new ArrayList<>();
    private static long tickCounter = 0;
    @Getter
    private static long cleanCount = 0;

    private static LivingEntity slimeTarget = null;
    private static LivingEntity lastTarget = null;

    private final NeuroFeatureCollector collector = new NeuroFeatureCollector();

    @EventHandler
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;

        tickCounter++;

        if (mode == Mode.SLIMES && (slimeTarget == null || !slimeTarget.isAlive() || slimeTarget.isRemoved())) {
            slimeTarget = spawnSlime();
            if (slimeTarget == null) return;
        }

        LivingEntity target = resolveTarget();
        boolean targetChanged = target != lastTarget;
        lastTarget = target;

        // clean=0 на смене цели: переход — не человеческое слежение,
        // и эпизод не должен пересекать смену цели
        boolean clean = target != null && !targetChanged
                && Float.isFinite(mc.player.getYaw()) && Float.isFinite(mc.player.getPitch());
        if (clean) cleanCount++;

        // Рекордер пишет прицеливание по центру хитбокса — инвариант датасета
        Vec3d aimPoint = target != null
                ? target.getBoundingBox().getCenter()
                : Vec3d.ZERO;
        rows.add(collector.pushFrame(mc.player, target, aimPoint, clean, tickCounter - 1));
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
        rows.clear();
        cleanCount = 0;
        tickCounter = 0;
        slimeTarget = null;
        lastTarget = null;
    }

    public static long stopRecording() {
        recording = false;
        long count = rows.size();
        removeSlimeTarget();
        lastTarget = null;
        return count;
    }

    /** RAW-строки записи. Копия — менеджер пишет их в CSV при сохранении. */
    public static List<float[]> getRows() {
        return new ArrayList<>(rows);
    }

    public static long getRowCount() {
        return rows.size();
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
        float yawRad = -yaw * (float) (Math.PI / 180.0) - (float) Math.PI;
        float pitchRad = -pitch * (float) (Math.PI / 180.0);
        float pitchCos = net.minecraft.util.math.MathHelper.cos(pitchRad);
        return new Vec3d(
                net.minecraft.util.math.MathHelper.sin(yawRad) * pitchCos,
                net.minecraft.util.math.MathHelper.sin(pitchRad),
                net.minecraft.util.math.MathHelper.cos(yawRad) * pitchCos
        );
    }
}
