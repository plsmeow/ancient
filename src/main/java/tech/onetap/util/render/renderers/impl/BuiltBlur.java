package tech.onetap.util.render.renderers.impl;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import tech.onetap.module.list.render.Optimization;
import tech.onetap.util.render.RenderShaders;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.renderers.IRenderer;
import tech.onetap.util.render.shader.BlurShader;

import java.util.List;

/**
 * Blurred rounded rectangle sampling the ported DeltaClient kawase chain
 * (mre:core/rect/blurred_rect over BlurShader framebuffers).
 * <p>
 * Visual style parity with the previous engine: the quad color tints the blurred
 * background (uMix=0, vertex-color multiply in the shader), the blur "radius"
 * parameter maps to the chain's inherent blur strength, and the alpha component of the
 * tint blends the blurred content back over the sharp background. When the chain is not
 * available (e.g. Optimization enabled) falls back to a flat rectangle like before.
 */
public record BuiltBlur(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float blurRadius
    ) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        List<SimpleFramebuffer> framebuffers = RenderShaders.BLUR.getFramebuffers();
        if (Optimization.isActive() || framebuffers.isEmpty()) {
            new BuiltRectangle(size, radius, color, smoothness).render(matrix, x, y, z);
            return;
        }

        SimpleFramebuffer framebuffer = framebuffers.getFirst();
        float width = size.width();
        float height = size.height();

        float scale = framebuffer.textureWidth / (float) MinecraftClient.getInstance().getWindow().getScaledWidth();
        float uLeft = (x * scale) / framebuffer.textureWidth;
        float uRight = ((x + width) * scale) / framebuffer.textureWidth;
        float vTop = 1.0f - ((y * scale) / framebuffer.textureHeight);
        float vBottom = 1.0f - (((y + height) * scale) / framebuffer.textureHeight);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());

        BlurShader shader = RenderShaders.BLUR;
        shader.bind();
        shader.setSize(width, height);
        shader.setRadius(radius.radius1(), radius.radius2(), radius.radius3(), radius.radius4());
        shader.setSmoothness(this.smoothness);
        shader.setMix(0.0f);
        shader.setAlpha(1.0f);
        shader.setGlowRadius(0.0f);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, x, y, z).texture(uLeft, vTop).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).texture(uLeft, vBottom).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).texture(uRight, vBottom).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).texture(uRight, vTop).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
