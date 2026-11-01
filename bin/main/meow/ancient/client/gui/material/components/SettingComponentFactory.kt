package meow.ancient.client.gui.material.components

import meow.ancient.module.setting.*

object SettingComponentFactory {
    fun create(setting: Setting<*>): SettingComponent = when (setting) {
        is BooleanSetting -> BooleanComponent(setting)
        is NumberSetting -> NumberComponent(setting)
        is EnumSetting<*> -> ModeComponent(setting)
        is ModeSetting -> ModeComponent(setting)
        is MultiModeSetting -> ModeComponent(setting)
        is ColorSetting -> ColorComponent(setting)
        is KeybindSetting -> KeybindComponent(setting)
        is PickerSetting<*> -> PickerComponent(setting)
        is TextSetting -> TextComponent(setting)
        is TargetSetting -> TargetPickerComponent(setting)
    }
}
