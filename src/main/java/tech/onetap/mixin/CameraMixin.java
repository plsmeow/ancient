package tech.onetap.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tech.onetap.event.list.RotationEvent;
import tech.onetap.module.list.render.NoRender;
import tech.onetap.util.base.Instance;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"
            )
    )
    private void redirectSetRotation(Camera instance, float yaw, float pitch, BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta) {
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
                    target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"
            )
    )
    private float redirectClipToSpace(Camera instance, float f) {
        if (Instance.get(NoRender.class).isEnabled() && Instance.get(NoRender.class).elements.isEnabled("Камераклип")) return f;

        return instance.clipToSpace(f);
    }
}