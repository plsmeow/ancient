package meow.ancient.util.render.renderers.impl;

import org.joml.Matrix4f;
import meow.ancient.util.render.font.Font;
import meow.ancient.util.render.msdf.MsdfFont;
import meow.ancient.util.render.renderers.IRenderer;

/**
 * MSDF text on the ported DeltaClient text program (mre:core/text/text) via
 * {@link MsdfFont}, which adapts the current font facade to the new engine.
 */
public record BuiltText(
        MsdfFont font,
        String text,
        float size,
        float thickness,
        int color,
        float smoothness,
        float spacing,
        int outlineColor,
        float outlineThickness
) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        if (text == null || text.isEmpty()) return;

        Font engine = font.getEngineFont();
        engine.drawText(matrix, text, x, y, size, thickness, color, -1, -1.0f,
                smoothness, spacing, outlineColor, outlineThickness);
    }
}
