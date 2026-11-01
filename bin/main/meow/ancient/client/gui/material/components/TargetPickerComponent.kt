package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.TargetSetting
import org.joml.Matrix4f

class TargetPickerComponent(val targetSetting: TargetSetting) : SettingComponent(targetSetting) {
    override fun render(matrix: Matrix4f, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, dt: Float): Float {
        this.x = x
        this.y = y
        this.width = width
        this.height = 24f

        val palette = M3Theme.current
        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        RenderUtils.roundedRect(matrix, x, y, width, height, if (hovered) palette.surfaceContainerHigh else palette.surfaceContainer, 6f, ClientRenderPipeline.WINDOW_RECT)

        val countText = "${targetSetting.value.size} Selected ›"
        val countW = Fonts.google_sans_medium.width(countText, 8.5f)
        val maxTextW = width - countW - 14f
        val title = fitText(targetSetting.name, maxTextW, 9.5f, 8f)

        Fonts.google_sans_medium.drawString(matrix, title.text, x + 5f, y + 6f, title.size, palette.onSurface, false, ClientRenderPipeline.WINDOW_TEXT)
        Fonts.google_sans_medium.drawRightString(matrix, countText, x + width - 5f, y + 6.5f, 8.5f, palette.primary, false, ClientRenderPipeline.WINDOW_TEXT)

        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !isHovered(mouseX, mouseY, x, y, width, height)) return false
        ClickGuiScreen.openTargetPicker(TargetPickerWindow(targetSetting))
        return true
    }
}
