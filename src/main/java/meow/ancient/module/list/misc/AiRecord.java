package meow.ancient.module.list.misc;

import meteordevelopment.orbit.EventHandler;
import meow.ancient.Ancient;
import meow.ancient.event.list.EventTick;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.module.settings.SliderSetting;
import meow.ancient.util.chat.ChatUtil;
import meow.ancient.util.neuro.rotation.NeuroRecorder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ModuleInformation(
        moduleName = "Ai Record",
        moduleDesc = "Записывает AI датасет для Neuro (ROKSTAR)",
        moduleCategory = ModuleCategory.MISC
)
public class AiRecord extends Module {
    private static final DateTimeFormatter DATASET_TIME = DateTimeFormatter.ofPattern("MMdd_HHmm");

    private final SliderSetting chatInterval = new SliderSetting("Чат интервал", 50, 10, 500, 10);
    private int lastChatSamples;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            ChatUtil.send("§cAi Record: зайдите в мир перед запуском записи");
            setEnabled(false);
            return;
        }

        super.onEnable();
        lastChatSamples = 0;

        NeuroRecorder recorder = getRecorder();
        if (recorder == null) {
            ChatUtil.send("§cAi Record: модуль KillAura не найден");
            setEnabled(false);
            return;
        }

        String datasetName = "ds_" + LocalDateTime.now().format(DATASET_TIME);
        String err = recorder.startRecording(datasetName);
        if (err != null) {
            ChatUtil.send("§c" + err);
            setEnabled(false);
            return;
        }

        ChatUtil.send("§aЗапись начата: §b" + datasetName + "§a. Наводитесь руками, бейте цели.");
        ChatUtil.send("§7Выключите модуль для остановки записи (или .neuro stop)");
    }

    @EventHandler
    public void onTick(EventTick event) {
        NeuroRecorder recorder = getRecorder();
        if (recorder == null || !recorder.isRecording()) return;

        int samples = recorder.getSessionTicks();
        int interval = (int) chatInterval.getValue();

        if (samples > 0 && samples != lastChatSamples && samples % interval == 0) {
            lastChatSamples = samples;
            ChatUtil.send("§eТиков записано: §f" + samples + " (" + NeuroRecorder.formatMinutes(samples) + ")");
        }
    }

    @Override
    public void onDisable() {
        NeuroRecorder recorder = getRecorder();
        if (recorder != null && recorder.isRecording()) {
            ChatUtil.send(recorder.stopRecording());
        }
        super.onDisable();
    }

    private NeuroRecorder getRecorder() {
        KillAura ka = Ancient.getInstance().getModuleStorage().get(KillAura.class);
        return ka != null ? ka.getNeuroRotation().getRecorder() : null;
    }
}
