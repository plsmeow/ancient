package meow.ancient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import meow.ancient.client.gui.hud.HudElement;
import meow.ancient.client.gui.hud.HudSettingsPopup;
import meow.ancient.client.listener.listeners.RenderListener;
import meow.ancient.module.modules.render.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ancient$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        int popupButton = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? 0
                : event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1
                : event.button() == InputConstants.MOUSE_BUTTON_MIDDLE ? 2 : event.button();
        if (HudSettingsPopup.INSTANCE.mouseClicked(event.x(), event.y(), popupButton, event.modifiers())) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            HudElement element = HudModule.INSTANCE.getDragController().hitElement(event.x(), event.y());
            if (element != null) {
                HudSettingsPopup.INSTANCE.open(element, event.x(), event.y());
                cir.setReturnValue(true);
            }
        } else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT &&
                HudModule.INSTANCE.getDragController().mouseClicked(event.x(), event.y(), 0)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ancient$popupKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (HudSettingsPopup.INSTANCE.keyPressed(event)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void ancient$popupMouseScrolled(double x, double y, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (HudSettingsPopup.INSTANCE.mouseScrolled(x, y, dx, dy)) cir.setReturnValue(true);
    }

    @Inject(method = {"onClose", "removed"}, at = @At("HEAD"))
    private void ancient$stopDragging(CallbackInfo ci) {
        HudSettingsPopup.INSTANCE.close();
        HudModule.INSTANCE.getDragController().clearInteraction();
    }

    @Inject(method = "resize", at = @At("HEAD"))
    private void ancient$resize(int width, int height, CallbackInfo ci) {
        HudSettingsPopup.INSTANCE.close();
        HudModule.INSTANCE.getDragController().clearInteraction();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ancient$renderHud(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        RenderListener.INSTANCE.renderChatHud(graphics);
    }
}
