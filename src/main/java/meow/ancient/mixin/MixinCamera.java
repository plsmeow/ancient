package meow.ancient.mixin;

import meow.ancient.module.modules.render.CameraTweaksModule;
import meow.ancient.module.modules.render.FreeCameraModule;
import meow.ancient.module.modules.render.FreeLookModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
            return;
        }
        if (FreeLookModule.INSTANCE.isActive()) {
            float yaw = FreeLookModule.INSTANCE.getYaw(partialTicks);
            float pitch = FreeLookModule.INSTANCE.getPitch(partialTicks);
            if (Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
                yaw += 180.0f;
                pitch = -pitch;
            }
            this.setRotation(yaw, pitch);
        }
    }

    @Inject(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private void onFreeLookBeforeOffset(float partialTicks, CallbackInfo ci) {
        ancient$applyFreeLookRotation(partialTicks);
    }

    @Unique
    private void ancient$applyFreeLookRotation(float partialTicks) {
        if (!FreeLookModule.INSTANCE.isActive()) return;
        float yaw = FreeLookModule.INSTANCE.getYaw(partialTicks);
        float pitch = FreeLookModule.INSTANCE.getPitch(partialTicks);
        if (Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            yaw += 180.0f;
            pitch = -pitch;
        }
        this.setRotation(yaw, pitch);
    }

    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifyCameraDist(float cameraDist) {
        if (FreeCameraModule.INSTANCE.isEnabled()) return 0.0f;
        if (CameraTweaksModule.INSTANCE.isEnabled()) {
            return (float) CameraTweaksModule.INSTANCE.getDistance();
        }
        return cameraDist;
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onGetMaxZoom(float cameraDist, CallbackInfoReturnable<Float> cir) {
        if (FreeCameraModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(0.0f);
            return;
        }
        if (CameraTweaksModule.INSTANCE.isEnabled() && CameraTweaksModule.INSTANCE.getCameraClip()) {
            cir.setReturnValue((float) CameraTweaksModule.INSTANCE.getDistance());
        }
    }
}
