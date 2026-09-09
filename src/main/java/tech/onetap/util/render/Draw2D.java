package tech.onetap.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.shader.BlurShader;
import tech.onetap.util.render.shader.GradientShader;
import tech.onetap.util.render.shader.RectangleShader;
import tech.onetap.util.render.shader.TextureShader;

import java.util.List;

/**
 * High-level 2D renderer ported from DeltaClient (aethereal.render.Draw2DProcessor)
 * on top of the ported shader wrappers. This is the drawing API the Delta-styled
 * ClickGUI and HUD widgets are written against: rounded rects, outlines, textures,
 * gradients, kawase blur and glow, all with the SDF AA padding of the original.
 */
public class Draw2D implements IMinecraft {
    private final RectangleShader rectangleShader = RenderShaders.RECTANGLE;
    private final TextureShader textureShader = RenderShaders.TEXTURE;
    private final GradientShader gradientShader = RenderShaders.GRADIENT;
    private final BlurShader blurShader = RenderShaders.BLUR;

    public RectangleShader getRectangleShader() {
        return this.rectangleShader;
    }

    public TextureShader getTextureShader() {
        return this.textureShader;
    }

    public GradientShader getGradientShader() {
        return this.gradientShader;
    }

    public BlurShader getBlurShader() {
        return this.blurShader;
    }

