package tech.onetap.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.RotationEvent;
import tech.onetap.module.list.player.FreeCamera;
import tech.onetap.module.list.render.NoRender;
import tech.onetap.util.base.Instance;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean thirdPerson;

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"
            )
    )
    private void redirectSetRotation(Camera instance, float yaw, float pitch, BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) {
            instance.setRotation((float) freeCamera.getYaw(tickDelta), (float) freeCamera.getPitch(tickDelta));
            return;
        }

        var event = new RotationEvent(yaw, pitch, tickDelta);
        event.post();

        float newYaw = event.getYaw();
        float newPitch = event.getPitch();

        if (thirdPerson && inverseView) {
            newYaw += 180.0F;
            newPitch = -newPitch;
        }

        instance.setRotation(newYaw, newPitch);
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"
            )
    )
    private void redirectSetPos(Camera instance, double x, double y, double z, BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) {
            instance.setPos(freeCamera.getX(tickDelta), freeCamera.getY(tickDelta), freeCamera.getZ(tickDelta));
        } else {
            instance.setPos(x, y, z);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdateTail(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) {
            this.thirdPerson = true;
        }
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"
            )
    )
    private float redirectClipToSpace(Camera instance, float f) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) return 0.0F;

        if (Instance.get(NoRender.class).isEnabled() && Instance.get(NoRender.class).elements.isEnabled("Камераклип")) return f;

        return instance.clipToSpace(f);
    }
}
