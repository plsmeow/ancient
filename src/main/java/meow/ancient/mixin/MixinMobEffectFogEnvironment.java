package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectFogEnvironment.class)
public abstract class MixinMobEffectFogEnvironment {
    @Shadow public abstract Holder<MobEffect> getMobEffect();

    @Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
    private void ancient$hideEffectFog(CallbackInfoReturnable<Boolean> cir) {
        if ((getMobEffect() == MobEffects.BLINDNESS && NoRenderModule.INSTANCE.noBlindness())
            || (getMobEffect() == MobEffects.DARKNESS && NoRenderModule.INSTANCE.noDarkness())) {
            cir.setReturnValue(false);
        }
    }
}
