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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventAttackBlock;
import tech.onetap.event.list.EventRightClickBlock;

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


    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void autoToolBeforeStartBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        var event = new EventAttackBlock(pos, direction);
        event.post();
    }
}
