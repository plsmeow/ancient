package tech.onetap.util.neuro.rotation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Автономная GRU+MDN нейросетевая модель ротации.
 *
 * Инференс полностью вычисляется на чистом Java без сторонних нативных библиотек
 * (onnxruntime и т.д.). Веса загружаются из JSON-файла.
 */
public final class NeuroModel {
    public static final int FEATURES = 17;
    public static final String DEFAULT_MODEL_NAME = "default";
    private static final float RHO_LIMIT = 0.95f;

    private static NeuroModel activeInstance;
    private static boolean activeLoaded;
    private static String activeModelName;

    private final float[][] wi;
    private final float[][] wh;
    private final float[][] w;
    private final float[] bi;
    private final float[] bh;
    private final float[] b;
    private final float[] error;
    private final int hidden;
    private final int mix;
    private final int freezeCut;
    private final float limitYaw;
    private final float limitPitch;

    private NeuroModel(JsonObject jsonObject) {
        this.hidden = jsonObject.get("hidden").getAsInt();
        this.mix = jsonObject.get("mix").getAsInt();
        this.freezeCut = jsonObject.has("freezeCut") ? jsonObject.get("freezeCut").getAsInt() : 12;

        JsonArray limitsArray = jsonObject.getAsJsonArray("limits");
        this.limitYaw = limitsArray.get(0).getAsFloat();
        this.limitPitch = limitsArray.get(1).getAsFloat();

        this.error = jsonObject.has("error") ? parse1DFloatArray(jsonObject.getAsJsonArray("error")) : new float[0];

        JsonObject gruObject = jsonObject.getAsJsonObject("gru");
        this.wi = parse2DFloatArray(gruObject.getAsJsonArray("wi"));
        this.wh = parse2DFloatArray(gruObject.getAsJsonArray("wh"));
        this.bi = parse1DFloatArray(gruObject.getAsJsonArray("bi"));
        this.bh = parse1DFloatArray(gruObject.getAsJsonArray("bh"));

        JsonObject outObject = jsonObject.getAsJsonObject("out");
        this.w = parse2DFloatArray(outObject.getAsJsonArray("w"));
        this.b = parse1DFloatArray(outObject.getAsJsonArray("b"));
    }

    private static float[][] parse2DFloatArray(JsonArray jsonArray) {
        float[][] result = new float[jsonArray.size()][];
        for (int i = 0; i < jsonArray.size(); ++i) {
            result[i] = parse1DFloatArray(jsonArray.get(i).getAsJsonArray());
        }
        return result;
    }

