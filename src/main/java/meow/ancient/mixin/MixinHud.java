package meow.ancient.mixin;

import meow.ancient.client.listener.listeners.RenderListener;
import meow.ancient.client.gui.hud.HudElement;
import meow.ancient.module.modules.render.HudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinHud {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderAncientHud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        RenderListener.INSTANCE.renderHud(guiGraphics, deltaTracker);
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void hideAncientPotionIcons(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (HudModule.INSTANCE.isEnabled() && HudModule.INSTANCE.isElementEnabled(HudElement.POTIONS)) {
            ci.cancel();
        }
    }
}
