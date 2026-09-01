package tech.onetap.module.list.misc;

import meteordevelopment.orbit.EventHandler;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.neuro.rotation.AIRotationRecorder;

import tech.onetap.Onetap;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ModuleInformation(
        moduleName = "Ai Record",
        moduleDesc = "Записывает AI датасет для Neuro",
        moduleCategory = ModuleCategory.MISC
)
public class AiRecord extends Module {

    private static final DateTimeFormatter DATASET_TIME = DateTimeFormatter.ofPattern("MMdd_HHmm");

    public final ModeSetting mode = new ModeSetting("Режим", "Слизни", "Слизни", "KillAura");
    private final BooleanSetting autoSave = new BooleanSetting("Auto Save", true);
    private final SliderSetting minSamples = new SliderSetting("Min Samples", 64, 1, 1000, 1).setVisible(autoSave::getValue);
    private final SliderSetting chatInterval = new SliderSetting("Чат интервал", 50, 10, 500, 10);

    private int lastChatSamples;
    private AIRotationRecorder recorderInstance;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            ChatUtil.send("§cAi Record: зайдите в мир перед запуском записи");
            setEnabled(false);
            return;
        }

        super.onEnable();
        lastChatSamples = 0;

        recorderInstance = new AIRotationRecorder();
        Onetap.getInstance().getEventBus().subscribe(recorderInstance);

        AIRotationRecorder.Mode recordMode = mode.is("Слизни")
                ? AIRotationRecorder.Mode.SLIMES
                : AIRotationRecorder.Mode.KILLAURA;

        // Метки — только человеческие движения мыши (источник «Ротация» удалён).
        AIRotationRecorder.startRecording(recordMode);

        if (recordMode == AIRotationRecorder.Mode.SLIMES) {
            ChatUtil.send("§aЗапись начата (слизни). Наводитесь и бейте слизней.");
        } else {
            ChatUtil.send("§aЗапись начата (KillAura). Атакуйте цель.");
        }
        ChatUtil.send("§7Выключите модуль для остановки записи");
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (!AIRotationRecorder.isRecording()) return;

        long samples = AIRotationRecorder.getRowCount();
        int interval = (int) chatInterval.getValue();

        if (samples > 0 && samples != lastChatSamples && samples % interval == 0) {
            lastChatSamples = (int) samples;
            ChatUtil.send("§eТиков записано: §f" + samples);
        }
    }

    @Override
    public void onDisable() {
        long samples = AIRotationRecorder.stopRecording();
        ChatUtil.send("§eЗапись остановлена, тиков: §f" + samples);

        if (recorderInstance != null) {
            Onetap.getInstance().getEventBus().unsubscribe(recorderInstance);
            recorderInstance = null;
        }

        reportBalance(samples);

        if (autoSave.getValue()) {
            if (samples >= minSamples.getIntValue()) {
                String datasetName = "ds" + LocalDateTime.now().format(DATASET_TIME);
                AIRotationManager.saveDataset(datasetName);
            } else {
                ChatUtil.send("§cДатасет не сохранён, мало сэмплов: §f" + samples + "§c/" + minSamples.getIntValue());
            }
        }

        super.onDisable();
    }

    /**
     * Отчёт по полезности записи: доля тиков с целью (эпизоды для тренера).
     */
    private void reportBalance(long samples) {
        if (samples == 0) return;

        long clean = AIRotationRecorder.getCleanCount();
        int percent = (int) (clean * 100 / samples);
        ChatUtil.send("§7Чистых тиков (с целью): §f" + clean + "§7/§f" + samples
                + " §7(§f" + percent + "%§7)");

        if (samples < 256) {
            ChatUtil.send("§eСтрок мало — модель обучится плохо. Запишите больше боёв");
        }
    }
}
