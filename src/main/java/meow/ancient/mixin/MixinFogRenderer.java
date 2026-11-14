package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @ModifyVariable(method = "getBuffer", at = @At("HEAD"), argsOnly = true)
    private FogRenderer.FogMode ancient$disableFog(FogRenderer.FogMode mode) {
        return NoRenderModule.INSTANCE.noFog() ? FogRenderer.FogMode.NONE : mode;
    }
}
