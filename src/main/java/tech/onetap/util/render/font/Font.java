package tech.onetap.util.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.GradientUtil;
import tech.onetap.util.render.ScissorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MSDF font renderer ported from DeltaClient (aethereal.render.Font) using the
 * mre:core/text/text shader program. Supports per-glyph color (incl. §-codes and styled
 * Text), outline, per-fragment fade range, scrolling text with fade, animated gradients
 * and kerning-aware measurement.
 */
public class Font {
    private final ShaderProgramKey shaderKey =
            new ShaderProgramKey(Identifier.of("mre", "core/text/text"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    private final String fontName;
    private final AbstractTexture fontTexture;
    private final FontData.AtlasData atlasData;
    private final FontData.MetricsData metricsData;
    private final Map<Integer, MsdfGlyph> glyphs;
    private final Map<Integer, Map<Integer, Float>> kernings;

    public Font(String name, AbstractTexture texture, FontData.AtlasData atlas, FontData.MetricsData metrics,
                Map<Integer, MsdfGlyph> glyphs, Map<Integer, Map<Integer, Float>> kernings) {
        this.fontName = name;
        this.fontTexture = texture;
        this.atlasData = atlas;
        this.metricsData = metrics;
        this.glyphs = glyphs;
        this.kernings = kernings;
    }

    public static FontBuilder builder() {
        return new FontBuilder();
    }

    public String getName() {
        return this.fontName;
    }

    public FontData.AtlasData getAtlas() {
        return this.atlasData;
    }

    public FontData.MetricsData getMetrics() {
        return this.metricsData;
    }

    private void setupShader(float outlineThickness, float thickness, float smoothness, int outlineColor,
                             float fadeStart, float fadeEnd) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, this.fontTexture.getGlId());
        ShaderProgram shader = RenderSystem.setShader(this.shaderKey);
        if (shader == null) {
            return;
        }
        shader.getUniform("uRange").set(this.atlasData.range());
        shader.getUniform("uThickness").set(thickness);
        shader.getUniform("uSmoothness").set(smoothness);
        boolean outlineEnabled = outlineThickness > 0.0f;
        shader.getUniform("uOutline").set(outlineEnabled ? 1 : 0);
        if (outlineEnabled) {
            shader.getUniform("uOutlineThickness").set(outlineThickness);
            float[] outlineComponents = ColorUtil.normalize(outlineColor);
            shader.getUniform("uOutlineColor").set(outlineComponents[0], outlineComponents[1],
                    outlineComponents[2], outlineComponents[3]);
        }
        boolean fadeEnabled = fadeEnd > fadeStart;
        shader.getUniform("uFadeEnabled").set(fadeEnabled ? 1 : 0);
        if (fadeEnabled) {
            shader.getUniform("uFadeStart").set(fadeStart);
            shader.getUniform("uFadeEnd").set(fadeEnd);
        }
    }

