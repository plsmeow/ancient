package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.concurrent.ThreadLocalRandom;

public class SlothRotation extends RotationMode {

    private float slothNew_yawSpeed = 0.0F;
    private float slothNew_pitchSpeed = 0.0F;
    private float slothNew_aimFatigue = 0.0F;
    private int slothNew_idleTicks = 0;
    private int slothNew_reactionDelay = 0;
    private float slothNew_jitterYaw = 0.0F;
    private float slothNew_jitterPitch = 0.0F;
    private long slothNew_lastJitterTime = 0L;
    private float slothNew_currentYaw = 0.0F;
    private float slothNew_currentPitch = 0.0F;
    private boolean slothNew_init = false;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (target == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Инициализация при первом вызове или смене цели
        if (!slothNew_init) {
            slothNew_currentYaw = mc.player.getYaw();
            slothNew_currentPitch = mc.player.getPitch();
            slothNew_init = true;
        }

        // Получение целевой точки
        Vec3d point = ka.resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            point = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        var targetAngle = new Rotation(RotationUtil.calculate(point));

        float targetYaw = targetAngle.getYaw();
        float targetPitch = targetAngle.getPitch();

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - slothNew_currentYaw);
        float deltaPitch = targetPitch - slothNew_currentPitch;

        boolean hasTrace = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 999.0, target.getBoundingBox());
        boolean isElytra = mc.player.isGliding();
        float angularDist = (float) Math.hypot(deltaYaw, deltaPitch);

        // Микро-джиттер (обновляется каждые 30-80 мс)
        if (now - slothNew_lastJitterTime > r.nextInt(30, 80)) {
            slothNew_jitterYaw = (r.nextFloat() - 0.5F) * 0.12F;
            slothNew_jitterPitch = (r.nextFloat() - 0.5F) * 0.08F;
            slothNew_lastJitterTime = now;
        }

        // Логика разгона / торможения
        if (!hasTrace) {
            int reactionThreshold = 2 + Math.min(slothNew_idleTicks / 3, 5) + r.nextInt(2);
            if (slothNew_reactionDelay < reactionThreshold) {
                slothNew_reactionDelay++;
                deltaYaw += (r.nextFloat() - 0.5F) * 1.2F;
                deltaPitch += (r.nextFloat() - 0.5F) * 0.6F;
            } else {
                float targetSpeed = angularDist > 12.0F ? 0.72F : 0.35F;
                slothNew_yawSpeed = MathHelper.lerp(0.035F, slothNew_yawSpeed, targetSpeed);
                slothNew_pitchSpeed = MathHelper.lerp(0.035F, slothNew_pitchSpeed, targetSpeed);
            }
            slothNew_idleTicks = 0;
        } else {
            slothNew_reactionDelay = 0;
            slothNew_yawSpeed = MathHelper.lerp(0.55F, slothNew_yawSpeed, 0.0F);
            slothNew_pitchSpeed = MathHelper.lerp(0.55F, slothNew_pitchSpeed, 0.0F);
            slothNew_idleTicks++;
        }

        // Усталость прицела (накапливается при больших углах, спадает при наведении)
        slothNew_aimFatigue = MathHelper.clamp(slothNew_aimFatigue + (angularDist * 0.00008F), 0.0F, 0.32F);
        if (hasTrace) slothNew_aimFatigue *= 0.97F;

        // Базовые скорости (медленнее на элитрах)
        float baseYawSpeed = r.nextFloat(8.0F, 14.0F) / (isElytra ? 2.2F : 1.0F);
        float basePitchSpeed = r.nextFloat(4.0F, 8.0F) / (isElytra ? 2.2F : 1.0F);

        // Smoothstep
        float yawS = slothNew_yawSpeed * slothNew_yawSpeed * (3.0F - 2.0F * slothNew_yawSpeed);
        float pitchS = slothNew_pitchSpeed * slothNew_pitchSpeed * (3.0F - 2.0F * slothNew_pitchSpeed);

        float yawSpeed = baseYawSpeed * yawS * (1.0F - slothNew_aimFatigue);
        float pitchSpeed = basePitchSpeed * pitchS * (1.0F - slothNew_aimFatigue);

        // Замедление в воздухе
        if (!mc.player.isOnGround()) {
            deltaPitch *= 0.12F;
            deltaYaw *= 0.85F;
        }

        // Случайный сдвиг при долгом удержании прицела
        if (hasTrace && slothNew_idleTicks > 3 && r.nextFloat() < 0.15F) {
            deltaYaw += (r.nextFloat() - 0.5F) * 2.0F;
            deltaPitch += (r.nextFloat() - 0.5F) * 1.0F;
        }

        // Ограничение шага за тик
        float clampedYaw = MathHelper.clamp(deltaYaw, -yawSpeed, yawSpeed);
        float clampedPitch = MathHelper.clamp(deltaPitch, -pitchSpeed, pitchSpeed);

        // Мёртвая зона (убирает микро-дрожь при наведении)
        if (hasTrace && Math.abs(clampedYaw) < 0.35F) clampedYaw = 0.0F;
        if (hasTrace && Math.abs(clampedPitch) < 0.18F) clampedPitch = 0.0F;

        float newYaw = slothNew_currentYaw + clampedYaw + slothNew_jitterYaw;
        float newPitch = slothNew_currentPitch + clampedPitch + slothNew_jitterPitch;

        // GCD-фикс
        float gcd = GCDFixer.getGCDValue();
        newYaw = slothNew_currentYaw + Math.round((newYaw - slothNew_currentYaw) / gcd) * gcd;
        newPitch = slothNew_currentPitch + Math.round((newPitch - slothNew_currentPitch) / gcd) * gcd;
        newPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);

        Rotation rot = new Rotation(newYaw, newPitch);
        RotationComponent.update(rot, 360.0F, 360.0F, 360.0F, 360.0F, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        slothNew_currentYaw = newYaw;
        slothNew_currentPitch = newPitch;
        ka.lastYaw = newYaw;
        ka.lastPitch = newPitch;
    }

    @Override
    public void reset(KillAura ka) {
        slothNew_init = false;
        slothNew_yawSpeed = 0.0F;
        slothNew_pitchSpeed = 0.0F;
        slothNew_aimFatigue = 0.0F;
        slothNew_idleTicks = 0;
        slothNew_reactionDelay = 0;
    }
}
