package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.PickerEntry
import meow.ancient.module.setting.PickerSetting
import org.joml.Matrix4f

class PickerComponent(private val pickerSetting: PickerSetting<*>) : SettingComponent(pickerSetting) {
    private var selection: Set<String>? = null
    private val previews = ArrayList<PickerEntry<*>>(3)

    override fun render(matrix: Matrix4f, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, dt: Float): Float {
        this.x = x
        this.y = y
        this.width = width
        if (selection !== pickerSetting.value) {
            selection = pickerSetting.value
            previews.clear()
            for (id in pickerSetting.value) {
                if (previews.size == 3) break
                previews.add(pickerSetting.catalog.find(id) ?: PickerEntry<Any>(id, "Missing: $id", null, null))
            }
        }
        height = 24f + if (previews.isEmpty()) 14f else previews.size * 19f
        val palette = M3Theme.current
        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        RenderUtils.roundedRect(matrix, x, y, width, height, if (hovered) palette.surfaceContainerHigh else palette.surfaceContainer, 6f, ClientRenderPipeline.WINDOW_RECT)
        val title = fitText(pickerSetting.name, width - 32f, 9.5f, 8f)
        Fonts.google_sans_medium.drawString(matrix, title.text, x + 5f, y + 5f, title.size, palette.onSurface, false, ClientRenderPipeline.WINDOW_TEXT)
        Fonts.google_sans_medium.drawRightString(matrix, "${pickerSetting.value.size} ›", x + width - 5f, y + 5f, 8.5f, palette.primary, false, ClientRenderPipeline.WINDOW_TEXT)
        if (previews.isEmpty()) {
            Fonts.google_sans_regular.drawString(matrix, "No entries selected", x + 5f, y + 23f, 7.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.WINDOW_TEXT)
        } else {
            for ((index, entry) in previews.withIndex()) {
                val rowY = y + 23f + index * 19f
                RenderUtils.currentGraphics?.let { entry.preview?.render(it, (x + 4f).toInt(), rowY.toInt(), 15) }
                val label = fitText(entry.label, width - 27f, 8f, 7f)
                Fonts.google_sans_regular.drawString(matrix, label.text, x + 23f, rowY + 3f, label.size, palette.onSurfaceVariant, false, ClientRenderPipeline.WINDOW_TEXT)
            }
        }
        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !isHovered(mouseX, mouseY, x, y, width, height)) return false
        ClickGuiScreen.openPicker(PickerWindow(pickerSetting))
        return true
    }
}
