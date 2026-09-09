package tech.onetap.util.render;

import tech.onetap.util.render.shader.BlurShader;
import tech.onetap.util.render.shader.BorderShader;
import tech.onetap.util.render.shader.GlowShader;
import tech.onetap.util.render.shader.GradientShader;
import tech.onetap.util.render.shader.RectangleShader;
import tech.onetap.util.render.shader.TextureShader;

/**
 * Holder for the shared shader wrapper instances of the ported DeltaClient render engine
 * (plus the retained border/glow shaders of the previous engine for exact style parity).
 */
public final class RenderShaders {
    public static final RectangleShader RECTANGLE = new RectangleShader();
    public static final TextureShader TEXTURE = new TextureShader();
    public static final GradientShader GRADIENT = new GradientShader();
    public static final BlurShader BLUR = new BlurShader();
    public static final GlowShader GLOW = new GlowShader();
    public static final BorderShader BORDER = new BorderShader();

    private RenderShaders() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
