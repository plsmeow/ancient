package meow.ancient.module.setting

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class CurvePoint(val x: Float, val y: Float)

enum class CurveInterpolation(val displayName: String) {
    LINEAR("Linear"),
    SMOOTH("Smooth"),
    STEP("Step")
}

class Curve(
    points: List<CurvePoint>,
    val interpolation: CurveInterpolation = CurveInterpolation.SMOOTH
) {
    val points: List<CurvePoint> = normalize(points)
    private val tangents = if (interpolation == CurveInterpolation.SMOOTH) calculateTangents() else EMPTY_TANGENTS

    fun withPoints(newPoints: List<CurvePoint>): Curve = Curve(newPoints, interpolation)

    fun withInterpolation(mode: CurveInterpolation): Curve = Curve(points, mode)

    fun evaluate(x: Float): Float {
        val input = if (x.isFinite()) x.coerceIn(0f, 1f) else 0f
        if (input <= 0f) return points.first().y
        if (input >= 1f) return points.last().y
        var low = 0
        var high = points.lastIndex
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (points[middle].x <= input) low = middle else high = middle
        }
        val left = points[low]
        val right = points[high]
        val span = right.x - left.x
        val t = (input - left.x) / span
        return when (interpolation) {
            CurveInterpolation.STEP -> left.y
            CurveInterpolation.LINEAR -> left.y + (right.y - left.y) * t
            CurveInterpolation.SMOOTH -> {
                val t2 = t * t
                val t3 = t2 * t
                ((2f * t3 - 3f * t2 + 1f) * left.y
                    + (t3 - 2f * t2 + t) * span * tangents[low]
                    + (-2f * t3 + 3f * t2) * right.y
                    + (t3 - t2) * span * tangents[high]).coerceIn(0f, 1f)
            }
        }
    }

    private fun calculateTangents(): FloatArray {
        val result = FloatArray(points.size)
        result[0] = (points[1].y - points[0].y) / (points[1].x - points[0].x)
        result[result.lastIndex] = (points.last().y - points[points.lastIndex - 1].y) /
            (points.last().x - points[points.lastIndex - 1].x)
        for (i in 1 until points.lastIndex) {
            val leftSpan = points[i].x - points[i - 1].x
            val rightSpan = points[i + 1].x - points[i].x
            val leftSlope = (points[i].y - points[i - 1].y) / leftSpan
            val rightSlope = (points[i + 1].y - points[i].y) / rightSpan
            if (leftSlope * rightSlope <= 0f) continue
            val leftWeight = 2f * rightSpan + leftSpan
            val rightWeight = rightSpan + 2f * leftSpan
            result[i] = (leftWeight + rightWeight) / (leftWeight / leftSlope + rightWeight / rightSlope)
        }
        return result
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is Curve && interpolation == other.interpolation && points == other.points

    override fun hashCode(): Int = 31 * points.hashCode() + interpolation.hashCode()

    companion object {
        const val MIN_POINT_GAP = 0.002f
        private val EMPTY_TANGENTS = FloatArray(0)
        private val LINEAR_POINTS = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

        val LINEAR = Curve(LINEAR_POINTS, CurveInterpolation.LINEAR)
        val EASE_IN = Curve(listOf(CurvePoint(0f, 0f), CurvePoint(0.45f, 0.12f), CurvePoint(0.78f, 0.45f), CurvePoint(1f, 1f)))
        val EASE_OUT = Curve(listOf(CurvePoint(0f, 0f), CurvePoint(0.22f, 0.55f), CurvePoint(0.55f, 0.88f), CurvePoint(1f, 1f)))
        val EASE_IN_OUT = Curve(listOf(CurvePoint(0f, 0f), CurvePoint(0.3f, 0.08f), CurvePoint(0.7f, 0.92f), CurvePoint(1f, 1f)))
        val AGGRESSIVE = Curve(listOf(CurvePoint(0f, 0.35f), CurvePoint(0.25f, 0.82f), CurvePoint(0.6f, 0.96f), CurvePoint(1f, 1f)))
        val HUMAN = Curve(listOf(CurvePoint(0f, 0.08f), CurvePoint(0.18f, 0.42f), CurvePoint(0.42f, 0.78f), CurvePoint(0.72f, 0.94f), CurvePoint(1f, 1f)))
        val SLOW_END = Curve(listOf(CurvePoint(0f, 0.2f), CurvePoint(0.35f, 0.75f), CurvePoint(0.7f, 0.95f), CurvePoint(1f, 0.6f)))
        val CONSTANT = Curve(listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f)), CurveInterpolation.LINEAR)

        val presets: List<Pair<String, Curve>> = listOf(
            "Linear" to LINEAR, "Ease In" to EASE_IN, "Ease Out" to EASE_OUT,
            "Ease In-Out" to EASE_IN_OUT, "Human" to HUMAN, "Aggressive" to AGGRESSIVE,
            "Slow End" to SLOW_END, "Constant" to CONSTANT
        )

        private fun normalize(source: List<CurvePoint>): List<CurvePoint> {
            val sorted = source.filter { it.x.isFinite() && it.y.isFinite() }
                .map { CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }.sortedBy { it.x }
            if (sorted.isEmpty()) return LINEAR_POINTS
            val normalized = ArrayList<CurvePoint>(sorted.size + 2)
            for (point in sorted) {
                val previous = normalized.lastOrNull()
                if (previous != null && point.x - previous.x < MIN_POINT_GAP) {
                    normalized[normalized.lastIndex] = CurvePoint(previous.x, point.y)
                } else {
                    normalized.add(point)
                }
            }
            if (normalized.first().x > MIN_POINT_GAP) normalized.add(0, CurvePoint(0f, normalized.first().y))
            else normalized[0] = CurvePoint(0f, normalized.first().y)
            if (normalized.last().x < 1f - MIN_POINT_GAP) normalized.add(CurvePoint(1f, normalized.last().y))
            else normalized[normalized.lastIndex] = CurvePoint(1f, normalized.last().y)
            if (normalized.size == 1) normalized.add(CurvePoint(1f, normalized[0].y))
            return java.util.Collections.unmodifiableList(normalized)
        }
    }
}

