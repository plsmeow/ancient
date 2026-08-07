package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
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
        Onetap.getInstance().getEventBus().register(recorderInstance);

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

    @Subscribe
    public void onTick(EventTick event) {
        if (!AIRotationRecorder.isRecording()) return;

        int samples = AIRotationRecorder.getSampleCount();
        int interval = (int) chatInterval.getValue();

        if (samples > 0 && samples != lastChatSamples && samples % interval == 0) {
            lastChatSamples = samples;
            ChatUtil.send("§eСэмплов: §f" + samples);
        }
    }

    @Override
    public void onDisable() {
        int samples = AIRotationRecorder.stopRecording();
        ChatUtil.send("§eЗапись остановлена, сэмплов: §f" + samples);

        if (recorderInstance != null) {
            Onetap.getInstance().getEventBus().unregister(recorderInstance);
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
     * Отчёт по балансу датасета (§27) — чтобы не получить 90% неподвижной цели.
     */
    private void reportBalance(int samples) {
        if (samples == 0) return;

        var balance = AIRotationRecorder.getBalance();
        int moving = balance.getMovingTarget();
        int stationary = balance.getStationaryTarget();
        int total = moving + stationary;

        if (total == 0) return;

        int movingPercent = moving * 100 / total;
        ChatUtil.send("§7Баланс: движ. цель §f" + movingPercent + "%§7, близко §f"
                + balance.getCloseDistance() + "§7 / средне §f" + balance.getMediumDistance()
                + "§7 / далеко §f" + balance.getLongDistance());

        if (movingPercent < 20) {
            ChatUtil.send("§eМало движущихся целей — модель будет плохо вести подвижного противника");
        }
    }
}
