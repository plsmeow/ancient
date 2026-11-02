package meow.ancient.client.command.impl

import meow.ancient.client.command.Command
import meow.ancient.client.command.CommandManager
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.neuro.NeuroModel
import meow.ancient.client.util.neuro.NeuroRecorder
import meow.ancient.client.util.neuro.NeuroTrainer
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.hitaura.rotation.impl.NeuroRotation
import java.awt.Desktop
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object NeuroCommand : Command(
    name = "neuro",
    description = "Manage neural rotation models, combat recordings and training",
    syntax = "neuro <status | list | models | load <name> | dir | train [name] [epochs|auto] | record <name> | rec <name> | stop | data | why>",
    aliases = listOf("ai", "nr", "neurorotation")
) {
    private val trainingRunning = AtomicBoolean(false)

    override fun execute(args: Array<String>) {
        val name = args.getOrNull(1)
        when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
            null, "status" -> showStatus()
            "list", "models" -> showList()
            "load" -> loadModel(name)
            "dir" -> openDirectory()
            "train" -> trainModel(name, args.getOrNull(2))
            "record", "rec" -> startRecord(name)
            "stop" -> stopRecord()
            "data" -> showData()
            "why" -> showWhy()
            else -> sendUsage()
        }
    }

    private fun validName(name: String): Boolean {
        if (NeuroModel.isValidName(name)) return true
        ChatUtil.error("Invalid name: use letters, numbers, underscores, dots or hyphens, without a path.")
        return false
    }

    private fun showStatus() {
        val model = NeuroModel.getActive()
        ChatUtil.send("Model §b${NeuroModel.getActiveName()}§r — " +
            (if (model == null) "§cfailed to load§r" else "§aready§r") +
            ", ${NeuroModel.listModels().size} models total.")
        val totalTicks = NeuroRecorder.getTotalTicks()
        val recorder = getRecorder()
        ChatUtil.send("Recorded: §b${NeuroRecorder.formatMinutes(totalTicks)}§r of combat " +
            "(${NeuroRecorder.evaluateQuality(totalTicks)})" +
            if (recorder?.isRecording == true) " §crecording ${recorder.getDatasetName()}§r" else "")
        NeuroRecorder.formatNeeded(totalTicks)?.let {
            ChatUtil.send("§7Record another $it before training.§r")
        }
        if (trainingRunning.get()) ChatUtil.send("§bTraining is running.§r")
        ChatUtil.send("Trainer: §7checking...§r")
        thread(name = "neuro-trainer-check", isDaemon = true) {
            val status = trainerStatus()
            printInMainThread("Trainer: $status")
        }
    }

    private fun trainerStatus(): String {
        val python = NeuroTrainer.findPython() ?: return "§7Python runtime not found (install python3).§r"
        if (NeuroTrainer.isEnvironmentReady()) return "§aready§r"
        val numpy = NeuroTrainer.hasModules(python, "numpy")
        val torch = NeuroTrainer.hasModules(python, "torch")
        val missing = when {
            !numpy && !torch -> "numpy torch"
            !numpy -> "numpy"
            else -> "torch"
        }
        return "§cmissing $missing (${python.absolutePath} -m pip install $missing).§r"
    }

    private fun showList() {
        val models = NeuroModel.listModels()
        val activeName = NeuroModel.getActiveName()
        ChatUtil.send("Models: §b${models.size}§r")
        for (model in models) {
            val active = model == activeName
            val builtin = !Files.isRegularFile(NeuroModel.getModelPath(model))
            ChatUtil.send((if (active) "§b > " else "§7 · ") + model +
                (if (builtin) " §8(bundled)" else "") + (if (active) " §8active" else "") + "§r")
        }
    }

    private fun loadModel(name: String?) {
        if (name.isNullOrBlank()) {
            ChatUtil.error("Usage: ${CommandManager.prefix}neuro load <name>")
            return
        }
        if (!validName(name)) return
        if (!NeuroModel.hasModel(name)) {
            ChatUtil.error("Model '$name' does not exist. List models: ${CommandManager.prefix}neuro list")
            return
        }
        NeuroModel.setActiveModel(name)
        if (NeuroModel.getActive() == null) {
            ChatUtil.error("Model '$name' was selected, but could not be loaded.")
        } else {
            ChatUtil.success("Model '$name' loaded.")
        }
    }

    private fun openDirectory() {
        val directory = NeuroModel.getNeuroDir()
        try {
            Files.createDirectories(directory)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile())
                ChatUtil.success("Opened models directory: $directory")
                return
            }
        } catch (_: Exception) {
        }
        try {
            ProcessBuilder("xdg-open", directory.toAbsolutePath().toString()).start()
            ChatUtil.success("Opened models directory: $directory")
            return
        } catch (_: Exception) {
        }
        ChatUtil.send("Models directory: $directory")
    }

    private fun trainModel(nameArg: String?, epochsArg: String?) {
        if (trainingRunning.get()) {
            ChatUtil.error("Training is already running.")
            return
        }
        val modelName = nameArg?.takeIf { it.isNotBlank() } ?: NeuroModel.getActiveName()
        if (!validName(modelName)) return
        val epochs = when {
            epochsArg == null -> "100"
            epochsArg.equals("auto", ignoreCase = true) -> "0"
            epochsArg.matches(Regex("\\d+")) && epochsArg.toIntOrNull() != null -> epochsArg
            else -> {
                ChatUtil.error("Epochs must be a non-negative integer or auto.")
                return
            }
        }
        val totalTicks = NeuroRecorder.getTotalTicks()
        if (totalTicks < 600) {
            ChatUtil.error("Not enough recordings: ${NeuroRecorder.formatMinutes(totalTicks)} of combat; at least 0.5 min required.")
            ChatUtil.send("§7Record more: ${CommandManager.prefix}neuro record <name>§r")
            return
        }
        if (!trainingRunning.compareAndSet(false, true)) {
            ChatUtil.error("Training is already running.")
            return
        }
        ChatUtil.send("Checking the Python trainer for model §b$modelName§r...")
        thread(name = "neuro-train", isDaemon = true) {
            try {
                val python = NeuroTrainer.findPython()
                if (python == null) {
                    printInMainThread("§cPython runtime not found. Install python3 to train models.§r")
                    return@thread
                }
                if (!NeuroTrainer.isEnvironmentReady()) {
                    printInMainThread("§cTraining requires numpy and torch: ${python.absolutePath} -m pip install numpy torch§r")
                    return@thread
                }
                printInMainThread("Training §b$modelName§r started (" +
                    (if (epochs.toInt() == 0) "auto, until validation stops improving" else "$epochs epochs") +
                    "). Progress will appear in chat.")
                runTrainingProcess(python, NeuroModel.getDataDir(), NeuroModel.getModelPath(modelName), modelName, epochs)
            } catch (e: Exception) {
                printInMainThread("§cTraining failed: ${e.message}§r")
            } finally {
                trainingRunning.set(false)
            }
        }
    }

    private fun runTrainingProcess(python: File, dataDir: Path, outPath: Path, modelName: String, epochs: String) {
        val process = NeuroTrainer.startTraining(python, dataDir, outPath, epochs)
        try {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        printInMainThread(when {
                            trimmed.startsWith("!! ") -> "§c${trimmed.substring(3)}§r"
                            trimmed.startsWith("! ") -> "§e${trimmed.substring(2)}§r"
                            else -> "§7$trimmed§r"
                        })
                    }
                }
            }
            val code = process.waitFor()
            if (code == 0) {
                mc.execute {
                    if (!NeuroModel.setActiveModel(modelName) || NeuroModel.getActive() == null) {
                        ChatUtil.error("Training finished, but model '$modelName' could not be loaded.")
                    } else {
                        ChatUtil.success("Training finished. Model '$modelName' is now active.")
                    }
                }
            } else {
                printInMainThread("§cTrainer exited with code $code.§r")
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun printInMainThread(message: String) {
        mc.execute { ChatUtil.send(message) }
    }

    private fun startRecord(name: String?) {
        val recorder = getRecorder()
        if (recorder == null) {
            ChatUtil.error("HitAura Neuro rotation is unavailable.")
            return
        }
        if (recorder.isRecording) {
            ChatUtil.error("Already recording '${recorder.getDatasetName()}'. Stop with ${CommandManager.prefix}neuro stop")
            return
        }
        if (name.isNullOrBlank()) {
            ChatUtil.error("Usage: ${CommandManager.prefix}neuro record <name>")
            return
        }
        if (!validName(name)) return
        val existing = NeuroRecorder.getDatasetTicks(name)
        val error = recorder.startRecording(name)
        if (error != null) {
            ChatUtil.error(error)
            return
        }
        ChatUtil.send("Recording §b$name§r" +
            (if (existing > 0) " (appending to ${NeuroRecorder.formatMinutes(existing)})" else "") +
            ". Aim and attack manually, with auras and aim assists off. Stop: ${CommandManager.prefix}neuro stop")
        NeuroRecorder.formatNeeded(NeuroRecorder.getTotalTicks())?.let {
            ChatUtil.send("§7Record another $it of combat before training.§r")
        }
    }

    private fun stopRecord() {
        val recorder = getRecorder()
        if (recorder == null) ChatUtil.error("HitAura Neuro rotation is unavailable.")
        else ChatUtil.send(recorder.stopRecording())
    }

    private fun showData() {
        val datasets = NeuroRecorder.listDatasets()
        if (datasets.isEmpty()) {
            ChatUtil.send("No datasets. Record one: ${CommandManager.prefix}neuro record <name>")
            return
        }
        val total = NeuroRecorder.getTotalTicks()
        ChatUtil.send("Datasets: §b${datasets.size}§r, §b${NeuroRecorder.formatMinutes(total)}§r of combat total " +
            "(${NeuroRecorder.evaluateQuality(total)})")
        datasets.forEach { ChatUtil.send("§8 · §7$it§r") }
        if (NeuroRecorder.getCorruptedLinesCount() > 0) {
            ChatUtil.send("§7The trainer automatically skips corrupted rows.§r")
        }
        NeuroRecorder.formatNeeded(total)?.let { ChatUtil.send("§7Record another $it before training.§r") }
    }

    private fun showWhy() {
        ChatUtil.send("§b[Neuro]§r Rotation state: model ${NeuroModel.getActiveName()} " +
            if (NeuroModel.getActive() != null) "(§aloaded§r)" else "(§cnot found§r)")
    }

    private fun getRecorder(): NeuroRecorder? =
        (HitAuraModule.rotationProcessor.getStrategy(HitAuraModule.RotationMode.NEURO) as? NeuroRotation)?.recorder
}
