package meow.ancient.module.setting

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.util.Locale
import kotlin.math.floor

class NumberSetting(
    name: String,
    defaultValue: Double,
    val min: Double,
    val max: Double,
    val step: Double = 0.1,
    val precision: Int = 1,
    val unit: String = "",
    description: String = "",
    isVisible: () -> Boolean = { true },
    val limitMax: Boolean = true
) : Setting<Double>(name, defaultValue, defaultValue, description, isVisible) {

    fun getFloat(): Float = value.toFloat()
    fun getInt(): Int = value.toInt()

    override fun set(newValue: Double) {
        if (!newValue.isFinite()) return
        val clamped = if (limitMax) newValue.coerceIn(min, max) else newValue.coerceAtLeast(min)
        val steps = clamped / step
        val stepped = if (step > 0 && steps.isFinite()) floor(steps + 0.5) * step else clamped
        val result = if (stepped.isFinite()) stepped else clamped
        super.set(if (limitMax) result.coerceIn(min, max) else result.coerceAtLeast(min))
    }

    fun getFormattedValue(): String {
        return if (precision <= 0) {
            String.format(Locale.US, "%.0f", value) + unit
        } else {
            String.format(Locale.US, "%.${precision}f$unit", value)
        }
    }

    override fun serialize(): JsonElement = JsonPrimitive(value)

    override fun deserialize(element: JsonElement) {
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            set(element.asDouble)
        }
    }
}
