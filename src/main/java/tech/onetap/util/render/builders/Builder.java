package tech.onetap.util.render.builders;

import tech.onetap.util.render.builders.impl.*;

public final class Builder {

    private static final RectangleBuilder RECTANGLE_BUILDER = new RectangleBuilder();
    private static final BorderBuilder BORDER_BUILDER = new BorderBuilder();
    private static final TextureBuilder TEXTURE_BUILDER = new TextureBuilder();
    private static final TextBuilder TEXT_BUILDER = new TextBuilder();

    private static final MutableTextBuilder MUTABLE_TEXT_BUILDER = new MutableTextBuilder();
    private static final BlurBuilder BLUR_BUILDER = new BlurBuilder();
    private static final GlowBuilder GLOW_BUILDER = new GlowBuilder();

    public static GlowBuilder glow() {
        return GLOW_BUILDER;
    }
    public static RectangleBuilder rectangle() {
        return RECTANGLE_BUILDER;
    }

    public static BorderBuilder border() {
        return BORDER_BUILDER;
    }

    public static TextureBuilder texture() {
        return TEXTURE_BUILDER;
    }
    public static TextBuilder text() {
        return TEXT_BUILDER;
    }
    public static MutableTextBuilder mutableText() {
        return MUTABLE_TEXT_BUILDER;
    }

    public static BlurBuilder blur() {
        return BLUR_BUILDER;
    }
}