    private static float[] parse1DFloatArray(JsonArray jsonArray) {
        float[] result = new float[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); ++i) {
            result[i] = jsonArray.get(i).getAsFloat();
        }
        return result;
    }

    public static Path getNeuroDir() {
        Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
        Path onetapDir = runDir.resolve("onetap").resolve("neuro");
        if (Files.isDirectory(onetapDir)) {
            return onetapDir;
        }
        Path ancientDir = runDir.resolve(".options").resolve("neuro");
        if (Files.isDirectory(ancientDir)) {
            return ancientDir;
        }
        return onetapDir;
    }

    public static Path getDataDir() {
        return getNeuroDir().resolve("data");
    }

    public static Path getModelPath(String name) {
        return getNeuroDir().resolve(name + ".json");
    }

    public static String getActiveName() {
        if (activeModelName == null) {
            try {
                Path activeFile = getNeuroDir().resolve("active.txt");
                activeModelName = Files.isRegularFile(activeFile, new LinkOption[0])
                        ? Files.readString(activeFile).trim()
                        : DEFAULT_MODEL_NAME;
            } catch (Exception exception) {
                activeModelName = DEFAULT_MODEL_NAME;
            }
            if (activeModelName.isEmpty()) {
                activeModelName = DEFAULT_MODEL_NAME;
            }
        }
        return activeModelName;
    }

    public static List<String> listModels() {
        List<String> list = new ArrayList<>();
        list.add(DEFAULT_MODEL_NAME);
        try (Stream<Path> stream = Files.list(getNeuroDir())) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .filter(name -> !list.contains(name))
                    .sorted()
                    .forEach(list::add);
        } catch (Exception ignored) {
        }
        return list;
    }

    public static boolean hasModel(String name) {
        return DEFAULT_MODEL_NAME.equals(name) || Files.isRegularFile(getModelPath(name), new LinkOption[0]);
    }

    public static boolean setActiveModel(String name) {
        if (!hasModel(name)) {
            return false;
        }
        activeModelName = name;
        try {
            Files.createDirectories(getNeuroDir(), new FileAttribute[0]);
            Files.writeString(getNeuroDir().resolve("active.txt"), name, new OpenOption[0]);
        } catch (Exception ignored) {
        }
        resetCache();
        return true;
    }

    public static NeuroModel getActive() {
        if (!activeLoaded) {
            activeLoaded = true;
            activeInstance = load(getActiveName());
        }
        return activeInstance;
    }

    public static void resetCache() {
        activeLoaded = false;
        activeInstance = null;
    }

    public static NeuroModel load(String name) {
        try {
            JsonObject jsonObject = null;
            Path path = getModelPath(name);
            if (Files.isRegularFile(path, new LinkOption[0])) {
                jsonObject = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            } else if (DEFAULT_MODEL_NAME.equals(name)) {
                InputStream inputStream = NeuroModel.class.getClassLoader().getResourceAsStream("assets/onetap/neuro/default.json");
                if (inputStream != null) {
                    try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                        jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                }
            }
            if (jsonObject == null) return null;
            if (jsonObject.get("features").getAsInt() != FEATURES) return null;
            if (!jsonObject.has("mix")) return null;
            if (!jsonObject.has("units")) return null;
            if ("deg".equals(jsonObject.get("units").getAsString())) {
                return new NeuroModel(jsonObject);
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    public int getHidden() {
        return this.hidden;
    }

    public int getMix() {
        return this.mix;
    }

    public int getFreezeCut() {
        return this.freezeCut;
    }

    public float[] createHiddenState() {
        return new float[this.hidden];
    }

    public float[] forward(float[] input, float[] hiddenState) {
        int hDim = this.hidden;
        float[] gatesI = new float[3 * hDim];
        float[] gatesH = new float[3 * hDim];

        for (int n = 0; n < 3 * hDim; ++n) {
            float fi = this.bi[n];
            float[] wiRow = this.wi[n];
            for (int j = 0; j < wiRow.length; ++j) {
                fi += wiRow[j] * input[j];
            }
            gatesI[n] = fi;

            float fh = this.bh[n];
            float[] whRow = this.wh[n];
            for (int j = 0; j < whRow.length; ++j) {
                fh += whRow[j] * hiddenState[j];
            }
            gatesH[n] = fh;
        }

        for (int n = 0; n < hDim; ++n) {
            float r = sigmoid(gatesI[n] + gatesH[n]);
            float z = sigmoid(gatesI[hDim + n] + gatesH[hDim + n]);
            float nCand = (float) Math.tanh(gatesI[2 * hDim + n] + r * gatesH[2 * hDim + n]);
            hiddenState[n] = (1.0f - z) * nCand + z * hiddenState[n];
        }

        float[] output = new float[this.w.length];
        for (int i = 0; i < this.w.length; ++i) {
            float val = this.b[i];
            float[] wRow = this.w[i];
            for (int j = 0; j < wRow.length; ++j) {
                val += wRow[j] * hiddenState[j];
            }
            output[i] = val;
        }
        return output;
    }

    public int sampleMixture(float[] output, float randomVal) {
        float maxPi = output[1];
        for (int i = 1; i < this.mix; ++i) {
            maxPi = Math.max(maxPi, output[1 + 6 * i]);
        }
        float sumExp = 0.0f;
        for (int i = 0; i < this.mix; ++i) {
            sumExp += (float) Math.exp(output[1 + 6 * i] - maxPi);
        }
        float threshold = randomVal * sumExp;
        for (int i = 0; i < this.mix; ++i) {
            threshold -= (float) Math.exp(output[1 + 6 * i] - maxPi);
            if (threshold <= 0.0f) {
                return i;
            }
        }
        return this.mix - 1;
    }

    public float sampleAimError(float randomFloat) {
        if (this.error.length == 0) {
            return 0.0f;
        }
        float scaled = Math.max(0.0f, Math.min(1.0f, randomFloat)) * (float) (this.error.length - 1);
        int idx = (int) scaled;
        if (idx >= this.error.length - 1) {
            return this.error[this.error.length - 1];
        }
        return this.error[idx] + (this.error[idx + 1] - this.error[idx]) * (scaled - (float) idx);
    }

    public float sampleCoordinate(float mu, float logSigma, boolean isYaw, float eps, float temperature) {
        float sigma = (float) Math.exp(Math.max(-4.0f, Math.min(1.5f, logSigma)));
        float u = Math.max(-8.0f, Math.min(8.0f, mu + eps * sigma * temperature));
        float limit = isYaw ? this.limitYaw : this.limitPitch;
        return Math.max(-limit, Math.min(limit, (float) Math.sinh(u)));
    }

    public static float sigmoid(float f) {
        return 1.0f / (1.0f + (float) Math.exp(-f));
    }

    public static float tanhCorr(float f) {
        return (float) Math.tanh(f) * RHO_LIMIT;
    }
}
