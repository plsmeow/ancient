package tech.onetap.util.render.shader;

import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

/**
 * Four-corner gradient rounded rectangle shader (mre:core/rect/gradient_rect)
 * ported from DeltaClient. Radius uniform order: (top-right, bottom-right,
 * bottom-left, top-left).
 */
public class GradientShader extends Shader {
    public Uniform size;
    public Uniform radius;
    public Uniform smoothness;
    public Uniform topLeftColor;
    public Uniform bottomLeftColor;
    public Uniform topRightColor;
    public Uniform bottomRightColor;

    public GradientShader() {
        super(Identifier.of("mre", "core/rect/gradient_rect"), VertexFormats.POSITION_COLOR);
    }

    @Override
    protected void bindUniforms() {
        this.size = uniform("uSize");
        this.radius = uniform("uRadius");
        this.smoothness = uniform("uSmoothness");
        this.topLeftColor = uniform("uTopLeftColor");
        this.bottomLeftColor = uniform("uBottomLeftColor");
        this.topRightColor = uniform("uTopRightColor");
        this.bottomRightColor = uniform("uBottomRightColor");
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

    public void setSmoothness(float smoothness) {
        if (this.smoothness != null) {
            this.smoothness.set(smoothness);
        }
    }

    public void setTopLeft(float r, float g, float b, float a) {
        if (this.topLeftColor != null) {
            this.topLeftColor.set(r, g, b, a);
        }
    }

    public void setBottomLeft(float r, float g, float b, float a) {
        if (this.bottomLeftColor != null) {
            this.bottomLeftColor.set(r, g, b, a);
        }
    }

    public void setTopRight(float r, float g, float b, float a) {
        if (this.topRightColor != null) {
            this.topRightColor.set(r, g, b, a);
        }
    }

    public void setBottomRight(float r, float g, float b, float a) {
        if (this.bottomRightColor != null) {
            this.bottomRightColor.set(r, g, b, a);
        }
    }
}
