package meow.ancient.mixin;

import meow.ancient.event.EventManager;
import meow.ancient.event.events.ClientDisconnectEvent;
import meow.ancient.module.modules.render.HideModule;
import meow.ancient.module.modules.render.FreeCameraModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Redirect(method = "pick(F)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult ancient$pickFromFreeCamera(LocalPlayer player, float partialTicks, Entity cameraEntity) {
        if (FreeCameraModule.INSTANCE.isEnabled()) return FreeCameraModule.INSTANCE.pick(partialTicks);
        return player.raycastHitResult(partialTicks, cameraEntity);
    }

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean ancient$useEntityFromFreeCamera(LocalPlayer player, Entity entity, double padding) {
        if (!FreeCameraModule.INSTANCE.isEnabled()) return player.isWithinEntityInteractionRange(entity, padding);
        double reach = player.entityInteractionRange() + padding;
        return !entity.isRemoved() && entity.getBoundingBox().distanceToSqr(Minecraft.getInstance().gameRenderer.mainCamera().position()) < reach * reach;
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, boolean transferring, boolean keepResourcePacks, CallbackInfo ci) {
        EventManager.INSTANCE.post(ClientDisconnectEvent.INSTANCE);
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void onClearClientLevel(Screen screen, CallbackInfo ci) {
        EventManager.INSTANCE.post(ClientDisconnectEvent.INSTANCE);
    }

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void ancient$getWindowTitle(CallbackInfoReturnable<String> cir) {
        if (HideModule.isActive) return;
        cir.setReturnValue("Ancient");
    }
}
