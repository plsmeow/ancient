package meow.ancient.mixin;

import meow.ancient.module.modules.render.FreeCameraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UseOnContext.class)
public abstract class MixinUseOnContext {
    @Shadow public abstract Player getPlayer();

    @Inject(method = "getHorizontalDirection", at = @At("HEAD"), cancellable = true)
    private void ancient$cameraHorizontalDirection(CallbackInfoReturnable<Direction> cir) {
        if (getPlayer() == Minecraft.getInstance().player && FreeCameraModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(FreeCameraModule.INSTANCE.interactionEntity().getDirection());
        }
    }

    @Inject(method = "getRotation", at = @At("HEAD"), cancellable = true)
    private void ancient$cameraRotation(CallbackInfoReturnable<Float> cir) {
        if (getPlayer() == Minecraft.getInstance().player && FreeCameraModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(FreeCameraModule.INSTANCE.getYaw());
        }
    }
}
