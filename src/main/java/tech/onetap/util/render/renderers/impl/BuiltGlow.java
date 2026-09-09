package tech.onetap.util.render.renderers.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import tech.onetap.util.render.RenderShaders;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.renderers.IRenderer;
import tech.onetap.util.render.shader.GlowShader;

/**
 * Orbital glow on the retained glow shader (mre:core/glow) — same uniforms as the
 * previous implementation so the Celestial HUD glow style is unchanged.
 */
public record BuiltGlow(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float glowRadius,
        float softness,
        float intensity,
        boolean additive
) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.defaultBlendFunc();

        float w = size.width();
        float h = size.height();

        GlowShader shader = RenderShaders.GLOW;
        shader.bind();
        shader.setSize(w, h);
        shader.setRadius(radius.radius1(), radius.radius2(), radius.radius3(), radius.radius4());
        shader.setGlowRadius(glowRadius);
        shader.setSoftness(softness);
        shader.setIntensity(intensity);

        BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bb.vertex(matrix, x, y, z).color(color.color1());
        bb.vertex(matrix, x, y + h, z).color(color.color2());
        bb.vertex(matrix, x + w, y + h, z).color(color.color3());
        bb.vertex(matrix, x + w, y, z).color(color.color4());

        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
