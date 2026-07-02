package tech.onetap.util.render.providers;

import net.minecraft.util.math.MathHelper;
import tech.onetap.module.settings.impl.ThemeManager;
import tech.onetap.util.render.math.MathUtil;

import java.awt.*;

public final class ColorProvider {
	public static int red(int c) {
		return c >> 16 & 0xFF;
	}

	public static int green(int c) {
		return c >> 8 & 0xFF;
	}

	public static int blue(int c) {
		return c & 0xFF;
	}

	public static int alpha(int c) {
		return c >> 24 & 0xFF;
	}

	public static int getThemeColor() {
		return ThemeManager.getInstance().getCurrentTheme().getColorFirst();
	}

	public static int getThemeColorTwo() {
		return ThemeManager.getInstance().getCurrentTheme().getColorSecond();
	}
	public static int[] getOrbitalRect(int c1, int c2, double speed, int alpha) {
		int[] colors = new int[4];
		double time = System.currentTimeMillis() / speed;
		for (int i = 0; i < 4; i++) {
			double phase = i * (Math.PI / 2.0);
			int color = interpolateColor(c1, c2, (float) (Math.sin(time + phase) * 0.5 + 0.5));
			colors[i] = setAlpha(color, alpha);
		}
		return colors;
	}
	public static int gradient(final int speed, final int index, final int... colors) {
		int angle = (int)((System.currentTimeMillis() / speed + index) % 360L);
		angle = ((angle > 180) ? (360 - angle) : angle) + 180;
		int colorIndex = (int)(angle / 360.0f * colors.length);
		if (colorIndex == colors.length) {
			--colorIndex;
		}
		final int color1 = colors[colorIndex];
		final int color2 = colors[(colorIndex == colors.length - 1) ? 0 : (colorIndex + 1)];
		return interpolateColor(color1, color2, angle / 360.0f * colors.length - colorIndex);
	}

	public static int interpolateColor(int from, int to, float amount) {
		amount = Math.min(1.0f, Math.max(0.0f, amount));
		int red1 = red(to);
		int green1 = green(to);
		int blue1 = blue(to);
		int alpha1 = alpha(to);
		int red2 = red(from);
		int green2 = green(from);
		int blue2 = blue(from);
		int alpha2 = alpha(from);
		int interpolatedRed = interpolateInt(red1, red2, amount);
		int interpolatedGreen = interpolateInt(green1, green2, amount);
		int interpolatedBlue = interpolateInt(blue1, blue2, amount);
		int interpolatedAlpha = interpolateInt(alpha1, alpha2, amount);
		return interpolatedAlpha << 24 | interpolatedRed << 16 | interpolatedGreen << 8 | interpolatedBlue;
	}

	private static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
		return interpolate(oldValue, newValue, (float)interpolationValue);
	}

	public static int interpolate(int start, int end, float value) {
		float[] startColor = rgba(start);
		float[] endColor = rgba(end);

		return rgba((int) MathUtil.interpolate(startColor[0] * 255, endColor[0] * 255, value),
				(int) MathUtil.interpolate(startColor[1] * 255, endColor[1] * 255, value),
				(int) MathUtil.interpolate(startColor[2] * 255, endColor[2] * 255, value),
				(int) MathUtil.interpolate(startColor[3] * 255, endColor[3] * 255, value));
	}

	public static float[] rgba(final int color) {
		return new float[] {
				(color >> 16 & 0xFF) / 255f,
				(color >> 8 & 0xFF) / 255f,
				(color & 0xFF) / 255f,
				(color >> 24 & 0xFF) / 255f
		};
	}

	public static int rgba(int r, int g, int b, double a) {
		return (int) (a) << 24 | r << 16 | g << 8 | b;
	}

	public static int rgba(int r, int g, int b, float a) {
		return (int) (a) << 24 | r << 16 | g << 8 | b;
	}

	public static int rgb(int r, int g, int b) {
		return 255 << 24 | r << 16 | g << 8 | b;
	}

	public static int setAlpha(int color, int alpha) {
		return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 16777215);
	}

	public static int setAlpha(int color, double alpha) {
		return (MathHelper.clamp((int) (alpha), 0, 255) << 24) | (color & 16777215);
	}

	public static int brighter(int color, float factor) {
		int r = Math.min(255, (int)(red(color) * factor));
		int g = Math.min(255, (int)(green(color) * factor));
		int b = Math.min(255, (int)(blue(color) * factor));
		int a = alpha(color);
		return rgba(r, g, b, a);
	}

	public static int pack(int red, int green, int blue, int alpha) {
		return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | ((blue & 0xFF) << 0);
	}
	
	public static int[] unpack(int color) {
		return new int[] {color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF};
	}
	
	public static float[] normalize(Color color) {
		return new float[] {color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f};
	}

	public static float[] normalize(int color) {
		int[] components = unpack(color);
		return new float[] {components[0] / 255.0f, components[1] / 255.0f, components[2] / 255.0f, components[3] / 255.0f};
	}
}