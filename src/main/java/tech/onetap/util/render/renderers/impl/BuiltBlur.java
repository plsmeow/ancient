package tech.onetap.util.render.renderers.impl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import tech.onetap.module.list.render.Optimization;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.providers.ResourceProvider;
import tech.onetap.util.render.renderers.IRenderer;

public record BuiltBlur(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float blurRadius
    ) implements IRenderer {

	private static final ShaderProgramKey BLUR_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("blur"),
		VertexFormats.POSITION_COLOR, Defines.EMPTY);
    private static final Supplier<SimpleFramebuffer> TEMP_FBO_SUPPLIER = Suppliers
        .memoize(() -> new SimpleFramebuffer(1920, 1024, false));
    private static final Framebuffer MAIN_FBO = MinecraftClient.getInstance().getFramebuffer();

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        if (Optimization.isActive()) {
            new BuiltRectangle(size, radius, color, smoothness).render(matrix, x, y, z);
            return;
        }

        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        SimpleFramebuffer fbo = TEMP_FBO_SUPPLIER.get();

        if (fbo.textureWidth != main.textureWidth || fbo.textureHeight != main.textureHeight) {
            fbo.resize(main.textureWidth, main.textureHeight);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        fbo.beginWrite(false);
        main.draw(fbo.textureWidth, fbo.textureHeight);

        main.beginWrite(false);

        RenderSystem.setShaderTexture(0, fbo.getColorAttachment());

        float width = size.width();
        float height = size.height();

        ShaderProgram shader = RenderSystem.setShader(BLUR_SHADER_KEY);
        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(radius.radius1(), radius.radius2(),
                radius.radius3(), radius.radius4());
        shader.getUniform("Smoothness").set(smoothness);
        shader.getUniform("BlurRadius").set(blurRadius);

        BufferBuilder builder = Tessellator.getInstance()
                .begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        builder.vertex(matrix, x, y, z).color(color.color1());
        builder.vertex(matrix, x, y + height, z).color(color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(color.color3());
        builder.vertex(matrix, x + width, y, z).color(color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
