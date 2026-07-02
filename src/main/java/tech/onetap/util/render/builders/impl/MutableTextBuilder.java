package tech.onetap.util.render.builders.impl;

import net.minecraft.text.Text;
import tech.onetap.util.render.builders.AbstractBuilder;
import tech.onetap.util.render.msdf.MsdfFont;
import tech.onetap.util.render.renderers.impl.BuiltMutableText;

import java.awt.*;

public final class MutableTextBuilder extends AbstractBuilder<BuiltMutableText> {
    private MsdfFont font;
    private Text text;
    private float size;
    private float thickness;
    private int color;
    private float smoothness;
    private float spacing;
    private int outlineColor;
    private float outlineThickness;
    private int alpha;

    public MutableTextBuilder font(MsdfFont font) {
        this.font = font;
        return this;
    }

    public MutableTextBuilder text(Text text) {
        this.text = text;
        return this;
    }

    public MutableTextBuilder size(float size) {
        this.size = size;
        return this;
    }

    public MutableTextBuilder thickness(float thickness) {
        this.thickness = thickness;
        return this;
    }

    public MutableTextBuilder color(int color) {
        this.color = color;
        return this;
    }

    public MutableTextBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    public MutableTextBuilder spacing(float spacing) {
        this.spacing = spacing;
        return this;
    }

    public MutableTextBuilder outline(Color color, float thickness) {
        return this.outline(color.getRGB(), thickness);
    }

    public MutableTextBuilder outline(int color, float thickness) {
        this.outlineColor = color;
        this.outlineThickness = thickness;
        return this;
    }

    public MutableTextBuilder alpha(int alpha) {
        this.alpha = alpha;
        return this;
    }

    @Override
    protected BuiltMutableText _build() {
        return new BuiltMutableText(
                this.font,
                this.text,
                this.size,
                this.thickness,
                this.color,
                this.smoothness,
                this.spacing,
                this.outlineColor,
                this.outlineThickness,
                this.alpha
        );
    }

    @Override
    protected void reset() {
        this.font = null;
        this.text = null;
        this.size = 0.0f;
        this.thickness = 0.05f;
        this.color = -1;
        this.smoothness = 0.5f;
        this.spacing = 0.0f;
        this.outlineColor = 0;
        this.outlineThickness = 0.0f;
    }

}