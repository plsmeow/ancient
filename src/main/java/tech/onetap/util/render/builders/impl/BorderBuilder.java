package tech.onetap.util.render.builders.impl;

import tech.onetap.util.render.builders.AbstractBuilder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.renderers.impl.BuiltBorder;

public final class BorderBuilder extends AbstractBuilder<BuiltBorder> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float thickness;
    private float internalSmoothness, externalSmoothness;

    public BorderBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public BorderBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public BorderBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public BorderBuilder thickness(float thickness) {
        this.thickness = thickness;
        return this;
    }

    public BorderBuilder smoothness(float internalSmoothness, float externalSmoothness) {
        this.internalSmoothness = internalSmoothness;
        this.externalSmoothness = externalSmoothness;
        return this;
    }

    @Override
    protected BuiltBorder _build() {
        return new BuiltBorder(
            this.size,
            this.radius,
            this.color,
            this.thickness,
            this.internalSmoothness, this.externalSmoothness
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.TRANSPARENT;
        this.thickness = 0.0f;
        this.internalSmoothness = 1.0f;
        this.externalSmoothness = 1.0f;
    }

}