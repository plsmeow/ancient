package meow.ancient.mixin;

import meow.ancient.client.util.render.NoRenderParticleState;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.particle.FireworkParticles$SparkParticle")
public class MixinFireworkSparkParticle {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ancient$rememberFireworkType(CallbackInfo ci) {
        ((NoRenderParticleState) this).ancient$setParticleType(ParticleTypes.FIREWORK);
    }
}
