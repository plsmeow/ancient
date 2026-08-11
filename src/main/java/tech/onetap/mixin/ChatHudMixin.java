package tech.onetap.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void skipAncientLog(ChatHudLine message, CallbackInfo ci) {
        if (message.content().getString().startsWith("Ancient ->")) {
            ci.cancel();
        }
    }
}
