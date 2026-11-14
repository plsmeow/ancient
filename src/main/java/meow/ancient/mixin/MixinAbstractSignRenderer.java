package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public class MixinAbstractSignRenderer {
    @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
    private void ancient$hideVisual(CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noSignText()) ci.cancel();
    }
}
