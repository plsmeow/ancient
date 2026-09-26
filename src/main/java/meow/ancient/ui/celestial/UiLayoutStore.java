package meow.ancient.ui.celestial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class UiLayoutStore {
    private static final String LEGACY_IMPORTS = "_legacyImports";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;

    public UiLayoutStore(Path configDirectory) {
        this.file = Objects.requireNonNull(configDirectory, "configDirectory")
                .toAbsolutePath().normalize().resolve("celestial-layout.json");
    }

    public synchronized Map<String, Position> loadSection(String section) {
        JsonObject root = readRoot();
        return decodeSection(root, section);
    }

    public synchronized Map<String, Position> loadSectionMigratingLegacy(
            String section,
            String legacyFileName,
            Map<String, String> legacyNames
    ) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(legacyFileName, "legacyFileName");
        Objects.requireNonNull(legacyNames, "legacyNames");

        JsonObject root = readRoot();
        Map<String, Position> result = new LinkedHashMap<>(decodeSection(root, section));
        LegacyFile legacy = newestLegacyFile(legacyFileName);
        if (legacy == null || legacy.modifiedAt <= importedTimestamp(root, section)) {
            return result;
        }

        Map<String, Position> legacyPositions = readLegacyPositions(legacy.path);
        legacyNames.forEach((legacyName, modernName) -> {
            Position position = legacyPositions.get(legacyName);
            if (position != null) result.put(modernName, position);
        });

        root.addProperty("schemaVersion", 1);
        root.add(section, encodeSection(result));
        JsonObject imports = root.has(LEGACY_IMPORTS)
                && root.get(LEGACY_IMPORTS).isJsonObject()
                ? root.getAsJsonObject(LEGACY_IMPORTS) : new JsonObject();
        imports.addProperty(section, legacy.modifiedAt);
        root.add(LEGACY_IMPORTS, imports);
        try {
            writeRoot(root);
        } catch (IOException ignored) {
        }
        return result;
    }

    private static Map<String, Position> decodeSection(JsonObject root, String section) {
        JsonElement rawSection = root.get(section);
        if (rawSection == null || !rawSection.isJsonObject()) return Map.of();

        Map<String, Position> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : rawSection.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject value = entry.getValue().getAsJsonObject();
            try {
                if (value.has("x") && value.has("y")) {
                    result.put(entry.getKey(), new Position(
                            value.get("x").getAsInt(), value.get("y").getAsInt()));
                }
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }

    public synchronized void saveSection(String section, Map<String, Position> positions) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("schemaVersion", 1);
        root.add(section, encodeSection(positions));
        writeRoot(root);
    }

    private static JsonObject encodeSection(Map<String, Position> positions) {
        JsonObject encoded = new JsonObject();
        positions.forEach((name, position) -> {
            JsonObject value = new JsonObject();
            value.addProperty("x", position.x());
            value.addProperty("y", position.y());
            encoded.add(name, value);
        });
        return encoded;
    }

    private void writeRoot(JsonObject root) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long importedTimestamp(JsonObject root, String section) {
        try {
            JsonElement imports = root.get(LEGACY_IMPORTS);
            if (imports != null && imports.isJsonObject()) {
                JsonElement timestamp = imports.getAsJsonObject().get(section);
                if (timestamp != null) return timestamp.getAsLong();
            }
        } catch (RuntimeException ignored) {
        }
        return Long.MIN_VALUE;
    }

    private static LegacyFile newestLegacyFile(String fileName) {
        Path gameDirectory = FabricLoader.getInstance().getGameDir()
                .toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(gameDirectory.resolve(fileName));
        candidates.add(gameDirectory.resolve("Celestial").resolve(fileName));
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Path parent = gameDirectory.getParent();
            if (parent != null) {
                candidates.add(parent.resolve(fileName));
                candidates.add(parent.resolve("Celestial").resolve(fileName));
            }
        }
        return candidates.stream()
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return new LegacyFile(path, Files.getLastModifiedTime(path).toMillis());
                    } catch (IOException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .max(Comparator.comparingLong(LegacyFile::modifiedAt))
                .orElse(null);
    }

    private static Map<String, Position> readLegacyPositions(Path file) {
        Map<String, Position> positions = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":", 3);
                if (parts.length != 3) continue;
                try {
                    positions.put(parts[0], new Position(
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return positions;
    }

    private JsonObject readRoot() {
        if (!Files.isRegularFile(file)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (IOException | RuntimeException ignored) {
            return new JsonObject();
        }
    }

    public record Position(int x, int y) {
    }

    private record LegacyFile(Path path, long modifiedAt) {
    }
}
