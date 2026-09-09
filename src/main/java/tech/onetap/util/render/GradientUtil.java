package tech.onetap.util.render;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.awt.Color;

/**
 * Animated per-character text gradient helpers ported from DeltaClient
 * (aethereal.ui.shader.GradientUtil); feeds the {@link Font} gradient draw overloads.
 */
public class GradientUtil {
    private GradientUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static MutableText twoColorGradient(String text, int startColor, int endColor, int speed, float ratio) {
        MutableText component = Text.literal("");
        if (text == null || text.isEmpty()) {
            return component;
        }
        float time = ((System.currentTimeMillis() % 10000) / 1000.0f) * (100.0f / speed);
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            int color = ColorUtil.makeGradient(startColor, endColor, i, length, time, ratio);
            Style style = Style.EMPTY.withColor(color & 0xFFFFFF);
            component.append(Text.literal(String.valueOf(c)).setStyle(style));
        }
        return component;
    }

    public static MutableText waveGradient(String text, int color, float speed, float offset) {
        MutableText component = Text.literal("");
        if (text == null || text.isEmpty()) {
            return component;
        }
        float time = (System.currentTimeMillis() % ((long) (speed * 1000.0f))) / (speed * 1000.0f);
        float[] hsb = Color.RGBtoHSB((color >> 16) & 255, (color >> 8) & 255, color & 255, null);
        for (int i = 0; i < text.length(); i++) {
            float factor = (float) ((Math.sin(((double) (time + ((i * offset) / text.length()))) * Math.PI * 2.0d) * 0.5d) + 0.5d);
            int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2] * (0.5f + (0.5f * factor))) & 0xFFFFFF;
            component.append(Text.literal(String.valueOf(text.charAt(i))).setStyle(Style.EMPTY.withColor(rgb)));
        }
        return component;
    }
}
