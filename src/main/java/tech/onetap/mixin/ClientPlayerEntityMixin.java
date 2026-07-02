package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.*;
import tech.onetap.module.list.player.FreeCamera;
import tech.onetap.module.list.player.NoPush;
import tech.onetap.util.base.Instance;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Final @Shadow protected MinecraftClient client;
    @Final @Shadow public ClientPlayNetworkHandler networkHandler;
    @Shadow public abstract void closeScreen();

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        new EventPlayerUpdate().post();
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void sendMovementPackets(CallbackInfo ci) {
        new EventPlayerSync().post();
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void sendMovementPacketsEnd(CallbackInfo ci) {
        new EventPlayerSyncEnd().post();
        new EventPostMotion().post();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickEnd(CallbackInfo ci) {
        new EventMoveInput().post();
    }

    @Inject(
            method = "sendMovementPackets",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendSprintingPacket()V",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void onSprint(CallbackInfo ci) {
        new EventSprintSync().post();
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"), require = 0)
    private boolean onIsUsingItemRedirect(ClientPlayerEntity player) {
        if (player.isUsingItem()) {
            EventNoSlow event = new EventNoSlow();
            event.post();
            return player.isUsingItem() && player.getVehicle() == null && !event.isCancelled();
         }else {
            return player.isUsingItem() && player.getVehicle() == null;
        }
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    public void pushOutOfBlocks(double x, double z, CallbackInfo ci) {
        if (Instance.get(FreeCamera.class).isEnabled()
                || Instance.get(NoPush.class).isEnabled()
                && Instance.get(NoPush.class).objects.isEnabled("Блоки")) {
            ci.cancel();
        }
    }
    
    /**
     * @author
     * @reason
     */
    @Overwrite
    public void closeHandledScreen() {
        var event = new EventCloseInv();
        event.post();

        if (!event.isCancelled()) {
            ScreenHandler screenHandler = ((PlayerEntity)(Object)this).currentScreenHandler;
            this.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(screenHandler.syncId));
        }

        this.closeScreen();
    }
}