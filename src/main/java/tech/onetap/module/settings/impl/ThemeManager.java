package tech.onetap.module.settings.impl;

public class ThemeManager {
    private static ThemeManager instance;

    // Создаем стандартную тему по умолчанию (Название, Цвет 1, Цвет 2 в HEX формате ARGB)
    // 0xFF4A90E2 - это синий цвет, 0xFF9013FE - фиолетовый.
    // Вы можете поменять эти HEX-коды на любые другие свои любимые цвета!
    private final Theme defaultTheme = new Theme("Default", 0xFF4A90E2, 0xFF9013FE);

    private ThemeManager() {
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public Theme getCurrentTheme() {
        return defaultTheme; // Просто всегда возвращаем эту дефолтную тему
    }

}