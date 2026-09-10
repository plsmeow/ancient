package tech.onetap.util.render.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import tech.onetap.util.render.font.FormattedTextProcessor;

import java.util.List;

/**
 * Facade text renderer over the ported DeltaClient text shader (mre:core/text/text).
 * Preserves the previous engine's exact visual metrics: thickness 0.05, smoothness 0.5,
 * draw offset x-0.75 / y+size*0.7, per-segment spacing -0.3 on styled text, and the
 * fadeout contract of the previous engine (fractions 0..1 of maxWidth relative to the
 * text start, converted to the pixel-space fade the new shader expects).
 */
@UtilityClass
public class MsdfRenderer {

    public final ShaderProgramKey MSDF_FONT_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("mre", "core/text/text"),
            VertexFormats.POSITION_TEXTURE_COLOR,
            Defines.EMPTY
    );

    public void renderText(
            MsdfFont font,
            String text,
            float size,
            int color,
            Matrix4f matrix,
            float x,
            float y,
            float z
    ) {
        renderText(font, text, size, color, matrix, x, y, z, false, 0.0f, 1.0f, 0.0F);
    }

    public void renderText(
            MsdfFont font,
            String text,
            float size,
            int color,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            boolean enableFadeout,
            float fadeoutStart,
            float fadeoutEnd,
            float maxWidth
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }

        float thickness = 0.05f;
        float spacing = 0;

        // Новый шейдер гасит по пиксельным координатам (FragX), старый фасад принимает
        // доли 0..1 от maxWidth относительно начала текста — переводим как раньше.
        float fadeStartPx = x + (maxWidth * fadeoutStart);
        float fadeEndPx = x + (maxWidth * fadeoutEnd);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, font.getTextureId());

        ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
        shader.getUniform("uRange").set(font.getAtlas().range());
        shader.getUniform("uThickness").set(thickness);
        shader.getUniform("uSmoothness").set(0.5f);
        shader.getUniform("uOutline").set(0);
        shader.getUniform("uFadeEnabled").set(enableFadeout ? 1 : 0);
        shader.getUniform("uFadeStart").set(fadeStartPx);
        shader.getUniform("uFadeEnd").set(fadeEndPx);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        boolean hasGlyphs = font.applyGlyphs(
                matrix,
                builder,
                text,
                size,
                thickness * 0.5f * size,
                spacing,
                // рокстарский MAGIC VALUE: небольшой оффсет, чтобы всегда быть внутри краев
                x - 0.75F,
                y + (size * 0.7F),
                z,
                color
        );

        if (hasGlyphs) {
            BufferRenderer.drawWithGlobalProgram(builder.end());
        } else {
            builder.endNullable();
        }

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void renderText(
            MsdfFont font,
            String text,
            float size,
            int color,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            boolean enableFadeout,
            float fadeoutStart,
            float fadeoutEnd
    ) {
        float maxWidth = font.getWidth(text, size) * 2.0F;
        renderText(font, text, size, color, matrix, x, y, z, enableFadeout, fadeoutStart, fadeoutEnd, maxWidth);
    }

    public void renderText(
            MsdfFont font,
            Text text,
            float size,
            Matrix4f matrix,
            float x,
            float y,
            float z
    ) {
        renderText(font, text, size, matrix, x, y, z, false, 0.0f, 1.0f, 0.0F);
    }

    public void renderText(
            MsdfFont font,
            Text text,
            float size,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            int alpha
    ) {
        renderText(font, text, size, matrix, x, y, z, false, 0.0f, 1.0f, 0.0F, alpha);
    }

    private void setupStyledText(MsdfFont font, boolean enableFadeout, float fadeStartPx, float fadeEndPx) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, font.getTextureId());

        ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
        shader.getUniform("uRange").set(font.getAtlas().range());
        shader.getUniform("uThickness").set(0.05f);
        shader.getUniform("uSmoothness").set(0.5f);
        shader.getUniform("uOutline").set(0);
        shader.getUniform("uFadeEnabled").set(enableFadeout ? 1 : 0);
        shader.getUniform("uFadeStart").set(fadeStartPx);
        shader.getUniform("uFadeEnd").set(fadeEndPx);
    }

    private void finishText() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void renderText(
            MsdfFont font,
            Text text,
            float size,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            boolean enableFadeout,
            float fadeoutStart,
            float fadeoutEnd,
            float maxWidth
    ) {
        renderStyledText(font, text, size, matrix, x, y, z, enableFadeout, fadeoutStart, fadeoutEnd, maxWidth, 255);
    }

    public void renderText(
            MsdfFont font,
            Text text,
            float size,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            boolean enableFadeout,
            float fadeoutStart,
            float fadeoutEnd,
            float maxWidth,
            int alpha
    ) {
        renderStyledText(font, text, size, matrix, x, y, z, enableFadeout, fadeoutStart, fadeoutEnd, maxWidth, alpha);
    }

    private void renderStyledText(
            MsdfFont font,
            Text text,
            float size,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            boolean enableFadeout,
            float fadeoutStart,
            float fadeoutEnd,
            float maxWidth,
            int alpha
    ) {
        List<FormattedTextProcessor.TextSegment> segments = FormattedTextProcessor.processText(text, -1);

        float currentX = x;
        float fadeStartPx = x + (maxWidth * fadeoutStart);
        float fadeEndPx = x + (maxWidth * fadeoutEnd);

        setupStyledText(font, enableFadeout, fadeStartPx, fadeEndPx);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        boolean hasGlyphs = false;

        for (FormattedTextProcessor.TextSegment segment : segments) {
            int color = segment.color();
            if (alpha != 255) {
                color = (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
            }
            hasGlyphs |= font.applyGlyphs(
                    matrix,
                    builder,
                    segment.text(),
                    size,
                    0.05f * 0.5f * size,
                    -0.3F,
                    // рокстарский MAGIC VALUE
                    currentX - 0.75F,
                    y + (size * 0.7F),
                    z,
                    color
            );

            currentX += font.getWidth(segment.text(), size);
        }

        if (hasGlyphs) {
            BufferRenderer.drawWithGlobalProgram(builder.end());
        } else {
            builder.endNullable();
        }

        finishText();
    }

    public void renderText(
            MsdfFont font,
            Text text,
            float size,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            boolean enableFadeout,
            float fadeoutStart,
            float fadeoutEnd
    ) {
        float maxWidth = font.getWidth(text, size) * 2.0F;
        renderText(font, text, size, matrix, x, y, z, enableFadeout, fadeoutStart, fadeoutEnd, maxWidth);
    }
}
