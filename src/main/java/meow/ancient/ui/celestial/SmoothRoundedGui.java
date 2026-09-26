package meow.ancient.ui.celestial;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import meow.ancient.util.render.RenderShaders;
import meow.ancient.util.render.shader.RectangleShader;

public final class SmoothRoundedGui {
    private static final float DEFAULT_NOISE_STRENGTH = 10.0f;

    private SmoothRoundedGui() {
    }

    public static void fill(
            DrawContext context, int x, int y, int width, int height,
            float radius, int color
    ) {
        fillFourCorners(context, x, y, width, height, radius,
                color, color, color, color);
    }

    public static void fillVertical(
            DrawContext context, int x, int y, int width, int height,
            float radius, int top, int bottom
    ) {
        fillFourCorners(context, x, y, width, height, radius,
                top, top, bottom, bottom);
    }

    public static void fillTopRounded(
            DrawContext context, int x, int y, int width, int height,
            float radius, int left, int right
    ) {
        if (width <= 0 || height <= 0) return;
        float smoothness = 0.8f;
        float horizontal = ((-smoothness) / 2.0f) + (smoothness * 2.0f);
        float vertical = (smoothness / 2.0f) + smoothness;
        float drawX = x - (horizontal / 2.0f);
        float drawY = y - (vertical / 2.0f);
        float drawWidth = width + horizontal;
        float drawHeight = height + vertical;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RectangleShader shader = RenderShaders.RECTANGLE;
        shader.bind();
        shader.setSize(width, height);
        // Radius uniform order: (top-right, bottom-right, bottom-left, top-left)
        // or symmetric top corners: top-right = radius, top-left = radius, bottoms = 0
        shader.setRadius(radius, 0.0f, 0.0f, radius);
        shader.setSmoothness(smoothness);
        shader.setOutlineWidth(0.0f);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, drawX, drawY, 0.0f).color(left);
        builder.vertex(matrix, drawX, drawY + drawHeight, 0.0f).color(left);
        builder.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).color(right);
        builder.vertex(matrix, drawX + drawWidth, drawY, 0.0f).color(right);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void fillFourCorners(
            DrawContext context, int x, int y, int width, int height,
            float radius, int topLeft, int topRight, int bottomRight, int bottomLeft
    ) {
        if (width <= 0 || height <= 0) return;
        float smoothness = 0.8f;
        float horizontal = ((-smoothness) / 2.0f) + (smoothness * 2.0f);
        float vertical = (smoothness / 2.0f) + smoothness;
        float drawX = x - (horizontal / 2.0f);
        float drawY = y - (vertical / 2.0f);
        float drawWidth = width + horizontal;
        float drawHeight = height + vertical;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RectangleShader shader = RenderShaders.RECTANGLE;
        shader.bind();
        shader.setSize(width, height);
        shader.setRadius(radius, radius, radius, radius);
        shader.setSmoothness(smoothness);
        shader.setOutlineWidth(0.0f);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, drawX, drawY, 0.0f).color(topLeft);
        builder.vertex(matrix, drawX, drawY + drawHeight, 0.0f).color(bottomLeft);
        builder.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).color(bottomRight);
        builder.vertex(matrix, drawX + drawWidth, drawY, 0.0f).color(topRight);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void fillNoise(
            DrawContext context, int x, int y, int width, int height,
            float radius, int color
    ) {
        fillNoise(context, x, y, width, height, radius, DEFAULT_NOISE_STRENGTH, color);
    }

    public static void fillNoise(
            DrawContext context, int x, int y, int width, int height,
            float radius, float noiseStrength, int color
    ) {
        fillNoiseFourCorners(context, x, y, width, height, radius, noiseStrength,
                color, color, color, color);
    }

    public static void fillNoiseFourCorners(
            DrawContext context, int x, int y, int width, int height,
            float radius, float noiseStrength,
            int topLeft, int topRight, int bottomRight, int bottomLeft
    ) {
        fillFourCorners(context, x, y, width, height, radius,
                topLeft, topRight, bottomRight, bottomLeft);
    }

    public static void fillGlow(
            DrawContext context, int x, int y, int width, int height,
            float radius, float glowRadius, int color
    ) {
        fillGlowFourCorners(context, x, y, width, height, radius, glowRadius,
                color, color, color, color);
    }

    public static void fillGlowFourCorners(
            DrawContext context, int x, int y, int width, int height,
            float radius, float glowRadius,
            int topLeft, int topRight, int bottomRight, int bottomLeft
    ) {
        if (width <= 0 || height <= 0) return;
        if (glowRadius > 0 && !meow.ancient.module.list.render.Optimization.isActive()) {
            LegacyHudShadow.Batch batch = LegacyHudShadow.batch();
            batch.add(x, y, width, height, (int) glowRadius, topLeft);
            batch.submit(context);
        }
        fillFourCorners(context, x, y, width, height, radius, topLeft, topRight, bottomRight, bottomLeft);
    }
}
