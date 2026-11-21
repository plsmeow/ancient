package meow.ancient.mixin;

import meow.ancient.module.modules.movement.EntityControlModule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// This storage exists on ViaVersion's <= 1.21.1 vehicle steering translation path.
@Pseudo
@Mixin(targets = "com.viaversion.viaversion.protocols.v1_21to1_21_2.storage.ClientVehicleStorage", remap = false)
public class MixinEntityControlViaVehicle {
    @Shadow @Final private int vehicleId;

    @Inject(method = {"forwardMovement", "sidewaysMovement"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ancient$nanSteering(CallbackInfoReturnable<Float> cir) {
        if (EntityControlModule.INSTANCE.isGrimNaNActive(vehicleId)) cir.setReturnValue(Float.NaN);
    }
}
