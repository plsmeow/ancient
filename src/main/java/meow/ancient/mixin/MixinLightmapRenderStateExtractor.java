package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightmapRenderStateExtractor {
    @Redirect(method = "extract", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float ancient$hideDarknessPulse(LocalPlayer player, Holder<MobEffect> effect, float partialTick) {
        return effect == MobEffects.DARKNESS && NoRenderModule.INSTANCE.noDarkness()
            ? 0.0f : player.getEffectBlendFactor(effect, partialTick);
    }
}
