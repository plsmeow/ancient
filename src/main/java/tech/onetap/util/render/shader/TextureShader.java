package tech.onetap.util.render.shader;

import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

/**
 * Textured rounded rectangle shader (mre:core/rect/texture_rect) ported from DeltaClient.
 * Radius uniform order: (top-right, bottom-right, bottom-left, top-left).
 */
public class TextureShader extends Shader {
    public Uniform size;
    public Uniform radius;
    public Uniform smoothness;

    public TextureShader() {
        super(Identifier.of("mre", "core/rect/texture_rect"), VertexFormats.POSITION_TEXTURE_COLOR);
    }

    @Override
    protected void bindUniforms() {
        this.size = uniform("uSize");
        this.radius = uniform("uRadius");
        this.smoothness = uniform("uSmoothness");
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

    public void setRadius(Vector4f radius) {
        setRadius(radius.x, radius.y, radius.z, radius.w);
    }

    public void setSmoothness(float smoothness) {
        if (this.smoothness != null) {
            this.smoothness.set(smoothness);
        }
    }
}
