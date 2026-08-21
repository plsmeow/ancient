package tech.onetap.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.onetap.event.list.EventChangeSprint;
import tech.onetap.event.list.EventItemUseFinish;
import tech.onetap.module.list.player.NoPush;
import tech.onetap.module.list.render.SwingAnimations;
import tech.onetap.util.base.Instance;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.RotationComponent;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void cancelPushAway(Entity entity, CallbackInfo ci) {
        if (Instance.get(NoPush.class).isEnabled() && Instance.get(NoPush.class).objects.isEnabled("Игроки")) ci.cancel();
    }

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity && sprinting) {
            var event = new EventChangeSprint(true);
            event.post();

            if (!event.isSprinting()) ci.cancel();
        }
    }

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void onGetHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        var swing = Instance.get(SwingAnimations.class);

        if (swing != null && swing.isEnabled()) {
            var speed = (int) swing.speed.getValue();
            cir.setReturnValue(25 - speed * 2);
        }
    }

    @ModifyExpressionValue(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float freelookJumpYaw(float original) {
        if ((Object) this == MinecraftClient.getInstance().player && RotationComponent.getInstance().isFreelookMovement()) {
            return FreeLookComponent.interactionYaw();
        }
        return original;
    }

    @Inject(method = "consumeItem()V", at = @At("HEAD"))
    private void onConsumeItem(CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        new EventItemUseFinish(((LivingEntity) (Object) this).getActiveItem()).post();
    }

}