    private void flush(BufferBuilder builder) {
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void drawText(MatrixStack matrixStack, Text text, float x, float y, float size, float alpha,
                         float thickness, float smoothness, float spacing, int outlineColor, float outlineThickness) {
        try {
            Matrix4f matrix = matrixStack.peek().getPositionMatrix();
            setupShader(outlineThickness, thickness, smoothness, outlineColor, -1.0f, -1.0f);
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            float adjustedThickness = (thickness + (outlineThickness * 0.5f)) * 0.5f * size;
            float baselineY = y + (this.metricsData.baselineHeight() * size);
            boolean hasGlyphs = drawGlyphs(matrix, builder, parseColoredGlyphs(text), size, alpha,
                    adjustedThickness, spacing, x, baselineY, 0.0f);
            if (hasGlyphs) {
                flush(builder);
            } else {
                builder.endNullable();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void drawText(MatrixStack matrixStack, String text, float x, float y, float size, float thickness,
                         int color, int colorSecond, float offset, float smoothness, float spacing,
                         int outlineColor, float outlineThickness) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        setupShader(outlineThickness, thickness, smoothness, outlineColor, -1.0f, -1.0f);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float adjustedThickness = (thickness + (outlineThickness * 0.5f)) * 0.5f * size;
        float baselineY = y + (this.metricsData.baselineHeight() * size);
        boolean hasGlyphs = drawGlyphs(matrix, builder, text, size, adjustedThickness, spacing, x, baselineY,
                0.0f, color, colorSecond, offset);
        if (hasGlyphs) {
            flush(builder);
        } else {
            builder.endNullable();
        }
    }

    /**
     * Same as {@link #drawText(MatrixStack, String, float, float, int, int, float, float, float, int, float)}
     * but with a caller-provided matrix (used by the BuiltText facade).
     */
    public void drawText(Matrix4f matrix, String text, float x, float y, float size, float thickness,
                         int color, int colorSecond, float offset, float smoothness, float spacing,
                         int outlineColor, float outlineThickness) {
        if (text == null || text.isEmpty()) {
            return;
        }
        setupShader(outlineThickness, thickness, smoothness, outlineColor, -1.0f, -1.0f);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float adjustedThickness = (thickness + (outlineThickness * 0.5f)) * 0.5f * size;
        float baselineY = y + (this.metricsData.baselineHeight() * size);
        boolean hasGlyphs = drawGlyphs(matrix, builder, text, size, adjustedThickness, spacing, x, baselineY,
                0.0f, color, colorSecond, offset);
        if (hasGlyphs) {
            flush(builder);
        } else {
            builder.endNullable();
        }
    }

    /**
     * Styled text draw with a caller-provided matrix (used by the BuiltMutableText facade).
     */
    public void drawText(Matrix4f matrix, Text text, float x, float y, float size, float alpha,
                         float thickness, float smoothness, float spacing, int outlineColor, float outlineThickness) {
        try {
            setupShader(outlineThickness, thickness, smoothness, outlineColor, -1.0f, -1.0f);
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            float adjustedThickness = (thickness + (outlineThickness * 0.5f)) * 0.5f * size;
            float baselineY = y + (this.metricsData.baselineHeight() * size);
            boolean hasGlyphs = drawGlyphs(matrix, builder, parseColoredGlyphs(text), size, alpha,
                    adjustedThickness, spacing, x, baselineY, 0.0f);
            if (hasGlyphs) {
                flush(builder);
            } else {
                builder.endNullable();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses a styled {@link Text} into colored glyph pairs (§-codes and style colors
     * respected); leading whitespace is stripped like in the DeltaClient original.
     */
    public List<MsdfGlyph.ColoredGlyph> parseColoredGlyphs(Text text) {
        List<MsdfGlyph.ColoredGlyph> result = new ArrayList<>();
        boolean[] started = {false};
        text.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }
            if (!started[0]) {
                string = string.replaceFirst("^\\s+", "");
                if (string.isEmpty()) {
                    return Optional.empty();
                }
                started[0] = true;
            }
            int color = style.getColor() != null ? style.getColor().getRgb() | 0xFF000000 : -1;
            result.addAll(parseColoredGlyphs(string, color));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private List<MsdfGlyph.ColoredGlyph> parseColoredGlyphs(String raw, int color) {
        List<MsdfGlyph.ColoredGlyph> result = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (i + 1 < raw.length()) {
                char n = raw.charAt(i + 1);
                if (c == '§' && "0123456789abcdefklor".indexOf(n) >= 0) {
                    i++;
                } else if (this.glyphs.containsKey((int) c)) {
                    result.add(new MsdfGlyph.ColoredGlyph(c, color));
                }
            } else if (this.glyphs.containsKey((int) c)) {
                result.add(new MsdfGlyph.ColoredGlyph(c, color));
            }
            i++;
        }
        return result;
    }

    public void drawText(MatrixStack matrixStack, Text text, float x, float y, float size) {
        drawText(matrixStack, text, x, y, size, 1.0f, 0.0f, 0.5f, 0.0f, 0, 0.0f);
    }

    public void drawText(MatrixStack matrixStack, Text text, float x, float y, float size, float alpha) {
        drawText(matrixStack, text, x, y, size, alpha, 0.0f, 0.5f, 0.0f, 0, 0.0f);
    }

    public void drawText(MatrixStack matrixStack, String text, float x, float y, float size, int color) {
        drawText(matrixStack, text, x, y, size, color, 0.0f);
    }

    public void drawText(MatrixStack matrixStack, String text, float x, float y, float size, int color, float thickness) {
        drawText(matrixStack, text, x, y, size, thickness, color, -1, -1.0f, 0.5f, 0.0f, 0, thickness);
    }

    public void drawGradientText(MatrixStack matrixStack, String text, float x, float y, float size, int color,
                                 float speed, float offset) {
        drawText(matrixStack, GradientUtil.waveGradient(text, color, speed, offset), x, y, size);
    }

    public void drawCenteredText(MatrixStack matrixStack, String text, float x, float y, float size, int color) {
        drawCenteredText(matrixStack, text, x, y, size, color, 0.0f);
    }

    public void drawCenteredText(MatrixStack matrixStack, String text, float x, float y, float size, int color,
                                 float thickness) {
        float textWidth = getWidth(text, size, thickness);
        drawText(matrixStack, text, x - (textWidth / 2.0f), y, size, color, thickness);
    }

    /**
     * Draws text clipped to {@code visibleWidth} with a per-fragment horizontal fade over
     * the last 5 pixels (ported from DeltaClient's Font.c overload).
     */
    public void drawClippedText(MatrixStack matrixStack, String text, float x, float y, float size, int color,
                                float visibleWidth) {
        drawClippedText(matrixStack, text, x, y, size, color, 0.0f, visibleWidth);
    }

    public void drawClippedText(MatrixStack matrixStack, String text, float x, float y, float size, int color,
                                float thickness, float visibleWidth) {
        if (text == null || text.isEmpty() || visibleWidth <= 0.0f) {
            return;
        }
        float fadeStart = x + Math.max(0.0f, visibleWidth - 5.0f);
        float fadeEnd = x + visibleWidth;
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        setupShader(0.0f, thickness, 0.5f, 0, fadeStart, fadeEnd);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float adjustedThickness = (thickness + (thickness * 0.5f)) * 0.5f * size;
        float baselineY = y + (this.metricsData.baselineHeight() * size);
        boolean hasGlyphs = drawGlyphs(matrix, builder, text, size, adjustedThickness, 0.0f, x, baselineY,
                0.0f, color, -1, -1.0f);
        if (hasGlyphs) {
            flush(builder);
        } else {
            builder.endNullable();
        }
    }

    public boolean drawGlyphs(Matrix4f matrix, VertexConsumer consumer, String text, float size, float thickness,
                              float spacing, float x, float y, float z, int color, int colorSecond, float offset) {
        int previousChar = -1;
        float totalWidth = getWidth(text, size);
        float time = (System.currentTimeMillis() % 3000.0f) / 3000.0f;
        boolean hasGlyphs = false;
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.charAt(i);
            if (codePoint == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            MsdfGlyph glyph = this.glyphs.get(codePoint);
            if (glyph != null) {
                hasGlyphs = true;
                float x2 = x + kerning(previousChar, codePoint, size);
                int currentColor = color;
                if (offset > 1.0f) {
                    currentColor = ColorUtil.makeGradient(color, colorSecond, x2 - x, totalWidth, time, offset);
                }
                x = x2 + glyph.render(matrix, consumer, size, x2, y, z, currentColor) + thickness + spacing;
                previousChar = codePoint;
            }
        }
        return hasGlyphs;
    }

    public boolean drawGlyphs(Matrix4f matrix, VertexConsumer consumer, List<MsdfGlyph.ColoredGlyph> coloredGlyphs,
                              float size, float alpha, float thickness, float spacing, float x, float y, float z) {
        int previousChar = -1;
        boolean started = false;
        boolean hasGlyphs = false;
        for (int i = 0; i < coloredGlyphs.size(); i++) {
            MsdfGlyph.ColoredGlyph glyphData = coloredGlyphs.get(i);
            int codePoint = glyphData.c();
            if (started || codePoint != 32) {
                started = true;
                int color = glyphData.color();
                MsdfGlyph glyph = this.glyphs.get(codePoint);
                if (glyph != null) {
                    hasGlyphs = true;
                    float x2 = x + kerning(previousChar, codePoint, size);
                    float advance = glyph.render(matrix, consumer, size, x2, y, z,
                            ColorUtil.applyAlphaToColor(color, alpha));
                    if (i < coloredGlyphs.size() - 1) {
                        advance += thickness + spacing;
                    }
                    x = x2 + advance;
                    previousChar = codePoint;
                }
            }
        }
        return hasGlyphs;
    }

    private float kerning(int previousChar, int currentChar, float size) {
        Map<Integer, Float> kerning = this.kernings.get(previousChar);
        if (kerning == null) {
            return 0.0f;
        }
        return kerning.getOrDefault(currentChar, 0.0f) * size;
    }

    public float getHeight(float size) {
        return size;
    }

    public float getWidth(Text text, float size) {
        return getWidth(text, size, 0.0f);
    }

    public float getWidth(Text text, float size, float thickness) {
        if (text == null) {
            return 0.0f;
        }
        List<MsdfGlyph.ColoredGlyph> coloredGlyphs = parseColoredGlyphs(text);
        return measureGlyphs(coloredGlyphs, size, thickness);
    }

    public float getWidth(String text, float size) {
        return getWidth(text, size, 0.0f);
    }

    public float getFirstInkWidth(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        MsdfGlyph glyph = this.glyphs.get((int) text.charAt(0));
        if (glyph == null) {
            return 0.0f;
        }
        return glyph.getInkWidth(size);
    }

    /**
     * Y coordinate that centers the first glyph's ink box on {@code centerY}
     * (ported from DeltaClient Font.a(String, float, float)).
     */
    public float centerInkY(String text, float size, float centerY) {
        if (text == null || text.isEmpty()) {
            return centerY - (getHeight(size) / 2.0f);
        }
        MsdfGlyph glyph = this.glyphs.get((int) text.charAt(0));
        if (glyph == null) {
            return centerY - (getHeight(size) / 2.0f);
        }
        float inkCenter = ((this.metricsData.baselineHeight() - glyph.getInkTop()) + (glyph.getInkHeight() / 2.0f)) * size;
        return centerY - inkCenter;
    }

    public float getWidth(String text, float size, float thickness) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        int previousChar = -1;
        float width = 0.0f;
        int renderedGlyphs = 0;
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.charAt(i);
            if (codePoint == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            MsdfGlyph glyph = this.glyphs.get(codePoint);
            if (glyph != null) {
                width = width + kerning(previousChar, codePoint, size) + glyph.getAdvance(size);
                renderedGlyphs++;
                previousChar = codePoint;
            }
        }
        return renderedGlyphs > 0 ? width + (renderedGlyphs * thickness) : width;
    }

    private float measureGlyphs(List<MsdfGlyph.ColoredGlyph> coloredGlyphs, float size, float thickness) {
        int previousChar = -1;
        float width = 0.0f;
        int renderedGlyphs = 0;
        for (MsdfGlyph.ColoredGlyph coloredGlyph : coloredGlyphs) {
            int codePoint = coloredGlyph.c();
            MsdfGlyph glyph = this.glyphs.get(codePoint);
            if (glyph != null) {
                width = width + kerning(previousChar, codePoint, size) + glyph.getAdvance(size);
                renderedGlyphs++;
                previousChar = codePoint;
            }
        }
        return renderedGlyphs > 1 ? width + ((renderedGlyphs - 1) * thickness) : width;
    }

    /**
     * Scrolling marquee text: scrolls when hovered or already offset, fades at the clip edge.
     * Returns the offset to persist between frames (ported from DeltaClient Font.a scroll overload).
     */
    public float drawScrollingText(MatrixStack matrixStack, String text, float x, float y, float size, int color,
                                    float maxWidth, boolean isHovered, float offset, float delta) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return 0.0f;
        }
        float textWidth = getWidth(text, size);
        float wrap = textWidth + 12.0f;
        if (isHovered || offset > 0.0f) {
            offset += delta * 1.5f;
            if (offset >= wrap) {
                offset = isHovered ? offset - wrap : 0.0f;
            }
        }
        if (textWidth <= maxWidth || offset == 0.0f) {
            drawClippedText(matrixStack, text, x, y, size, color, 0.0f, maxWidth);
            return offset;
        }
        ScissorUtil.push(matrixStack, x - 1.0f, y - (size * 0.5f), maxWidth + 2.0f, (size * 1.5f) + 0.5f);
        setupShader(0.0f, 0.0f, 0.5f, 0, (x + maxWidth) - 5.0f, x + maxWidth);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float baselineY = y + (this.metricsData.baselineHeight() * size);
        drawGlyphs(matrixStack.peek().getPositionMatrix(), builder, text, size, 0.0f, 0.0f, x - offset, baselineY,
                0.0f, color, -1, -1.0f);
        drawGlyphs(matrixStack.peek().getPositionMatrix(), builder, text, size, 0.0f, 0.0f, (x - offset) + wrap, baselineY,
                0.0f, color, -1, -1.0f);
        flush(builder);
        ScissorUtil.pop(matrixStack);
        return offset;
    }

    public int getTextureId() {
        return this.fontTexture.getGlId();
    }

    public Map<Integer, MsdfGlyph> getGlyphs() {
        return this.glyphs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Font font)) return false;
        return Objects.equals(this.fontName, font.fontName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.fontName);
    }
}
