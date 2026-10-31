package meow.ancient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noFireOverlay()) ci.cancel();
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void onWater(PlayerRenderState.WaterOverlay waterOverlay, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noLiquidOverlay()) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void onBlock(Identifier identifier, float f, float g, float h, float i, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int j, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noInWallOverlay()) ci.cancel();
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void onTotem(PlayerRenderState playerRenderState, PoseStack poseStack, float f, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noTotemAnimation()) ci.cancel();
    }
}
