package tech.onetap.util.render;

import java.util.Locale;

/**
 * Delta-styled theme palette ported from DeltaClient (aethereal.config.ThemeInfo),
 * resolving named colors for the dark/light ThemeType like the original
 * ThemeProcessor.a(ThemeInfo) calls.
 */
public enum DeltaThemeInfo {
    PRIMARY(133, 156, 255, 75, 240, 242, 245, 75),
    BACKGROUND_HUD(11, 11, 22, 170, 240, 242, 245, 170),
    BACKGROUND_GUI(8, 8, 8, 255, 253, 254, 255, 255),
    OUTLINE_SMALL(255, 255, 255, 5, 17, 18, 22, 5),
    OUTLINE_MEDIUM(255, 255, 255, 10, 17, 18, 22, 5),
    TEXT(255, 255, 255, 255, 17, 18, 22, 255),
    TEXT_DISABLED(67, 70, 81, 255, 160, 174, 192, 255);

    private final int darkColor;
    private final int lightColor;

    DeltaThemeInfo(int dr, int dg, int db, int da, int lr, int lg, int lb, int la) {
        this.darkColor = ColorUtil.convertToARGB(dr, dg, db, da);
        this.lightColor = ColorUtil.convertToARGB(lr, lg, lb, la);
    }

    /**
     * Resolves this theme color for the current Delta GUI light/dark mode
     * (dark by default, like DeltaClient's ThemeType.DARK).
     */
    public int resolve() {
        return DeltaThemeType.current() == DeltaThemeType.LIGHT ? this.lightColor : this.darkColor;
    }

    public int dark() {
        return this.darkColor;
    }

    public int light() {
        return this.lightColor;
    }

    public float alphaFloat() {
        int color = resolve();
        return ((color >> 24) & 255) / 255.0f;
    }

    public enum DeltaThemeType {
        DARK,
        LIGHT;

        private static DeltaThemeType current = DARK;

        public static DeltaThemeType current() {
            return current;
        }

        public static void setCurrent(DeltaThemeType type) {
            current = type == null ? DARK : type;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
