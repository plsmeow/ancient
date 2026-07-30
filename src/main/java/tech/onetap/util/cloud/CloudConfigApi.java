package tech.onetap.util.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.auth.HWIDUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class CloudConfigApi implements IMinecraft {

    private static final String BASE_URL = "https://ubbmhndbahbkbowmghfb.supabase.co";
    private static final String API_KEY = "sb_publishable_6QS1MXmoMmzioYeOZ1UQeg_dMy5_18Q";
    private static final String TABLE = "cloud_configs";
    private static final int MAX_RETRIES = 5;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService CLOUD_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "CloudCfg-Thread");
                t.setDaemon(true);
                return t;
            });

    private static final String HWID = HWIDUtil.generateHWID();

    public record UploadResult(String code, String name) {
    }

    public record FetchResult(String name, String jsonData) {
    }

    public enum Result {
        OK, NOT_FOUND, ERROR
    }

    private CloudConfigApi() {
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .header("apikey", API_KEY)
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json");
    }

    private static void runMain(Runnable r) {
        if (mc != null) {
            mc.execute(r);
        } else {
            r.run();
        }
    }

    public static void upload(String name, String jsonData, Consumer<UploadResult> onSuccess, Consumer<String> onError) {
        tryUploadWithRetry(name, jsonData, 0, onSuccess, onError);
    }

    private static void tryUploadWithRetry(String name, String jsonData, int attempt,
                                           Consumer<UploadResult> onSuccess, Consumer<String> onError) {
        String code = CodeGenerator.randomCode();

        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        body.addProperty("name", name);
        body.addProperty("hwid", HWID);
        body.add("data", JsonParser.parseString(jsonData).getAsJsonObject());

        HttpRequest request = request("/rest/v1/" + TABLE)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Prefer", "return=representation,resolution=ignore-duplicates")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> {
                    try {
                        if (!text.startsWith("[")) {
                            onError.accept(parseError(text));
                            return;
                        }
                        JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                        if (arr.isEmpty()) {
                            if (attempt + 1 < MAX_RETRIES) {
                                tryUploadWithRetry(name, jsonData, attempt + 1, onSuccess, onError);
                            } else {
                                onError.accept("Коллизия кода, попробуй ещё раз");
                            }
                            return;
                        }
                        JsonObject row = arr.get(0).getAsJsonObject();
                        String savedCode = row.get("code").getAsString();
                        String savedName = row.get("name").getAsString();
                        runMain(() -> onSuccess.accept(new UploadResult(savedCode, savedName)));
                    } catch (Exception e) {
                        onError.accept("Ошибка ответа сервера");
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    e.printStackTrace();
                    runMain(() -> onError.accept("Ошибка сети"));
                    return null;
                });
    }

    public static void updateByCode(String code, String jsonData, Runnable onOk, Consumer<Result> onResult) {
        String encoded = code;
        JsonObject body = new JsonObject();
        body.add("data", JsonParser.parseString(jsonData).getAsJsonObject());
        body.addProperty("updated_at", java.time.Instant.now().toString());

        HttpRequest request = request("/rest/v1/" + TABLE
                + "?code=eq." + encoded + "&hwid=eq." + HWID)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Prefer", "return=representation")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> {
                    try {
                        if (!text.startsWith("[")) {
                            runMain(() -> onResult.accept(Result.ERROR));
                            return;
                        }
                        JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                        if (arr.isEmpty()) {
                            runMain(() -> onResult.accept(Result.NOT_FOUND));
                        } else {
                            runMain(() -> {
                                onOk.run();
                                onResult.accept(Result.OK);
                            });
                        }
                    } catch (Exception e) {
                        runMain(() -> onResult.accept(Result.ERROR));
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    e.printStackTrace();
                    runMain(() -> onResult.accept(Result.ERROR));
                    return null;
                });
    }

    public static void updateByName(String name, String jsonData, Runnable onOk, Consumer<Result> onResult) {
        JsonObject body = new JsonObject();
        body.add("data", JsonParser.parseString(jsonData).getAsJsonObject());
        body.addProperty("updated_at", java.time.Instant.now().toString());

        HttpRequest request = request("/rest/v1/" + TABLE
                + "?name=eq." + urlEncode(name) + "&hwid=eq." + HWID)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Prefer", "return=representation")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> {
                    try {
                        if (!text.startsWith("[")) {
                            runMain(() -> onResult.accept(Result.ERROR));
                            return;
                        }
                        JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                        if (arr.isEmpty()) {
                            runMain(() -> onResult.accept(Result.NOT_FOUND));
                        } else {
                            runMain(() -> {
                                onOk.run();
                                onResult.accept(Result.OK);
                            });
                        }
                    } catch (Exception e) {
                        runMain(() -> onResult.accept(Result.ERROR));
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    e.printStackTrace();
                    runMain(() -> onResult.accept(Result.ERROR));
                    return null;
                });
    }

    public static void fetchByCode(String code, Consumer<FetchResult> onSuccess, Runnable onNotFound) {
        HttpRequest request = request("/rest/v1/" + TABLE
                + "?code=eq." + code + "&select=name,data&limit=1")
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> {
                    try {
                        if (!text.startsWith("[")) {
                            runMain(onNotFound::run);
                            return;
                        }
                        JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                        if (arr.isEmpty()) {
                            runMain(onNotFound::run);
                            return;
                        }
                        JsonObject row = arr.get(0).getAsJsonObject();
                        String name = row.get("name").getAsString();
                        String data = row.get("data").toString();
                        runMain(() -> onSuccess.accept(new FetchResult(name, data)));
                    } catch (Exception e) {
                        runMain(onNotFound::run);
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    e.printStackTrace();
                    runMain(onNotFound::run);
                    return null;
                });
    }

    public static void listByHwid(Consumer<List<CloudConfigEntry>> onSuccess) {
        HttpRequest request = request("/rest/v1/" + TABLE
                + "?hwid=eq." + HWID + "&select=code,name,created_at&order=created_at.desc")
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> {
                    try {
                        List<CloudConfigEntry> list = new ArrayList<>();
                        if (text.startsWith("[")) {
                            JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                            for (JsonElement e : arr) {
                                JsonObject o = e.getAsJsonObject();
                                list.add(new CloudConfigEntry(
                                        o.get("code").getAsString(),
                                        o.get("name").getAsString(),
                                        o.has("created_at") && !o.get("created_at").isJsonNull()
                                                ? o.get("created_at").getAsString()
                                                : ""
                                ));
                            }
                        }
                        List<CloudConfigEntry> finalList = list;
                        runMain(() -> onSuccess.accept(finalList));
                    } catch (Exception e) {
                        runMain(() -> onSuccess.accept(List.of()));
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    e.printStackTrace();
                    runMain(() -> onSuccess.accept(List.of()));
                    return null;
                });
    }

    public static void deleteByCode(String code, Runnable onOk, Consumer<Result> onResult) {
        HttpRequest request = request("/rest/v1/" + TABLE
                + "?code=eq." + code + "&hwid=eq." + HWID)
                .DELETE()
                .header("Prefer", "return=representation")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> {
                    try {
                        if (!text.startsWith("[")) {
                            runMain(() -> onResult.accept(Result.ERROR));
                            return;
                        }
                        JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                        if (arr.isEmpty()) {
                            runMain(() -> onResult.accept(Result.NOT_FOUND));
                        } else {
                            runMain(() -> {
                                onOk.run();
                                onResult.accept(Result.OK);
                            });
                        }
                    } catch (Exception e) {
                        runMain(() -> onResult.accept(Result.ERROR));
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    e.printStackTrace();
                    runMain(() -> onResult.accept(Result.ERROR));
                    return null;
                });
    }

    private static String parseError(String text) {
        try {
            if (text.startsWith("{")) {
                JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                if (o.has("message")) return o.get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "Ошибка сервера";
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
