package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.particle.ElderGuardianParticle;
import net.minecraft.client.particle.ElderGuardianParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Queue;
import java.util.stream.Stream;

@Mixin(ElderGuardianParticleGroup.class)
public class MixinElderGuardianParticleGroup {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;"))
    private Stream<ElderGuardianParticle> ancient$filterExistingParticles(Queue<ElderGuardianParticle> particles) {
        return particles.stream().filter(particle -> !NoRenderModule.INSTANCE.noParticle(particle));
    }
}
