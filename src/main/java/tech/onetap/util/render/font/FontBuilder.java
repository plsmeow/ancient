package tech.onetap.util.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import tech.onetap.util.IMinecraft;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MSDF font loader ported from DeltaClient (aethereal.render.FontBuilder); loads
 * mre:fonts/&lt;name&gt;.json + .png and builds glyph/kerning maps.
 */
public class FontBuilder implements IMinecraft {
    private final Gson gson = new Gson();
    private Identifier fontJsonId;
    private Identifier fontTextureId;
    private String fontName;

    public FontBuilder name(String fontName) {
        this.fontName = fontName;
        this.fontJsonId = Identifier.of("mre", "fonts/" + fontName + ".json");
        this.fontTextureId = Identifier.of("mre", "fonts/" + fontName + ".png");
        return this;
    }

    public Font build() {
        FontData data = readFontData();
        AbstractTexture texture = loadTexture();
        Map<Integer, MsdfGlyph> glyphs = buildGlyphs(data);
        Map<Integer, Map<Integer, Float>> kernings = buildKernings(data);
        return new Font(this.fontName, texture, data.atlas(), data.metrics(), glyphs, kernings);
    }

    private FontData readFontData() {
        FontData data = this.gson.fromJson(readResource(this.fontJsonId), FontData.class);
        if (data == null) {
            throw new RuntimeException("Failed to read font data file: " + this.fontJsonId
                    + ". Are you sure this is a valid JSON file? Check its syntax.");
        }
        return data;
    }

    private AbstractTexture loadTexture() {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(this.fontTextureId);
        RenderSystem.recordRenderCall(() -> texture.setFilter(true, false));
        return texture;
    }

    private Map<Integer, MsdfGlyph> buildGlyphs(FontData data) {
        float atlasWidth = data.atlas().width();
        float atlasHeight = data.atlas().height();
        return data.glyphs().stream().collect(Collectors.toMap(
                FontData.GlyphData::unicode,
                glyphData -> new MsdfGlyph(glyphData, atlasWidth, atlasHeight)));
    }

    private Map<Integer, Map<Integer, Float>> buildKernings(FontData data) {
        Map<Integer, Map<Integer, Float>> kernings = new HashMap<>();
        data.kernings().forEach(kerning -> {
            Map<Integer, Float> kerningMap = kernings.computeIfAbsent(kerning.leftChar(), k -> new HashMap<>());
            kerningMap.put(kerning.rightChar(), kerning.advance());
        });
        return kernings;
    }

    private String readResource(Identifier identifier) {
        try (InputStream inputStream = mc.getResourceManager().open(identifier);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read resource: " + identifier, ex);
        }
    }
}
