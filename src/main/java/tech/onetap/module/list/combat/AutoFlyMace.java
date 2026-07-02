package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.base.Instance;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;
import tech.onetap.util.render.math.GCDFixer;

@ModuleInformation(moduleName = "AutoFlyMace", moduleDesc = "Автоматически встаёт в воздухе над целью для удара булавой", moduleCategory = ModuleCategory.COMBAT)
public class AutoFlyMace extends Module {

    private final SliderSetting threshold = new SliderSetting("Порог точности", 0.2, 0.05, 1.0, 0.05);
    private final SliderSetting retargetDistance = new SliderSetting("Сброс цели", 30, 10, 64, 1);
    private final BooleanSetting predict = new BooleanSetting("Предикт цели", true);
    private final SliderSetting predictTicks = new SliderSetting("Сила предикта", 3, 1, 10, 0.5)
            .setVisible(predict::getValue);

    @Getter
    private LivingEntity target;

    private Vec2f rotation = new Vec2f(0, 0);
    private float rotationYawOffset = Integer.MIN_VALUE;
    private boolean hasInput = false;
    private boolean aligned = false;

    @Subscribe
    private void onStrafe(MoveInputEvent e) {
        if (mc.player == null || target == null) return;
        if (!hasInput) return;
        // Симуляция нажатия клавиши W (стрейф в полёте) — игрок реально идёт на цель,
        // никакой жёсткой телепортации или подмены вектора скорости.
        e.forward = 1f;
        e.strafe = 0f;
    }

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null) return;

        updateTarget();

        if (target == null || mc.player.isOnGround()) {
            hasInput = false;
            aligned = false;
            return;
        }

        Vec3d aim = predict.getValue() ? PredictUtils.getPredicted(target, predictTicks.getValue()) : target.getPos();
        double dx = aim.x - mc.player.getX();
        double dz = aim.z - mc.player.getZ();
        double horizontalDist = Math.hypot(dx, dz);

        // Порог точности: как только мы ровно над целью — сбрасываем стрейфы и держим позицию при падении.
        aligned = horizontalDist <= threshold.getValue();
        hasInput = !aligned;

        if (aligned) {
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            return;
        }

        // Ротация в сторону цели по X/Z (как в ElytraFlight) — горизонтальный полёт, pitch ровный.
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = 0f;

        float gcd = GCDFixer.getGCDValue();
        yaw -= (yaw - rotation.x) % gcd;
        rotation = new Vec2f(yaw, pitch);
        rotationYawOffset = rotation.x;

        // Silent-ротация: серверный yaw наводится на цель, камера не дёргается (как в ElytraFlight).
        RotationComponent.update(new Rotation(rotation.x, rotation.y), 360, 360, 360, 360, 0, 3, false);
    }

    private void updateTarget() {
        // Удерживаем зафиксированную цель, пока она валидна и не «убежала» (например, телепортом эндр-жемчуга).
        if (target != null) {
            if (!isValidTarget(target) || mc.player.distanceTo(target) > retargetDistance.getValue()) {
                target = null;
            }
        }

        if (target == null) {
            target = findNearestTarget();
        }
    }

    private LivingEntity findNearestTarget() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (!isValidTarget(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }

        return best;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        if (player == mc.player) return false;
        if (!player.isAlive()) return false;

        // Игнорируем друзей.
        if (!FriendRepository.shouldAttack(player)) return false;
        // Игнорируем ботов.
        if (Onetap.getInstance().getModuleStorage().get(AntiBot.class).isBot(player)) return false;

        return true;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        target = null;
        rotation = new Vec2f(mc.player != null ? mc.player.getYaw() : 0, 0);
        rotationYawOffset = Integer.MIN_VALUE;
        hasInput = false;
        aligned = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        target = null;
        rotationYawOffset = Integer.MIN_VALUE;
        hasInput = false;
        aligned = false;
    }
}
