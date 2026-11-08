package meow.ancient.mixin;

import meow.ancient.event.EventManager;
import meow.ancient.event.events.ClientDisconnectEvent;
import meow.ancient.module.modules.render.HideModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {
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
