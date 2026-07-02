package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(
        moduleName = "DragonFly",
        moduleCategory = ModuleCategory.MOVEMENT,
        moduleDesc = "Ускоряет полёт в креативе (/fly) + резкие движения"
)
public final class DragonFly extends Module {

    // ========== НАСТРОЙКИ ==========
    private final SliderSetting speedX = new SliderSetting("Скорость X", 2.0F, 0.5F, 10.0F, 0.5F);
    private final SliderSetting speedY = new SliderSetting("Скорость Y", 2.0F, 0.5F, 40.0F, 0.5F);
    private final BooleanSetting instantMotion = new BooleanSetting("Резкие движения", true);

    public DragonFly() {}

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null) {
            // Возвращаем стандартную скорость полёта
            mc.player.getAbilities().setFlySpeed(0.05f);
            mc.player.sendAbilitiesUpdate();
        }
    }

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;

        // Работает только если игрок в режиме полёта (креатив / команда)
        if (mc.player.getAbilities().flying) {
            // Устанавливаем ускоренную скорость (по горизонтали)
            float flySpeed = (float) (speedX.getValue() * 0.05f);
            mc.player.getAbilities().setFlySpeed(flySpeed);
            mc.player.sendAbilitiesUpdate();
        } else {
            // Если полёт выключен — сбрасываем на стандарт
            mc.player.getAbilities().setFlySpeed(0.05f);
            mc.player.sendAbilitiesUpdate();
        }
    }

    @Subscribe
    private void onStrafe(MoveInputEvent e) {
        if (mc.player == null) return;
        if (!mc.player.getAbilities().flying) return;
        if (!instantMotion.getValue()) return;

        Vec3d velocity = mc.player.getVelocity();

        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;

        if (forward == 0 && sideways == 0) {
            mc.player.setVelocity(0, velocity.y, 0);
        } else {
            // ГОРИЗОНТАЛЬ — ПОЛНАЯ СКОРОСТЬ
            double speedVal = speedX.getValue();

            Vec3d forwardVec = Vec3d.fromPolar(0, mc.player.getYaw()).normalize();
            Vec3d rightVec = Vec3d.fromPolar(0, mc.player.getYaw() - 90).normalize();

            double velX = forwardVec.x * forward * speedVal + rightVec.x * sideways * speedVal;
            double velZ = forwardVec.z * forward * speedVal + rightVec.z * sideways * speedVal;

            mc.player.setVelocity(velX, velocity.y, velZ);
        }

        // Вертикаль
        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, speedY.getValue() * 0.2, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -speedY.getValue() * 0.2, mc.player.getVelocity().z);
        }
    }

    // ========== ГЕТТЕРЫ ==========
    public SliderSetting getSpeedX() { return speedX; }
    public SliderSetting getSpeedY() { return speedY; }
    public BooleanSetting getInstantMotion() { return instantMotion; }
}