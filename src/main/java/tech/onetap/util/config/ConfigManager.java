package tech.onetap.util.config;

import com.google.gson.*;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.settings.*;
import tech.onetap.module.settings.impl.Theme;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FOLDER = Paths.get(".options/configs");

    public static void save(String name) {
        JsonObject root = new JsonObject();

        for (Module module : Onetap.getInstance().getModuleStorage().getModules()) {
            JsonObject moduleObject = new JsonObject();
            moduleObject.addProperty("enabled", module.isEnabled());
            moduleObject.addProperty("keybind", module.getKey());

            JsonObject settingsObject = new JsonObject();
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BooleanSetting s) {
                    settingsObject.addProperty(setting.getName(), s.getValue());
                } else if (setting instanceof BindSetting s) {
                    settingsObject.addProperty(setting.getName(), s.getValue());
                } else if (setting instanceof ModeSetting s) {
                    settingsObject.addProperty(setting.getName(), s.getValue());
                } else if (setting instanceof SliderSetting s) {
                    settingsObject.addProperty(setting.getName(), s.getValue());
                } else if (setting instanceof ThemeSetting s) {
                    settingsObject.addProperty(setting.getName(), s.getValue().name);
                } else if (setting instanceof ModeListSetting s) {
                    JsonArray enabledModes = new JsonArray();
                    for (String name2 : s.getEnabledModules()) {
                        enabledModes.add(name2);
                    }
                    settingsObject.add(setting.getName(), enabledModes);
                }
            }

            moduleObject.add("settings", settingsObject);

            JsonObject bindsObject = new JsonObject();
            for (Setting setting : module.getSettings()) {
                if (!setting.isBound()) continue;
                JsonObject bind = new JsonObject();
                bind.addProperty("key", setting.getKey());
                if (setting.getBindValue() != null) {
                    bind.addProperty("value", setting.getBindValue());
                }
                bindsObject.add(setting.getName(), bind);
            }
            moduleObject.add("binds", bindsObject);

            root.add(module.getName(), moduleObject);
        }

        try {
            Files.createDirectories(CONFIG_FOLDER);
            Path configFile = CONFIG_FOLDER.resolve(name + ".json");
            Files.write(configFile, gson.toJson(root).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load(String name) {
        Path configFile = CONFIG_FOLDER.resolve(name + ".json");
        if (!Files.exists(configFile)) return;

        try (Reader reader = Files.newBufferedReader(configFile)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);

            for (Module module : Onetap.getInstance().getModuleStorage().getModules()) {
                if (!root.has(module.getName())) continue;
                JsonObject moduleObject = root.getAsJsonObject(module.getName());

                if (moduleObject.has("enabled")) {
                    boolean enabled = moduleObject.get("enabled").getAsBoolean();
                    module.setEnabled(enabled);
                }
                if (moduleObject.has("keybind")) {
                    int keybind = moduleObject.get("keybind").getAsInt();
                    module.setKey(keybind);
                }
                if (moduleObject.has("settings")) {
                    JsonObject settingsObject = moduleObject.getAsJsonObject("settings");
                    for (Setting setting : module.getSettings()) {
                        if (!settingsObject.has(setting.getName())) continue;

                        JsonElement element = settingsObject.get(setting.getName());
                        if (setting instanceof BooleanSetting s) {
                            s.setValue(element.getAsBoolean());
                        } else if (setting instanceof BindSetting s) {
                            s.setValue(element.getAsInt());
                        } else if (setting instanceof ModeSetting s) {
                            s.setValue(element.getAsString());
                        } else if (setting instanceof SliderSetting s) {
                            s.setValue(element.getAsDouble());
                        } else if (setting instanceof ThemeSetting s) {
                            String themeName = element.getAsString();
                            for (Theme theme : s.getThemes()) {
                                if (theme.name.equals(themeName)) {
                                    s.setValue(theme);
                                    break;
                                }
                            }
                        } else if (setting instanceof ModeListSetting s && element.isJsonArray()) {
                            JsonArray array = element.getAsJsonArray();
                            List<String> enabled = new ArrayList<>();
                            for (JsonElement e : array) {
                                enabled.add(e.getAsString());
                            }
                            for (BooleanSetting subSetting : s.getSettings()) {
                                subSetting.setValue(enabled.contains(subSetting.getName()));
                            }
                        }
                    }
                }
                if (moduleObject.has("binds")) {
                    JsonObject bindsObject = moduleObject.getAsJsonObject("binds");
                    for (Setting setting : module.getSettings()) {
                        if (!bindsObject.has(setting.getName())) continue;
                        JsonObject bind = bindsObject.getAsJsonObject(setting.getName());
                        if (bind.has("key")) setting.setKey(bind.get("key").getAsInt());
                        if (bind.has("value")) setting.setBindValue(bind.get("value").getAsString());
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getConfigs() {
        List<String> configs = new ArrayList<>();
        try {
            if (Files.exists(CONFIG_FOLDER)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(CONFIG_FOLDER, "*.json")) {
                    for (Path path : stream) {
                        String fileName = path.getFileName().toString();
                        if (fileName.endsWith(".json")) {
                            configs.add(fileName.substring(0, fileName.length() - 5));
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return configs;
    }
}