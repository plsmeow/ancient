package meow.ancient.client.listener.listeners

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.listener.Listener
import meow.ancient.event.EventManager
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.module.ModuleManager
import meow.ancient.module.modules.render.ClickGuiModule
import meow.ancient.module.setting.KeybindSetting
import sweetie.evaware.flora.api.Commando

object InputListener : Listener() {
    override fun init() {
        EventManager.register(this)
    }

    fun mouseX(): Int = (mc.mouseHandler.xpos() / mc.window.guiScale).toInt()

    fun mouseY(): Int = (mc.mouseHandler.ypos() / mc.window.guiScale).toInt()

    @Commando
    fun onKeyPress(event: KeyPressEvent) {
        if (event.action != InputConstants.PRESS) return
        if (event.key == InputConstants.UNKNOWN.value) return

        val screen = currentScreen
        val clickGuiKey = if (ClickGuiModule.key != InputConstants.UNKNOWN.value) ClickGuiModule.key else InputConstants.KEY_RSHIFT

        if (screen === ClickGuiScreen) {
            if (event.key == clickGuiKey && !ClickGuiScreen.isBindingKey()) {
                ClickGuiModule.toggle()
                event.cancel = true
            }
            return
        }

        if (event.key == clickGuiKey) {
            ClickGuiModule.toggle()
            event.cancel = true
            return
        }

        if (screen != null) return

        for (module in ModuleManager.modules) {
            if (module !== ClickGuiModule && module.key != InputConstants.UNKNOWN.value && module.key == event.key) {
                if (KeybindSetting.isMouseButton(event.key)) {
                    event.cancel = true
                }
                module.toggle()
            }
        }
    }
}
