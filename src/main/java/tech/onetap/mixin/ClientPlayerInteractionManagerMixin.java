package tech.onetap.mixin;

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
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventAttackBlock;
import tech.onetap.event.list.EventRightClickBlock;
import tech.onetap.event.list.EventUseItem;
import tech.onetap.util.rotation.FreeLookComponent;

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

    @Unique private float onetap$savedYaw;
    @Unique private float onetap$savedPitch;
    @Unique private boolean onetap$rotationSwapped;

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void onUseItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        var event = new EventUseItem(hand);
        event.post();
        if (event.isCancelled()) cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void freelookUseHead(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!FreeLookComponent.interactionActive()) {
            onetap$rotationSwapped = false;
            return;
        }
        onetap$savedYaw = player.getYaw();
        onetap$savedPitch = player.getPitch();
        onetap$rotationSwapped = true;
        player.setYaw(FreeLookComponent.interactionYaw());
        player.setPitch(FreeLookComponent.interactionPitch());
    }

    @Inject(method = "interactItem", at = @At("RETURN"))
    private void freelookUseReturn(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!onetap$rotationSwapped) return;
        onetap$rotationSwapped = false;
        player.setYaw(onetap$savedYaw);
        player.setPitch(onetap$savedPitch);
    }
}
