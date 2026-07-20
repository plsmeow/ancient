package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import tech.onetap.Onetap;
import tech.onetap.event.list.*;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.player.move.MoveUtil;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "Speed", moduleCategory = ModuleCategory.MOVEMENT)
public class Speed extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Contact", "Contact", "Vulcan", "Vanilla");

    private final SliderSetting boost = new SliderSetting("Сила буста", 8.0f, 1.0f, 20.0f, 0.1f).setVisible(() -> mode.is("Contact"));
    private final SliderSetting targetRange = new SliderSetting("Радиус цели", 3.0f, 0.5f, 10.0f, 0.1f).setVisible(() -> mode.is("Contact"));
    private final SliderSetting contactRange = new SliderSetting("Радиус контакта", 0.5f, 0.1f, 2.0f, 0.1f).setVisible(() -> mode.is("Contact"));

    private final BooleanSetting playersOnly = new BooleanSetting("Только игроки", true).setVisible(() -> mode.is("Contact"));
    private final BooleanSetting contactOnlyWhileMoving = new BooleanSetting("Только в движении", true).setVisible(() -> mode.is("Contact"));
    private final BooleanSetting onlyWithAura = new BooleanSetting("Только с Aura", false).setVisible(() -> mode.is("Contact"));

    private final BooleanSetting predict = new BooleanSetting("Предикт", true).setVisible(() -> mode.is("Contact"));
    private final SliderSetting predictStrength = new SliderSetting("Сила предикта", 2.0f, 0.1f, 10.0f, 0.1f).setVisible(() -> mode.is("Contact") && predict.getValue());

    private final BooleanSetting vulcanOnlyWhileMoving = new BooleanSetting("Только в движении", true).setVisible(() -> mode.is("Vulcan"));

    private final SliderSetting vanillaSpeed = new SliderSetting("Скорость", 1.18f, 1.05f, 10.0f, 0.01f).setVisible(() -> mode.is("Vanilla"));

    // HvH Target — Vanilla: автоматически идём к цели KillAura с предиктом по X/Z
    private final BooleanSetting hvhTarget = new BooleanSetting("HvH Target", false).setVisible(() -> mode.is("Vanilla"));
    private final SliderSetting hvhTargetRange = new SliderSetting("Радиус цели", ValueUnit.countable("блок", "блока", "блоков"), 25, 1, 50, 0.5f)
            .setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue());
    private final SliderSetting hvhPredictStrength = new SliderSetting("Сила предикта", 2.0f, 0.1f, 10.0f, 0.1f)
            .setVisible(() -> mode.is("Vanilla") && hvhTarget.getValue());

    private static final double VANILLA_DEFAULT_SPEED = 0.2873;

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Subscribe
    private void onTick(EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Vulcan")) {
            handleVulcan();
            return;
        }

        if (mode.is("Vanilla")) {
            handleVanilla();
            return;
        }

        handleContact();
    }

    private void handleVanilla() {
        if (!MoveUtil.hasPlayerMovement()) return;

        double speed = vanillaSpeed.getValue() * VANILLA_DEFAULT_SPEED;

        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            double value = (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1) * 0.205;
            speed += speed * value;
        }

        // HvH Target: если включён и есть валидная цель KillAura в радиусе — идём к предсказанной точке
        if (hvhTarget.getValue()) {
            KillAura aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
            if (aura != null && aura.isEnabled() && aura.getTarget() != null) {
                LivingEntity target = aura.getTarget();
                Vec3d toTarget = target.getPos().subtract(mc.player.getPos());
                double horizontalDistSq = toTarget.x * toTarget.x + toTarget.z * toTarget.z;
                double range = hvhTargetRange.getValue();
                if (horizontalDistSq <= range * range) {
                    // Предикт позиции цели по X/Z с учётом её velocity и направления движения
                    Vec3d targetPos = target.getPos();
                    Vec3d targetMotion = target.getVelocity();
                    double horizontalMotionSq = targetMotion.x * targetMotion.x + targetMotion.z * targetMotion.z;
                    if (horizontalMotionSq > 1.0E-4) {
                        targetPos = targetPos.add(
                                targetMotion.x * hvhPredictStrength.getValue(),
                                0.0,
                                targetMotion.z * hvhPredictStrength.getValue()
                        );
                    }

                    double[] dir = getDirectionToPoint(mc.player.getPos(), targetPos, speed);
                    Vec3d current = mc.player.getVelocity();
                    mc.player.setVelocity(dir[0], current.y, dir[1]);
                    return;
                }
            }
        }

        double[] change = transformVanillaStrafe(speed);

        Vec3d current = mc.player.getVelocity();
        mc.player.setVelocity(change[0], current.y, change[1]);
    }

    private double[] transformVanillaStrafe(double speed) {
        float forward = Math.signum(mc.player.input.movementForward);
        float side = Math.signum(mc.player.input.movementSideways);
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && side == 0.0f) return new double[]{0.0, 0.0};

        float strafe = 90 * side;
        if (forward != 0) strafe *= forward * 0.5f;

        yaw = yaw - strafe;
        if (forward < 0) yaw -= 180;
        double yawRadians = Math.toRadians(yaw);

        return new double[]{-Math.sin(yawRadians) * speed, Math.cos(yawRadians) * speed};
    }

    private void handleVulcan() {
        if (vulcanOnlyWhileMoving.getValue() && !MoveUtil.hasPlayerMovement()) return;
        if (!mc.player.isOnGround() || mc.player.horizontalCollision) return;
        if (mc.options.jumpKey.isPressed()) return;

        mc.player.jump();
        mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
        MoveUtil.setMotion(0.40f);
    }

    private void handleContact() {

        KillAura aura = null;
        if (onlyWithAura.getValue()) {
            aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
            if (aura == null || !aura.isEnabled() || aura.getTarget() == null) return;
        }

        Box contactBox = mc.player.getBoundingBox().expand(contactRange.getValue());
        int contactCount = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            if (contactBox.intersects(entity.getBoundingBox())) contactCount++;
        }

        if (contactCount <= 0) return;
        if (contactOnlyWhileMoving.getValue() && !MoveUtil.hasPlayerMovement()) return;

        double motionBoost = boost.getValue() * 0.01 * contactCount;
        if (motionBoost <= 0.0) return;

        Entity nearest = (onlyWithAura.getValue() && aura != null) ? aura.getTarget() : findNearestTarget(targetRange.getValue());
        if (nearest == null) return;

        Vec3d targetPos = nearest.getPos();
        if (predict.getValue()) {
            Vec3d targetMotion = nearest.getVelocity();
            double horizontalMotionSq = targetMotion.x * targetMotion.x + targetMotion.z * targetMotion.z;
            if (horizontalMotionSq > 1.0E-4) {
                targetPos = targetPos.add(targetMotion.x * predictStrength.getValue(), 0.0, targetMotion.z * predictStrength.getValue());
            }
        }

        double[] direction = getDirectionToPoint(mc.player.getPos(), targetPos, motionBoost);
        mc.player.addVelocity(direction[0], 0.0, direction[1]);
    }

    private Entity findNearestTarget(double maxRange) {
        Entity nearest = null;
        double bestDistanceSq = Double.MAX_VALUE;
        double maxDistanceSq = maxRange * maxRange;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;

            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            double distanceSq = dx * dx + dz * dz;

            if (distanceSq <= maxDistanceSq && distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                nearest = entity;
            }
        }

        return nearest;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player || !entity.isAlive()) return false;
        if (playersOnly.getValue() && !(entity instanceof PlayerEntity)) return false;
        return entity instanceof LivingEntity || entity instanceof BoatEntity;
    }

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double speedValue) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6) return new double[]{0.0, 0.0};
        return new double[]{dx / length * speedValue, dz / length * speedValue};
    }
}
