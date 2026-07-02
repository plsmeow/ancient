package tech.onetap.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.onetap.event.list.EventChangeSprint;
import tech.onetap.module.list.player.NoPush;
import tech.onetap.module.list.render.SwingAnimations;
import tech.onetap.util.base.Instance;

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

}