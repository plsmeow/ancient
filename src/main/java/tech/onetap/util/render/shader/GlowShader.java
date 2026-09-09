package tech.onetap.util.render.shader;

import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Radial glow shader (mre:core/glow) retained from the previous engine: DeltaClient's blur
 * shader only supports a single glow color, while the current HUD style relies on animated
 * four-corner ("orbital") glow colors, so this shader is kept to preserve the style.
 */
public class GlowShader extends Shader {
    public Uniform size;
    public Uniform radius;
    public Uniform glowRadius;
    public Uniform softness;
    public Uniform intensity;

    public GlowShader() {
        super(Identifier.of("mre", "core/glow"), VertexFormats.POSITION_COLOR);
    }

    @Override
    protected void bindUniforms() {
        this.size = uniform("Size");
        this.radius = uniform("Radius");
        this.glowRadius = uniform("GlowRadius");
        this.softness = uniform("Softness");
        this.intensity = uniform("Intensity");
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

    public void setGlowRadius(float glowRadius) {
        if (this.glowRadius != null) {
            this.glowRadius.set(glowRadius);
        }
    }

    public void setSoftness(float softness) {
        if (this.softness != null) {
            this.softness.set(softness);
        }
    }

    public void setIntensity(float intensity) {
        if (this.intensity != null) {
            this.intensity.set(intensity);
        }
    }
}
