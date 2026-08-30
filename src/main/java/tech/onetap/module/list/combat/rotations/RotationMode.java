package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.rotation.Rotation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Базовый класс для всех ротаций KillAura.
 * Каждая ротация хранит своё внутреннее состояние и получает доступ
 * к общему состоянию через переданный экземпляр {@link KillAura}.
 *
 * <p>Содержит общий просчёт точки наведения: точки на хитбоксе, стабильный
 * во времени джиттер, именованные рампы и дельту наведения.
 */
public abstract class RotationMode implements IMinecraft {

    private final Map<String, AimLerp> lerps = new HashMap<>();
    private final Map<String, Vec3d> jitterOffsets = new HashMap<>();
    private final Map<String, Long> jitterTimes = new HashMap<>();

    public abstract void update(KillAura killAura, LivingEntity target);

    /**
     * Сброс внутреннего состояния ротации (вызывается при потере цели,
     * выключении модуля и т.п.).
     */
    public void reset(KillAura killAura) {
        clearAimState();
    }

    /** Дельта наведения на точку: целевые yaw/pitch, дельты и ограниченные скорости шага. */
    public record AimDelta(float targetYaw, float targetPitch, float yawDelta, float pitchDelta,
                           float finalYawSpeed, float finalPitchSpeed) {
    }

    protected AimDelta aimAt(Rotation current, Vec3d point, float maxYawSpeed, float maxPitchSpeed) {
        Vec3d delta = point.subtract(mc.player.getEyePos());
        float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        float yawDelta = MathHelper.wrapDegrees(targetYaw - current.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - current.getPitch());
        float yawSpeed = Math.min(Math.abs(yawDelta), maxYawSpeed);
        float pitchSpeed = Math.min(Math.abs(pitchDelta), maxPitchSpeed);
        return new AimDelta(targetYaw, targetPitch, yawDelta, pitchDelta, yawSpeed, pitchSpeed);
    }

    /** Точка на хитбоксе цели: центр по XZ, высота — доля от роста (0..1). */
    protected Vec3d hitboxPoint(LivingEntity target, double heightFactor) {
        return target.getPos().add(0.0D, target.getHeight() * heightFactor, 0.0D);
    }

    /** Случайное смещение, обновляемое раз в periodMs; между обновлениями стабильно. */
    protected Vec3d jitter(String key, long periodMs, double xRange, double yRange, double zRange) {
        long now = System.currentTimeMillis();
        long last = jitterTimes.getOrDefault(key, 0L);
        if (now - last >= periodMs || !jitterOffsets.containsKey(key)) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            jitterOffsets.put(key, new Vec3d(
                    rng.nextDouble(-xRange, xRange),
                    rng.nextDouble(-yRange, yRange),
                    rng.nextDouble(-zRange, zRange)));
            jitterTimes.put(key, now);
        }
        return jitterOffsets.get(key);
    }

    protected Vec3d withJitter(Vec3d point, String key, long periodMs, double xRange, double yRange, double zRange) {
        return point.add(jitter(key, periodMs, xRange, yRange, zRange));
    }

    /** Точка на хитбоксе с джиттером + просчёт дельты наведения. */
    protected AimDelta aimEntity(Rotation current, LivingEntity target, double heightFactor,
                                 String jitterKey, long jitterPeriodMs,
                                 double jitterX, double jitterY, double jitterZ,
                                 float maxYawSpeed, float maxPitchSpeed) {
        Vec3d point = withJitter(hitboxPoint(target, heightFactor), jitterKey, jitterPeriodMs, jitterX, jitterY, jitterZ);
        return aimAt(current, point, maxYawSpeed, maxPitchSpeed);
    }

    /** Именованная рампа (ease-in/ease-out), состояние хранится в ротации. */
    protected AimLerp lerp(String key, int durationMs) {
        AimLerp lerp = lerps.computeIfAbsent(key, ignored -> new AimLerp(durationMs));
        lerp.setDuration(durationMs);
        return lerp;
    }

    protected static float angularLerp(float from, float to, float t) {
        return from + MathHelper.wrapDegrees(to - from) * t;
    }

    protected void clearAimState() {
        lerps.clear();
        jitterOffsets.clear();
        jitterTimes.clear();
    }
}
