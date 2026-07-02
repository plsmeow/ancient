package tech.onetap.util.render.renderers.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.providers.ResourceProvider;
import tech.onetap.util.render.renderers.IRenderer;

public record BuiltGlow(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float glowRadius,
        float softness,
        float intensity,
        boolean additive
) implements IRenderer {

    private static final ShaderProgramKey GLOW_SHADER_KEY = new ShaderProgramKey(
            ResourceProvider.getShaderIdentifier("glow"),
            VertexFormats.POSITION_COLOR,
            Defines.EMPTY
    );

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();


            RenderSystem.defaultBlendFunc();


        float w = size.width();
        float h = size.height();

        ShaderProgram shader = RenderSystem.setShader(GLOW_SHADER_KEY);
        shader.getUniform("Size").set(w, h);
        shader.getUniform("Radius").set(radius.radius1(), radius.radius2(), radius.radius3(), radius.radius4());
        shader.getUniform("GlowRadius").set(glowRadius);
        shader.getUniform("Softness").set(softness);
        shader.getUniform("Intensity").set(intensity);

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