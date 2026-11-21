package meow.ancient.mixin;

import meow.ancient.module.modules.movement.EntityControlModule;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBoat.class)
public class MixinEntityControlBoat {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void ancient$moveBoat(AbstractBoat boat, MoverType type, Vec3 motion) {
        if (!EntityControlModule.INSTANCE.travel(boat)) boat.move(type, motion);
    }
}
