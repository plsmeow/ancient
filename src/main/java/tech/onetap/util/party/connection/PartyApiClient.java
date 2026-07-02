package tech.onetap.util.party.connection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import net.minecraft.util.Formatting;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.party.PartyPlayerPos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class PartyApiClient implements IMinecraft {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String BASE_URL =
            "";

    private static final ExecutorService PARTY_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Party-API-Thread");
                t.setDaemon(true);
                return t;
            });

    public static void postAsync(String path, JsonObject body, Consumer<JsonObject> callback) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, PARTY_EXECUTOR)
                .thenAcceptAsync(text -> {
                    try {
                        if (!text.startsWith("{")) return;
                        JsonObject json = JsonParser.parseString(text).getAsJsonObject();

                        mc.execute(() -> callback.accept(json));

                    } catch (Exception ignored) {}
                }, PARTY_EXECUTOR)
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });
    }

    @Getter
    private static volatile List<PartyPlayerPos> cached = List.of();

    public static void fetchPartyStateAsync() {
        if (mc.player == null) return;

        JsonObject req = new JsonObject();
        req.addProperty("player", mc.player.getNameForScoreboard());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/party/state"))
                .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                .header("Content-Type", "application/json")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    try {
                        if (response.statusCode() != 200) return;

                        String body = response.body().trim();
                        if (!body.startsWith("{")) return;

                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        JsonArray arr = json.getAsJsonArray("members");

                        List<PartyPlayerPos> list = new ArrayList<>();
                        for (JsonElement e : arr) {
                            JsonObject o = e.getAsJsonObject();
                            list.add(new PartyPlayerPos(
                                    o.get("playerId").getAsString(),
                                    o.get("x").getAsDouble(),
                                    o.get("y").getAsDouble(),
                                    o.get("z").getAsDouble()
                            ));
                        }

                        cached = list;
                    } catch (Exception ignored) {}
                });
    }

    public static void fetchInvitesAsync() {
        if (mc.player == null) return;

        JsonObject req = new JsonObject();
        req.addProperty("player", mc.player.getNameForScoreboard());

        postAsync("/party/invites", req, json -> {
            JsonArray arr = json.getAsJsonArray("invites");
            for (JsonElement e : arr) {
                String party = e.getAsString();
                ChatUtil.send(
                        Formatting.GRAY + "Вас пригласили в пати " +
                                Formatting.WHITE + party +
                                Formatting.GRAY + ", напишите " +
                                Formatting.WHITE + ".party join " + party
                );
            }
        });
    }
}