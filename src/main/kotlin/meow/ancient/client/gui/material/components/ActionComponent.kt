package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.ActionSetting
import org.joml.Matrix4f

class ActionComponent(private val actionSetting: ActionSetting) : SettingComponent(actionSetting) {
    override fun render(matrix: Matrix4f, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, dt: Float): Float {
        this.x = x
        this.y = y
        this.width = width
        height = 30f
        val palette = M3Theme.current
        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        RenderUtils.roundedRect(matrix, x + 3f, y + 3f, width - 6f, 24f,
            if (hovered) palette.primaryContainer else palette.surfaceContainerHigh, 7f, ClientRenderPipeline.WINDOW_RECT)
        val title = fitText(setting.name, width - 18f, 9f, 7.5f)
        Fonts.google_sans_medium.drawCenteredString(matrix, title.text, x + width * 0.5f, y + 10f, title.size,
            if (hovered) palette.onPrimaryContainer else palette.primary, false, ClientRenderPipeline.WINDOW_TEXT)
        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !isHovered(mouseX, mouseY, x, y, width, height)) return false
        actionSetting.action()
        return true
    }
}
