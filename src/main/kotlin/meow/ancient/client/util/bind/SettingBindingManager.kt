package meow.ancient.client.util.bind

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.hud.HudNotificationCenter
import meow.ancient.module.ModuleManager
import meow.ancient.module.modules.render.ClientSoundsModule
import meow.ancient.module.modules.render.HideModule
import meow.ancient.module.setting.*
import org.lwjgl.sdl.SDLMouse

data class BoundSettingEntry(
    val moduleName: String,
    val settingName: String,
    val key: Int,
    val active: Boolean,
    val visible: Boolean
)

object SettingBindingManager {
    private class State(
        val setting: Setting<*>,
        val binding: SettingBinding,
        val value: Any?,
        var down: Boolean,
        var blocked: Boolean,
        var acceptingInput: Boolean,
        var toggled: Boolean = false,
        var active: Boolean = false
    )

    private val states = ArrayList<State>()

    fun supports(setting: Setting<*>): Boolean =
        setting !is KeybindSetting && setting !is CurveSetting && setting !is HitboxPointsSetting && setting !is ActionSetting

    fun configure(setting: Setting<*>, binding: SettingBinding?) {
        val index = states.indexOfFirst { it.setting === setting }
        if (binding == null || !supports(setting)) {
            if (index >= 0) states.removeAt(index)
            setting.storeBinding(null)
            setting.clearOverride()
            return
        }
        require(binding.key == InputConstants.UNKNOWN.value || validKey(binding.key))
        val editor = cloneSetting(setting)
        readValue(editor, binding.value.deepCopy())
        val definition = binding.copy(value = editor.serialize().deepCopy())
        val down = isKeyDown(definition.key)
        val allowed = inputAllowed()
        val state = State(setting, definition, editor.value, down, down, allowed)
        if (index >= 0) states[index] = state else states.add(state)
        setting.storeBinding(definition)
        setting.clearOverride()
        if (states.any { it === state }) refresh(setting, state, allowed, notify = false)
    }

    fun clear(setting: Setting<*>) {
        configure(setting, null)
    }

    fun createValueSetting(setting: Setting<*>): Setting<*> {
        require(supports(setting))
        val editor = cloneSetting(setting)
        val binding = setting.binding
        when {
            binding != null -> readValue(editor, binding.value.deepCopy())
            editor is BooleanSetting && setting is BooleanSetting -> editor.set(!setting.baseValue)
        }
        return editor
    }

    fun onKey(key: Int, action: Int) {
        if (action != InputConstants.PRESS && action != InputConstants.RELEASE) return
        if (!validKey(key)) return
        val allowed = inputAllowed()
        var index = 0
        while (index < states.size) {
            val state = states[index]
            if (sameKey(state.binding.key, key)) {
                if (allowed) state.acceptingInput = true
                transition(state, action == InputConstants.PRESS, allowed)
                refresh(state.setting, state, allowed)
            }
            if (states.getOrNull(index) === state) index++
        }
    }

    fun entries(): List<BoundSettingEntry> {
        val result = ArrayList<BoundSettingEntry>(states.size)
        for (state in states) {
            val module = ModuleManager.modules.firstOrNull { it.settings.any { setting -> setting === state.setting } }
            if (module == null) continue
            result += BoundSettingEntry(module.name, state.setting.name, state.binding.key, state.active, state.binding.visible)
        }
        return result
    }

    fun update() {
        if (states.isEmpty()) return
        val allowed = inputAllowed()
        var index = 0
        while (index < states.size) {
            val state = states[index]
            transition(state, isKeyDown(state.binding.key), allowed)
            refresh(state.setting, state, allowed)
            if (states.getOrNull(index) === state) index++
        }
    }

    private fun transition(state: State, down: Boolean, allowed: Boolean) {
        if (!allowed || !state.acceptingInput) {
            state.acceptingInput = allowed
            state.down = down
            state.blocked = down
            return
        }
        if (!down) {
            state.down = false
            state.blocked = false
            return
        }
        if (!state.down && !state.blocked && state.binding.mode == SettingBindMode.TOGGLE) {
            state.toggled = !state.toggled
        }
        state.down = true
    }

