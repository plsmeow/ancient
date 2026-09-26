package meow.ancient.ui.celestial;

import meow.ancient.util.config.ConfigManager;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class CelestialConfigProfiles {
    private static final Path CONFIG_FOLDER = Paths.get(".options/configs");

    public List<String> names() {
        return ConfigManager.getConfigs().stream()
                .filter(name -> !name.equalsIgnoreCase("autocfg"))
                .toList();
    }

    public void save(String name) throws IOException {
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase("autocfg")) return;
        ConfigManager.save(trimmed);
    }

    public boolean load(String name) throws IOException {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.trim();
        Path target = CONFIG_FOLDER.resolve(trimmed + ".json");
        if (!Files.exists(target)) return false;
        ConfigManager.load(trimmed);
        return true;
    }

    public boolean delete(String name) throws IOException {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.trim();
        Path target = CONFIG_FOLDER.resolve(trimmed + ".json");
        return Files.deleteIfExists(target);
    }

    public void openDirectory() throws IOException {
        Files.createDirectories(CONFIG_FOLDER);
        Util.getOperatingSystem().open(CONFIG_FOLDER.toFile());
    }

    public Path directory() {
        return CONFIG_FOLDER;
    }
}
