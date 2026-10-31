package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class MixinWeatherEffectRenderer {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onWeather(ClientLevel clientLevel, float f, Vec3 vec3, WeatherRenderState weatherRenderState, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noWeather()) {
            ci.cancel();
        }
    }
}
