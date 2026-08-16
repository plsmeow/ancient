package tech.onetap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.RotationComponent;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @ModifyExpressionValue(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isControlledByPlayer()Z"))
    private boolean fixFallDistanceCalculation(boolean original) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            return false;
        }

        return original;
    }

    @ModifyExpressionValue(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float freelookMovementYaw(float original) {
        if ((Object) this == MinecraftClient.getInstance().player && RotationComponent.getInstance().isFreelookMovement()) {
            return FreeLookComponent.interactionYaw();
        }
        return original;
    }
}
