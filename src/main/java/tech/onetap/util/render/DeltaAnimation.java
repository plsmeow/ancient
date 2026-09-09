package tech.onetap.util.render;

import net.minecraft.client.MinecraftClient;

/**
 * Float animator ported from DeltaClient (aethereal.render.AnimationUtil): tracks
 * current/previous value, frame-delta smoothing, easing direction ticks.
 */
public class DeltaAnimation {
    private float currentValue;
    private float previousValue;
    private float animationSpeed;
    private float animationValue;
    private float fromValue = 0.0f;
    private float toValue = 1.0f;
    private long lastUpdateTime = System.currentTimeMillis();

    public void setCurrentValue(float value) {
        this.currentValue = value;
    }

    public void setPreviousValue(float prevValue) {
        this.previousValue = prevValue;
    }

    public float getCurrentValue() {
        return this.currentValue;
    }

    public float getPreviousValue() {
        return this.previousValue;
    }

    public float getAnimationValue() {
        return this.animationValue;
    }

    public void setAnimationValue(float animationValue) {
        this.animationValue = animationValue;
    }

    /**
     * Directional expansion toward 1 or 0 at the given speed (per-second units of 20 ticks).
     */
    public void tick(boolean expanding) {
        this.previousValue = this.currentValue;
        float direction = expanding ? 1.0f : -1.0f;
        this.currentValue = MathUtil2.clamp(this.currentValue + (direction * this.animationSpeed * 20.0f * deltaSeconds()),
                this.fromValue, this.toValue);
    }

    /**
     * Captures the frame interpolation state (call every frame).
     */
    public void update(float fromValue, float toValue, float animationSpeed, DeltaEasing.EasingFunction easing, float partialTicks) {
        this.animationSpeed = animationSpeed;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.animationValue = MathUtil2.lerp(this.previousValue, this.currentValue, partialTicks);
    }

    public void add(float amount) {
        this.toValue += amount;
    }

    public void set(float value) {
        this.toValue = value;
        this.currentValue = value;
    }

    /**
     * Smoothly moves the value toward min..max-bounded target; returns the eased current value.
     */
    public float smooth(float min, float max, float speed) {
        this.toValue = MathUtil2.clamp(this.toValue, min, max);
        this.currentValue = MathUtil2.frameSmooth(this.currentValue, this.toValue, speed);
        return this.currentValue;
    }

    public float smooth(float target, float speed) {
        return smooth(target, target, speed);
    }

    private float deltaSeconds() {
        long now = System.currentTimeMillis();
        float delta = (now - this.lastUpdateTime) / 1000.0f;
        this.lastUpdateTime = now;
        return delta;
    }

    private static final class MathUtil2 {
        static float lerp(float start, float end, float delta) {
            return start + ((end - start) * delta);
        }

        static float clamp(float num, float min, float max) {
            return Math.min(Math.max(num, min), max);
        }

        static float frameSmooth(float current, float target, float speed) {
            float delta = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration();
            return current + ((target - current) * (1.0f - (float) Math.exp(-speed * delta)));
        }
    }
}
