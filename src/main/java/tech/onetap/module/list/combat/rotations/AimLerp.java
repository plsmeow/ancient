package tech.onetap.module.list.combat.rotations;

/**
 * Короткая float-рампа для ротаций (ease-in к 1, ease-out к 0).
 */
public class AimLerp {

    private int durationMs;
    private float value;
    private float from;
    private float to;
    private long startMs;

    public AimLerp(int durationMs) {
        this.durationMs = Math.max(1, durationMs);
        this.startMs = System.currentTimeMillis();
    }

    public void setDuration(int durationMs) {
        this.durationMs = Math.max(1, durationMs);
    }

    /** Плавно к 0 (медленное наведение). */
    public void easeOut() {
        animateTo(0.0F);
    }

    /** Плавно к 1 (snap / резкое наведение). */
    public void easeIn() {
        animateTo(1.0F);
    }

    public float getFactor() {
        long elapsed = System.currentTimeMillis() - startMs;
        float t = Math.min(1.0F, (float) elapsed / (float) durationMs);
        value = from + (to - from) * t;
        return value;
    }

    private void animateTo(float target) {
        from = value;
        to = target;
        startMs = System.currentTimeMillis();
    }
}
