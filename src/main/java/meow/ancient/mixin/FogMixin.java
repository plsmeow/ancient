package meow.ancient.mixin;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import meow.ancient.Ancient;
import meow.ancient.module.list.render.Ambience;

@Mixin(BackgroundRenderer.class)
public class FogMixin {

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
    private static void onApplyFog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, 
                                    float viewDistance, boolean thickenFog, float tickDelta, 
                                    CallbackInfoReturnable<Fog> cir) {
        try {
            if (Ancient.getInstance() == null) return;
            if (Ancient.getInstance().getModuleStorage() == null) return;

            Ambience ambience = Ancient.getInstance().getModuleStorage().get(Ambience.class);
            if (ambience != null && ambience.isEnabled() && ambience.isFogEnabled()) {
                float fogDist = ambience.getFogDistance();
                Fog customFog = new Fog(fogDist * 0.25f, fogDist, FogShape.SPHERE, 
                        color.x, color.y, color.z, color.w);
                cir.setReturnValue(customFog);
            }
        } catch (Exception ignored) {}
    }
}
