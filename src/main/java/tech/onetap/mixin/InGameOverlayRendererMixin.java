package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.module.list.render.NoRender;
import tech.onetap.util.base.Instance;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At(value = "HEAD"), cancellable = true)
    private static void renderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (Instance.get(NoRender.class).isEnabled() && Instance.get(NoRender.class).elements.isEnabled("Огонь")) ci.cancel();
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At(value = "HEAD"), cancellable = true)
    private static void renderUnderwaterOverlay(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (Instance.get(NoRender.class).isEnabled() && Instance.get(NoRender.class).elements.isEnabled("Размытие в воде")) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At(value = "HEAD"), cancellable = true)
    private static void renderInWallOverlay(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (Instance.get(NoRender.class).isEnabled() && Instance.get(NoRender.class).elements.isEnabled("Зрение в блоках")) ci.cancel();
    }
}