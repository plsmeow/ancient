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
import tech.onetap.util.render.shader.RectangleShader;

/**
 * Filled rounded rectangle on the ported DeltaClient rect shader (mre:core/rect/rect).
 * Visual metrics identical to the previous implementation: no geometric padding, smoothness
 * passed straight through, 1px AA padding handled inside the shader's SDF.
 */
public record BuiltRectangle(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness
    ) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        float width = this.size.width();
        float height = this.size.height();

        RectangleShader shader = RenderShaders.RECTANGLE;
        shader.bind();
        shader.setSize(width, height);
        shader.setRadius(radius.radius1(), radius.radius2(), radius.radius3(), radius.radius4());
        shader.setSmoothness(this.smoothness);
        shader.setOutlineWidth(0.0f);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, x, y, z).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

}
