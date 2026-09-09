package tech.onetap.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.onetap.util.draggable.DragManager;

@Mixin(ChatScreen.class)
public class ChatScreenMixin extends Screen {

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "removed", at = @At(value = "HEAD"))
    private void removed(CallbackInfo ci) {
        DragManager.onReleaseAll(0);
    }

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    private void injectDragClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        tech.onetap.module.list.render.Interface interfaceModule =
                tech.onetap.util.base.Instance.get(tech.onetap.module.list.render.Interface.class);
        if (interfaceModule != null && button == 0 && interfaceModule.onPopupLeftClick(mouseX, mouseY)) {
            return;
        }
        DragManager.onClickAll(button);
    }

    @Inject(method = "render", at = @At(value = "RETURN"))
    public void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        DragManager.onDrawAll();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        DragManager.onReleaseAll(button);
        return super.mouseReleased(mouseX, mouseY, button);
    }
}