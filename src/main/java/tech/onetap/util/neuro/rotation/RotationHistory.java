package tech.onetap.util.neuro.rotation;

import java.util.Arrays;

/**
 * Ring buffer для истории поворотов.
 * Хранит SEQ_LEN последних строк фич.
 */
public class RotationHistory {

    private final float[][] buffer;
    private final int seqLen;
    private final int featureCount;
    private int head = 0;
    private int size = 0;

    public RotationHistory(int seqLen, int featureCount) {
        this.seqLen = seqLen;
        this.featureCount = featureCount;
        this.buffer = new float[seqLen][featureCount];
    }

    /**
     * Добавляет строку фич в историю.
     */
    public void push(float[] features) {
        if (features.length != featureCount) {
            throw new IllegalArgumentException("Feature count mismatch: expected " + featureCount + ", got " + features.length);
        }
        System.arraycopy(features, 0, buffer[head], 0, featureCount);
        head = (head + 1) % seqLen;
        if (size < seqLen) {
            size++;
        }
    }

    /**
     * Заполняет dest плоским массивом: [t-7, t-6, ..., t-1, t].
     * Если история неполная, старые слоты забиваются нулями.
     */
    public void fillFlat(float[] dest) {
        if (dest.length != seqLen * featureCount) {
            throw new IllegalArgumentException("Dest size mismatch: expected " + (seqLen * featureCount) + ", got " + dest.length);
        }

        int writeIdx = 0;

        // Padding для недостающих шагов
        int padding = seqLen - size;
        for (int i = 0; i < padding; i++) {
            Arrays.fill(dest, writeIdx, writeIdx + featureCount, 0.0f);
            writeIdx += featureCount;
        }

        // Заполняем от самого старого к самому новому
        int readIdx = (head + seqLen - size) % seqLen;
        for (int i = 0; i < size; i++) {
            System.arraycopy(buffer[readIdx], 0, dest, writeIdx, featureCount);
            readIdx = (readIdx + 1) % seqLen;
            writeIdx += featureCount;
        }
    }

    /**
     * Очищает историю.
     */
    public void reset() {
        head = 0;
        size = 0;
        for (float[] row : buffer) {
            Arrays.fill(row, 0.0f);
        }
    }

    /**
     * Проверяет, набрана ли полная история.
     */
    public boolean isWarm() {
        return size == seqLen;
    }

    public int getSize() {
        return size;
    }
}
