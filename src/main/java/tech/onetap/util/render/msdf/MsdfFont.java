package tech.onetap.util.render.msdf;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import tech.onetap.util.parse.ParseTextUtil;
import tech.onetap.util.render.font.Font;
import tech.onetap.util.render.font.FontData;
import tech.onetap.util.render.font.MsdfGlyph;

import java.util.List;

/**
 * Adapter keeping the previous font facade API (texture id, glyph application, width
 * measurement, ColoredGlyph parsing) while delegating to the ported DeltaClient
 * {@link Font} engine underneath. All metrics are identical, so layout code is unaffected.
 */
public final class MsdfFont {

    private final Font engine;

    private MsdfFont(Font engine) {
        this.engine = engine;
    }

    public Font getEngineFont() {
        return this.engine;
    }

    public int getTextureId() {
        return this.engine.getTextureId();
    }

    public void applyGlyphs(Matrix4f matrix, VertexConsumer consumer, String text, float size, float thickness, float spacing, float x, float y, float z, int color) {
        this.engine.drawGlyphs(matrix, consumer, text, size, thickness, spacing, x, y, z, color, -1, -1.0f);
    }

    public void applyGlyphs(Matrix4f matrix, VertexConsumer consumer,
                            List<ColoredGlyph> glyphs, float size, float thickness, float spacing,
                            float x, float y, float z) {
        this.engine.drawGlyphs(matrix, consumer, toEngineGlyphs(glyphs), size, 1.0f, thickness, spacing, x, y, z);
    }

    public float getWidth(Text text, float size) {
        return this.engine.getWidth(text, size);
    }

    public float getWidth(String text, float size) {
        return this.engine.getWidth(text, size);
    }

    public record ColoredGlyph(char c, int color) {}

    public String getName() {
        return this.engine.getName();
    }

    public FontData.AtlasData getAtlas() {
        return this.engine.getAtlas();
    }

    public FontData.MetricsData getMetrics() {
        return this.engine.getMetrics();
    }

    private static List<MsdfGlyph.ColoredGlyph> toEngineGlyphs(List<ColoredGlyph> glyphs) {
        return glyphs.stream().map(g -> new MsdfGlyph.ColoredGlyph(g.c(), g.color())).toList();
    }

    public static MsdfFont.Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name = "?";

        private Builder() {}

        public MsdfFont.Builder name(String name) {
            this.name = name;
            return this;
        }

        public MsdfFont.Builder data(String dataFileName) {
            return this;
        }

        public MsdfFont.Builder atlas(String atlasFileName) {
            this.name = atlasFileName;
            return this;
        }

        public MsdfFont build() {
            return new MsdfFont(Font.builder().name(this.name).build());
        }
    }

}
