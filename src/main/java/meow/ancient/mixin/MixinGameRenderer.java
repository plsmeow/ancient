package meow.ancient.mixin;

import meow.ancient.client.listener.listeners.RenderListener;
import meow.ancient.client.util.render.ChamsRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "render()V", at = @At("HEAD"))
    private void ancient$beginChamsFrame(CallbackInfo ci) {
        ChamsRenderer.INSTANCE.beginFrame();
    }

    @Inject(
        method = "render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V",
        at = @At("HEAD")
    )
    private void ancient$renderWorldChams(CallbackInfo ci) {
        ChamsRenderer.INSTANCE.renderWorld();
    }

    @Inject(
        method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;",
            shift = At.Shift.BEFORE
        )
    )
    private void ancient$renderHandsChams(CallbackInfo ci) {
        ChamsRenderer.INSTANCE.renderHands();
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void ancient$closeChamsRenderer(CallbackInfo ci) {
        ChamsRenderer.INSTANCE.close();
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void renderGuiPipelines(CallbackInfo ci) {
        RenderListener.INSTANCE.hookRenderArray(
            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false),
            RenderListener.ALL_GUI_PIPELINES
        );
    }
}
