package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.Curve
import meow.ancient.module.setting.CurveInterpolation
import meow.ancient.module.setting.CurveSetting
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.joml.Matrix4f
import java.util.Locale

class CurveEditorWindow(private val setting: CurveSetting) : EditorWindow {
    override var closed = false
        private set
    private val undo = ArrayDeque<Curve>()
    private val redo = ArrayDeque<Curve>()
    private var beforeEdit: Curve? = null
    private var dirty = false
    private val graph = CurveGraph(setting, ::beginEdit, { dirty = true }, ::commitEdit)
    private val xInput = CustomTextField(maxLength = 12, hint = "X") { text ->
        val number = text.replace(',', '.').toFloatOrNull()
        val point = graph.selectedPoint
        if (number != null && number.isFinite() && point != null) {
            beginEdit()
            graph.moveSelected(number, point.y)
        }
    }
    private val yInput = CustomTextField(maxLength = 12, hint = "Y") { text ->
        val number = text.replace(',', '.').toFloatOrNull()
        val point = graph.selectedPoint
        if (number != null && number.isFinite() && point != null) {
            beginEdit()
            graph.moveSelected(point.x, number)
        }
    }
    private var x = -1f
    private var y = -1f
    private var width = 0f
    private var height = 0f
    private var draggingWindow = false
    private var dragX = 0f
    private var dragY = 0f
    private var inputCurve: Curve? = null
    private var inputIndex = -2
    private val columns get() = if (width >= 420f) 4 else 2
    private val presetRows get() = (Curve.presets.size + columns - 1) / columns
    private val presetY get() = y + height - presetRows * 25f - 12f
    private val numericY get() = presetY - 32f
    private val graphX get() = x + 34f
    private val graphY get() = y + 84f
    private val graphWidth get() = width - 58f
    private val graphHeight get() = (numericY - graphY - 30f).coerceAtLeast(48f)
    private val chipWidth get() = (width - 28f - (columns - 1) * 5f) / columns

    private fun bounds() {
        val screenWidth = mc.window.guiScaledWidth.toFloat()
        val screenHeight = mc.window.guiScaledHeight.toFloat()
        width = minOf(560f, screenWidth - 20f).coerceAtLeast(220f)
        height = minOf(410f, screenHeight - 20f).coerceAtLeast(250f)
        if (x < 0f) x = (screenWidth - width) * 0.5f
        if (y < 0f) y = (screenHeight - height) * 0.5f
        x = x.coerceIn(0f, (screenWidth - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenHeight - height).coerceAtLeast(0f))
    }

