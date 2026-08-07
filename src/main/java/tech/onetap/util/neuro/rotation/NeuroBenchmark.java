package tech.onetap.util.neuro.rotation;

import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;

import java.util.Arrays;

/**
 * Замер времени inference (§34). Печатает P50/P95/P99.
 */
public final class NeuroBenchmark implements IMinecraft {

    private static final int WARMUP = 50;
    private static final int ITERATIONS = 500;

    private NeuroBenchmark() {
    }

    public static void run() {
        ActiveModel model = AIRotationManager.getActive();
        if (model == null) {
            ChatUtil.send("§cНет активной модели. Загрузите её: §f.ai load <model>");
            return;
        }

        // Считаем на отдельном потоке, чтобы не морозить рендер
        Thread thread = new Thread(() -> benchmark(model), "NeuroBenchmark");
        thread.setDaemon(true);
        thread.start();
    }

    private static void benchmark(ActiveModel model) {
        int seqLen = model.getMeta().getSeqLen();
        int featureCount = model.getMeta().getFeatureCount();
        float[] input = new float[seqLen * featureCount];

        // Заполняем правдоподобным шумом
        java.util.Random random = new java.util.Random(1337);
        for (int i = 0; i < input.length; i++) {
            input[i] = (float) random.nextGaussian();
        }

        ChatUtil.send("§7Прогрев...");

        try {
            for (int i = 0; i < WARMUP; i++) {
                model.getEngine().predict(input);
            }
        } catch (Throwable t) {
            ChatUtil.send("§cОшибка inference: " + t.getMessage());
            return;
        }

        long[] samples = new long[ITERATIONS];

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                long start = System.nanoTime();
                model.getEngine().predict(input);
                samples[i] = System.nanoTime() - start;
            }
        } catch (Throwable t) {
            ChatUtil.send("§cОшибка inference: " + t.getMessage());
            return;
        }

        Arrays.sort(samples);

        double p50 = samples[(int) (ITERATIONS * 0.50)] / 1_000_000.0;
        double p95 = samples[(int) (ITERATIONS * 0.95)] / 1_000_000.0;
        double p99 = samples[(int) (ITERATIONS * 0.99)] / 1_000_000.0;

        ChatUtil.send("§e§l=== Inference Benchmark ===");
        ChatUtil.send("§7Модель: §f" + model.getName() + " §7(" + model.getMeta().getArch() + ")");
        ChatUtil.send(String.format("§7P50: §f%.3f ms", p50));
        ChatUtil.send(String.format("§7P95: §f%.3f ms", p95));
        ChatUtil.send(String.format("§7P99: §f%.3f ms", p99));

        if (p95 < 1.0) {
            ChatUtil.send("§aЦель §f<1 ms §aдостигнута");
        } else {
            ChatUtil.send("§eP95 превышает 1 ms — модель тяжеловата для тика");
        }
    }
}
