package tech.onetap.util.target;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import lombok.Getter;
import tech.onetap.util.QuickLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TargetRepository implements QuickLogger {

    private static final File file = new File(".options/targets.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Getter
    private static final List<String> targets = new ArrayList<>();

    public static void addTarget(String name) {
        if (!isTarget(name)) {
            targets.add(name);
        }
    }

    public static void removeTarget(String name) {
        targets.removeIf(t -> t.equalsIgnoreCase(name));
    }

    public static boolean isTarget(String name) {
        return targets.stream().anyMatch(t -> t.equalsIgnoreCase(name));
    }

    public static void clear() {
        targets.clear();
    }

    public static void save() {
        try {
            file.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(targets, writer);
            }
        } catch (IOException e) {
        }
    }

    public static void load() {
        if (!file.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonArray()) return;

            JsonArray array = element.getAsJsonArray();
            targets.clear();
            for (JsonElement el : array) {
                if (el.isJsonPrimitive()) {
                    targets.add(el.getAsString());
                }
            }
        } catch (IOException e) {
        }
    }
}
