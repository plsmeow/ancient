package tech.onetap.util.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import tech.onetap.util.IMinecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Kawase blur chain ported from DeltaClient (aethereal.ui.shader.BlurShader).
 * <p>
 * The chain (downscale x4, upscale x3 over full-resolution framebuffers) is computed once per
 * frame via {@link #runChain(MatrixStack)}; blurred rectangles then sample the final chain
 * framebuffer ({@link #getFramebuffers()}). Framebuffers are rebuilt lazily when the window
 * size changes (replaces DeltaClient's ResizeEvent wiring).
 */
public class BlurShader extends Shader implements IMinecraft {
    private static final ShaderProgramKey DOWNSCALE_KEY =
            new ShaderProgramKey(Identifier.of("mre", "core/blur/downscale"), VertexFormats.POSITION, Defines.EMPTY);
    private static final ShaderProgramKey UPSCALE_KEY =
            new ShaderProgramKey(Identifier.of("mre", "core/blur/upscale"), VertexFormats.POSITION, Defines.EMPTY);

    private final List<SimpleFramebuffer> framebuffers = new ArrayList<>();

    public Uniform size;
    public Uniform radius;
    public Uniform smoothness;
    public Uniform mix;
    public Uniform alpha;
    public Uniform topLeftColor;
    public Uniform bottomLeftColor;
    public Uniform topRightColor;
    public Uniform bottomRightColor;
    public Uniform glowColor;
    public Uniform glowRadius;

    public BlurShader() {
        super(Identifier.of("mre", "core/rect/blurred_rect"), VertexFormats.POSITION_TEXTURE_COLOR);
    }

    /**
     * Blurred full-frame framebuffers; the first entry holds the final chain result.
     * Empty (or stale) until {@link #runChain(MatrixStack)} has run at the current window size.
     */
    public List<SimpleFramebuffer> getFramebuffers() {
        return this.framebuffers;
    }

    @Override
    protected void bindUniforms() {
        this.size = uniform("uSize");
        this.radius = uniform("uRadius");
        this.smoothness = uniform("uSmoothness");
        this.mix = uniform("uMix");
        this.alpha = uniform("uAlpha");
        this.topLeftColor = uniform("uTopLeftColor");
        this.bottomLeftColor = uniform("uBottomLeftColor");
        this.topRightColor = uniform("uTopRightColor");
        this.bottomRightColor = uniform("uBottomRightColor");
        this.glowColor = uniform("uGlowColor");
        this.glowRadius = uniform("uGlowRadius");
    }

    /**
     * Runs the kawase chain over the main framebuffer. Call once per frame, before any
     * blurred-rectangle draws.
     */
    public void runChain(MatrixStack matrixStack) {
        ensureFramebuffers();
        if (this.framebuffers.isEmpty()) {
            return;
        }
        int actualPasses = Math.max(this.framebuffers.size() - 1, 1);
        try {
            renderPass(matrixStack, DOWNSCALE_KEY, mc.getFramebuffer(), this.framebuffers.getFirst(), 0, 24);
            for (int i = 0; i < actualPasses; i++) {
                renderPass(matrixStack, DOWNSCALE_KEY, this.framebuffers.get(i), this.framebuffers.get(i + 1), i + 1, 24);
            }
            for (int i = actualPasses; i > 0; i--) {
                renderPass(matrixStack, UPSCALE_KEY, this.framebuffers.get(i), this.framebuffers.get(i - 1), i, 24);
            }
        } finally {
            mc.getFramebuffer().beginWrite(false);
        }
    }

    private void ensureFramebuffers() {
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (this.framebuffers.isEmpty()
                || this.framebuffers.getFirst().textureWidth != width
                || this.framebuffers.getFirst().textureHeight != height) {
            this.framebuffers.forEach(Framebuffer::delete);
            this.framebuffers.clear();
            for (int i = 0; i <= 3; i++) {
                this.framebuffers.add(new SimpleFramebuffer(width, height, false));
            }
        }
    }

    private void renderPass(MatrixStack matrixStack, ShaderProgramKey shaderKey, Framebuffer source, Framebuffer destination, int pass, int offset) {
        destination.beginWrite(false);
        RenderSystem.setShaderTexture(0, source.getColorAttachment());
        net.minecraft.client.gl.ShaderProgram shader = RenderSystem.setShader(shaderKey);
        Uniform halfTexelSize = shader.getUniform("uHalfTexelSize");
        Uniform uOffset = shader.getUniform("uOffset");
        if (halfTexelSize != null) {
            halfTexelSize.set(0.5f / source.textureWidth, 0.5f / source.textureHeight);
        }
        if (uOffset != null) {
            uOffset.set(offset * (pass / 3.0f));
        }
        drawFullscreenQuad(matrixStack.peek().getPositionMatrix());
        destination.endWrite();
    }

    private void drawFullscreenQuad(Matrix4f matrix4f) {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        builder.vertex(matrix4f, 0.0f, 0.0f, 0.0f);
        builder.vertex(matrix4f, 0.0f, mc.getWindow().getScaledHeight(), 0.0f);
        builder.vertex(matrix4f, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), 0.0f);
        builder.vertex(matrix4f, mc.getWindow().getScaledWidth(), 0.0f, 0.0f);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void setSize(float width, float height) {
        if (this.size != null) {
            this.size.set(width, height);
        }
    }

    public void setRadius(Vector4f radius) {
        if (this.radius != null) {
            this.radius.set(radius.x, radius.y, radius.z, radius.w);
        }
    }

    public void setRadius(float r1, float r2, float r3, float r4) {
        if (this.radius != null) {
            this.radius.set(r1, r2, r3, r4);
        }
    }

    public void setSmoothness(float smoothness) {
        if (this.smoothness != null) {
            this.smoothness.set(smoothness);
        }
    }

    public void setMix(float mix) {
        if (this.mix != null) {
            this.mix.set(mix);
        }
    }

    public void setAlpha(float alpha) {
        if (this.alpha != null) {
            this.alpha.set(alpha);
        }
    }

    public void setGlowRadius(float glowRadius) {
        if (this.glowRadius != null) {
            this.glowRadius.set(glowRadius);
        }
    }

    public void setGlowColor(float r, float g, float b, float a) {
        if (this.glowColor != null) {
            this.glowColor.set(r, g, b, a);
        }
    }

    public void setCornerColors(float[] topLeft, float[] bottomLeft, float[] bottomRight, float[] topRight) {
        if (this.topLeftColor != null) {
            this.topLeftColor.set(topLeft[0], topLeft[1], topLeft[2], topLeft[3]);
        }
        if (this.bottomLeftColor != null) {
            this.bottomLeftColor.set(bottomLeft[0], bottomLeft[1], bottomLeft[2], bottomLeft[3]);
        }
        if (this.bottomRightColor != null) {
            this.bottomRightColor.set(bottomRight[0], bottomRight[1], bottomRight[2], bottomRight[3]);
        }
        if (this.topRightColor != null) {
            this.topRightColor.set(topRight[0], topRight[1], topRight[2], topRight[3]);
        }
    }
}
