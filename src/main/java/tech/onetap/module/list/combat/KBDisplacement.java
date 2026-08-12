package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
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
import tech.onetap.util.base.Instance;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "KB Displacement", moduleDesc = "Displaces sprint-hit knockback direction", moduleCategory = ModuleCategory.COMBAT)
public class KBDisplacement extends Module {
    private static final String OWNER = "KB Displacement";
    private static final int HIT_TIMEOUT = 5;

    public final ModeSetting mode = new ModeSetting("Режим", "Left", "Left", "Right", "Back", "Custom");
    public final SliderSetting angle = new SliderSetting("Угол", 0, -180, 180, 1)
            .setVisible(() -> mode.is("Custom"));
    public final BooleanSetting noRot = new BooleanSetting("NoRot", false);

    private State state = State.IDLE;
    private PlayerEntity target;
    private int stateTicks;

    @EventHandler
    public void onTick(EventTick ignored) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        KillAura aura = Instance.get(KillAura.class);
        PlayerEntity auraTarget = (aura != null && aura.isEnabled()) ? asPlayer(aura.getTarget()) : null;

        if (auraTarget == null || !validTarget(auraTarget)) {
            if (state != State.IDLE) {
                restore();
            }
            return;
        }

        if (aura.canStopSprinting()) {
            if (state != State.WAITING_FOR_SPRINT_HIT || target != auraTarget) {
                target = auraTarget;
                stateTicks = 0;
            }
            state = State.WAITING_FOR_SPRINT_HIT;

            float yaw = targetYaw(target) + displacementAngle();
            RotationComponent.update(new Rotation(MathHelper.wrapDegrees(yaw), mc.player.getPitch()),
                    360, 360, 360, 360, HIT_TIMEOUT, 2, false, MoveFixMode.CORRECT, OWNER);
            return;
        }

        if (state == State.WAITING_FOR_SPRINT_HIT) {
            if (++stateTicks >= HIT_TIMEOUT) {
                restore();
            }
        }
    }

    @EventHandler
    public void onAttack(EventAttack event) {
        if (state != State.WAITING_FOR_SPRINT_HIT || target == null || event.getEntity() != target) {
            return;
        }
        restore();
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    private static PlayerEntity asPlayer(LivingEntity entity) {
        return entity instanceof PlayerEntity player ? player : null;
    }

    private void restore() {
        state = State.IDLE;
        stateTicks = 0;
        target = null;
        clearOwner();
        RotationComponent.getInstance().stopRotation();
    }

    private boolean validTarget(PlayerEntity candidate) {
        return candidate != null && candidate.isAlive()
                && !candidate.isSpectator() && !fullyNetherite(candidate)
                && mc.player.squaredDistanceTo(candidate) <= 4.5 * 4.5;
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
        IDLE, WAITING_FOR_SPRINT_HIT
    }
}
