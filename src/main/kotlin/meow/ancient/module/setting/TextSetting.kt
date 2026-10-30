package meow.ancient.module.setting

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class TextSetting(
    name: String,
    defaultValue: String,
    val maxLength: Int = 256,
    description: String = "",
    isVisible: () -> Boolean = { true }
) : Setting<String>(name, normalize(defaultValue, maxLength), normalize(defaultValue, maxLength), description, isVisible) {
    override fun set(newValue: String) {
        super.set(normalize(newValue, maxLength))
    }

    override fun serialize(): JsonElement = JsonPrimitive(value)

    override fun deserialize(element: JsonElement) {
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            set(element.asString)
        }
    }

    companion object {
        fun normalize(value: String, maxLength: Int): String {
            var result: StringBuilder? = null
            var index = 0
            var count = 0
            while (index < value.length && count < maxLength) {
                val start = index
                val codepoint = value.codePointAt(index)
                index += Character.charCount(codepoint)
                if (codepoint < 32 || codepoint == 127 || codepoint == 167 || codepoint in 0xD800..0xDFFF) {
                    if (result == null) result = StringBuilder(value.length).append(value, 0, start)
                    continue
                }
                result?.appendCodePoint(codepoint)
                count++
            }
            return result?.toString() ?: if (index == value.length) value else value.substring(0, index)
        }
    }
}
