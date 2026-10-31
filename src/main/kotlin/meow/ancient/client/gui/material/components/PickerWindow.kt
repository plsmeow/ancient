package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.PickerEntry
import meow.ancient.module.setting.PickerSetting
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.joml.Matrix4f
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

class PickerWindow(val setting: PickerSetting<*>) {
    private val searchField = CustomTextField(hint = "Search name or namespace:id") {
        query = it.lowercase(Locale.ROOT)
        rebuild()
        scroll.fill(0f)
    }
    var closed = false
        private set
    private var x = 0f
    private var y = 0f
    private var width = 0f
    private var height = 0f
    private var positioned = false
    private var query = ""
    private var selection: Set<String>? = null
    private val inactive = ArrayList<PickerEntry<*>>()
    private val active = ArrayList<PickerEntry<*>>()
    private val scroll = FloatArray(2)
    private var drag = -1
    private var dragX = 0f
    private var dragY = 0f
    private var thumbOffset = 0f
    private val rowHeight = 29f
    private val columnWidth get() = (width - 26f) * 0.5f
    private val listY get() = y + 94f
    private val listHeight get() = (height - 104f).coerceAtLeast(1f)

    init {
        bounds()
        rebuild()
    }

    private fun bounds() {
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()
        width = minOf(540f, (screenW - 16f).coerceAtLeast(80f))
        height = minOf(380f, (screenH - 16f).coerceAtLeast(118f))
        if (!positioned) {
            x = (screenW - width) * 0.5f
            y = (screenH - height) * 0.5f
            positioned = true
        }
        x = x.coerceIn(0f, (screenW - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenH - height).coerceAtLeast(0f))
        searchField.setBounds(x + 9f, y + 34f, width - 18f, 29f)
        clampScroll()
    }

    private fun rebuild() {
        selection = setting.value
        inactive.clear()
        active.clear()
        for (entry in setting.catalog.entries) {
            if (!entry.matches(query)) continue
            if (setting.isSelected(entry.id)) active.add(entry) else inactive.add(entry)
        }
        for (id in setting.value) {
            if (setting.catalog.find(id) != null) continue
            val entry = PickerEntry<Any>(id, "Missing registry entry", null, null)
            if (entry.matches(query)) active.add(entry)
        }
        clampScroll()
    }

    private fun entries(column: Int): List<PickerEntry<*>> = if (column == 0) inactive else active
    private fun columnX(column: Int): Float = x + 9f + column * (columnWidth + 8f)
    private fun maxScroll(column: Int): Float = (entries(column).size * rowHeight - listHeight).coerceAtLeast(0f)
    private fun clampScroll() {
        for (column in 0..1) scroll[column] = scroll[column].coerceIn(0f, maxScroll(column))
    }

    fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        bounds()
        if (selection !== setting.value) rebuild()
        val palette = M3Theme.current
        RenderUtils.currentGraphics?.nextStratum()
        RenderUtils.rect(matrix, 0f, 0f, mc.window.guiScaledWidth.toFloat(), mc.window.guiScaledHeight.toFloat(), palette.scrim.with(a = 130), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surface, 12f, ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_medium.drawString(matrix, "${setting.name} · ${setting.catalog.title}", x + 14f, y + 12f, 11f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_medium.drawCenteredString(matrix, "×", x + width - 20f, y + 9f, 15f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
        searchField.render(matrix, dt, popup = true)
        Fonts.google_sans_medium.drawString(matrix, "Inactive (${inactive.size})", columnX(0) + 4f, y + 75f, 9f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_medium.drawString(matrix, "Active (${active.size}/${setting.value.size})", columnX(1) + 4f, y + 75f, 9f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_medium.drawRightString(matrix, "Clear", x + width - 14f, y + 75f, 8.5f, palette.error, false, ClientRenderPipeline.POPUP_TEXT)
        for (column in 0..1) renderColumn(matrix, column, mouseX, mouseY)
    }

    private fun renderColumn(matrix: Matrix4f, column: Int, mouseX: Int, mouseY: Int) {
        val palette = M3Theme.current
        val cx = columnX(column)
        val rows = entries(column)
        RenderUtils.roundedRect(matrix, cx, listY, columnWidth, listHeight, palette.surfaceContainer, 7f, ClientRenderPipeline.POPUP_RECT)
        ScissorUtil.withScissor(cx + 2f, listY, columnWidth - 9f, listHeight) {
            val first = floor(scroll[column] / rowHeight).toInt()
            val end = minOf(rows.size, ceil((scroll[column] + listHeight) / rowHeight).toInt())
            for (index in first until end) {
                val entry = rows[index]
                val rowY = listY + index * rowHeight - scroll[column]
                if (over(mouseX.toDouble(), mouseY.toDouble(), cx, rowY, columnWidth - 8f, rowHeight)) {
                    RenderUtils.roundedRect(matrix, cx + 2f, rowY + 1f, columnWidth - 11f, rowHeight - 2f, palette.stateHover, 4f, ClientRenderPipeline.POPUP_RECT)
                }
                val cbX = cx + 6f
                val cbY = rowY + 7.5f
                val cbSize = 13f
                if (column == 1) {
                    RenderUtils.roundedRect(matrix, cbX, cbY, cbSize, cbSize, palette.primary, 3f, ClientRenderPipeline.POPUP_RECT)
                    Fonts.google_sans_medium.drawCenteredString(matrix, "✓", cbX + cbSize * 0.5f, cbY + 0.5f, 9f, palette.onPrimary, false, ClientRenderPipeline.POPUP_TEXT)
                } else {
                    RenderUtils.roundedRectWithBorder(matrix, cbX, cbY, cbSize, cbSize, palette.surfaceContainerLow, 3f, 1f, palette.outlineVariant, ClientRenderPipeline.POPUP_RECT)
                }
                if (entry.preview != null) {
                    RenderUtils.currentGraphics?.let { entry.preview.render(it, (cx + 23f).toInt(), (rowY + 5f).toInt(), 18) }
                }
                val textX = if (entry.preview != null) cx + 45f else cx + 24f
                val textWidth = columnWidth - (if (entry.preview != null) 55f else 34f)
                drawClippedText(matrix, entry.label, textX, rowY + 4f, textWidth, 8.5f, false)
                drawClippedText(matrix, entry.id, textX, rowY + 16f, textWidth, 6.5f, true)
            }
            if (rows.isEmpty()) {
                Fonts.google_sans_regular.drawString(matrix, "No matches", cx + 8f, listY + 10f, 8.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
            }
        }
        if (maxScroll(column) > 0f) {
            val thumbHeight = thumbHeight(column)
            val thumbY = listY + scroll[column] / maxScroll(column) * (listHeight - thumbHeight)
            RenderUtils.roundedRect(matrix, cx + columnWidth - 5f, thumbY, 3f, thumbHeight, palette.primary.with(a = 160), 1.5f, ClientRenderPipeline.POPUP_RECT)
        }
    }

    private fun drawClippedText(matrix: Matrix4f, text: String, x: Float, y: Float, width: Float, size: Float, secondary: Boolean) {
        val palette = M3Theme.current
        ScissorUtil.withScissor(x, y, width.coerceAtLeast(1f), 11f) {
            Fonts.google_sans_regular.drawString(matrix, text, x, y, size, if (secondary) palette.onSurfaceVariant else palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        }
    }

    private fun thumbHeight(column: Int): Float = (listHeight * listHeight / (entries(column).size * rowHeight)).coerceIn(minOf(18f, listHeight), listHeight)

    fun mouseClicked(event: MouseButtonEvent, button: Int) {
        bounds()
        if (!over(event.x(), event.y(), x, y, width, height)) {
            close()
            return
        }
        if (button != 0) return
        if (over(event.x(), event.y(), x + width - 34f, y + 4f, 30f, 26f)) {
            close()
            return
        }
        CustomTextField.blurOutside(event.x(), event.y())
        if (searchField.mouseClicked(event.x(), event.y(), button)) return
        if (over(event.x(), event.y(), x + width - 43f, y + 68f, 36f, 23f)) {
            setting.set(emptySet())
            rebuild()
            return
        }
        if (over(event.x(), event.y(), x, y, width - 36f, 30f)) {
            drag = 2
            dragX = event.x().toFloat() - x
            dragY = event.y().toFloat() - y
            return
        }
        for (column in 0..1) {
            val cx = columnX(column)
            if (!over(event.x(), event.y(), cx, listY, columnWidth, listHeight)) continue
            if (event.x() >= cx + columnWidth - 8f && maxScroll(column) > 0f) {
                drag = column
                val thumbY = listY + scroll[column] / maxScroll(column) * (listHeight - thumbHeight(column))
                thumbOffset = if (event.y() in thumbY.toDouble()..(thumbY + thumbHeight(column)).toDouble()) event.y().toFloat() - thumbY else thumbHeight(column) * 0.5f
                dragScroll(event.y(), column)
            } else {
                val index = floor((event.y() - listY + scroll[column]) / rowHeight).toInt()
                entries(column).getOrNull(index)?.let { setting.toggle(it.id) }
                rebuild()
            }
            return
        }
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double) {
        for (column in 0..1) {
            if (over(mouseX, mouseY, columnX(column), listY, columnWidth, listHeight)) {
                scroll[column] = (scroll[column] - delta.toFloat() * rowHeight * 2f).coerceIn(0f, maxScroll(column))
                return
            }
        }
    }

    private fun dragScroll(mouseY: Double, column: Int) {
        val track = listHeight - thumbHeight(column)
        scroll[column] = if (track <= 0f) 0f else ((mouseY.toFloat() - listY - thumbOffset) / track).coerceIn(0f, 1f) * maxScroll(column)
    }

    fun mouseDragged(event: MouseButtonEvent, button: Int, deltaX: Double, deltaY: Double) {
        if (button != 0) return
        when (drag) {
            0, 1 -> dragScroll(event.y(), drag)
            2 -> {
                x = event.x().toFloat() - dragX
                y = event.y().toFloat() - dragY
                bounds()
            }
            else -> searchField.mouseDragged(event.x(), button)
        }
    }

    fun mouseReleased(event: MouseButtonEvent) {
        drag = -1
        searchField.mouseReleased(event.button())
    }

    fun keyPressed(event: KeyEvent) {
        if (searchField.keyPressed(event.key(), event.keycode(), event.modifiers())) return
        if (event.key() == InputConstants.KEY_ESCAPE) close()
    }
    fun charTyped(event: CharacterEvent) {
        searchField.charTyped(event)
    }

    fun close() {
        closed = true
        drag = -1
        searchField.blur()
    }

    fun focusSearch() {
        searchField.focus()
    }

    private fun over(mouseX: Double, mouseY: Double, x: Float, y: Float, width: Float, height: Float): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
}
