package meow.ancient.mixin;

import meow.ancient.client.listener.listeners.RenderListener;
import meow.ancient.client.gui.hud.HudElement;
import meow.ancient.module.modules.render.HudModule;
import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinHud {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderAncientHud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        RenderListener.INSTANCE.renderHud(guiGraphics, deltaTracker);
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void hideAncientPotionIcons(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if ((HudModule.INSTANCE.isEnabled() && HudModule.INSTANCE.isElementEnabled(HudElement.POTIONS))
            || NoRenderModule.INSTANCE.noPotionIcons()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void onBossOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noBossBar()) ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void onScoreboardSidebar(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noScoreboard()
            || (HudModule.INSTANCE.isEnabled() && HudModule.INSTANCE.isElementEnabled(HudElement.SCOREBOARD))) {
            ci.cancel();
        }
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void onCrosshair(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noCrosshair()) ci.cancel();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void onTitle(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noTitle()) ci.cancel();
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void onSelectedItemName(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noHeldItemName()) ci.cancel();
    }

    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onSpyglassOverlay(GuiGraphicsExtractor guiGraphics, float scale, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noSpyglassOverlay()) ci.cancel();
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void onVignette(GuiGraphicsExtractor guiGraphics, Entity entity, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noVignette()) ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onPortalOverlay(GuiGraphicsExtractor guiGraphics, float alpha, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noPortalOverlay()) ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void onConfusionOverlay(GuiGraphicsExtractor guiGraphics, float alpha, CallbackInfo ci) {
        if (NoRenderModule.INSTANCE.noNausea()) ci.cancel();
    }

    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void onTextureOverlay(GuiGraphicsExtractor guiGraphics, Identifier identifier, float f, CallbackInfo ci) {
        String path = identifier.getPath();
        if (path.contains("pumpkin") && NoRenderModule.INSTANCE.noPumpkinOverlay()) ci.cancel();
        else if (path.contains("powder_snow") && NoRenderModule.INSTANCE.noPowderedSnowOverlay()) ci.cancel();
    }
}
