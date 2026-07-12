package tech.onetap.util.neuro.rotation;

import ai.djl.training.Trainer;
import ai.djl.training.listener.TrainingListenerAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

public class OverlayTrainingListener extends TrainingListenerAdapter {

    private final int maxEpochs;
    private int currentEpoch = 0;
    private int lastReportedBatch = -1;

    public OverlayTrainingListener(int maxEpochs) {
        this.maxEpochs = maxEpochs;
        this.currentEpoch = 0;
        this.lastReportedBatch = -1;
    }

    @Override
    public void onEpoch(Trainer trainer) {
        currentEpoch++;
        lastReportedBatch = -1;
        super.onEpoch(trainer);
    }

    @Override
    public void onTrainingBatch(Trainer trainer, BatchData batchData) {
        var batch = batchData.getBatch();
        int progress = (int) ((float) batch.getProgress() / batch.getProgressTotal() * 100);

        if (progress >= lastReportedBatch + 25 || progress == 100) {
            lastReportedBatch = (progress / 25) * 25;

            int barLen = 25;
            int filled = (int) (progress / 100.0f * barLen);

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options == null) {
                super.onTrainingBatch(trainer, batchData);
                return;
            }

            MutableText message = Text.literal("")
                    .append(Text.literal("Training Epoch ").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(Text.literal(currentEpoch + "/" + maxEpochs).styled(s -> s.withColor(Formatting.GREEN)))
                    .append(Text.literal(" - Batch ").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(Text.literal(progress + "%").styled(s -> s.withColor(Formatting.GREEN)))
                    .append(Text.literal("\n"));

            StringBuilder barStr = new StringBuilder("[");
            for (int i = 0; i < barLen; i++) {
                barStr.append(i < filled ? "█" : "░");
            }
            barStr.append("]");

            message.append(Text.literal(barStr.toString()).styled(s -> s.withColor(filled == barLen ? Formatting.GREEN : Formatting.DARK_GRAY)));

            mc.execute(() -> mc.inGameHud.setOverlayMessage(message, false));
        }

        super.onTrainingBatch(trainer, batchData);
    }
}
