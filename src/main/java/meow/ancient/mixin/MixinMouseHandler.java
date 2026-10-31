package meow.ancient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import meow.ancient.client.gui.hud.HudSettingsPopup;
import meow.ancient.event.EventManager;
import meow.ancient.event.events.KeyPressEvent;
import meow.ancient.module.modules.render.HudModule;
import meow.ancient.module.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void ancient$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (window == 0 || window != minecraft.getWindow().handle()) return;
        if (minecraft.gui.screen() == null || minecraft.gui.screen() instanceof TitleScreen) {
            if (buttonInfo.button() >= 4 || buttonInfo.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
                int key = KeybindSetting.fromMouseButton(buttonInfo.button());
                KeyPressEvent event = new KeyPressEvent(key, 0, action, buttonInfo.modifiers());
                EventManager.INSTANCE.post(event);
                if (event.getCancel()) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "onMove(JDDDD)V", at = @At("TAIL"))
    private void ancient$mouseMoved(long window, double x, double y, double deltaX, double deltaY, CallbackInfo ci) {
        if (window == 0 || window != minecraft.getWindow().handle() || !(minecraft.gui.screen() instanceof ChatScreen)) return;
        if (HudSettingsPopup.INSTANCE.isOpen()) return;
        HudModule.INSTANCE.getDragController().mouseDragged(
                MouseHandler.getScaledXPos(minecraft.getWindow(), x),
                MouseHandler.getScaledYPos(minecraft.getWindow(), y), 0);
    }

    @Redirect(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
    private boolean ancient$mouseReleased(Screen screen, MouseButtonEvent event) {
        if (screen instanceof ChatScreen) {
            if (HudSettingsPopup.INSTANCE.isOpen()) {
                HudSettingsPopup.INSTANCE.mouseReleased(event.x(), event.y(), ancient$popupButton(event.button()));
                return true;
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT &&
                    HudModule.INSTANCE.getDragController().mouseReleased(event.x(), event.y(), 0)) {
                return true;
            }
        }
        return screen.mouseReleased(event);
    }

    @Redirect(method = "handleAccumulatedMovement()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
    private boolean ancient$mouseDragged(Screen screen, MouseButtonEvent event, double deltaX, double deltaY) {
        if (screen instanceof ChatScreen) {
            if (HudSettingsPopup.INSTANCE.isOpen() && HudSettingsPopup.INSTANCE.mouseDragged(
                    event.x(), event.y(), ancient$popupButton(event.button()), deltaX, deltaY)) {
                return true;
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT &&
                    HudModule.INSTANCE.getDragController().mouseDragged(event.x(), event.y(), 0)) {
                return true;
            }
        }
        return screen.mouseDragged(event, deltaX, deltaY);
    }

    @Unique
    private static int ancient$popupButton(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button;
        };
    }
}
