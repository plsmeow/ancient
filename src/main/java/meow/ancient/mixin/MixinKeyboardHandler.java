package meow.ancient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import meow.ancient.client.gui.hud.HudSettingsPopup;
import meow.ancient.event.EventManager;
import meow.ancient.event.events.KeyPressEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        if (window != 0 && window == minecraft.getWindow().handle() &&
                minecraft.gui.screen() instanceof ChatScreen && HudSettingsPopup.INSTANCE.isOpen()) {
            if ((action == InputConstants.PRESS || action == InputConstants.REPEAT) &&
                    HudSettingsPopup.INSTANCE.keyPressed(keyEvent)) {
                ci.cancel();
            }
            return;
        }
        KeyPressEvent event = new KeyPressEvent(keyEvent.key(), keyEvent.keycode(), action, keyEvent.modifiers());
        EventManager.INSTANCE.post(event);
        if (event.getCancel()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
    private void ancient$popupCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (window != 0 && window == minecraft.getWindow().handle() &&
                minecraft.gui.screen() instanceof ChatScreen && HudSettingsPopup.INSTANCE.isOpen() &&
                HudSettingsPopup.INSTANCE.charTyped(event)) {
            ci.cancel();
        }
    }
}
