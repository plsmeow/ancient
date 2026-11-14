package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Consumable.class)
public class MixinConsumable {
    // Only consumption particles; preserve sounds, consumption and broken-item particles.
    @Redirect(method = "emitParticlesAndSounds", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;spawnItemParticles(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void ancient$hideEatingParticles(LivingEntity entity, ItemStack stack, int count) {
        if (entity.level().isClientSide() && NoRenderModule.INSTANCE.noEatParticles()) return;
        entity.spawnItemParticles(stack, count);
    }
}
