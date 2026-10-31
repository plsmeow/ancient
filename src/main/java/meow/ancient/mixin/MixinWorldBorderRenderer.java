package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public class MixinWorldBorderRenderer {
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void onWorldBorder(WorldBorder worldBorder, float f, Vec3 vec3, double d, WorldBorderRenderState worldBorderRenderState, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noWorldBorder()) {
            ci.cancel();
        }
    }
}
