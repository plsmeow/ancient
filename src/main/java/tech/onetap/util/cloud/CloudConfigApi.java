package tech.onetap.util.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.auth.HWIDUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class CloudConfigApi implements IMinecraft {

    private static final String BASE_URL = "https://ubbmhndbahbkbowmghfb.supabase.co";
    private static final String API_KEY = "sb_publishable_6QS1MXmoMmzioYeOZ1UQeg_dMy5_18Q";
    private static final String TABLE = "cloud_configs";
    private static final int MAX_RETRIES = 5;
    private static final int MAX_JSON_BYTES = 1_000_000;
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9 _\\-.]{1,64}$");

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

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String strOrEmpty(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static JsonObject parseData(String jsonData) {
        try {
            JsonElement el = JsonParser.parseString(jsonData);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String validateJson(String jsonData) {
        if (jsonData == null || parseData(jsonData) == null) {
            return "Конфиг повреждён: файл не является JSON-объектом";
        }
        if (jsonData.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            return "Конфиг слишком большой (лимит ~1 МБ)";
        }
        return null;
    }

    public static String sanitizeConfigName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..")) return null;
        return SAFE_NAME.matcher(trimmed).matches() ? trimmed : null;
    }

    public static void upload(String name, String jsonData, Consumer<UploadResult> onSuccess, Consumer<String> onError) {
        String safeName = sanitizeConfigName(name);
        if (safeName == null) {
            runMain(() -> onError.accept(
                    "Имя конфига невалидное. Допустимы A-Z, a-z, 0-9, пробел, _, - и точка (до 64 символов)"));
            return;
        }
        String jsonError = validateJson(jsonData);
        if (jsonError != null) {
            runMain(() -> onError.accept(jsonError));
            return;
        }
        tryUploadWithRetry(safeName, jsonData, 0, new HashSet<>(), onSuccess, onError);
    }

    private static void tryUploadWithRetry(String name, String jsonData, int attempt, Set<String> attemptedCodes,
                                           Consumer<UploadResult> onSuccess, Consumer<String> onError) {
        String code;
        do {
            code = CodeGenerator.randomCode();
        } while (attemptedCodes.contains(code));
        attemptedCodes.add(code);

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
                            runMain(() -> onError.accept(parseError(text)));
                            return;
                        }
                        JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                        if (arr.isEmpty()) {
                            if (attempt + 1 < MAX_RETRIES) {
                                tryUploadWithRetry(name, jsonData, attempt + 1, attemptedCodes, onSuccess, onError);
                            } else {
                                runMain(() -> onError.accept("Коллизия кода, попробуй ещё раз"));
                            }
                            return;
                        }
                        JsonObject row = arr.get(0).getAsJsonObject();
                        String savedCode = row.get("code").getAsString();
                        String savedName = row.get("name").getAsString();
                        runMain(() -> onSuccess.accept(new UploadResult(savedCode, savedName)));
                    } catch (Exception e) {
                        runMain(() -> onError.accept("Ошибка ответа сервера"));
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    ;
                    runMain(() -> onError.accept("Ошибка сети"));
                    return null;
                });
    }

    public static void updateByCode(String code, String jsonData, Runnable onOk, Consumer<Result> onResult) {
        String jsonError = validateJson(jsonData);
        if (jsonError != null) {
            runMain(() -> onResult.accept(Result.ERROR));
            return;
        }

        JsonObject body = new JsonObject();
        body.add("data", JsonParser.parseString(jsonData).getAsJsonObject());
        body.addProperty("updated_at", Instant.now().toString());

        HttpRequest request = request("/rest/v1/" + TABLE
                + "?code=eq." + encode(code) + "&hwid=eq." + encode(HWID))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Prefer", "return=representation")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> handleSingleRow(text, onOk, onResult), CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    ;
                    runMain(() -> onResult.accept(Result.ERROR));
                    return null;
                });
    }

    public static void updateByName(String name, String jsonData, Runnable onOk, Consumer<Result> onResult) {
        String jsonError = validateJson(jsonData);
        if (jsonError != null) {
            runMain(() -> onResult.accept(Result.ERROR));
            return;
        }

        JsonObject body = new JsonObject();
        body.add("data", JsonParser.parseString(jsonData).getAsJsonObject());
        body.addProperty("updated_at", Instant.now().toString());

        HttpRequest request = request("/rest/v1/" + TABLE
                + "?name=eq." + encode(name) + "&hwid=eq." + encode(HWID))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Prefer", "return=representation")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> handleSingleRow(text, onOk, onResult), CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    ;
                    runMain(() -> onResult.accept(Result.ERROR));
                    return null;
                });
    }

    private static void handleSingleRow(String text, Runnable onOk, Consumer<Result> onResult) {
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
    }

    public static void fetchByCode(String code, Consumer<FetchResult> onSuccess, Consumer<Result> onResult) {
        HttpRequest request = request("/rest/v1/" + TABLE
                + "?code=eq." + encode(code) + "&select=name,data&limit=1")
                .GET()
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
                            return;
                        }
                        JsonObject row = arr.get(0).getAsJsonObject();
                        String name = row.get("name").getAsString();
                        String data = row.get("data").toString();
                        runMain(() -> onSuccess.accept(new FetchResult(name, data)));
                    } catch (Exception e) {
                        runMain(() -> onResult.accept(Result.ERROR));
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    ;
                    runMain(() -> onResult.accept(Result.ERROR));
                    return null;
                });
    }

    public static void listByHwid(Consumer<List<CloudConfigEntry>> onSuccess, Consumer<String> onError) {
        HttpRequest request = request("/rest/v1/" + TABLE
                + "?hwid=eq." + encode(HWID) + "&select=code,name,created_at,updated_at&order=created_at.desc")
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
                                        strOrEmpty(o, "created_at"),
                                        strOrEmpty(o, "updated_at")
                                ));
                            }
                        }
                        List<CloudConfigEntry> finalList = list;
                        runMain(() -> onSuccess.accept(finalList));
                    } catch (Exception e) {
                        runMain(() -> onError.accept("Ошибка при разборе списка конфигов"));
                    }
                }, CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    ;
                    runMain(() -> onError.accept("Ошибка сети при получении списка"));
                    return null;
                });
    }

    public static void deleteByCode(String code, Runnable onOk, Consumer<Result> onResult) {
        HttpRequest request = request("/rest/v1/" + TABLE
                + "?code=eq." + encode(code) + "&hwid=eq." + encode(HWID))
                .DELETE()
                .header("Prefer", "return=representation")
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, CLOUD_EXECUTOR)
                .thenAcceptAsync(text -> handleSingleRow(text, onOk, onResult), CLOUD_EXECUTOR)
                .exceptionally(e -> {
                    ;
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
}
