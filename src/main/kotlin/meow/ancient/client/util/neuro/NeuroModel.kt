package meow.ancient.client.util.neuro

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import meow.ancient.client.extensions.mc
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class NeuroModel private constructor(json: JsonObject) {
    val hidden: Int = json.get("hidden").getAsInt()
    val mix: Int = json.get("mix").getAsInt()
    val freezeCut: Int = if (json.has("freezeCut")) json.get("freezeCut").getAsInt() else 12

    private val limitYaw: Float
    private val limitPitch: Float
    private val error: FloatArray
    private val wi: Array<FloatArray>
    private val wh: Array<FloatArray>
    private val bi: FloatArray
    private val bh: FloatArray
    private val w: Array<FloatArray>
    private val b: FloatArray

    init {
        val limits = json.getAsJsonArray("limits")
        limitYaw = limits.get(0).getAsFloat()
        limitPitch = limits.get(1).getAsFloat()
        error = if (json.has("error")) parse1D(json.getAsJsonArray("error")) else FloatArray(0)
        val gru = json.getAsJsonObject("gru")
        wi = parse2D(gru.getAsJsonArray("wi"))
        wh = parse2D(gru.getAsJsonArray("wh"))
        bi = parse1D(gru.getAsJsonArray("bi"))
        bh = parse1D(gru.getAsJsonArray("bh"))
        val out = json.getAsJsonObject("out")
        w = parse2D(out.getAsJsonArray("w"))
        b = parse1D(out.getAsJsonArray("b"))
    }

    class InferenceState internal constructor(model: NeuroModel) {
        internal val hidden = FloatArray(model.hidden)
        internal val gatesI = FloatArray(3 * model.hidden)
        internal val gatesH = FloatArray(3 * model.hidden)
        internal val output = FloatArray(model.w.size)
    }

    fun createInferenceState(): InferenceState = InferenceState(this)

    fun forward(input: FloatArray, state: InferenceState): FloatArray {
        val hDim = hidden
        val hiddenState = state.hidden
        val gatesI = state.gatesI
        val gatesH = state.gatesH
        for (n in 0 until 3 * hDim) {
            var fi = bi[n]
            val wiRow = wi[n]
            for (j in wiRow.indices) fi += wiRow[j] * input[j]
            gatesI[n] = fi
            var fh = bh[n]
            val whRow = wh[n]
            for (j in whRow.indices) fh += whRow[j] * hiddenState[j]
            gatesH[n] = fh
        }
        for (n in 0 until hDim) {
            val r = sigmoid(gatesI[n] + gatesH[n])
            val z = sigmoid(gatesI[hDim + n] + gatesH[hDim + n])
            val nCand = Math.tanh((gatesI[2 * hDim + n] + r * gatesH[2 * hDim + n]).toDouble()).toFloat()
            hiddenState[n] = (1.0f - z) * nCand + z * hiddenState[n]
        }
        val output = state.output
        for (i in w.indices) {
            var v = b[i]
            val wRow = w[i]
            for (j in wRow.indices) v += wRow[j] * hiddenState[j]
            output[i] = v
        }
        return output
    }

    fun sampleMixture(output: FloatArray, randomVal: Float): Int {
        var maxPi = output[1]
        for (i in 1 until mix) maxPi = maxOf(maxPi, output[1 + 6 * i])
        var sumExp = 0.0f
        for (i in 0 until mix) sumExp += Math.exp((output[1 + 6 * i] - maxPi).toDouble()).toFloat()
        var threshold = randomVal * sumExp
        for (i in 0 until mix) {
            threshold -= Math.exp((output[1 + 6 * i] - maxPi).toDouble()).toFloat()
            if (threshold <= 0.0f) return i
        }
        return mix - 1
    }

    fun sampleAimError(randomFloat: Float): Float {
        if (error.isEmpty()) return 0.0f
        val scaled = randomFloat.coerceIn(0.0f, 1.0f) * (error.size - 1)
        val idx = scaled.toInt()
        if (idx >= error.size - 1) return error.last()
        return error[idx] + (error[idx + 1] - error[idx]) * (scaled - idx)
    }

    fun sampleCoordinate(mu: Float, logSigma: Float, isYaw: Boolean, eps: Float, temperature: Float): Float {
        val sigma = Math.exp(logSigma.toDouble().coerceIn(-4.0, 1.5)).toFloat()
        val u = (mu + eps * sigma * temperature).coerceIn(-8.0f, 8.0f)
        val limit = if (isYaw) limitYaw else limitPitch
        return Math.sinh(u.toDouble()).toFloat().coerceIn(-limit, limit)
    }

    companion object {
        const val FEATURES = 17
        const val DEFAULT_MODEL_NAME = "default"
        private const val RHO_LIMIT = 0.95f

        private var activeInstance: NeuroModel? = null
        private var activeLoaded = false
        private var activeModelName: String? = null

        fun getNeuroDir(): Path {
            val runDir = mc.gameDirectory.toPath()
            return runDir.resolve("ancient").resolve("neuro")
        }

        private val validName = Regex("[\\p{L}\\p{N}_.-]+")

        fun isValidName(name: String): Boolean = name != "." && name != ".." && validName.matches(name)

        fun getDataDir(): Path = getNeuroDir().resolve("data")

        fun getModelPath(name: String): Path {
            require(isValidName(name)) { "Invalid model name" }
            return getNeuroDir().resolve("$name.json")
        }

        fun getActiveName(): String {
            if (activeModelName == null) {
                val activeFile = getNeuroDir().resolve("active.txt")
                activeModelName = try {
                    if (Files.isRegularFile(activeFile)) {
                        Files.readString(activeFile).trim().takeIf(::isValidName) ?: DEFAULT_MODEL_NAME
                    } else DEFAULT_MODEL_NAME
                } catch (_: Exception) {
                    DEFAULT_MODEL_NAME
                }
            }
            return activeModelName!!
        }

        fun listModels(): List<String> {
            val list = mutableListOf(DEFAULT_MODEL_NAME)
            try {
                Files.list(getNeuroDir()).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".json") }
                        .map { it.fileName.toString().removeSuffix(".json") }
                        .filter { isValidName(it) && it !in list }
                        .sorted()
                        .forEach { list += it }
                }
            } catch (_: Exception) {}
            return list
        }

        fun hasModel(name: String): Boolean =
            isValidName(name) && (DEFAULT_MODEL_NAME == name || Files.isRegularFile(getModelPath(name)))

        fun setActiveModel(name: String): Boolean {
            if (!hasModel(name)) return false
            activeModelName = name
            try {
                Files.createDirectories(getNeuroDir())
                Files.writeString(getNeuroDir().resolve("active.txt"), name)
            } catch (_: Exception) {}
            resetCache()
            return true
        }

        fun getActive(): NeuroModel? {
            if (!activeLoaded) {
                activeLoaded = true
                activeInstance = load(getActiveName())
            }
            return activeInstance
        }

        fun resetCache() {
            activeLoaded = false
            activeInstance = null
        }

        fun load(name: String): NeuroModel? {
            return try {
                val json: JsonObject?
                val path = getModelPath(name)
                json = if (Files.isRegularFile(path)) {
                    JsonParser.parseString(Files.readString(path)).asJsonObject
                } else if (DEFAULT_MODEL_NAME == name) {
                    val stream = NeuroModel::class.java.classLoader
                        .getResourceAsStream("assets/ancient/neuro/default.json") ?: return null
                    stream.use { JsonParser.parseReader(InputStreamReader(it, StandardCharsets.UTF_8)).asJsonObject }
                } else null
                if (json == null) return null
                if (json.get("features").asInt != FEATURES) return null
                if (!json.has("mix") || !json.has("units")) return null
                if (json.get("units").asString != "deg") return null
                NeuroModel(json)
            } catch (_: Exception) { null }
        }

        fun sigmoid(f: Float): Float = 1.0f / (1.0f + Math.exp(-f.toDouble()).toFloat())

        fun tanhCorr(f: Float): Float = Math.tanh(f.toDouble()).toFloat() * RHO_LIMIT

        private fun parse2D(arr: JsonArray): Array<FloatArray> =
            Array(arr.size()) { parse1D(arr.get(it).asJsonArray) }

        private fun parse1D(arr: JsonArray): FloatArray =
            FloatArray(arr.size()) { arr.get(it).asFloat }
    }
}
