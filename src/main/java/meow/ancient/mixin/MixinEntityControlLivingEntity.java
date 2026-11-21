package meow.ancient.mixin;

import meow.ancient.module.modules.movement.EntityControlModule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinEntityControlLivingEntity {
    // Move at the normal travel phase, preserving the rest of the mob's tick and animations.
    @Inject(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getControllingPassenger()Lnet/minecraft/world/entity/LivingEntity;"))
    private void ancient$moveMount(CallbackInfo ci) {
        EntityControlModule.INSTANCE.travel((LivingEntity) (Object) this);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void ancient$cancelMountTravel(Vec3 input, CallbackInfo ci) {
        if (EntityControlModule.INSTANCE.controls((LivingEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "travelRidden", at = @At("HEAD"), cancellable = true)
    private void ancient$cancelRiddenTravel(Player rider, Vec3 input, CallbackInfo ci) {
        if (EntityControlModule.INSTANCE.controls((LivingEntity) (Object) this)) ci.cancel();
    }
}
