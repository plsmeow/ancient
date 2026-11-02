package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.bind.SettingBindingManager
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.KeybindSetting
import meow.ancient.module.setting.Setting
import meow.ancient.module.setting.SettingBindMode
import meow.ancient.module.setting.SettingBinding
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.joml.Matrix4f

class SettingBindingWindow(val setting: Setting<*>, private var x: Float, private var y: Float) {
    private val valueSetting = SettingBindingManager.createValueSetting(setting)
    private val editor = SettingComponentFactory.create(valueSetting)
    private var key = setting.binding?.key ?: InputConstants.UNKNOWN.value
    private var mode = setting.binding?.mode ?: SettingBindMode.HOLD
    private var dirty = false
    private var width = 280f
    private var height = 248f
    private var contentHeight = 46f
    private var scroll = 0f
    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var editorCaptured = false
    var isListening = false
        private set
    var closed = false
        private set
    private val contentY get() = y + 161f
    private val contentWidth get() = (width - 28f).coerceAtLeast(1f)
    private val contentHeightVisible get() = (height - 201f).coerceAtLeast(1f)

    init {
        valueSetting.addListener { dirty = true }
        bounds()
    }

    fun owns(candidate: Setting<*>): Boolean = candidate === valueSetting

    private fun bounds() {
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()
        width = minOf(280f, (screenW - 16f).coerceAtLeast(80f))
        height = minOf(201f + contentHeight.coerceAtLeast(30f), 380f, (screenH - 16f).coerceAtLeast(210f))
        x = x.coerceIn(0f, (screenW - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenH - height).coerceAtLeast(0f))
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float, interactive: Boolean) {
        bounds()
        val palette = M3Theme.current
        RenderUtils.currentGraphics?.nextStratum()
        RenderUtils.rect(matrix, 0f, 0f, mc.window.guiScaledWidth.toFloat(), mc.window.guiScaledHeight.toFloat(), palette.scrim.with(a = 130), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surface, 12f, ClientRenderPipeline.POPUP_RECT)
        ScissorUtil.withScissor(x + 12f, y + 8f, (width - 44f).coerceAtLeast(1f), 20f) {
            Fonts.google_sans_medium.drawString(matrix, "${setting.name} · Bind", x + 14f, y + 12f, 11f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        }
        Fonts.google_sans_medium.drawCenteredString(matrix, "×", x + width - 20f, y + 9f, 15f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
        val keyHovered = interactive && over(mouseX.toDouble(), mouseY.toDouble(), x + 12f, y + 35f, width - 24f, 29f)
        RenderUtils.roundedRect(matrix, x + 12f, y + 35f, width - 24f, 29f, when {
            isListening -> palette.primaryContainer
            keyHovered -> palette.surfaceContainerHighest
            else -> palette.surfaceContainer
        }, 7f, ClientRenderPipeline.POPUP_RECT)
        val keyText = if (isListening) "Press a key or mouse button…" else "Key · ${KeybindSetting.formatKey(key)}   ›"
        ScissorUtil.withScissor(x + 18f, y + 40f, (width - 36f).coerceAtLeast(1f), 20f) {
            Fonts.google_sans_medium.drawString(matrix, keyText, x + 20f, y + 44f, 10f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
        }
        Fonts.google_sans_medium.drawString(matrix, "Mode · ${mode.displayName}", x + 14f, y + 73f, 9f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        val modeWidth = (width - 28f) * 0.5f
        for ((index, option) in SettingBindMode.entries.withIndex()) {
            val mx = x + 12f + index % 2 * (modeWidth + 4f)
            val my = y + 89f + index / 2 * 26f
            val selected = mode == option
            val hovered = interactive && over(mouseX.toDouble(), mouseY.toDouble(), mx, my, modeWidth, 22f)
            RenderUtils.roundedRect(matrix, mx, my, modeWidth, 22f, when {
                selected -> palette.primary
                hovered -> palette.surfaceContainerHighest
                else -> palette.surfaceContainerHigh
            }, 5f, ClientRenderPipeline.POPUP_RECT)
            ScissorUtil.withScissor(mx + 2f, my, (modeWidth - 4f).coerceAtLeast(1f), 22f) {
                Fonts.google_sans_medium.drawCenteredString(matrix, option.displayName, mx + modeWidth * 0.5f, my + 6f, 9f, if (selected) palette.onPrimary else palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
            }
        }
        Fonts.google_sans_medium.drawString(matrix, "Active value", x + 14f, y + 146f, 9f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        RenderUtils.roundedRect(matrix, x + 10f, contentY - 3f, width - 20f, contentHeightVisible + 6f, palette.surfaceContainerLowest, 6f, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.currentGraphics?.nextStratum()
        ScissorUtil.withScissor(x + 14f, contentY, contentWidth, contentHeightVisible) {
            val hovered = interactive && !isListening && overContent(mouseX.toDouble(), mouseY.toDouble())
            contentHeight = editor.render(matrix, x + 14f, contentY - scroll, contentWidth, if (hovered) mouseX else Int.MIN_VALUE, if (hovered) mouseY else Int.MIN_VALUE, dt)
        }
        if (maxScroll() > 0f) {
            val thumbHeight = (contentHeightVisible * contentHeightVisible / contentHeight).coerceIn(minOf(12f, contentHeightVisible), contentHeightVisible)
            val thumbY = contentY + (contentHeightVisible - thumbHeight) * scroll / maxScroll()
            RenderUtils.roundedRect(matrix, x + width - 10f, thumbY, 2f, thumbHeight, palette.primary.with(a = 160), 1f, ClientRenderPipeline.POPUP_RECT)
        }
        val footerY = y + height - 29f
        RenderUtils.roundedRect(matrix, x + width - 67f, footerY, 55f, 20f, palette.primary, 5f, ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_medium.drawCenteredString(matrix, "Done", x + width - 39.5f, footerY + 5.5f, 9f, palette.onPrimary, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_medium.drawString(matrix, "Remove bind", x + 14f, footerY + 5.5f, 9f, if (setting.binding == null) palette.onSurfaceVariant else palette.error, false, ClientRenderPipeline.POPUP_TEXT)
    }

    fun mouseClicked(event: MouseButtonEvent, button: Int) {
        bounds()
        if (!over(event.x(), event.y(), x, y, width, height)) {
            close()
            return
        }
        if (button == 0 && over(event.x(), event.y(), x + width - 34f, y + 4f, 30f, 26f)) {
            close()
            return
        }
        if (isListening) {
            key = KeybindSetting.fromMouseButton(event.button())
            isListening = false
            dirty = true
            commit()
            return
        }
        CustomTextField.blurOutside(event.x(), event.y())
        if (button == 0 && over(event.x(), event.y(), x + 12f, y + 35f, width - 24f, 29f)) {
            releaseEditorInput()
            isListening = true
            return
        }
        if (button == 0 && over(event.x(), event.y(), x + width - 67f, y + height - 29f, 55f, 20f)) {
            close()
            return
        }
        if (button == 0 && over(event.x(), event.y(), x + 12f, y + height - 31f, width - 87f, 24f)) {
            releaseEditorInput(applyNumber = false)
            dirty = false
            SettingBindingManager.clear(setting)
            ConfigManager.saveConfig("default")
            closed = true
            return
        }
        if (button == 0) {
            val modeWidth = (width - 28f) * 0.5f
            for ((index, option) in SettingBindMode.entries.withIndex()) {
                if (over(event.x(), event.y(), x + 12f + index % 2 * (modeWidth + 4f), y + 89f + index / 2 * 26f, modeWidth, 22f)) {
                    releaseEditorInput()
                    mode = option
                    dirty = true
                    commit()
                    return
                }
            }
            if (over(event.x(), event.y(), x, y, width - 36f, 30f)) {
                releaseEditorInput()
                dragging = true
                dragX = event.x().toFloat() - x
                dragY = event.y().toFloat() - y
                return
            }
        }
        if (overContent(event.x(), event.y())) {
            editorCaptured = editor.mouseClicked(event.x(), event.y(), button, event.modifiers())
            commit()
        } else {
            releaseEditorInput()
            commit()
        }
    }

    fun mouseReleased(event: MouseButtonEvent, button: Int) {
        dragging = false
        editor.mouseReleased(event.x(), event.y(), button)
        editorCaptured = false
        commit()
    }

    fun mouseDragged(event: MouseButtonEvent, button: Int, deltaX: Double, deltaY: Double) {
        if (dragging && button == 0) {
            x = event.x().toFloat() - dragX
            y = event.y().toFloat() - dragY
            bounds()
        } else if (editorCaptured) {
            editor.mouseDragged(event.x(), event.y(), button, deltaX, deltaY)
        }
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double) {
        if (overContent(mouseX, mouseY)) {
            releaseEditorInput()
            scroll = (scroll - delta.toFloat() * 22f).coerceIn(0f, maxScroll())
            commit()
        }
    }

    fun keyPressed(event: KeyEvent) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            close(applyNumber = false)
            return
        }
        if (isListening) {
            key = event.key()
            isListening = false
            dirty = true
            commit()
            return
        }
        editor.keyPressed(event.key(), event.keycode(), event.modifiers())
        commit()
    }

    fun charTyped(event: CharacterEvent) {
        if (!isListening) {
            editor.charTyped(event)
            commit()
        }
    }

    fun releaseEditorInput(exceptNumber: NumberComponent? = null, applyNumber: Boolean = true) {
        isListening = false
        dragging = false
        editorCaptured = false
        editor.mouseReleased(-1.0, -1.0, 0)
        when (val component = editor) {
            is NumberComponent -> if (component !== exceptNumber) component.stopEditing(apply = applyNumber)
            is TextComponent -> component.blur()
            is KeybindComponent -> component.stopListening()
        }
    }

    fun commit() {
        if (!dirty) return
        SettingBindingManager.configure(setting, SettingBinding(key, mode, valueSetting.serialize()))
        ConfigManager.saveConfig("default")
        dirty = false
    }

    fun close(applyNumber: Boolean = true) {
        if (closed) return
        releaseEditorInput(applyNumber = applyNumber)
        commit()
        closed = true
    }

    private fun maxScroll(): Float = (contentHeight - contentHeightVisible).coerceAtLeast(0f)

    private fun overContent(mouseX: Double, mouseY: Double): Boolean = over(mouseX, mouseY, x + 14f, contentY, contentWidth, contentHeightVisible)

    private fun over(mouseX: Double, mouseY: Double, x: Float, y: Float, width: Float, height: Float): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
}
