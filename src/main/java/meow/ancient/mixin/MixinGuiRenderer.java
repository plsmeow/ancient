package meow.ancient.mixin;

import meow.ancient.client.util.render.KawaseBlur;
import meow.ancient.module.modules.render.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudModule.INSTANCE.isEnabled() && HudModule.INSTANCE.getBlur()
            && minecraft.player != null && minecraft.level != null && !minecraft.gui.hud.isHidden()) {
            KawaseBlur.INSTANCE.updateBlur(
                (float) HudModule.INSTANCE.getBlurRadius(),
                (int) HudModule.INSTANCE.getBlurIterations()
            );
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        KawaseBlur.INSTANCE.close();
    }
}
