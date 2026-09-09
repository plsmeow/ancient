package tech.onetap.ui.delta;

/**
 * Small math helpers for the Delta UI (ported from DeltaClient MathUtil.a/b/scale).
 */
public final class DeltaMath {
    private DeltaMath() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
    }

    public static boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
    }

    public static float clamp(float num, float min, float max) {
        return Math.min(Math.max(num, min), max);
    }

    public static float clamp01(float value) {
        float clamped = clamp(value, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - (2.0f * clamped));
    }

    /**
     * Scales a GUI coordinate from the current GUI scale to target scale 2
     * (DeltaClient MathUtil.scale — the Delta GUI always renders at scale 2).
     */
    public static double scaleToGui2(double coordinate, net.minecraft.client.MinecraftClient mc) {
        return (coordinate * mc.getWindow().getScaleFactor())
                / mc.getWindow().calculateScaleFactor(2, mc.forcesUnicodeFont());
    }
}
