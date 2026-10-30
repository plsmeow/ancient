package meow.ancient.module.setting

import com.google.gson.JsonArray
import com.google.gson.JsonElement

class PickerSetting<T>(
    name: String,
    defaultValue: Set<String>,
    val catalog: PickerCatalog<T>,
    description: String = "",
    isVisible: () -> Boolean = { true }
) : Setting<Set<String>>(name, defaultValue.toSet(), defaultValue.toSet(), description, isVisible) {
    fun isSelected(id: String): Boolean = id in value

    fun toggle(id: String) {
        val selected = value.toMutableSet()
        if (!selected.remove(id)) selected.add(id)
        set(selected)
    }

    override fun set(newValue: Set<String>) {
        super.set(newValue.toSet())
    }

    override fun serialize(): JsonElement = JsonArray().also { array -> value.forEach(array::add) }

    override fun deserialize(element: JsonElement) {
        if (!element.isJsonArray) return
        val selected = linkedSetOf<String>()
        for (entry in element.asJsonArray) {
            if (entry.isJsonPrimitive && entry.asJsonPrimitive.isString) selected.add(entry.asString)
        }
        set(selected)
    }
}
