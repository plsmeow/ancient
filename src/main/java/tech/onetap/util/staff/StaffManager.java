package tech.onetap.util.staff;

import com.google.gson.*;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StaffManager {
    @Getter
    private static final Set<Staff> staffList = new HashSet<>();

    private static final Set<String> VANISH_KEYWORDS = new HashSet<>(Arrays.asList(
            "supp", "ꜱupp", "mod", "der", "adm", "wne",
            "мод", "помо", "адм", "владе", "отри",
            "таф", "taf", "curat", "курато",
            "dev", "раз", "сапп", "yt", "ютуб",
            "стажер", "сотрудник"
    ));

    private static final Map<String, Long> vanishDetections = new ConcurrentHashMap<>();
    private static final long VANISH_TIMEOUT_MS = 5000L;

    private final File file = new File(".options/staff.json");

    public static void addStaff(Staff staff) {
        staffList.add(staff);
    }

    public static void removeStaff(String staff) {
        staffList.removeIf(s -> s.name.equalsIgnoreCase(staff));
    }

    public static boolean isStaff(String name) {
        for (Staff staff : staffList) {
            if (staff.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static void clearStaff() {
        staffList.clear();
    }

    public void save() {
        JsonArray array = new JsonArray();
        for (Staff staff : staffList) {
            array.add(staff.name);
        }

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(array, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (!file.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            if (!element.isJsonArray()) return;

            JsonArray array = element.getAsJsonArray();
            staffList.clear();

            for (JsonElement el : array) {
                String name = el.getAsString();
                staffList.add(new Staff(name));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void detectVanish(String playerName) {
        vanishDetections.put(playerName.toLowerCase(Locale.ROOT), System.currentTimeMillis());
    }

    public static boolean isVanishDetectedRecently(String name) {
        Long timestamp = vanishDetections.get(name.toLowerCase(Locale.ROOT));
        if (timestamp == null) return false;
        return System.currentTimeMillis() - timestamp < VANISH_TIMEOUT_MS;
    }

    public static Set<String> getRecentlyVanished() {
        long now = System.currentTimeMillis();
        vanishDetections.entrySet().removeIf(entry -> now - entry.getValue() > VANISH_TIMEOUT_MS);
        return new HashSet<>(vanishDetections.keySet());
    }

    public static boolean containsVanishKeyword(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : VANISH_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static void checkScoreboardTeams() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        for (Team team : scoreboard.getTeams()) {
            String prefix = team.getPrefix().getString();
            if (containsVanishKeyword(prefix)) {
                for (String member : team.getPlayerList()) {
                    detectVanish(member);
                }
            }
        }
    }
}