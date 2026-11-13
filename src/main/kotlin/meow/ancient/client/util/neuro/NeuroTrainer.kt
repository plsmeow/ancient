package meow.ancient.client.util.neuro

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

object NeuroTrainer {
    private const val RESOURCE_TRAINER_DIR = "assets/ancient/neuro/trainer/"
    private val SCRIPT_NAMES = listOf("common.py", "train_aura.py")

    @Volatile
    private var cachedPython: File? = null
    @Volatile
    private var environmentReady = false

    @Synchronized
    fun findPython(): File? {
        val cached = cachedPython
        if (cached != null && cached.isFile && cached.canExecute() && environmentReady) return cached

        val candidates = collectCandidatePythons()
        for (candidate in candidates) {
            if (hasModules(candidate, "numpy", "torch")) {
                cachedPython = candidate
                environmentReady = true
                return candidate
            }
        }
        val fallback = candidates.firstOrNull { hasModules(it, "torch") || hasModules(it, "numpy") }
            ?: candidates.firstOrNull(::isValidPython)
        cachedPython = fallback
        environmentReady = false
        return fallback
    }

    private fun collectCandidatePythons(): List<File> {
        val candidates = LinkedHashMap<String, File>()
        fun add(file: File) {
            if (file.isFile && file.canExecute()) candidates.putIfAbsent(file.absolutePath, file)
        }
        fun addConfigured(path: String?) {
            if (!path.isNullOrBlank()) add(File(path.trim()))
        }
        fun addEnvironment(path: String?, windowsName: String) {
            if (path.isNullOrBlank()) return
            val directory = File(path.trim())
            add(File(directory, "bin/python3"))
            add(File(directory, "bin/python"))
            add(File(directory, windowsName))
        }

        addConfigured(System.getProperty("python.path"))
        addConfigured(System.getenv("PYTHON_PATH"))
        addConfigured(System.getenv("PYTHON"))
        try {
            val customFile = NeuroModel.getNeuroDir().resolve("python.txt")
            if (Files.isRegularFile(customFile)) addConfigured(Files.readString(customFile, StandardCharsets.UTF_8))
        } catch (_: Exception) {
        }
        addEnvironment(System.getenv("VIRTUAL_ENV"), "Scripts/python.exe")
        addEnvironment(System.getenv("CONDA_PREFIX"), "python.exe")
        addEnvironment(".venv", "Scripts/python.exe")
        addEnvironment(NeuroModel.getNeuroDir().resolve(".venv").toString(), "Scripts/python.exe")

        val names = arrayOf("python3", "python", "python3.exe", "python.exe")
        System.getenv("PATH")?.split(File.pathSeparator)?.filter { it.isNotBlank() }?.forEach { directory ->
            for (name in names) add(File(directory, name))
        }
        val home = System.getProperty("user.home")
        if (!home.isNullOrBlank()) {
            for (path in listOf(
                ".local/share/mise/shims/python3", ".local/share/mise/shims/python",
                ".local/share/mise/installs/python/latest/bin/python3", ".local/share/mise/installs/python/latest/bin/python",
                ".pyenv/shims/python3", ".pyenv/shims/python", ".asdf/shims/python3", ".asdf/shims/python",
                ".local/bin/python3", ".local/bin/python"
            )) add(File(home, path))
        }
        for (directory in listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")) {
            add(File(directory, "python3"))
            add(File(directory, "python"))
        }
        val localAppData = System.getenv("LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            File(localAppData, "Programs/Python").listFiles()?.filter { it.isDirectory }?.forEach {
                add(File(it, "python.exe"))
            }
        }
        return candidates.values.toList()
    }

    private fun isValidPython(python: File): Boolean = probe(python, "--version", timeoutSeconds = 3)

    fun hasModules(python: File, vararg modules: String): Boolean =
        probe(python, "-c", "import ${modules.joinToString(", ")}", timeoutSeconds = 10)

    private fun probe(python: File, vararg args: String, timeoutSeconds: Long): Boolean {
        if (!python.isFile || !python.canExecute()) return false
        var process: Process? = null
        return try {
            process = ProcessBuilder(listOf(python.absolutePath) + args).apply {
                environment()["PYTHONWARNINGS"] = "ignore"
                redirectErrorStream(true)
                redirectOutput(ProcessBuilder.Redirect.DISCARD)
            }.start()
            if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) process.exitValue() == 0 else false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        } finally {
            process?.takeIf { it.isAlive }?.destroyForcibly()
        }
    }

    fun isEnvironmentReady(): Boolean = findPython() != null && environmentReady

    fun hasModule(module: String): Boolean = findPython()?.let { hasModules(it, module) } ?: false

    fun startTraining(python: File, dataDir: Path, outPath: Path, epochs: String?): Process {
        val trainerDir = NeuroModel.getNeuroDir().resolve("trainer")
        Files.createDirectories(trainerDir)
        Files.createDirectories(outPath.toAbsolutePath().parent)
        for (name in SCRIPT_NAMES) {
            val stream = NeuroTrainer::class.java.classLoader.getResourceAsStream(RESOURCE_TRAINER_DIR + name)
                ?: throw IllegalStateException("Missing bundled Neuro trainer: $name")
            stream.use { Files.copy(it, trainerDir.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
        }
        val command = mutableListOf(
            python.absolutePath, "-u", trainerDir.resolve("train_aura.py").toAbsolutePath().toString(),
            "--data", dataDir.toAbsolutePath().toString(), "--out", outPath.toAbsolutePath().toString()
        )
        if (epochs != null) command.addAll(listOf("--epochs", epochs))
        return ProcessBuilder(command).redirectErrorStream(true).apply {
            environment()["PYTHONIOENCODING"] = "utf-8"
            environment()["PYTHONDONTWRITEBYTECODE"] = "1"
            environment()["PYTHONWARNINGS"] = "ignore"
            environment()["MKL_ENABLE_INSTRUCTIONS"] = "SSE4_2"
            environment()["ONEDNN_MAX_CPU_ISA"] = "SSE41"
        }.start()
    }
}
