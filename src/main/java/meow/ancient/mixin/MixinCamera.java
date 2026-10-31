package meow.ancient.mixin;

import meow.ancient.module.modules.render.FreeCameraModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void onAlignWithEntityTail(float partialTicks, CallbackInfo ci) {
        if (FreeCameraModule.INSTANCE.isEnabled()) {
            this.setRotation(FreeCameraModule.INSTANCE.getYaw(partialTicks), FreeCameraModule.INSTANCE.getPitch(partialTicks));
            this.setPosition(FreeCameraModule.INSTANCE.getX(partialTicks), FreeCameraModule.INSTANCE.getY(partialTicks), FreeCameraModule.INSTANCE.getZ(partialTicks));
            this.detached = true;
        }
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onGetMaxZoom(float cameraDist, CallbackInfoReturnable<Float> cir) {
        if (FreeCameraModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(0.0f);
        }
    }
}
