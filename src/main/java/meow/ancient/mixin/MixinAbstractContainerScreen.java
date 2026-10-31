package meow.ancient.mixin;
import meow.ancient.client.gui.material.ClickGuiScreen;
import meow.ancient.event.EventManager;
import meow.ancient.event.events.ScreenCloseEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class MixinAbstractContainerScreen {
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void onCloseHook(CallbackInfo ci) {
        ScreenCloseEvent event = new ScreenCloseEvent((Screen) (Object) this);
        EventManager.INSTANCE.post(event);
        if (event.getCancel()) {
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void onRemoved(CallbackInfo ci) {
        if (ClickGuiScreen.parentScreen == (Object) this) {
            ci.cancel();
        }
    }
}
