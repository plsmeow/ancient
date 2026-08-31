package tech.onetap.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.LookEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "Free Camera", moduleDesc = "Свободная камера", moduleCategory = ModuleCategory.PLAYER)
public class FreeCamera extends Module {
    public final SliderSetting speed = new SliderSetting("Скорость", 1.0, 0.1, 5.0, 0.1);
    private final BooleanSetting reloadChunks = new BooleanSetting("Перезагрузка чанков", true);

    private Vec3d pos = Vec3d.ZERO;
    private Vec3d prevPos = Vec3d.ZERO;
    private float yaw, pitch, prevYaw, prevPitch;

    private boolean forward, backward, left, right, up, down;

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        pos = camPos;
        prevPos = camPos;

        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        prevYaw = yaw;
        prevPitch = pitch;

        GameOptions o = mc.options;
        forward = o.forwardKey.isPressed();
        backward = o.backKey.isPressed();
        left = o.leftKey.isPressed();
        right = o.rightKey.isPressed();
        up = o.jumpKey.isPressed();
        down = o.sneakKey.isPressed();

        unpress();
        if (reloadChunks.getValue() && mc.worldRenderer != null) mc.worldRenderer.reload();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        forward = backward = left = right = up = down = false;
        unpress();
        if (reloadChunks.getValue() && mc.worldRenderer != null) mc.worldRenderer.reload();
    }

    private void unpress() {
        GameOptions o = mc.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
    }

    @EventHandler
    private void onTick(EventTick e) {
        if (mc.player == null) return;
        unpress();

        if (mc.currentScreen != null) {
            forward = backward = left = right = up = down = false;
            prevPos = pos;
            return;
        }

        Vec3d fwd = Vec3d.fromPolar(0, yaw);
        Vec3d rgt = Vec3d.fromPolar(0, yaw + 90);

        double velX = 0, velY = 0, velZ = 0;
        double speedVal = speed.getValue();
        double s = mc.options.sprintKey.isPressed() ? 1.0 : 0.5;

        boolean movingXZ = false, movingSide = false;
        if (forward) {
            velX += fwd.x * s * speedVal;
            velZ += fwd.z * s * speedVal;
            movingXZ = true;
        }
        if (backward) {
            velX -= fwd.x * s * speedVal;
            velZ -= fwd.z * s * speedVal;
            movingXZ = true;
        }
        if (right) {
            velX += rgt.x * s * speedVal;
            velZ += rgt.z * s * speedVal;
            movingSide = true;
        }
        if (left) {
            velX -= rgt.x * s * speedVal;
            velZ -= rgt.z * s * speedVal;
            movingSide = true;
        }
        if (movingXZ && movingSide) {
            double diagonal = 1 / Math.sqrt(2);
            velX *= diagonal;
            velZ *= diagonal;
        }
        if (up) velY += s * speedVal;
        if (down) velY -= s * speedVal;

        prevPos = pos;
        pos = pos.add(velX, velY, velZ);
    }

    @EventHandler
    private void onLook(LookEvent e) {
        changeLookDirection(e.getYaw() * 0.15, e.getPitch() * 0.15);
        e.cancelEvent();
    }

    @EventHandler
    private void onKey(EventKeyInput e) {
        if (mc.currentScreen != null) return;

        boolean pressed = e.getAction() != 0;
        int key = e.getKey();
        GameOptions o = mc.options;

        if (matches(o.forwardKey, key)) {
            forward = pressed;
            o.forwardKey.setPressed(false);
        } else if (matches(o.backKey, key)) {
            backward = pressed;
            o.backKey.setPressed(false);
        } else if (matches(o.rightKey, key)) {
            right = pressed;
            o.rightKey.setPressed(false);
        } else if (matches(o.leftKey, key)) {
            left = pressed;
            o.leftKey.setPressed(false);
        } else if (matches(o.jumpKey, key)) {
            up = pressed;
            o.jumpKey.setPressed(false);
        } else if (matches(o.sneakKey, key)) {
            down = pressed;
            o.sneakKey.setPressed(false);
        }
    }

    @EventHandler
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof PlayerRespawnS2CPacket
                || e.getPacket() instanceof GameJoinS2CPacket
                || e.getPacket() instanceof DisconnectS2CPacket) {
            setEnabled(false);
        }
    }

    private boolean matches(KeyBinding binding, int key) {
        return binding.matchesKey(key, -1) || binding.matchesMouse(key);
    }

    private void changeLookDirection(double deltaX, double deltaY) {
        prevYaw = yaw;
        prevPitch = pitch;
        yaw += (float) deltaX;
        pitch += (float) deltaY;
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
    }

    public boolean reloadChunks() {
        return reloadChunks.getValue();
    }

    public double getX(float tickDelta) {
        return MathHelper.lerp((double) tickDelta, prevPos.x, pos.x);
    }

    public double getY(float tickDelta) {
        return MathHelper.lerp((double) tickDelta, prevPos.y, pos.y);
    }

    public double getZ(float tickDelta) {
        return MathHelper.lerp((double) tickDelta, prevPos.z, pos.z);
    }

    public double getYaw(float tickDelta) {
        return MathHelper.lerp((double) tickDelta, prevYaw, yaw);
    }

    public double getPitch(float tickDelta) {
        return MathHelper.lerp((double) tickDelta, prevPitch, pitch);
    }

    public Vec3d getCameraPos(float tickDelta) {
        return new Vec3d(getX(tickDelta), getY(tickDelta), getZ(tickDelta));
    }

    public Vec3d getCameraDirection(float tickDelta) {
        return Vec3d.fromPolar((float) getPitch(tickDelta), (float) getYaw(tickDelta));
    }
}
