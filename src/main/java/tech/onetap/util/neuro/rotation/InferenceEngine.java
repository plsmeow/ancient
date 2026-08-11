package tech.onetap.util.neuro.rotation;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Долгоживущий движок inference на ONNX Runtime.
 *
 * Сессия создаётся только при загрузке модели, никогда в тике.
 * Вход пишется в переиспользуемый direct FloatBuffer, поэтому per-tick
 * аллокаций нет — это требование §29 (inference < 1 мс).
 *
 * НЕ потокобезопасен: предназначен для использования только с игрового потока.
 */
public class InferenceEngine implements Closeable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final int seqLen;
    private final int featureCount;
    private final String inputName;

    /** Переиспользуемый direct-буфер входа. */
    private final FloatBuffer inputBuffer;
    private final long[] inputShape;

    /** Переиспользуемая map для run(). */
    private final Map<String, OnnxTensor> inputMap = new HashMap<>(2);

    public InferenceEngine(Path modelPath, int seqLen, int featureCount) throws IOException, OrtException {
        if (!Files.exists(modelPath)) {
            throw new IOException("Файл модели не найден: " + modelPath);
        }

        this.seqLen = seqLen;
        this.featureCount = featureCount;
        this.inputShape = new long[]{1, seqLen, featureCount};

        this.inputBuffer = ByteBuffer
                .allocateDirect(seqLen * featureCount * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        this.env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        // Модель крошечная — один поток быстрее, чем накладные расходы на пул.
        opts.setIntraOpNumThreads(1);
        opts.setInterOpNumThreads(1);

        this.session = env.createSession(modelPath.toString(), opts);

        if (session.getInputNames().isEmpty()) {
            throw new IOException("У модели нет входов");
        }
        this.inputName = session.getInputNames().iterator().next();
    }

    /**
     * Inference по уже нормализованному плоскому входу (seqLen * featureCount).
     * @return [deltaYaw, deltaPitch]
     */
    public float[] predict(float[] normalizedInput) throws OrtException {
        int expected = seqLen * featureCount;
        if (normalizedInput.length != expected) {
            throw new IllegalArgumentException(
                    "Размер входа " + normalizedInput.length + ", ожидается " + expected
            );
        }

        inputBuffer.clear();
        inputBuffer.put(normalizedInput);
        inputBuffer.flip();

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)) {
            inputMap.clear();
            inputMap.put(inputName, inputTensor);

            try (OrtSession.Result result = session.run(inputMap)) {
                Object raw = result.get(0).getValue();
                if (!(raw instanceof float[][] output) || output.length == 0) {
                    throw new OrtException("Неожиданная форма выхода модели");
                }
                return output[0];
            }
        }
    }

    public int getSeqLen() {
        return seqLen;
    }

    public int getFeatureCount() {
        return featureCount;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            ;
        }
        // OrtEnvironment — синглтон процесса, его закрывать нельзя.
    }
}
