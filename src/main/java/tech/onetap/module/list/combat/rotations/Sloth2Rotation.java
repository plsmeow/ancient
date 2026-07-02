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
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

/**
 * Ротация "Sloth2" (updateSlothTestRotation).
 */
public class Sloth2Rotation extends RotationMode {

    private float sloth2_g = 0.0F;
    private float sloth2_h = 0.0F;
    private float sloth2_i = 0.0F;
    private int sloth2_j = 0;
    private int sloth2_k = 0;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null || target == null) return;

        // 1. Получение целевой точки (с поддержкой предикта на элитрах)
        Vec3d targetPoint = ka.resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            targetPoint = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        // Расчет идеальных углов до цели
        var idealRot = new Rotation(RotationUtil.calculate(targetPoint));

        float deltaYaw = MathHelper.wrapDegrees(idealRot.getYaw() - ka.lastYaw);
        float deltaPitch = idealRot.getPitch() - ka.lastPitch;

        // 2. Проверка наведения (Raytrace аналог bW.a из исходников)
        // Симулируем луч через расширенный хитбокс
        boolean isLooking = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, target.getBoundingBox().expand(-0.1, -0.1, -0.1));
        boolean isGliding = mc.player.isGliding();

        float distanceToTarget = (float) Math.hypot(deltaYaw, deltaPitch);

        // 3. Логика Sloth2: Расчет коэффициентов сглаживания (g, h) и джиттера
        if (!isLooking) {
            int maxJerkTicks = 3 + Math.min(sloth2_j / 4, 6) + (int) (Math.random() * 2.0);
            if (sloth2_k < maxJerkTicks) {
                sloth2_k++;
                if (Math.random() < 0.25) {
                    // Симуляция человеческого микро-тремора при доводке
                    deltaYaw += (float) (Math.random() - 0.5) * 2.0F;
                    deltaPitch += (float) (Math.random() - 0.5) * 0.9F;
                }
            } else {
                // Линейная интерполяция скорости поворота в зависимости от дистанции до прицела
                float targetLerp = distanceToTarget > 10.0F ? 0.85F : 0.4F;
                sloth2_g = MathHelper.lerp(0.04F, sloth2_g, targetLerp);
                sloth2_h = MathHelper.lerp(0.04F, sloth2_h, targetLerp);
            }
            sloth2_j = 0;
        } else {
            // Если прицел на цели - резко замедляем скорость (обход Grim AntiCheat)
            sloth2_k = 0;
            sloth2_g = MathHelper.lerp(0.65F, sloth2_g, 0.0F);
            sloth2_h = MathHelper.lerp(0.65F, sloth2_h, 0.0F);
            sloth2_j++;
        }

        // Коэффициент утомления / сопротивления
        sloth2_i = MathHelper.clamp(sloth2_i + 1.5E-4F, 0.0F, 0.4F);

        // Базовая скорость (замедляется в 2 раза, если игрок летит на элитрах)
        float speedYawMax = (float) (Math.random() * 7.0 + 12.0) / (isGliding ? 2.0F : 1.0F);
        float speedPitchMax = (float) (Math.random() * 4.0 + 6.0) / (isGliding ? 2.0F : 1.0F);

        // Применение сглаживания по кривой Безье (Smoothstep)
        float smoothYaw = sloth2_g * sloth2_g * (3.0F - 2.0F * sloth2_g);
        float smoothPitch = sloth2_h * sloth2_h * (3.0F - 2.0F * sloth2_h);

        float finalSpeedYaw = speedYawMax * smoothYaw * (1.0F - sloth2_i);
        float finalSpeedPitch = speedPitchMax * smoothPitch * (1.0F - sloth2_i);

        // Если игрок в воздухе (не на земле), вертикальное наведение замедляется в 10 раз
        if (!mc.player.isOnGround()) {
            deltaPitch *= 0.1F;
        }

        // Искусственный промах удержания (20% шанс слегка сместить прицел при залипании)
        if (isLooking && sloth2_j > 2 && Math.random() < 0.2) {
            deltaYaw += (float) (Math.random() - 0.5) * 2.8F;
            deltaPitch += (float) (Math.random() - 0.5) * 1.4F;
        }

        // Ограничение шага ротации за один тик
        float stepYaw = MathHelper.clamp(deltaYaw, -finalSpeedYaw, finalSpeedYaw);
        float stepPitch = MathHelper.clamp(deltaPitch, -finalSpeedPitch, finalSpeedPitch);

        // Мертвая зона: убираем микро-тряску, если прицел наведен почти идеально
        if (isLooking && Math.abs(stepYaw) < 0.4F) {
            stepYaw = 0.0F;
        }
        if (isLooking && Math.abs(stepPitch) < 0.2F) {
            stepPitch = 0.0F;
        }

        // Формирование новых результирующих углов
        float newYaw = ka.lastYaw + stepYaw;
        float newPitch = MathHelper.clamp(ka.lastPitch + stepPitch, -90.0F, 90.0F);

        // 4. Применение GCD Fixer (исправление углов под сетку чувствительности мыши)
        float gcd = GCDFixer.getGCDValue();
        newYaw -= (newYaw - ka.lastYaw) % gcd;
        newPitch -= (newPitch - ka.lastPitch) % gcd;

        var finalRot = new Rotation(newYaw, newPitch);

        // 5. Отправка пакетов / обновление клиентского взгляда
        RotationComponent.update(finalRot, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue());

        // Сохранение состояния для следующего тика
        ka.lastYaw = finalRot.getYaw();
        ka.lastPitch = finalRot.getPitch();
    }

    @Override
    public void reset(KillAura ka) {
        sloth2_g = 0.0F;
        sloth2_h = 0.0F;
        sloth2_i = 0.0F;
        sloth2_j = 0;
        sloth2_k = 0;
    }
}