    /**
     * Filled rounded rectangle (radius per corner Vector4f).
     */
    public void drawRounded(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int color) {
        float[] padding = padding(0.8f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = x - (padding[0] / 2.0f);
        float drawY = y - (padding[1] / 2.0f);
        float drawWidth = width + padding[0];
        float drawHeight = height + padding[1];
        beginDraw();
        this.rectangleShader.bind();
        this.rectangleShader.setSize(width, height);
        this.rectangleShader.setRadius(radius);
        this.rectangleShader.setSmoothness(0.8f);
        this.rectangleShader.setOutlineWidth(0.0f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vertexQuad(buffer, matrix, drawX, drawY, drawWidth, drawHeight, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endDraw();
    }

    public void drawRounded(MatrixStack matrices, float x, float y, float width, float height, float radius, int color) {
        drawRounded(matrices, x, y, width, height, uniformRadius(radius), color);
    }

    /**
     * Rounded outline drawn symmetrically around the box edge.
     */
    public void drawOutline(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, float outlineWidth, int color) {
        float[] padding = padding(0.8f);
        float halfOutlineWidth = outlineWidth * 0.5f;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = (x - halfOutlineWidth) - (padding[0] / 2.0f);
        float drawY = (y - halfOutlineWidth) - (padding[1] / 2.0f);
        float drawWidth = width + outlineWidth + padding[0];
        float drawHeight = height + outlineWidth + padding[1];
        beginDraw();
        this.rectangleShader.bind();
        this.rectangleShader.setSize(width + outlineWidth, height + outlineWidth);
        this.rectangleShader.setRadius(new Vector4f(radius.x + halfOutlineWidth, radius.y + halfOutlineWidth,
                radius.z + halfOutlineWidth, radius.w + halfOutlineWidth));
        this.rectangleShader.setSmoothness(0.8f);
        this.rectangleShader.setOutlineWidth(outlineWidth);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vertexQuad(buffer, matrix, drawX, drawY, drawWidth, drawHeight, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endDraw();
    }

    public void drawOutline(MatrixStack matrices, float x, float y, float width, float height, float radius, float outlineWidth, int color) {
        drawOutline(matrices, x, y, width, height, uniformRadius(radius), outlineWidth, color);
    }

    /**
     * Textured rounded rectangle by Identifier.
     */
    public void drawTexture(MatrixStack matrices, Identifier texture, float x, float y, float width, float height, float radius, int color) {
        drawTexture(matrices, x, y, width, height, radius, color, 0.0f, 0.0f, 1.0f, 1.0f,
                mc.getTextureManager().getTexture(texture).getGlId());
    }

    public void drawTexture(MatrixStack matrices, float x, float y, float width, float height, float radius, int color,
                            float u, float v, float textureWidth, float textureHeight, int textureId) {
        drawTexture(matrices, x, y, width, height, uniformRadius(radius), color, u, v, textureWidth, textureHeight, textureId);
    }

    public void drawTexture(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int color,
                            float u, float v, float textureWidth, float textureHeight, int textureId) {
        float[] padding = padding(0.8f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = x - (padding[0] / 2.0f);
        float drawY = y - (padding[1] / 2.0f);
        float drawWidth = width + padding[0];
        float drawHeight = height + padding[1];
        beginDraw();
        RenderSystem.setShaderTexture(0, textureId);
        this.textureShader.bind();
        this.textureShader.setSize(width, height);
        this.textureShader.setRadius(radius);
        this.textureShader.setSmoothness(0.8f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        vertexTexturedQuad(buffer, matrix, drawX, drawY, drawWidth, drawHeight, u, v, textureWidth, textureHeight, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endDraw();
    }

    /**
     * Player skin head (face + hat layers) into a rounded rect.
     */
    public void drawSkinHead(MatrixStack matrices, Identifier skin, LivingEntity target, float x, float y, float width, float height, float radius, float alpha) {
        drawSkinHead(matrices, skin, x, y, width, height, radius, alpha);
    }

    /**
     * Player skin head (face + hat layers, 8x8/64 regions) into a rounded rect.
     */
    public void drawSkinHead(MatrixStack matrices, Identifier skin, float x, float y, float width, float height, float radius, float alpha) {
        if (skin == null) {
            return;
        }
        int color = ColorUtil.convertToARGB(255, 255, 255, (int) (alpha * 255.0f));
        int textureId = mc.getTextureManager().getTexture(skin).getGlId();
        drawTexture(matrices, x, y, width, height, radius, color, 0.125f, 0.125f, 0.125f, 0.125f, textureId);
        drawTexture(matrices, x, y, width, height, radius, color, 0.625f, 0.125f, 0.125f, 0.125f, textureId);
    }

    /**
     * Four-corner gradient rounded rectangle.
     */
    public void drawGradient(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius,
                             int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        float[] padding = padding(1.0f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float[] normalizedTopLeft = ColorUtil.normalize(topLeftColor);
        float[] normalizedBottomLeft = ColorUtil.normalize(bottomLeftColor);
        float[] normalizedBottomRight = ColorUtil.normalize(bottomRightColor);
        float[] normalizedTopRight = ColorUtil.normalize(topRightColor);
        float drawX = x - (padding[0] / 2.0f);
        float drawY = y - (padding[1] / 2.0f);
        float drawWidth = width + padding[0];
        float drawHeight = height + padding[1];
        beginDraw();
        this.gradientShader.bind();
        this.gradientShader.setSize(width, height);
        this.gradientShader.setRadius(radius);
        this.gradientShader.setSmoothness(1.0f);
        this.gradientShader.setTopLeft(normalizedTopLeft[0], normalizedTopLeft[1], normalizedTopLeft[2], normalizedTopLeft[3]);
        this.gradientShader.setBottomLeft(normalizedBottomLeft[0], normalizedBottomLeft[1], normalizedBottomLeft[2], normalizedBottomLeft[3]);
        this.gradientShader.setBottomRight(normalizedBottomRight[0], normalizedBottomRight[1], normalizedBottomRight[2], normalizedBottomRight[3]);
        this.gradientShader.setTopRight(normalizedTopRight[0], normalizedTopRight[1], normalizedTopRight[2], normalizedTopRight[3]);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, drawX, drawY, 0.0f).color(topLeftColor);
        buffer.vertex(matrix, drawX, drawY + drawHeight, 0.0f).color(bottomLeftColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).color(bottomRightColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY, 0.0f).color(topRightColor);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endDraw();
    }

    public void drawGradient(MatrixStack matrices, float x, float y, float width, float height, float radius,
                             int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        drawGradient(matrices, x, y, width, height, uniformRadius(radius), topLeftColor, topRightColor, bottomLeftColor, bottomRightColor);
    }

    /**
     * Blurred rounded rectangle sampling the kawase chain, tinted by the quad color.
     */
    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height, float radius, int color) {
        drawBlur(matrices, x, y, width, height, radius, color, 0.8f);
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float mix) {
        drawBlur(matrices, x, y, width, height, uniformRadius(radius), color, color, color, color, mix);
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius,
                         int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor, float mix) {
        if (tech.onetap.module.list.render.Optimization.isActive()) {
            drawRounded(matrices, x, y, width, height, radius, topLeftColor);
            return;
        }
        List<SimpleFramebuffer> framebuffers = this.blurShader.getFramebuffers();
        if (framebuffers.isEmpty()) {
            return;
        }
        Framebuffer framebuffer = framebuffers.getFirst();
        float uLeft = 0.0f;
        float uRight = 0.0f;
        float vTop = 0.0f;
        float vBottom = 0.0f;
        if (mix != 1.0f) {
            float scale = framebuffer.textureWidth / mc.getWindow().getScaledWidth();
            uLeft = (x * scale) / framebuffer.textureWidth;
            uRight = ((x + width) * scale) / framebuffer.textureWidth;
            vTop = 1.0f - ((y * scale) / framebuffer.textureHeight);
            vBottom = 1.0f - (((y + height) * scale) / framebuffer.textureHeight);
        }
        float[] normalizedTopLeft = ColorUtil.normalize(topLeftColor);
        float[] normalizedBottomLeft = ColorUtil.normalize(bottomLeftColor);
        float[] normalizedBottomRight = ColorUtil.normalize(bottomRightColor);
        float[] normalizedTopRight = ColorUtil.normalize(topRightColor);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = x - (1.2f * 0.5f);
        float drawY = y - (1.2f * 0.5f);
        float drawWidth = width + 1.2f;
        float drawHeight = height + 1.2f;
        beginDraw();
        RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());
        this.blurShader.bind();
        this.blurShader.setSize(width, height);
        this.blurShader.setRadius(radius);
        this.blurShader.setSmoothness(0.8f);
        this.blurShader.setMix(mix);
        this.blurShader.setAlpha((normalizedTopLeft[3] + normalizedBottomLeft[3] + normalizedBottomRight[3] + normalizedTopRight[3]) * 0.25f);
        this.blurShader.setGlowRadius(0.0f);
        this.blurShader.setCornerColors(normalizedTopLeft, normalizedBottomLeft, normalizedBottomRight, normalizedTopRight);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, drawX, drawY, 0.0f).texture(uLeft, vTop).color(topLeftColor);
        buffer.vertex(matrix, drawX, drawY + drawHeight, 0.0f).texture(uLeft, vBottom).color(bottomLeftColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).texture(uRight, vBottom).color(bottomRightColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY, 0.0f).texture(uRight, vTop).color(topRightColor);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endDraw();
    }

    /**
     * Blur + glow halo behind a rounded rect, then a solid fill on top
     * (DeltaClient Draw2DProcessor.a glow overload).
     */
    public void drawGlowBlur(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius,
                             int color, float alpha, int glowColor, float glowRadius) {
        if (tech.onetap.module.list.render.Optimization.isActive()) {
            drawRounded(matrices, x, y, width, height, radius, color);
            return;
        }
        List<SimpleFramebuffer> framebuffers = this.blurShader.getFramebuffers();
        if (framebuffers.isEmpty()) {
            return;
        }
        float clampedGlowRadius = Math.max(glowRadius, 0.0f);
        float padding = 0.8f * 1.5f;
        Framebuffer framebuffer = framebuffers.getFirst();
        float scale = framebuffer.textureWidth / mc.getWindow().getScaledWidth();
        float uLeft = (x * scale) / framebuffer.textureWidth;
        float uRight = ((x + width) * scale) / framebuffer.textureWidth;
        float vTop = 1.0f - ((y * scale) / framebuffer.textureHeight);
        float vBottom = 1.0f - (((y + height) * scale) / framebuffer.textureHeight);
        float[] normalizedGlowColor = ColorUtil.normalize(glowColor);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        drawGlowPass(matrix, x - (padding * 0.5f), y - (padding * 0.5f), width + padding, height + padding, width, height,
                radius, alpha, 0.8f, 0.0f, null, uLeft, vTop, uRight, vBottom, framebuffer);
        float innerWidth = width - ((-1.1f) * 2.0f);
        float innerHeight = height - ((-1.1f) * 2.0f);
        Vector4f innerRadius = new Vector4f(Math.max(0.0f, radius.x - (-1.1f)), Math.max(0.0f, radius.y - (-1.1f)),
                Math.max(0.0f, radius.z - (-1.1f)), Math.max(0.0f, radius.w - (-1.1f)));
        drawGlowPass(matrix, (x - 1.1f) - clampedGlowRadius, (y - 1.1f) - clampedGlowRadius,
                innerWidth + (clampedGlowRadius * 2.0f), innerHeight + (clampedGlowRadius * 2.0f), innerWidth, innerHeight,
                innerRadius, alpha, 0.8f, clampedGlowRadius, normalizedGlowColor, uLeft, vTop, uRight, vBottom, framebuffer);
        drawRounded(matrices, x, y, width, height, radius, color);
    }

    public void drawGlowBlur(MatrixStack matrices, float x, float y, float width, float height, float radius,
                             int color, float alpha, int glowColor, float glowRadius) {
        drawGlowBlur(matrices, x, y, width, height, uniformRadius(radius), color, alpha, glowColor, glowRadius);
    }

    /**
     * Blur halo + solid fill without the inner glow pass.
     */
    public void drawShadowBlur(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float alpha) {
        if (tech.onetap.module.list.render.Optimization.isActive()) {
            drawRounded(matrices, x, y, width, height, radius, color);
            return;
        }
        List<SimpleFramebuffer> framebuffers = this.blurShader.getFramebuffers();
        if (framebuffers.isEmpty()) {
            return;
        }
        float padding = 0.8f * 1.5f;
        Framebuffer framebuffer = framebuffers.getFirst();
        float scale = framebuffer.textureWidth / mc.getWindow().getScaledWidth();
        float uLeft = (x * scale) / framebuffer.textureWidth;
        float uRight = ((x + width) * scale) / framebuffer.textureWidth;
        float vTop = 1.0f - ((y * scale) / framebuffer.textureHeight);
        float vBottom = 1.0f - (((y + height) * scale) / framebuffer.textureHeight);
        drawGlowPass(matrices.peek().getPositionMatrix(), x - (padding * 0.5f), y - (padding * 0.5f), width + padding,
                height + padding, width, height, uniformRadius(radius), alpha, 0.8f, 0.0f, null, uLeft, vTop, uRight, vBottom, framebuffer);
        drawRounded(matrices, x, y, width, height, radius, color);
    }

    private void drawGlowPass(Matrix4f matrix, float drawX, float drawY, float drawWidth, float drawHeight,
                              float width, float height, Vector4f radius, float alpha, float smoothness, float glowRadius,
                              float[] glowColor, float uLeft, float vTop, float uRight, float vBottom, Framebuffer framebuffer) {
        beginDraw();
        RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());
        this.blurShader.bind();
        this.blurShader.setSize(width, height);
        this.blurShader.setRadius(radius);
        this.blurShader.setAlpha(alpha);
        this.blurShader.setGlowRadius(glowRadius);
        if (glowColor != null) {
            this.blurShader.setGlowColor(glowColor[0], glowColor[1], glowColor[2], glowColor[3]);
        }
        this.blurShader.setSmoothness(smoothness);
        this.blurShader.setMix(0.0f);
        this.blurShader.setCornerColors(new float[]{1, 1, 1, 1}, new float[]{1, 1, 1, 1},
                new float[]{1, 1, 1, 1}, new float[]{1, 1, 1, 1});
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, drawX, drawY, 0.0f).texture(uLeft, vTop).color(-1);
        buffer.vertex(matrix, drawX, drawY + drawHeight, 0.0f).texture(uLeft, vBottom).color(-1);
        buffer.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).texture(uRight, vBottom).color(-1);
        buffer.vertex(matrix, drawX + drawWidth, drawY, 0.0f).texture(uRight, vTop).color(-1);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endDraw();
    }

    /**
     * Offline-player skin Identifier by nickname (DeltaClient TextureShader.b).
     */
    public Identifier getSkinIdentifier(String nickname) {
        java.util.UUID uuid;
        if (nickname == null || nickname.isEmpty()) {
            uuid = new java.util.UUID(0L, 0L);
        } else {
            uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return DefaultSkinHelper.getSkinTextures(uuid).texture();
    }

    private void beginDraw() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
    }

    private void endDraw() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private Vector4f uniformRadius(float radius) {
        return new Vector4f(radius, radius, radius, radius);
    }

    /**
     * SDF AA padding ported from DeltaClient MathUtil.b(smoothness).
     */
    private float[] padding(float smoothness) {
        float horizontal = ((-smoothness) / 2.0f) + (smoothness * 2.0f);
        float vertical = (smoothness / 2.0f) + smoothness;
        return new float[]{horizontal, vertical};
    }

    private void vertexQuad(BufferBuilder buffer, Matrix4f matrix, float x, float y, float width, float height, int color) {
        buffer.vertex(matrix, x, y, 0.0f).color(color);
        buffer.vertex(matrix, x, y + height, 0.0f).color(color);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(color);
        buffer.vertex(matrix, x + width, y, 0.0f).color(color);
    }

    private void vertexTexturedQuad(BufferBuilder buffer, Matrix4f matrix, float x, float y, float width, float height,
                                    float u, float v, float textureWidth, float textureHeight, int color) {
        buffer.vertex(matrix, x, y, 0.0f).texture(u, v).color(color);
        buffer.vertex(matrix, x, y + height, 0.0f).texture(u, v + textureHeight).color(color);
        buffer.vertex(matrix, x + width, y + height, 0.0f).texture(u + textureWidth, v + textureHeight).color(color);
        buffer.vertex(matrix, x + width, y, 0.0f).texture(u + textureWidth, v).color(color);
    }
}
