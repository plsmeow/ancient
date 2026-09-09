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
import tech.onetap.util.render.shader.BorderShader;

/**
 * Rounded border on the retained border shader (mre:core/border) — same math as the
 * previous implementation: thickness + internal/external smoothness, no geometric padding.
 */
public record BuiltBorder(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float thickness,
        float internalSmoothness, float externalSmoothness
    ) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        float width = this.size.width();
        float height = this.size.height();

        BorderShader shader = RenderShaders.BORDER;
        shader.bind();
        shader.setSize(width, height);
        shader.setRadius(this.radius.radius1(), this.radius.radius2(),
                this.radius.radius3(), this.radius.radius4());
        shader.setThickness(this.thickness);
        shader.setSmoothness(this.internalSmoothness, this.externalSmoothness);

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
