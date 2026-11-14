package meow.ancient.client.util.render;

import net.minecraft.core.particles.ParticleType;

/** Originating registry type, retained so already spawned particles can be hidden. */
public interface NoRenderParticleState {
    ParticleType<?> ancient$getParticleType();
    void ancient$setParticleType(ParticleType<?> type);
}
