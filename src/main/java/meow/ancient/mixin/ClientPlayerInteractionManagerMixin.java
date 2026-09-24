package meow.ancient.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import meow.ancient.event.list.EventAttack;
import meow.ancient.event.list.EventAttackBlock;
import meow.ancient.event.list.EventRightClickBlock;
import meow.ancient.event.list.EventUseItem;
import meow.ancient.util.rotation.FreeLookComponent;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At(value = "HEAD"), cancellable = true)
    private void attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        var event = new EventAttack(target);
        event.post();
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(
            method = "interactBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        var event = new EventRightClickBlock(hand, hitResult);
        event.post();
        if (event.isCancelled()) cir.setReturnValue(ActionResult.FAIL);
    }


    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void autoToolBeforeStartBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        var event = new EventAttackBlock(pos, direction);
        event.post();
        if (event.isCancelled()) cir.setReturnValue(false);
    }

    @Unique private float ancient$savedYaw;
    @Unique private float ancient$savedPitch;
    @Unique private boolean ancient$rotationSwapped;

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void onUseItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        var event = new EventUseItem(hand);
        event.post();
        if (event.isCancelled()) cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void freelookUseHead(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!FreeLookComponent.interactionActive()) {
            ancient$rotationSwapped = false;
            return;
        }
        ancient$savedYaw = player.getYaw();
        ancient$savedPitch = player.getPitch();
        ancient$rotationSwapped = true;
        player.setYaw(FreeLookComponent.interactionYaw());
        player.setPitch(FreeLookComponent.interactionPitch());
    }

    @Inject(method = "interactItem", at = @At("RETURN"))
    private void freelookUseReturn(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!ancient$rotationSwapped) return;
        ancient$rotationSwapped = false;
        player.setYaw(ancient$savedYaw);
        player.setPitch(ancient$savedPitch);
    }
}
