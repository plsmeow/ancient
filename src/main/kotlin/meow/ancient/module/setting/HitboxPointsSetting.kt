package meow.ancient.module.setting

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class HitboxPoint(val x: Double, val y: Double, val z: Double)

class HitboxPointsSetting(
    name: String,
    defaultValue: List<HitboxPoint>,
    val maxPoints: Int = 32,
    description: String = "",
    isVisible: () -> Boolean = { true }
) : Setting<List<HitboxPoint>>(name, normalize(defaultValue, maxPoints), normalize(defaultValue, maxPoints), description, isVisible) {
    init {
        require(maxPoints >= 1)
    }

    override fun set(newValue: List<HitboxPoint>) = super.set(normalize(newValue, maxPoints))

    fun addPoint(point: HitboxPoint): Int {
        if (value.size >= maxPoints) return -1
        set(value + point)
        return value.lastIndex
    }

    fun movePoint(index: Int, point: HitboxPoint) {
        if (index !in value.indices || point == value[index]) return
        set(value.toMutableList().also { it[index] = point })
    }

    fun removePoint(index: Int) {
        if (value.size <= 1 || index !in value.indices) return
        set(value.filterIndexed { i, _ -> i != index })
    }

    override fun serialize(): JsonElement {
        val array = JsonArray()
        for (point in value) {
            val obj = JsonObject()
            obj.addProperty("x", point.x)
            obj.addProperty("y", point.y)
            obj.addProperty("z", point.z)
            array.add(obj)
        }
        return array
    }

    override fun deserialize(element: JsonElement) {
        if (!element.isJsonArray) return
        val points = ArrayList<HitboxPoint>()
        for (item in element.asJsonArray) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val x = obj.get("x")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: continue
            val y = obj.get("y")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: continue
            val z = obj.get("z")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: continue
            if (x.asDouble.isFinite() && y.asDouble.isFinite() && z.asDouble.isFinite()) {
                points.add(HitboxPoint(x.asDouble, y.asDouble, z.asDouble))
            }
        }
        if (points.isNotEmpty()) set(points)
    }

    companion object {
        private val CENTER = listOf(HitboxPoint(0.5, 0.55, 0.5))

        private fun normalize(points: List<HitboxPoint>, limit: Int): List<HitboxPoint> {
            val valid = points.asSequence().filter { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
                .take(limit.coerceAtLeast(1)).map {
                    HitboxPoint(it.x.coerceIn(0.0, 1.0), it.y.coerceIn(0.0, 1.0), it.z.coerceIn(0.0, 1.0))
                }.distinct().toList()
            return if (valid.isEmpty()) CENTER else java.util.Collections.unmodifiableList(valid)
        }
    }
}
