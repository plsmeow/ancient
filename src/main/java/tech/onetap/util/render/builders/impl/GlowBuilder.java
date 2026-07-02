package tech.onetap.util.render.builders.impl;

import tech.onetap.util.render.builders.AbstractBuilder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.renderers.impl.BuiltGlow;

public final class GlowBuilder extends AbstractBuilder<BuiltGlow> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;

    private float glowRadius;
    private float softness;
    private float intensity;
    private boolean additive;

    public GlowBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public GlowBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public GlowBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public GlowBuilder glowRadius(float glowRadius) {
        this.glowRadius = glowRadius;
        return this;
    }

    public GlowBuilder softness(float softness) {
        this.softness = softness;
        return this;
    }

    public GlowBuilder intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public GlowBuilder additive(boolean additive) {
        this.additive = additive;
        return this;
    }

    @Override
    protected BuiltGlow _build() {
        return new BuiltGlow(size, radius, color, glowRadius, softness, intensity, additive);
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.TRANSPARENT;

        this.glowRadius = 8.0f;   // дефолт норм под "Celestial"
        this.softness = 1.25f;    // антиалиас края
        this.intensity = 1.0f;    // множитель альфы
        this.additive = true;     // красивее для глоу
    }
}