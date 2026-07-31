package tech.onetap.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.util.log.ClientLogBuffer;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Final @Shadow private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;
    @Final @Shadow private ResourceReload reload;
    @Final @Shadow private Consumer<Optional<Throwable>> exceptionHandler;

    @Unique private static final Identifier ANCIENT_LOGO = Identifier.of("mre", "splash_logo");

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ancient$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        long now = Util.getMeasuringTimeMs();

        if (this.reloading && this.reloadStartTime == -1L) {
            this.reloadStartTime = now;
        }

        float fadeOut = this.reloadCompleteTime > -1L ? (float) (now - this.reloadCompleteTime) / 1000.0F : -1.0F;
        float fadeIn = this.reloadStartTime > -1L ? (float) (now - this.reloadStartTime) / 500.0F : -1.0F;

        float fade;
        int bgAlpha;
        if (fadeOut >= 1.0F) {
            if (mc.currentScreen != null) {
                mc.currentScreen.render(context, 0, 0, delta);
            }
            fade = 1.0F - MathHelper.clamp(fadeOut - 1.0F, 0.0F, 1.0F);
            bgAlpha = MathHelper.ceil(fade * 255.0F);
        } else if (this.reloading) {
            if (mc.currentScreen != null && fadeIn < 1.0F) {
                mc.currentScreen.render(context, mouseX, mouseY, delta);
            }
            fade = MathHelper.clamp(fadeIn, 0.0F, 1.0F);
            bgAlpha = MathHelper.ceil(MathHelper.clamp((double) fadeIn, 0.15, 1.0) * 255.0);
        } else {
            fade = 1.0F;
            bgAlpha = 255;
            com.mojang.blaze3d.platform.GlStateManager._clearColor(0, 0, 0, 1);
            com.mojang.blaze3d.platform.GlStateManager._clear(16384);
        }

        float actual = this.reload.getProgress();
        this.progress = MathHelper.clamp(this.progress * 0.95F + actual * 0.050000012F, 0.0F, 1.0F);

        context.fill(0, 0, w, h, ancient$withAlpha(0x000000, bgAlpha));

        ancient$ensureTexture(mc, ANCIENT_LOGO, "/assets/mre/textures/splash_logo.png");

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (this.reloading) {
            try {
                int lineHeight = 9;
                List<String> lines = ClientLogBuffer.tail(Math.max(1, (h - 8) / lineHeight));
                int logColor = ancient$withAlpha(0xC0C0C0, MathHelper.ceil(fade * 190.0F));
                for (int i = 0; i < lines.size(); i++) {
                    context.drawTextWithShadow(mc.textRenderer, lines.get(i), 4, 4 + i * lineHeight, logColor);
                }
            } catch (Throwable ignored) {
            }
        }

        int cx = w / 2;
        int cy = h / 2;
        int logoW = 300;
        int logoH = 75;

        RenderSystem.setShaderColor(0.15F, 0.15F, 0.15F, fade);
        context.drawTexture(RenderLayer::getGuiTextured, ANCIENT_LOGO, cx - logoW / 2, cy - logoH / 2, 0, 0, logoW, logoH, logoW, logoH);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade);
        int progressWidth = (int) (logoW * this.progress);
        if (progressWidth > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, ANCIENT_LOGO, cx - logoW / 2, cy - logoH / 2, 0, 0, progressWidth, logoH, logoW, logoH);
        }

        int barW = 200;
        int barH = 10;
        int barX = cx - barW / 2;
        int barY = cy + logoH / 2 + 20;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        context.fill(barX, barY, barX + barW, barY + barH, ancient$withAlpha(0xFFFFFF, MathHelper.ceil(40 * fade)));
        if (this.progress > 0.01f) {
            context.fill(barX, barY, barX + (int) (barW * this.progress), barY + barH,
                    ancient$withAlpha(0xFFFFFF, MathHelper.ceil(255 * fade)));
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        if (fadeOut >= 2.0F) {
            mc.setOverlay(null);
            return;
        }

        if (this.reloadCompleteTime == -1L && this.reload.isComplete() && (!this.reloading || fadeIn >= 2.0F)) {
            try {
                this.reload.throwException();
                this.exceptionHandler.accept(Optional.empty());
            } catch (Throwable t) {
                this.exceptionHandler.accept(Optional.of(t));
            }

            this.reloadCompleteTime = Util.getMeasuringTimeMs();
            if (mc.currentScreen != null) {
                mc.currentScreen.init(mc, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            }
        }
    }

    @Unique
    private static void ancient$ensureTexture(MinecraftClient mc, Identifier id, String path) {
        try {
            if (mc.getTextureManager().getTexture(id) instanceof NativeImageBackedTexture) {
                return;
            }
            try (InputStream in = SplashOverlayMixin.class.getResourceAsStream(path)) {
                if (in != null) {
                    mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(NativeImage.read(in)));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static int ancient$withAlpha(int color, int alpha) {
        return color & 0xFFFFFF | MathHelper.clamp(alpha, 0, 255) << 24;
    }
}
