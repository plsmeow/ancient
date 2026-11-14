package meow.ancient.mixin;

import meow.ancient.client.util.render.NoRenderParticleState;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public class MixinParticle implements NoRenderParticleState {
    @Unique private ParticleType<?> ancient$particleType;

    @Override
    public ParticleType<?> ancient$getParticleType() {
        return ancient$particleType;
    }

    @Override
    public void ancient$setParticleType(ParticleType<?> type) {
        ancient$particleType = type;
    }
}