    override fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        bounds()
        val palette = M3Theme.current
        RenderUtils.rect(matrix, 0f, 0f, mc.window.guiScaledWidth.toFloat(), mc.window.guiScaledHeight.toFloat(), palette.scrim.alpha(140), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surface, 12f, ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_medium.drawString(matrix, "${setting.name} · Curve Editor", x + 14f, y + 12f, 11f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_medium.drawCenteredString(matrix, "×", x + width - 19f, y + 8f, 16f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        for (i in CurveInterpolation.entries.indices) {
            val mode = CurveInterpolation.entries[i]
            button(matrix, mode.displayName, x + 14f + i * 67f, y + 35f, 62f, 21f,
                mode == setting.value.interpolation, mouseX, mouseY)
        }
        button(matrix, "Сброс", x + width - 70f, y + 35f, 56f, 21f, false, mouseX, mouseY)
        val hint = if (width >= 420f) "ЛКМ — добавить / переместить · ПКМ — удалить · стрелки — точная правка" else "ЛКМ: добавить / двигать · ПКМ: удалить"
        Fonts.google_sans_regular.drawString(matrix, hint, x + 14f, y + 64f, 8f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        graph.render(matrix, graphX, graphY, graphWidth, graphHeight, mouseX, mouseY, true)
        for (i in 0..4) {
            val label = when (i) { 0 -> "0"; 1 -> ".25"; 2 -> ".5"; 3 -> ".75"; else -> "1" }
            Fonts.google_sans_regular.drawCenteredString(matrix, label, graphX + graphWidth * i / 4f, graphY + graphHeight + 9f, 7.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
            Fonts.google_sans_regular.drawRightString(matrix, label, graphX - 11f, graphY + graphHeight * (1f - i / 4f) - 3f, 7.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        }
        Fonts.google_sans_regular.drawCenteredString(matrix, setting.xLabel, graphX + graphWidth * 0.5f, graphY + graphHeight + 20f, 8f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        syncInputs()
        if (graph.selectedPoint != null) {
            Fonts.google_sans_medium.drawString(matrix, "#${graph.selectedIndex + 1}", x + 14f, numericY + 6f, 9f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
            xInput.setBounds(x + 44f, numericY, 62f, 22f)
            yInput.setBounds(x + 111f, numericY, 62f, 22f)
            xInput.render(matrix, dt, popup = true)
            yInput.render(matrix, dt, popup = true)
            Fonts.google_sans_regular.drawString(matrix, setting.yLabel, x + 180f, numericY + 7f, 8f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        } else {
            Fonts.google_sans_regular.drawString(matrix, "Выберите точку · ${setting.value.points.size}/${setting.maxPoints}", x + 14f, numericY + 7f, 8.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        }
        if (width >= 360f) {
            button(matrix, "↶", x + width - 71f, numericY, 26f, 22f, false, mouseX, mouseY)
            button(matrix, "↷", x + width - 40f, numericY, 26f, 22f, false, mouseX, mouseY)
        }
        for (i in Curve.presets.indices) {
            val preset = Curve.presets[i]
            button(matrix, preset.first, x + 14f + (i % columns) * (chipWidth + 5f), presetY + (i / columns) * 25f,
                chipWidth, 21f, setting.value == preset.second, mouseX, mouseY)
        }
    }

    private fun button(matrix: Matrix4f, text: String, x: Float, y: Float, w: Float, h: Float, selected: Boolean, mouseX: Int, mouseY: Int) {
        val palette = M3Theme.current
        val hovered = over(mouseX.toDouble(), mouseY.toDouble(), x, y, w, h)
        RenderUtils.roundedRect(matrix, x, y, w, h, when {
            selected -> palette.primaryContainer
            hovered -> palette.surfaceContainerHigh
            else -> palette.surfaceContainer
        }, 6f, ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_medium.drawCenteredString(matrix, text, x + w * 0.5f, y + 6f, 8.5f,
            if (selected) palette.onPrimaryContainer else palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
    }

    override fun mouseClicked(event: MouseButtonEvent, button: Int) {
        bounds()
        val mx = event.x()
        val my = event.y()
        if (!over(mx, my, x, y, width, height)) { close(); return }
        if (button == 0 && over(mx, my, x + width - 34f, y + 3f, 30f, 27f)) { close(); return }
        CustomTextField.blurOutside(mx, my)
        if (graph.selectedPoint != null && (xInput.mouseClicked(mx, my, button) || yInput.mouseClicked(mx, my, button))) return
        commitEdit()
        if (button == 0) {
            for (i in CurveInterpolation.entries.indices) {
                if (over(mx, my, x + 14f + i * 67f, y + 35f, 62f, 21f)) {
                    applyCurve(setting.value.withInterpolation(CurveInterpolation.entries[i]))
                    return
                }
            }
            if (over(mx, my, x + width - 70f, y + 35f, 56f, 21f)) { applyCurve(setting.defaultValue); return }
            if (width >= 360f && over(mx, my, x + width - 71f, numericY, 26f, 22f)) { restoreHistory(undo, redo); return }
            if (width >= 360f && over(mx, my, x + width - 40f, numericY, 26f, 22f)) { restoreHistory(redo, undo); return }
            for (i in Curve.presets.indices) {
                if (over(mx, my, x + 14f + (i % columns) * (chipWidth + 5f), presetY + (i / columns) * 25f, chipWidth, 21f)) {
                    applyCurve(Curve.presets[i].second)
                    return
                }
            }
            if (over(mx, my, x, y, width - 35f, 30f)) {
                draggingWindow = true
                dragX = mx.toFloat() - x
                dragY = my.toFloat() - y
                return
            }
        }
        graph.mouseClicked(mx, my, button)
        inputIndex = -2
    }

    override fun mouseDragged(event: MouseButtonEvent, button: Int, deltaX: Double, deltaY: Double) {
        if (button != 0) return
        if (draggingWindow) {
            x = event.x().toFloat() - dragX
            y = event.y().toFloat() - dragY
            bounds()
        } else if (!xInput.mouseDragged(event.x(), button) && !yInput.mouseDragged(event.x(), button)) {
            graph.mouseDragged(event.x(), event.y())
        }
    }

    override fun mouseReleased(event: MouseButtonEvent, button: Int) {
        if (button != 0) return
        draggingWindow = false
        graph.release()
        xInput.mouseReleased(button)
        yInput.mouseReleased(button)
        if (!xInput.isFocused && !yInput.isFocused) commitEdit()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double) {
        val point = graph.selectedPoint ?: return
        if (!over(mouseX, mouseY, graphX, graphY, graphWidth, graphHeight)) return
        beginEdit()
        graph.moveSelected(point.x, point.y + delta.toFloat() * 0.005f)
        commitEdit()
    }

    override fun keyPressed(event: KeyEvent) {
        if (xInput.keyPressed(event.key(), event.keycode(), event.modifiers()) || yInput.keyPressed(event.key(), event.keycode(), event.modifiers())) {
            if (!xInput.isFocused && !yInput.isFocused) { commitEdit(); inputIndex = -2 }
            return
        }
        if (event.key() == InputConstants.KEY_ESCAPE) { close(); return }
        if (event.modifiers() and InputConstants.MOD_CONTROL != 0) {
            if (event.key() == InputConstants.KEY_Z) { restoreHistory(undo, redo); return }
            if (event.key() == InputConstants.KEY_Y) { restoreHistory(redo, undo); return }
        }
        graph.keyPressed(event.key(), event.modifiers())
    }

    override fun charTyped(event: CharacterEvent) {
        CustomTextField.charTypedFocused(event)
    }

    private fun beginEdit() {
        if (beforeEdit == null) beforeEdit = setting.value
    }

    private fun commitEdit() {
        val previous = beforeEdit ?: return
        beforeEdit = null
        if (previous != setting.value) {
            undo.addLast(previous)
            if (undo.size > 32) undo.removeFirst()
            redo.clear()
            dirty = true
        }
        if (dirty) { ConfigManager.saveCurrentConfig(); dirty = false }
    }

    private fun applyCurve(curve: Curve) {
        beginEdit()
        setting.set(curve)
        graph.clearSelection()
        commitEdit()
    }

    private fun restoreHistory(source: ArrayDeque<Curve>, destination: ArrayDeque<Curve>) {
        releaseInput()
        if (source.isEmpty()) return
        destination.addLast(setting.value)
        setting.set(source.removeLast())
        graph.clearSelection()
        ConfigManager.saveCurrentConfig()
    }

    private fun syncInputs() {
        if (inputCurve === setting.value && inputIndex == graph.selectedIndex) return
        val point = graph.selectedPoint
        if (point != null) {
            if (!xInput.isFocused) xInput.setText(String.format(Locale.ROOT, "%.3f", point.x))
            if (!yInput.isFocused) yInput.setText(String.format(Locale.ROOT, "%.3f", point.y))
        }
        inputCurve = setting.value
        inputIndex = graph.selectedIndex
    }

    override fun releaseInput() {
        draggingWindow = false
        graph.release()
        xInput.blur()
        yInput.blur()
        commitEdit()
    }

    override fun close() {
        if (closed) return
        releaseInput()
        closed = true
    }

    private fun over(mx: Double, my: Double, x: Float, y: Float, w: Float, h: Float): Boolean =
        mx >= x && mx <= x + w && my >= y && my <= y + h
}
