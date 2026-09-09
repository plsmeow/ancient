package tech.onetap.util.render.shader;

import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Border shader (mre:core/border) retained from the previous engine. DeltaClient's
 * rect shader draws an inner outline only; the current style draws the border
 * symmetrically around the box edge with separate internal/external smoothness,
 * which this shader reproduces exactly.
 */
public class BorderShader extends Shader {
    public Uniform size;
    public Uniform radius;
    public Uniform thickness;
    public Uniform smoothness;

    public BorderShader() {
        super(Identifier.of("mre", "core/border"), VertexFormats.POSITION_COLOR);
    }

    @Override
    protected void bindUniforms() {
        this.size = uniform("Size");
        this.radius = uniform("Radius");
        this.thickness = uniform("Thickness");
        this.smoothness = uniform("Smoothness");
    }

    public void setSize(float width, float height) {
        if (this.size != null) {
            this.size.set(width, height);
        }
    }

    public void setRadius(float r1, float r2, float r3, float r4) {
        if (this.radius != null) {
            this.radius.set(r1, r2, r3, r4);
        }
    }

    public void setThickness(float thickness) {
        if (this.thickness != null) {
            this.thickness.set(thickness);
        }
    }

    public void setSmoothness(float internal, float external) {
        if (this.smoothness != null) {
            this.smoothness.set(internal, external);
        }
    }
}
