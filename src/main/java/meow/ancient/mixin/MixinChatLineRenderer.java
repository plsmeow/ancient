package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class MixinChatLineRenderer {
    @Redirect(method = "accept", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;tag()Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;"))
    private GuiMessageTag ancient$hideSignatureIndicator(GuiMessage.Line line) {
        return NoRenderModule.INSTANCE.noSignatures() ? null : line.tag();
    }
}
