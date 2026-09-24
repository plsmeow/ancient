package meow.ancient.util.render.renderers.impl;

import net.minecraft.text.Text;
import org.joml.Matrix4f;
import meow.ancient.util.render.font.Font;
import meow.ancient.util.render.msdf.MsdfFont;
import meow.ancient.util.render.renderers.IRenderer;

/**
 * Styled (§-code / sibling color) text on the ported DeltaClient text program via
 * {@link Font#parseColoredGlyphs}; visual behavior matches the previous implementation
 * (per-glyph colors with applied alpha, baseline positioning, outline support).
 */
public record BuiltMutableText(
        MsdfFont font,
        Text text,
        float size,
        float thickness,
        int color,
        float smoothness,
        float spacing,
        int outlineColor,
        float outlineThickness,
        int alpha
) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        Font engine = font.getEngineFont();
        engine.drawText(matrix, text, x, y, size, this.alpha / 255.0f,
                thickness, smoothness, spacing, outlineColor, outlineThickness);
    }
}