    private fun refresh(setting: Setting<*>, state: State, allowed: Boolean, notify: Boolean = true) {
        val active = when (state.binding.mode) {
            SettingBindMode.HOLD -> validKey(state.binding.key) && allowed && state.down && !state.blocked
            SettingBindMode.TOGGLE -> validKey(state.binding.key) && state.toggled
            SettingBindMode.INVERSE_HOLD -> validKey(state.binding.key) && allowed && !state.down && !state.blocked
            SettingBindMode.ALWAYS -> true
        }
        if (state.active == active) return
        state.active = active
        if (active) applyOverride(setting, state.value) else setting.clearOverride()
        if (notify) {
            ClientSoundsModule.play(active)
            if (state.binding.visible) {
                val module = ModuleManager.modules.firstOrNull { it.settings.any { candidate -> candidate === setting } }
                val name = if (module != null) "${module.name} ${setting.name}" else setting.name
                HudNotificationCenter.settingToggled(name, active)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyOverride(setting: Setting<*>, value: Any?) {
        (setting as Setting<Any?>).setOverride(value)
    }

    private fun inputAllowed(): Boolean {
        if (HideModule.isActive) return false
        val window = runCatching { mc.window }.getOrNull() ?: return false
        val isFocused = runCatching { window.isFocused }.getOrDefault(false)
        if (!isFocused) return false
        val screen = runCatching { currentScreen }.getOrNull()
        return screen == null
    }

    private fun validKey(key: Int): Boolean = key > 0 || KeybindSetting.isMouseButton(key)

    private fun sameKey(first: Int, second: Int): Boolean = first == second ||
        KeybindSetting.isMouseButton(first) && KeybindSetting.isMouseButton(second) &&
        KeybindSetting.toMouseButton(first) == KeybindSetting.toMouseButton(second)

    private fun isKeyDown(key: Int): Boolean {
        if (!validKey(key)) return false
        return runCatching {
            if (!KeybindSetting.isMouseButton(key)) InputConstants.isKeyDown(key)
            else {
                val button = KeybindSetting.toMouseButton(key)
                button in 1..8 && (SDLMouse.SDL_GetMouseState(null, null) and (1 shl (button - 1))) != 0
            }
        }.getOrDefault(false)
    }

    private fun cloneSetting(setting: Setting<*>): Setting<*> = when (setting) {
        is BooleanSetting -> BooleanSetting(setting.name, setting.value, setting.description)
        is NumberSetting -> NumberSetting(
            setting.name, setting.value, setting.min, setting.max, setting.step, setting.precision,
            setting.unit, setting.description, limitMax = setting.limitMax
        )
        is ModeSetting -> ModeSetting(setting.name, setting.value, setting.modes, setting.description)
        is MultiModeSetting -> MultiModeSetting(setting.name, setting.value.toSet(), setting.modes, setting.description)
        is EnumSetting<*> -> cloneEnum(setting)
        is PickerSetting<*> -> PickerSetting(setting.name, setting.value.toSet(), setting.catalog, setting.description)
        is TargetSetting -> TargetSetting(setting.name, setting.value.toSet(), setting.description)
        is TextSetting -> TextSetting(setting.name, setting.value, setting.maxLength, setting.description)
        is ColorSetting -> ColorSetting(setting.name, setting.value, setting.hasAlpha, setting.description)
        is KeybindSetting -> throw IllegalArgumentException("Keybind settings cannot have setting binds")
        is CurveSetting -> throw IllegalArgumentException("Curve settings cannot have setting binds")
        is HitboxPointsSetting -> throw IllegalArgumentException("Hitbox point settings cannot have setting binds")
        is ActionSetting -> throw IllegalArgumentException("Action settings cannot have setting binds")
    }
    private fun <E : Enum<E>> cloneEnum(setting: EnumSetting<E>): EnumSetting<E> = EnumSetting(
        setting.name, setting.value, setting.entries, setting.description, setting.nameMapper
    )

    private fun readValue(setting: Setting<*>, element: JsonElement) {
        when (setting) {
            is BooleanSetting -> require(element.isJsonPrimitive && element.asJsonPrimitive.isBoolean)
            is NumberSetting -> require(
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber && element.asDouble.isFinite()
            )
            is ModeSetting -> {
                require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
                val mode = setting.modes.firstOrNull { it.equals(element.asString, ignoreCase = true) }
                require(mode != null)
                setting.deserialize(JsonPrimitive(mode))
                return
            }
            is EnumSetting<*> -> {
                require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
                require(setting.entries.any {
                    it.name.equals(element.asString, ignoreCase = true) ||
                        enumName(setting, it).equals(element.asString, ignoreCase = true)
                })
            }
            is MultiModeSetting -> {
                require(stringArray(element))
                require(element.asJsonArray.all { it.asString in setting.modes })
            }
            is PickerSetting<*>, is TargetSetting -> require(stringArray(element))
            is TextSetting -> require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
            is ColorSetting -> {
                require(element.isJsonObject)
                for (channel in arrayOf("r", "g", "b", "a")) {
                    val value = element.asJsonObject.get(channel) ?: continue
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite())
                }
            }
            is KeybindSetting -> throw IllegalArgumentException("Keybind settings cannot have setting binds")
            is CurveSetting -> throw IllegalArgumentException("Curve settings cannot have setting binds")
            is HitboxPointsSetting -> throw IllegalArgumentException("Hitbox point settings cannot have setting binds")
            is ActionSetting -> throw IllegalArgumentException("Action settings cannot have setting binds")
        }
        setting.deserialize(element)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <E : Enum<E>> enumName(setting: EnumSetting<E>, entry: Enum<*>): String =
        setting.nameMapper(entry as E)

    private fun stringArray(element: JsonElement): Boolean = element.isJsonArray &&
        element.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }
}