class CurveSetting(
    name: String,
    defaultValue: Curve,
    val xLabel: String = "Input",
    val yLabel: String = "Output",
    val maxPoints: Int = 12,
    description: String = "",
    isVisible: () -> Boolean = { true }
) : Setting<Curve>(name, defaultValue, defaultValue, description, isVisible) {
    init {
        require(maxPoints >= 2)
    }

    fun evaluate(x: Float): Float = value.evaluate(x)

    override fun set(newValue: Curve) {
        if (newValue.points.size <= maxPoints) super.set(newValue)
        else super.set(Curve(newValue.points.take(maxPoints - 1) + newValue.points.last(), newValue.interpolation))
    }

    fun setPoints(points: List<CurvePoint>) = set(value.withPoints(points))

    fun setInterpolation(mode: CurveInterpolation) = set(value.withInterpolation(mode))

    fun addPoint(point: CurvePoint) {
        if (value.points.size >= maxPoints || point.x <= Curve.MIN_POINT_GAP || point.x >= 1f - Curve.MIN_POINT_GAP) return
        if (value.points.any { kotlin.math.abs(it.x - point.x) < Curve.MIN_POINT_GAP }) return
        setPoints(value.points + point)
    }

    fun removePoint(index: Int) {
        val points = value.points
        if (index <= 0 || index >= points.lastIndex) return
        setPoints(points.filterIndexed { i, _ -> i != index })
    }

    fun movePoint(index: Int, x: Float, y: Float) {
        val points = value.points
        if (index !in points.indices || !x.isFinite() || !y.isFinite()) return
        val clampedX = when (index) {
            0 -> 0f
            points.lastIndex -> 1f
            else -> x.coerceIn(points[index - 1].x + Curve.MIN_POINT_GAP, points[index + 1].x - Curve.MIN_POINT_GAP)
        }
        val replacement = CurvePoint(clampedX, y.coerceIn(0f, 1f))
        if (replacement == points[index]) return
        setPoints(points.toMutableList().also { it[index] = replacement })
    }

    override fun serialize(): JsonElement {
        val root = JsonObject()
        root.addProperty("interpolation", value.interpolation.name)
        val array = JsonArray()
        for (point in value.points) {
            val obj = JsonObject()
            obj.addProperty("x", point.x)
            obj.addProperty("y", point.y)
            array.add(obj)
        }
        root.add("points", array)
        return root
    }

    override fun deserialize(element: JsonElement) {
        if (!element.isJsonObject) return
        val root = element.asJsonObject
        val array = root.get("points")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val points = ArrayList<CurvePoint>(minOf(array.size(), maxPoints))
        for (item in array) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val x = obj.get("x")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: continue
            val y = obj.get("y")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: continue
            if (!x.asFloat.isFinite() || !y.asFloat.isFinite()) continue
            points.add(CurvePoint(x.asFloat, y.asFloat))
        }
        if (points.size < 2) return
        val mode = root.get("interpolation")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.let { raw -> CurveInterpolation.entries.firstOrNull { it.name == raw.asString } }
            ?: defaultValue.interpolation
        set(Curve(points, mode))
    }
}
