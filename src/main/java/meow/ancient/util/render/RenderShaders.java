package meow.ancient.util.render;

import meow.ancient.util.render.shader.BlurShader;
import meow.ancient.util.render.shader.BorderShader;
import meow.ancient.util.render.shader.GlowShader;
import meow.ancient.util.render.shader.GradientShader;
import meow.ancient.util.render.shader.RectangleShader;
import meow.ancient.util.render.shader.TextureShader;

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
