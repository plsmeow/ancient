package meow.ancient.module.setting

import com.google.gson.JsonElement
import com.google.gson.JsonNull

class ActionSetting(
    name: String,
    val action: () -> Unit,
    description: String = "",
    isVisible: () -> Boolean = { true }
) : Setting<Unit>(name, Unit, Unit, description, isVisible) {
    override fun serialize(): JsonElement = JsonNull.INSTANCE

    override fun deserialize(element: JsonElement) {
        if (element.isJsonNull) set(Unit)
    }
}
