package meow.ancient.mixin;

import meow.ancient.client.util.render.NoRenderParticleState;
import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {
    // Keep the factory result valid: firework callers configure it without a null check.
    // Filtering at add() skips registration and preserves normal particle-limit bookkeeping.
    @Inject(method = "makeParticle", at = @At("RETURN"))
    private void ancient$rememberParticleType(ParticleOptions options, double x, double y, double z,
                                              double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
        if (cir.getReturnValue() != null) {
            ((NoRenderParticleState) cir.getReturnValue()).ancient$setParticleType(options.getType());
        }
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void ancient$filterDirectParticle(Particle particle, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noParticle(particle)) ci.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
        at = @At("HEAD"), cancellable = true)
    private void ancient$filterEmitter(Entity entity, ParticleOptions options, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noParticle(options.getType())) ci.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
        at = @At("HEAD"), cancellable = true)
    private void ancient$filterTimedEmitter(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noParticle(options.getType())) ci.cancel();
    }
}
