package tech.onetap.util.render.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import tech.onetap.util.render.providers.ResourceProvider;

import java.util.List;

@UtilityClass
public class MsdfRenderer {

    public final ShaderProgramKey MSDF_FONT_SHADER_KEY = new ShaderProgramKey(
            ResourceProvider.getShaderIdentifier("msdf_font"),
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


        float thickness = 0.05f;
        float smoothness = 0.5f;
        float spacing = 0;
//        NameProtect nameProtectModule = Rockstar.getInstance().getModuleManager().getModule(NameProtect.class);
//
//        if (nameProtectModule.isEnabled()) {
//            text = nameProtectModule.patchName(text);
//        }

//        if (Batching.getActive() != null) {
//            // Для батчинга пока оставляем стандартную отрисовку
//            // TODO: Реализовать fadeout для батчинга
//            font.applyGlyphs(
//                    matrix,
//                    Batching.getActive().getBuilder(),
//                    text,
//                    size,
//                    thickness * 0.5f * size,
//                    spacing,
//                    // Так называемый рокстарвский MAGIC VALUE
//                    x - 0.75F, // небольшой оффсет чтобы мы всегда были внутри краев
//                    y + (size * 0.7F),
//                    z,
//                    color
//            );
//            return;
//        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, font.getTextureId());

        ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
        shader.getUniform("Range").set(font.getAtlas().range());
        shader.getUniform("Thickness").set(thickness);
        shader.getUniform("Smoothness").set(0.5f);

        shader.getUniform("EnableFadeout").set(enableFadeout ? 1 : 0);
        shader.getUniform("FadeoutStart").set(fadeoutStart);
        shader.getUniform("FadeoutEnd").set(fadeoutEnd);
        shader.getUniform("MaxWidth").set(maxWidth);
        shader.getUniform("TextPosX").set(x);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        font.applyGlyphs(
                matrix,
                builder,
                text,
                size,
                thickness * 0.5f * size,
                spacing,
                // Так называемый рокстарвский MAGIC VALUE
                x - 0.75F, // небольшой оффсет чтобы мы всегда были внутри краев
                y + (size * 0.7F),
                z,
                color
        );

        BuiltBuffer builtBuffer = builder.endNullable();
        if (builtBuffer != null)
            BufferRenderer.drawWithGlobalProgram(builtBuffer);

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
        float thickness = 0.05f;
        float smoothness = 0.5f;
        float spacing = 0;
        List<FormattedTextProcessor.TextSegment> segments = FormattedTextProcessor.processText(text, -1);

        float currentX = x;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, font.getTextureId());

        ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
        shader.getUniform("Range").set(font.getAtlas().range());
        shader.getUniform("Thickness").set(thickness);
        shader.getUniform("Smoothness").set(0.5f);

        shader.getUniform("EnableFadeout").set(enableFadeout ? 1 : 0);
        shader.getUniform("FadeoutStart").set(fadeoutStart);
        shader.getUniform("FadeoutEnd").set(fadeoutEnd);
        shader.getUniform("MaxWidth").set(maxWidth);
        shader.getUniform("TextPosX").set(x);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (FormattedTextProcessor.TextSegment segment : segments) {

        font.applyGlyphs(
                matrix,
                builder,
                segment.text(),
                size,
                thickness * 0.5f * size,
                spacing - 0.3F,
                // Так называемый рокстарвский MAGIC VALUE
                currentX - 0.75F, // небольшой оффсет чтобы мы всегда были внутри краев
                y + (size * 0.7F),
                z,
                segment.color()
        );

            currentX += font.getWidth(segment.text(), size);
        }

        BuiltBuffer builtBuffer = builder.endNullable();
        if (builtBuffer != null)
            BufferRenderer.drawWithGlobalProgram(builtBuffer);

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
            float maxWidth,
            int alpha
    ) {
        float thickness = 0.05f;
        float smoothness = 0.5f;
        float spacing = 0;
        List<FormattedTextProcessor.TextSegment> segments = FormattedTextProcessor.processText(text, -1);

        float currentX = x;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, font.getTextureId());

        ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
        shader.getUniform("Range").set(font.getAtlas().range());
        shader.getUniform("Thickness").set(thickness);
        shader.getUniform("Smoothness").set(0.5f);

        shader.getUniform("EnableFadeout").set(enableFadeout ? 1 : 0);
        shader.getUniform("FadeoutStart").set(fadeoutStart);
        shader.getUniform("FadeoutEnd").set(fadeoutEnd);
        shader.getUniform("MaxWidth").set(maxWidth);
        shader.getUniform("TextPosX").set(x);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (FormattedTextProcessor.TextSegment segment : segments) {
            int color = segment.color();
            if (alpha != 255) {
                color = (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
            }
            font.applyGlyphs(
                    matrix,
                    builder,
                    segment.text(),
                    size,
                    thickness * 0.5f * size,
                    spacing - 0.3F,
                    // Так называемый рокстарвский MAGIC VALUE
                    currentX - 0.75F, // небольшой оффсет чтобы мы всегда были внутри краев
                    y + (size * 0.7F),
                    z,
                    color
            );

            currentX += font.getWidth(segment.text(), size);
        }

        BuiltBuffer builtBuffer = builder.endNullable();
        if (builtBuffer != null)
            BufferRenderer.drawWithGlobalProgram(builtBuffer);

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
            float fadeoutEnd
    ) {
        float maxWidth = font.getWidth(text, size) * 2.0F;
        renderText(font, text, size, matrix, x, y, z, enableFadeout, fadeoutStart, fadeoutEnd, maxWidth);

    }
}