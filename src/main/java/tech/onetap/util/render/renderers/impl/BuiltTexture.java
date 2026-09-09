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
import tech.onetap.util.render.shader.TextureShader;

/**
 * Textured rounded rectangle on the ported DeltaClient texture shader
 * (mre:core/rect/texture_rect); identical geometry/UV layout to the previous engine.
 */
public record BuiltTexture(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float u, float v,
        float texWidth, float texHeight,
        int textureId
    ) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, this.textureId);

        float width = this.size.width();
        float height = this.size.height();

        TextureShader shader = RenderShaders.TEXTURE;
        shader.bind();
        shader.setSize(width, height);
        shader.setRadius(this.radius.radius1(), this.radius.radius2(),
                this.radius.radius3(), this.radius.radius4());
        shader.setSmoothness(this.smoothness);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, x, y, z).texture(this.u, this.v).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).texture(this.u, this.v + this.texHeight).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).texture(this.u + this.texWidth, this.v + this.texHeight).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).texture(this.u + this.texWidth, this.v).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

}
