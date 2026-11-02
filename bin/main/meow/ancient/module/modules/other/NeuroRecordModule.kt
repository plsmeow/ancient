package meow.ancient.module.modules.other

import meow.ancient.client.extensions.OTHER
import meow.ancient.client.util.neuro.NeuroRecorder
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.hitaura.rotation.impl.NeuroRotation

object NeuroRecordModule : Module(
    name = "NeuroRecord",
    category = OTHER,
    description = "Record manual combat for Neuro training datasets"
) {
    private val datasetSetting = text("Dataset", "session", maxLength = 64)

    override fun onEnable() {
        val name = datasetSetting.value.trim()
        if (name.isEmpty()) {
            ChatUtil.error("Set a dataset name first.")
            setEnabled(false)
            return
        }
        if (!isDatasetNameValid(name)) {
            ChatUtil.error("Invalid dataset name: $name")
            setEnabled(false)
            return
        }
        val recorder = getRecorder()
        if (recorder == null) {
            ChatUtil.error("HitAura Neuro rotation is unavailable.")
            setEnabled(false)
            return
        }
        if (recorder.isRecording) {
            ChatUtil.error("Already recording '${recorder.getDatasetName()}'.")
            setEnabled(false)
            return
        }
        val existing = NeuroRecorder.getDatasetTicks(name)
        val error = recorder.startRecording(name)
        if (error != null) {
            ChatUtil.error(error)
            setEnabled(false)
            return
        }
        ChatUtil.send("Recording §b$name§r" +
            (if (existing > 0) " (appending to ${NeuroRecorder.formatMinutes(existing)})" else "") +
            ". Aim and attack manually, with auras and aim assists off.")
        NeuroRecorder.formatNeeded(NeuroRecorder.getTotalTicks())?.let {
            ChatUtil.send("§7Record another $it of combat before training.§r")
        }
    }

    override fun onDisable() {
        val recorder = getRecorder() ?: return
        if (recorder.isRecording && recorder.getDatasetName() == datasetSetting.value.trim()) {
            ChatUtil.send(recorder.stopRecording())
        }
    }

    private fun getRecorder(): NeuroRecorder? =
        (HitAuraModule.rotationProcessor.getStrategy(HitAuraModule.RotationMode.NEURO) as? NeuroRotation)?.recorder

    private fun isDatasetNameValid(name: String): Boolean =
        name != "." && name != ".." && Regex("[\\p{L}\\p{N}_.-]+").matches(name)
}
