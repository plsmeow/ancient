package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;
import tech.onetap.event.list.LookEvent;
import tech.onetap.event.list.RotationEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;

@ModuleInformation(moduleName = "Third Person", moduleDesc = "Вращение камеры без поворота тела", moduleCategory = ModuleCategory.RENDER)
public class FreeLook extends Module {

    public final BindSetting key = new BindSetting("Клавиша", GLFW.GLFW_KEY_LEFT_ALT);

    private boolean active = false;
    private float cameraYaw = 0;
    private float cameraPitch = 0;
    private float playerYaw = 0;
    private float playerPitch = 0;

    @Override
    public void onEnable() {
        super.onEnable();
        resetCamera();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        active = false;
    }

    private void resetCamera() {
        if (mc.player != null) {
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
            playerYaw = mc.player.getYaw();
            playerPitch = mc.player.getPitch();
        }
    }

    private boolean isKeyPressed() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        int keyCode = key.getValue();
        return GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS;
    }

    @Subscribe
    public void onLook(LookEvent event) {
        if (mc.player == null) return;

        boolean wasActive = active;
        active = isKeyPressed();

        if (active) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            if (!wasActive) {
                playerYaw = mc.player.getYaw();
                playerPitch = mc.player.getPitch();
                cameraYaw = playerYaw;
                cameraPitch = playerPitch;
            }

            cameraYaw += (float) event.getYaw() * 0.15f;
            cameraPitch += (float) event.getPitch() * 0.15f;
            cameraPitch = Math.max(-90, Math.min(90, cameraPitch));

            event.setCancelled(true);
        } else if (wasActive) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
            resetCamera();
        }
    }

    @Subscribe
    public void onRotation(RotationEvent event) {
        if (mc.player == null) return;

        if (active) {
            event.setYaw(cameraYaw);
            event.setPitch(cameraPitch);
        }
    }

    public boolean isActive() {
        return active && isEnabled();
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }
}
