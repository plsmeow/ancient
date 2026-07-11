package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.base.Instance;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "ElytraFlight", moduleDesc = "Быстрое и четкое передвижение на элитрах", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraFlight extends Module {

    private final SliderSetting fireworkDelay = new SliderSetting("Задержка феера", 750, 100, 2000, 50);

    private final StopWatch stopWatch = new StopWatch();
    private Vec2f rotation = new Vec2f(0, 0);
    private float rotationYawOffset = Integer.MIN_VALUE;
    private float rotationPitchOffset = Integer.MIN_VALUE;
    private boolean hasInput = false;

    @Subscribe
    private void onStrafe(MoveInputEvent e) {
        if (mc.player == null || !mc.player.isGliding()) return;
        if (!hasInput) return;
        if (rotationYawOffset == Integer.MIN_VALUE || rotationPitchOffset == Integer.MIN_VALUE) return;
        RotationComponent.fixMovement(e, rotationYawOffset);
    }

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;

        if (!mc.player.isGliding()) {
            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                } else {
                    NetworkUtils.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    mc.player.startGliding();
                    InventoryUtil.swapAndUseWithGuiBypass(Items.FIREWORK_ROCKET);
                    stopWatch.reset();
                }
                return;
            }
        }

        if (!mc.player.isGliding()) return;

        KillAura killAura = Instance.get(KillAura.class);
        boolean killauraActive = killAura != null && killAura.isEnabled() && killAura.getTarget() != null;

        if (stopWatch.isReached((long) fireworkDelay.getValue())) {
            InventoryUtil.swapAndUseWithGuiBypass(Items.FIREWORK_ROCKET);
            stopWatch.reset();
        }

        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();
        boolean jump = mc.options.jumpKey.isPressed();
        boolean sneak = mc.options.sneakKey.isPressed();
        boolean isMoving = forward || back || left || right;

        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float yaw;
        float pitch = 0;

        if (isMoving) {
            float yawOffset = 0f;
            if (forward) {
                if (left) yawOffset = -45f;
                else if (right) yawOffset = 45f;
            } else if (back) {
                yawOffset = 180f;
                if (left) yawOffset = -135f;
                else if (right) yawOffset = 135f;
            } else {
                if (left) yawOffset = -90f;
                else if (right) yawOffset = 90f;
            }
            yaw = cameraYaw + yawOffset;
        } else {
            yaw = cameraYaw;
        }

        if (jump) pitch = isMoving ? -25 : -90;
        else if (sneak) pitch = isMoving ? 25 : 90;

        boolean hasAnyInput = isMoving || jump || sneak;
        this.hasInput = hasAnyInput;

        if (!killauraActive && isMoving) {
            float motion = (pitch == -25 || pitch == 25) ? 0.5f : (pitch == 90 || pitch == -90) ? 0.25f : 1f;
            double rad = Math.toRadians(yaw + 90);
            double motionX = Math.cos(rad) * motion;
            double motionZ = Math.sin(rad) * motion;
            double vy = 0;
            if (pitch == -25) vy = 0.5;
            else if (pitch == -90) vy = 1;
            else if (pitch == 25) vy = -0.5;
            else if (pitch == 90) vy = -1;
            mc.player.setVelocity(motionX, vy, motionZ);
        } else if (!killauraActive && !isMoving && (jump || sneak)) {
            double vy = jump ? 1 : -1;
            mc.player.setVelocity(0, vy, 0);
        } else if (!killauraActive && !hasAnyInput) {
            mc.player.setVelocity(0, 0, 0);
        }

        if (hasAnyInput && !killauraActive) {
            yaw -= (yaw - rotation.x) % GCDFixer.getGCDValue();
            pitch -= (pitch - rotation.y) % GCDFixer.getGCDValue();
            rotation = new Vec2f(yaw, MathHelper.clamp(pitch, -90, 90));
            rotationYawOffset = rotation.x;
            rotationPitchOffset = rotation.y;
            RotationComponent.update(new Rotation(rotation.x, rotation.y), 360, 360, 360, 360, 0, 3, false);
            mc.player.setYaw(rotation.x);
            mc.player.setPitch(rotation.y);
            mc.player.headYaw = rotation.x;
            mc.player.bodyYaw = rotation.x;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        stopWatch.reset();
        rotation = new Vec2f(mc.player != null ? mc.player.getYaw() : 0, 0);
        rotationYawOffset = Integer.MIN_VALUE;
        rotationPitchOffset = Integer.MIN_VALUE;
        hasInput = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        rotationYawOffset = Integer.MIN_VALUE;
        rotationPitchOffset = Integer.MIN_VALUE;
        hasInput = false;
    }
}
