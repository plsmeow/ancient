package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import meow.ancient.event.EventManager;
import meow.ancient.event.events.ScreenCloseEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void onCloseHook(CallbackInfo ci) {
        if (!((Object) this instanceof AbstractContainerScreen<?>)) {
            ScreenCloseEvent event = new ScreenCloseEvent((Screen) (Object) this);
            EventManager.INSTANCE.post(event);
            if (event.getCancel()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = {
        "extractBackground",
        "extractBlurredBackground",
        "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
        "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",
        "extractTransparentBackground"
    }, at = @At("HEAD"), cancellable = true)
    private void ancient$hideBackground(CallbackInfo ci) {
        if (Minecraft.getInstance().level != null && NoRenderModule.INSTANCE.noGuiBackground()) ci.cancel();
    }

}
