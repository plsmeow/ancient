package meow.ancient.mixin;

import meow.ancient.client.util.rotation.MoveFixProcessor;
import meow.ancient.client.util.rotation.RotationManager;
import meow.ancient.module.modules.movement.ScaffoldModule;
import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientInput.class)
public class MixinClientInput {
    @Inject(method = "makeJump", at = @At("HEAD"), cancellable = true)
    private void ancient$waitForScaffoldSupport(CallbackInfo ci) {
        if (ScaffoldModule.INSTANCE.shouldBlockAutoJump()) ci.cancel();
    }

    @Inject(method = "hasForwardImpulse", at = @At("HEAD"), cancellable = true)
    private void onHasForwardImpulse(CallbackInfoReturnable<Boolean> cir) {
        if (RotationManager.INSTANCE.isActive() && MoveFixProcessor.INSTANCE.shouldOverrideForwardImpulse((ClientInput) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
