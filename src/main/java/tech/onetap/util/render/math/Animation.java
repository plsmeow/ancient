package tech.onetap.util.render.math;

import lombok.Data;

@Data
public class Animation {

    private long duration;
    private float value;
    private Easing easing;
    private long startTime;
    private float startValue;
    private float targetValue;
    private boolean done;

    public Animation(long duration, float initialValue, Easing easing) {
        this.duration = duration;
        this.easing = easing;
        this.value = initialValue;
        this.startValue = initialValue;
        this.targetValue = initialValue;
        this.done = true;
    }


    public Animation(Easing easing, long duration) {
        this(duration, 0.0F, easing);
    }

    public void run(boolean bool) {
        run(bool ? 1 : 0);
    }


    public float run(float newValue) {
        long currentTime = System.currentTimeMillis();

        if (newValue != targetValue) {
            startValue = value;
            targetValue = newValue;
            startTime = currentTime;
            done = false;
        }

        long elapsed = currentTime - startTime;
        if (elapsed >= duration) {
            value = targetValue;
            done = true;
            return value;
        }

        float progress = (float) elapsed / duration;
        float easedProgress = easing.ease(progress, 0, 1, 1);
        value = startValue + (targetValue - startValue) * easedProgress;
        return value;
    }


    public void setValue(float newValue) {
        this.value = newValue;
        this.startValue = newValue;
        this.targetValue = newValue;
        this.done = true;
    }



    public void reset(float initialValue) {
        this.value = initialValue;
        this.startValue = initialValue;
        this.targetValue = initialValue;
        this.done = true;
    }


    public void reset() {
        reset(0.0F);
    }

    public void animateTo(float newTarget) {
        if (newTarget != this.targetValue) {
            this.startValue = this.value;
            this.targetValue = newTarget;
            this.startTime = System.currentTimeMillis();
            this.done = false;
        }
    }

    public float run() {
        return run(targetValue);
    }

    private boolean direction;
}