package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "KB Displacement", moduleDesc = "Displaces sprint-hit knockback direction", moduleCategory = ModuleCategory.COMBAT)
public class KBDisplacement extends Module {
    private static final String OWNER = "KB Displacement";
    private static final int TARGET_TIMEOUT = 8;
    private static final int HIT_TIMEOUT = 5;

    public final ModeSetting mode = new ModeSetting("Режим", "Left", "Left", "Right", "Back", "Custom");
    public final SliderSetting angle = new SliderSetting("Угол", 0, -180, 180, 1)
            .setVisible(() -> mode.is("Custom"));
    public final BooleanSetting noRot = new BooleanSetting("NoRot", false);

    private State state = State.IDLE;
    private PlayerEntity target;
    private int stateTicks;

    @EventHandler
    public void onEvent(EventTick ignored) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        switch (state) {
            case IDLE, PREPARE_TARGET -> prepareTarget();
            case AIM_TARGET -> aimTarget();
            case PREPARE_DISPLACEMENT -> prepareDisplacement();
            case WAITING_FOR_SPRINT_HIT -> waitForHit();
            case RESTORE_CAMERA -> restoreCamera();
        }
    }

    @EventHandler
    public void onAttack(EventAttack event) {
        if (state != State.WAITING_FOR_SPRINT_HIT || target == null || event.getEntity() != target
                || !isKillAuraTarget(target) || !validSprintHit()) {
            return;
        }

        state = State.RESTORE_CAMERA;
        stateTicks = 0;
        clearOwner();
        RotationComponent.getInstance().stopRotation();
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    private void prepareTarget() {
        if (!isHitImminentForKillAura()) {
            reset();
            return;
        }

        KillAura aura = getKillAura();
        target = aura.getTarget() instanceof PlayerEntity player ? player : null;
        if (target == null || !validTarget(target)) {
            reset();
            return;
        }

        state = noRot.getValue() ? State.PREPARE_DISPLACEMENT : State.AIM_TARGET;
        stateTicks = 0;
    }

    private void aimTarget() {
        if (!isHitImminentForKillAura() || !isKillAuraTarget(target) || !validTarget(target) || stateTicks++ >= TARGET_TIMEOUT) {
            state = State.PREPARE_TARGET;
            stateTicks = 0;
            return;
        }
        RotationComponent.update(Rotation.from(mc.player, target), 360, 360, 360, 360, TARGET_TIMEOUT, 2, false,
                MoveFixMode.CORRECT, OWNER);
        if (new Rotation(mc.player).getDelta(Rotation.from(mc.player, target)) < 2) {
            state = State.PREPARE_DISPLACEMENT;
            stateTicks = 0;
        }
    }

    private void prepareDisplacement() {
        if (!isHitImminentForKillAura() || !isKillAuraTarget(target) || !validTarget(target) || stateTicks++ >= TARGET_TIMEOUT) {
            state = State.PREPARE_TARGET;
            stateTicks = 0;
            clearOwner();
            return;
        }
        float yaw = targetYaw(target) + displacementAngle();
        RotationComponent.update(new Rotation(MathHelper.wrapDegrees(yaw), mc.player.getPitch()), 360, 360, 360, 360,
                HIT_TIMEOUT, 2, false, MoveFixMode.CORRECT, OWNER);
        state = State.WAITING_FOR_SPRINT_HIT;
        stateTicks = 0;
    }

    private void waitForHit() {
        if (!isKillAuraTarget(target) || !validTarget(target) || stateTicks++ >= HIT_TIMEOUT) {
            state = State.RESTORE_CAMERA;
            stateTicks = 0;
            clearOwner();
            RotationComponent.getInstance().stopRotation();
        }
    }

    private void restoreCamera() {
        clearOwner();
        if (!RotationComponent.getInstance().isRotating()) {
            state = State.PREPARE_TARGET;
            stateTicks = 0;
        }
    }

    private KillAura getKillAura() {
        return tech.onetap.Onetap.getInstance().getModuleStorage().get(KillAura.class);
    }

    private boolean isHitImminentForKillAura() {
        KillAura aura = getKillAura();
        return aura != null && aura.isEnabled() && aura.isHitImminent(2);
    }

    private boolean isKillAuraTarget(PlayerEntity candidate) {
        KillAura aura = getKillAura();
        return aura != null && aura.isEnabled() && aura.getTarget() == candidate;
    }

    private boolean validTarget(PlayerEntity candidate) {
        return candidate != null && candidate != mc.player && candidate.isAlive()
                && !candidate.isSpectator() && !fullyNetherite(candidate)
                && mc.player.squaredDistanceTo(candidate) <= 4.5 * 4.5;
    }

    private boolean validSprintHit() {
        return target != null && mc.player.isSprinting()
                && mc.player.getAttackCooldownProgress(0.5f) >= 0.99f
                && !isCritical();
    }

    private boolean isCritical() {
        return !mc.player.isOnGround() && mc.player.fallDistance > 0.0f
                && !mc.player.getAbilities().flying && !mc.player.isClimbing()
                && !mc.player.isTouchingWater() && !mc.player.isInLava();
    }

    private float targetYaw(PlayerEntity entity) {
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
    }

    private float displacementAngle() {
        return switch (mode.getValue()) {
            case "Right" -> 90.0f;
            case "Back" -> 180.0f;
            case "Custom" -> angle.getFloatValue();
            default -> -90.0f;
        };
    }

    private boolean fullyNetherite(PlayerEntity player) {
        return isNetherite(player.getInventory().getArmorStack(0).getItem())
                && isNetherite(player.getInventory().getArmorStack(1).getItem())
                && isNetherite(player.getInventory().getArmorStack(2).getItem())
                && isNetherite(player.getInventory().getArmorStack(3).getItem());
    }

    private boolean isNetherite(Item item) {
        return item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
                || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS;
    }

    private void clearOwner() {
        RotationComponent.getInstance().clearMoveFixMode(OWNER);
    }

    private void reset() {
        clearOwner();
        target = null;
        state = State.IDLE;
        stateTicks = 0;
        if (mc.player != null) RotationComponent.getInstance().stopRotation();
    }

    private enum State {
        IDLE, PREPARE_TARGET, AIM_TARGET, PREPARE_DISPLACEMENT, WAITING_FOR_SPRINT_HIT, RESTORE_CAMERA
    }
}
