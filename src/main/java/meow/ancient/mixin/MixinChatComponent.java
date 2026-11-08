package meow.ancient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class MixinChatComponent {
    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void skipAncientLog(GuiMessage message, CallbackInfo ci) {
        String raw = message.content().getString();
        String unformatted = raw.replaceAll("(?i)§[0-9a-fk-or]", "");
        if (raw.startsWith("Ancient ->")
                || raw.startsWith("§8[§dAncient§8]")
                || unformatted.startsWith("[Ancient]")
                || unformatted.startsWith("Ancient ->")
                || unformatted.contains("Код dehide:")
                || unformatted.contains("%show")) {
            ci.cancel();
        }
    }
}
