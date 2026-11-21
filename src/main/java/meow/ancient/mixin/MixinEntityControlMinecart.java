package meow.ancient.mixin;

import meow.ancient.module.modules.movement.EntityControlModule;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractMinecart.class)
public class MixinEntityControlMinecart {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/vehicle/minecart/MinecartBehavior;tick()V"))
    private void ancient$moveMinecart(MinecartBehavior behavior) {
        if (!EntityControlModule.INSTANCE.travel((AbstractMinecart) (Object) this)) behavior.tick();
    }
}
