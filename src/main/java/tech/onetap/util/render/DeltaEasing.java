package tech.onetap.util.render;

/**
 * Easing function library ported from DeltaClient (aethereal.render.EasingList) —
 * same formulas, readable names: i = SINE_IN_OUT, p = EXPO_OUT, s = BACK_OUT,
 * g = SINE_OUT, f = SINE_IN.
 */
public final class DeltaEasing {
    private static final double C1 = 1.7015791506357025;
    private static final double C2 = 2.5949079670432127;
    private static final double C3 = 2.701578217685146;
    private static final double C4 = 2.094396299644887;
    private static final double C5 = 1.3962634699317127;

    public static final EasingFunction SINE_IN = value ->
            (float) (1.0 - Math.cos((value * Math.PI) / 2.0));
    public static final EasingFunction SINE_OUT = value ->
            (float) Math.sin((value * Math.PI) / 2.0);
    public static final EasingFunction SINE_IN_OUT = value ->
            (float) (-(Math.cos(Math.PI * value) - 1.0) / 2.0);
    public static final EasingFunction CIRC_IN = value ->
            (float) (1.0 - Math.sqrt(1.0 - Math.pow(value, 2)));
    public static final EasingFunction CIRC_OUT = value ->
            (float) Math.sqrt(1.0 - Math.pow(value - 1.0, 2));
    public static final EasingFunction CIRC_IN_OUT = value ->
            (float) (value < 0.5 ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * value, 2))) / 2.0
                    : (Math.sqrt(1.0 - Math.pow((-2.0 * value) + 2.0, 2)) + 1.0) / 2.0);
    public static final EasingFunction ELASTIC_IN = value ->
            (value == 0.0 || value == 1.0) ? value
                    : (float) (Math.pow(-2.0, (10.0 * value) - 10.0) * Math.sin(((value * 10.0) - 10.75) * C4));
    public static final EasingFunction ELASTIC_OUT = value ->
            (value == 0.0 || value == 1.0) ? value
                    : (float) ((Math.pow(2.0, (-10.0 * value)) * Math.sin(((value * 10.0) - 0.75) * C4)) + 1.0);
    public static final EasingFunction ELASTIC_IN_OUT = value -> {
        if (value == 0.0 || value == 1.0) {
            return value;
        }
        return (float) (value < 0.5
                ? -(Math.pow(2.0, (20.0 * value) - 10.0) * Math.sin(((20.0 * value) - 11.125) * C5)) / 2.0
                : ((Math.pow(2.0, (-20.0 * value) + 10.0) * Math.sin(((20.0 * value) - 11.125) * C5)) / 2.0) + 1.0);
    };
    public static final EasingFunction EXPO_IN = value ->
            value != 0.0 ? (float) Math.pow(2.0, (10.0 * value) - 10.0) : value;
    public static final EasingFunction EXPO_OUT = value ->
            value != 1.0 ? (float) (1.0 - Math.pow(2.0, (-10.0 * value))) : value;
    public static final EasingFunction EXPO_IN_OUT = value -> {
        if (value == 0.0 || value == 1.0) {
            return value;
        }
        return (float) (value < 0.5 ? Math.pow(2.0, (20.0 * value) - 10.0) / 2.0
                : (2.0 - Math.pow(2.0, (-20.0 * value) + 10.0)) / 2.0);
    };
    public static final EasingFunction BACK_IN = value ->
            (float) ((C3 * Math.pow(value, 3)) - (C1 * Math.pow(value, 2)));
    public static final EasingFunction BACK_OUT = value ->
            (float) (1.0 + (C3 * Math.pow(value - 1.0, 3)) + (C1 * Math.pow(value - 1.0, 2)));
    public static final EasingFunction LINEAR = value -> value;
    public static final EasingFunction BACK_IN_OUT = value ->
            (float) (value < 0.5 ? (Math.pow(2.0 * value, 2) * ((7.189816336825345 * value) - C2)) / 2.0
                    : ((Math.pow((2.0 * value) - 2.0, 2) * ((3.594908086491756 * ((value * 2.0) - 2.0)) + C2)) + 2.0) / 2.0);
    public static final EasingFunction BOUNCE_OUT = value -> {
        if (value < 1.0 / 2.75) {
            return (float) (7.5625 * Math.pow(value, 2));
        }
        if (value < 2.0 / 2.75) {
            return (float) ((7.5625 * Math.pow(value - (1.5 / 2.75), 2)) + 0.75);
        }
        return (float) (value < 2.5 / 2.75
                ? (7.5625 * Math.pow(value - (2.25 / 2.75), 2)) + 0.9375
                : (7.5625 * Math.pow(value - (2.625 / 2.75), 2)) + 0.984375);
    };
    public static final EasingFunction BOUNCE_IN = value ->
            (float) (1.0 - BOUNCE_OUT.ease(1.0f - value));
    public static final EasingFunction BOUNCE_IN_OUT = value ->
            (float) (value < 0.5 ? (1.0 - BOUNCE_OUT.ease(1.0f - (2.0f * value))) / 2.0
                    : (1.0 + BOUNCE_OUT.ease((2.0f * value) - 1.0f)) / 2.0);
    public static final EasingFunction QUINTIC_IN_OUT = value ->
            (float) (value < 0.5 ? 16.0 * value * value * value * value * value
                    : 1.0 - (Math.pow((-2.0f * value) + 2.0f, 5.0) / 2.0));

    @FunctionalInterface
    public interface EasingFunction {
        float ease(float value);
    }

    private DeltaEasing() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
