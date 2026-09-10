package tech.onetap.module.settings.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    public record ThemePreset(String name, int color1, int color2) {
    }

    public static final List<ThemePreset> DEFAULT_THEMES = List.of(
            new ThemePreset("Crimson", new Color(220, 20, 60, 255).getRGB(), new Color(30, 0, 0, 255).getRGB()),
            new ThemePreset("Cyber Blue", new Color(0, 200, 255, 255).getRGB(), new Color(10, 10, 20, 255).getRGB()),
            new ThemePreset("Violet Void", new Color(180, 0, 255, 255).getRGB(), new Color(30, 0, 40, 255).getRGB()),
            new ThemePreset("Abyss Blue", new Color(0, 102, 204, 255).getRGB(), new Color(10, 10, 30, 255).getRGB()),
            new ThemePreset("Obsidian Glow", new Color(200, 200, 255, 255).getRGB(), new Color(10, 10, 15, 230).getRGB()),
            new ThemePreset("Quantum Shift", new Color(100, 255, 230, 255).getRGB(), new Color(0, 20, 25, 220).getRGB()),
            new ThemePreset("White-Black", new Color(255, 255, 255, 255).getRGB(), new Color(0, 0, 0, 255).getRGB()),
            new ThemePreset("Serenity", new Color(137, 159, 255, 255).getRGB(), new Color(20, 20, 35, 255).getRGB())
    );

    private static final File THEME_DIR = new File(".options");
    private static final File THEME_FILE = new File(THEME_DIR, "themes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ThemeManager instance;

    private final Theme defaultTheme;
    private final List<ThemePreset> customThemes = new ArrayList<>();
    private ThemePreset activePreset;

    private ThemeManager() {
        ThemePreset fallback = DEFAULT_THEMES.get(0);
        defaultTheme = new Theme("Default", fallback.color1(), fallback.color2());
        loadThemes();
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public Theme getCurrentTheme() {
        return defaultTheme;
    }

    public List<ThemePreset> getCustomThemes() {
        return customThemes;
    }

    public ThemePreset getActivePreset() {
        return activePreset;
    }

    private void loadThemes() {
        if (!THEME_FILE.exists()) return;

        try {
            JsonObject json = JsonParser.parseString(Files.readString(THEME_FILE.toPath())).getAsJsonObject();

            if (json.has("customThemes")) {
                customThemes.clear();
                for (JsonElement el : json.getAsJsonArray("customThemes")) {
                    JsonObject obj = el.getAsJsonObject();
                    customThemes.add(new ThemePreset(
                            obj.get("name").getAsString(),
                            obj.get("color1").getAsInt(),
                            obj.get("color2").getAsInt()));
                }
            }

            if (json.has("activeTheme")) {
                activePreset = findPreset(json.get("activeTheme").getAsString());
                if (activePreset != null) {
                    defaultTheme.setColorsInstant(activePreset.color1(), activePreset.color2());
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void saveThemes(List<ThemePreset> customThemes, String activeThemeName) {
        this.customThemes.clear();
        this.customThemes.addAll(customThemes);
        this.activePreset = findPreset(activeThemeName);

        try {
            if (!THEME_DIR.exists()) THEME_DIR.mkdirs();

            JsonObject json = new JsonObject();
            json.addProperty("activeTheme", activeThemeName);

            JsonArray customThemesArray = new JsonArray();
            for (ThemePreset theme : customThemes) {
                JsonObject themeObj = new JsonObject();
                themeObj.addProperty("name", theme.name());
                themeObj.addProperty("color1", theme.color1());
                themeObj.addProperty("color2", theme.color2());
                customThemesArray.add(themeObj);
            }
            json.add("customThemes", customThemesArray);

            Files.writeString(THEME_FILE.toPath(), GSON.toJson(json));
        } catch (IOException ignored) {
        }
    }

    private ThemePreset findPreset(String name) {
        for (ThemePreset preset : DEFAULT_THEMES) {
            if (preset.name().equals(name)) return preset;
        }
        for (ThemePreset preset : customThemes) {
            if (preset.name().equals(name)) return preset;
        }
        return null;
    }
}
