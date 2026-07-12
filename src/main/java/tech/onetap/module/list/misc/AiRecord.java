package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.neuro.rotation.AIRotationRecorder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ModuleInformation(
        moduleName = "Ai Record",
        moduleDesc = "Записывает AI датасет на слизнях для Neuro",
        moduleCategory = ModuleCategory.MISC
)
public class AiRecord extends Module {

    private static final DateTimeFormatter DATASET_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BooleanSetting autoSave = new BooleanSetting("Auto Save", true);
    private final BooleanSetting debugChat = new BooleanSetting("Debug Chat", true);
    private final SliderSetting minSamples = new SliderSetting("Min Samples", 64, 1, 1000, 1).setVisible(autoSave::getValue);

    private int lastDebugSamples;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            ChatUtil.send("§cAi Record: зайдите в мир перед запуском записи");
            setEnabled(false);
            return;
        }

        super.onEnable();
        lastDebugSamples = 0;
        AIRotationRecorder.startRecording(AIRotationRecorder.Mode.SLIMES);
        ChatUtil.send("§aAi Record: тренировка на слизнях запущена. Наводитесь и бейте слизней.");
    }

    @Subscribe
    private void onTick(EventTick event) {
        if (!debugChat.getValue() || !AIRotationRecorder.isRecording()) return;

        int samples = AIRotationRecorder.getSampleCount();
        if (samples > 0 && samples != lastDebugSamples && samples % 20 == 0) {
            lastDebugSamples = samples;
            ChatUtil.send("§7Ai Record debug: сэмплов записано: §f" + samples);
        }
    }

    @Override
    public void onDisable() {
        int samples = AIRotationRecorder.stopRecording();
        ChatUtil.send("§eAi Record: запись остановлена, сэмплов: §f" + samples);

        if (autoSave.getValue()) {
            if (samples >= minSamples.getIntValue()) {
                String datasetName = "slimes_" + LocalDateTime.now().format(DATASET_TIME);
                AIRotationManager.saveDataset(datasetName);
            } else {
                ChatUtil.send("§cAi Record: датасет не сохранен, мало сэмплов: §f" + samples + "§c/" + minSamples.getIntValue());
            }
        }

        super.onDisable();
    }
}
