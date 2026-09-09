package tech.onetap.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.EventHUD;
import tech.onetap.util.render.RenderShaders;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void runBlurChain(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (tech.onetap.module.list.render.Optimization.isActive()) {
            return;
        }
        RenderShaders.BLUR.runChain(context.getMatrices());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        new EventHUD(context, tickCounter).post();
    }
}
