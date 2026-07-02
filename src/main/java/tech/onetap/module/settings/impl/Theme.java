package tech.onetap.module.settings.impl;

import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.awt.*;

public class Theme {
    public Animation animation = new Animation(Easing.QUINTIC_OUT, 550);
    public Animation checkAnimation = new Animation(Easing.QUINTIC_OUT, 350);

    public float x;
    public float y;
    public String name;
    public int color1;
    public int color2;

    private int fromColor1;
    private int fromColor2;

    public Theme(String name, int color1, int color2) {
        this.name = name;
        this.color1 = saturateColor(color1);
        this.color2 = saturateColor(color2);
        this.fromColor1 = this.color1;
        this.fromColor2 = this.color2;
    }

    public void setColors(int newColor1, int newColor2) {
        int sat1 = saturateColor(newColor1);
        int sat2 = saturateColor(newColor2);

        if (this.color1 != sat1 || this.color2 != sat2) {
            startAnimation(this.color1, this.color2);
            this.color1 = sat1;
            this.color2 = sat2;
        }
    }

    public void startAnimation(int oldColor1, int oldColor2) {
        this.fromColor1 = oldColor1;
        this.fromColor2 = oldColor2;
        animation.reset();
    }

    public int getColorFirst() {
        animation.run(true);
        float progress = (float) Math.max(0, Math.min(1, animation.getValue()));
        return interpolateColorClean(fromColor1, color1, progress);
    }

    public int getColorSecond() {
        // Мы не запускаем тут run(true), чтобы анимация не ускорялась в 2 раза за один кадр
        float progress = (float) Math.max(0, Math.min(1, animation.getValue()));
        return interpolateColorClean(fromColor2, color2, progress);
    }

    public int getColorTheme(int index) {
        // Заменили сломанный градиент из ColorProvider на идеальный локальный
        return getGradientClean(3, index, getColorFirst(), getColorSecond());
    }

    public int getStaticColorTheme(int index) {
        return getGradientClean(Integer.MAX_VALUE, index, getColorFirst(), getColorSecond());
    }

    // ==========================================
    // ФИКС: ИДЕАЛЬНЫЙ ГРАДИЕНТ ДЛЯ ХУДА (НА ОСНОВЕ СИНУСОИДЫ)
    // ==========================================
    public static int getGradientClean(int speed, int index, int c1, int c2) {
        if (speed == Integer.MAX_VALUE || speed == 0) return c1;

        // Рассчитываем время
        int time = (int) (System.currentTimeMillis() / Math.max(1, speed) + index);
        float angle = (time % 360) / 360f;

        // Математически идеальное переливание туда-обратно через Cos
        float factor = (float) (-Math.cos(angle * Math.PI * 2) * 0.5 + 0.5);

        return interpolateColorClean(c1, c2, factor);
    }

    // ==========================================
    // ФИКС: ПРАВИЛЬНАЯ ИНТЕРПОЛЯЦИЯ ЦВЕТА
    // ==========================================
    public static int interpolateColorClean(int color1, int color2, float amount) {
        amount = Math.min(1.0f, Math.max(0.0f, amount));

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * amount);
        int r = (int) (r1 + (r2 - r1) * amount);
        int g = (int) (g1 + (g2 - g1) * amount);
        int b = (int) (b1 + (b2 - b1) * amount);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void drawTheme(double alpha) {
        if (this.name != null && this.name.equals("Радужный")) {
            int[] rainbowColors = new int[4];
            for (int i = 0; i < 4; i++) {
                rainbowColors[i] = Color.HSBtoRGB((float) (i / 4.0f), 0.8f, 1.0f);
            }
            DrawUtil.drawRound(x, y, 16, 15, 1,
                    ColorProvider.setAlpha(rainbowColors[0], alpha),
                    ColorProvider.setAlpha(rainbowColors[1], alpha),
                    ColorProvider.setAlpha(rainbowColors[2], alpha),
                    ColorProvider.setAlpha(rainbowColors[3], alpha));
        } else {
            String renderName = this.name != null ? this.name : "Custom";
            DrawUtil.drawText(Fonts.SFREGULAR.get(), renderName, x + 2, y + 2.25f,
                    ColorProvider.setAlpha(-1, (160 + checkAnimation.getValue() * 95) * alpha), 7);

            DrawUtil.drawRound(x + 71.5f, y, 19, 9, 2,
                    ColorProvider.brighter(ColorProvider.setAlpha(color1, alpha * 255), (float) (0.5f + checkAnimation.getValue() * 0.5f)),
                    ColorProvider.brighter(ColorProvider.setAlpha(color1, alpha * 255), (float) (0.5f + checkAnimation.getValue() * 0.5f)),
                    ColorProvider.brighter(ColorProvider.setAlpha(color2, alpha * 255), (float) (0.5f + checkAnimation.getValue() * 0.5f)),
                    ColorProvider.brighter(ColorProvider.setAlpha(color2, alpha * 255), (float) (0.5f + checkAnimation.getValue() * 0.5f)));
        }
    }

    private int saturateColor(int color) {
        int alpha = (color >> 24) & 0xFF; // Теперь сохраняем альфа-канал!
        float[] hsb = Color.RGBtoHSB(
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF,
                null
        );
        hsb[1] = Math.min(1.0f, hsb[1] * 1.5f);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);

        return (rgb & 0x00FFFFFF) | (alpha << 24); // Возвращаем с прозрачностью
    }
}