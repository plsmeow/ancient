package meow.ancient.module.modules.render

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.module.Module

object ClickGuiModule : Module(
    name = "ClickGUI",
    category = RENDER,
    description = "Material 3 DropDown Graphical User Interface",
    key = InputConstants.KEY_RSHIFT
) {
    val blur by boolean(
        name = "Background Scrim",
        value = true,
        description = "Dim background behind the GUI"
    )

    val animations by boolean(
        name = "Animations",
        value = true,
        description = "Fly-in open and fly-out close animations"
    )

    override fun toggle() {
        if (state && mc.gui.screen() === ClickGuiScreen) {
            ClickGuiScreen.onClose()
            return
        }
        super.toggle()
    }

    override fun onEnable() {
        val screen = mc.gui.screen()
        if (screen !== ClickGuiScreen) {
            ClickGuiScreen.parentScreen = screen
        }
        mc.gui.setScreen(ClickGuiScreen)
    }

    override fun onDisable() {
        if (mc.gui.screen() === ClickGuiScreen) {
            val parent = ClickGuiScreen.parentScreen
            ClickGuiScreen.parentScreen = null
            mc.gui.setScreen(parent)
        }
    }
}
