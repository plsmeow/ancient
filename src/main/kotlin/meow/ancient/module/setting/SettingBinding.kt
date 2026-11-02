package meow.ancient.module.setting

import com.google.gson.JsonElement

enum class SettingBindMode(val displayName: String) {
    HOLD("Hold"),
    TOGGLE("Toggle"),
    INVERSE_HOLD("Inverse Hold"),
    ALWAYS("Always")
}

data class SettingBinding(
    val key: Int,
    val mode: SettingBindMode,
    val value: JsonElement
)
