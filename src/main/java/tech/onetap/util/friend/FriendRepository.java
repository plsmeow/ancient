package tech.onetap.util.friend;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import net.minecraft.entity.player.PlayerEntity;
import tech.onetap.util.QuickLogger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class FriendRepository implements QuickLogger {

    private static final File file = new File(".options/friends.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Getter
    private static final List<Friend> friends = new ArrayList<>();

    public static void addFriend(String name) {
        friends.add(new Friend(name));
    }

    public static void removeFriend(String name) {
        friends.removeIf(friend -> friend.name().equalsIgnoreCase(name));
    }

    public static boolean shouldAttack(PlayerEntity player) {
        return !isFriend(player.getNameForScoreboard());
    }

    public static boolean isFriend(String friend) {
        return friends.stream().anyMatch(f -> (f.name().equalsIgnoreCase(friend)));
    }

    public static Friend getFriend(String name) {
        return friends.stream()
                .filter(f -> f.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static void clear() {
        friends.clear();
    }

    public static void save() {
        try {
            file.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(friends, writer);
            }
        } catch (IOException e) {
        }
    }

    public static void load() {
        if (!file.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<Friend>>() {}.getType();
            List<Friend> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                friends.clear();
                friends.addAll(loaded);
            }
        } catch (IOException e) {
        }
    }
}