package meow.ancient.mixin;

import meow.ancient.client.util.rotation.RotationManager;
import meow.ancient.event.EventManager;
import meow.ancient.event.events.PlayerTickEndEvent;
import meow.ancient.event.events.PlayerUpdateEvent;
import meow.ancient.module.modules.movement.InventoryMoveModule;
import meow.ancient.module.modules.movement.NoSlowModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        RotationManager.INSTANCE.onPreUpdate();
        EventManager.INSTANCE.post(PlayerUpdateEvent.INSTANCE);
        RotationManager.INSTANCE.onPostUpdate();
        InventoryMoveModule.INSTANCE.applySlownessInput(((LocalPlayer) (Object) this).input);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickEnd(CallbackInfo ci) {
        EventManager.INSTANCE.post(PlayerTickEndEvent.INSTANCE);
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void onPreSendChanges(CallbackInfo ci) {
        RotationManager.INSTANCE.onPreSendChanges((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendChanges", at = @At("RETURN"))
    private void onPostSendChanges(CallbackInfo ci) {
        RotationManager.INSTANCE.onPostSendChanges((LocalPlayer) (Object) this);
    }

    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void onIsSlowDueToUsingItem(CallbackInfoReturnable<Boolean> cir) {
        if (NoSlowModule.INSTANCE.shouldCancelSlowdown()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void onItemUseSpeedMultiplier(CallbackInfoReturnable<Float> cir) {
        if (NoSlowModule.INSTANCE.shouldCancelSlowdown()) {
            cir.setReturnValue(1.0f);
        }
    }
}
