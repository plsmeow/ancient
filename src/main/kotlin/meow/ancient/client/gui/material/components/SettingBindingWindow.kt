package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.AnimFloat
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
    private var visible = setting.binding?.visible ?: true
    private val visibleAnim = AnimFloat(if (visible) 1f else 0f, if (visible) 1f else 0f, 16f)
    private val modeAnims = Array(SettingBindMode.entries.size) { index ->
        val selected = if (SettingBindMode.entries[index] == mode) 1f else 0f
        AnimFloat(selected, selected, 16f)
    }
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
    private val contentX get() = x + 16f
    private val contentY get() = y + 193f
    private val contentWidth get() = (width - 32f).coerceAtLeast(1f)
    private val contentHeightVisible get() = (height - 235f).coerceAtLeast(1f)
    private val modeWidth get() = (width - 28f) * 0.5f
    private val footerY get() = y + height - 30f
    private val doneX get() = x + width - 64f

    init {
        valueSetting.addListener { dirty = true }
        bounds()
    }

    fun owns(candidate: Setting<*>): Boolean = candidate === valueSetting

    private fun bounds() {
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()
        width = minOf(280f, (screenW - 16f).coerceAtLeast(80f))
        height = minOf(235f + contentHeight.coerceAtLeast(30f), 380f, (screenH - 16f).coerceAtLeast(240f))
        x = x.coerceIn(0f, (screenW - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenH - height).coerceAtLeast(0f))
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float, interactive: Boolean) {
        bounds()
        val palette = M3Theme.current
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        RenderUtils.currentGraphics?.nextStratum()
        RenderUtils.rect(matrix, 0f, 0f, mc.window.guiScaledWidth.toFloat(), mc.window.guiScaledHeight.toFloat(), palette.scrim.alpha(95), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.currentGraphics?.nextStratum()
        RenderUtils.roundedRectWithBorder(matrix, x, y, width, height, palette.surface, 11f,
            0.6f, palette.outlineVariant.alpha(65), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.currentGraphics?.nextStratum()
        ScissorUtil.withScissor(x + 12f, y + 8f, (width - 44f).coerceAtLeast(1f), 20f) {
            Fonts.google_sans_medium.drawString(matrix, "${setting.name} · Bind", x + 14f, y + 12f, 11f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        }
        val closeHovered = interactive && over(mx, my, x + width - 34f, y + 4f, 30f, 26f)
        if (closeHovered) {
            RenderUtils.roundedRect(matrix, x + width - 31f, y + 6f, 22f, 22f,
                palette.stateHover, 6f, ClientRenderPipeline.POPUP_RECT)
        }
        Fonts.google_sans_medium.drawCenteredString(matrix, "×", x + width - 20f, y + 9f, 15f,
            if (closeHovered) palette.onSurface else palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)

        val keyHovered = interactive && over(mx, my, x + 12f, y + 35f, width - 24f, 29f)
        RenderUtils.roundedRect(matrix, x + 12f, y + 35f, width - 24f, 29f, when {
            isListening -> palette.primaryContainer
            keyHovered -> palette.surfaceContainer
            else -> palette.surfaceContainerLow
        }, 7f, ClientRenderPipeline.POPUP_RECT)
        if (isListening) {
            ScissorUtil.withScissor(x + 20f, y + 40f, (width - 40f).coerceAtLeast(1f), 20f) {
                Fonts.google_sans_regular.drawString(matrix, "Press a key or mouse button…", x + 20f, y + 44f,
                    9f, palette.onPrimaryContainer, false, ClientRenderPipeline.POPUP_TEXT)
            }
        } else {
            val keyText = KeybindSetting.formatKey(key)
            val chipWidth = (Fonts.google_sans_medium.width(keyText, 9f) + 16f)
                .coerceIn(minOf(36f, width - 40f), (width - 40f).coerceAtLeast(1f))
            val chipX = x + width - 20f - chipWidth
            ScissorUtil.withScissor(x + 20f, y + 40f, (chipX - x - 26f).coerceAtLeast(1f), 20f) {
                Fonts.google_sans_regular.drawString(matrix, "Key", x + 20f, y + 44f, 10f,
                    palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
            }
            RenderUtils.roundedRect(matrix, chipX, y + 40.5f, chipWidth, 18f,
                palette.surfaceContainerHigh, 5f, ClientRenderPipeline.POPUP_RECT)
            ScissorUtil.withScissor(chipX + 4f, y + 40.5f, chipWidth - 8f, 18f) {
                Fonts.google_sans_medium.drawCenteredString(matrix, keyText, chipX + chipWidth * 0.5f,
                    y + 44.5f, 9f, if (key == InputConstants.UNKNOWN.value) palette.onSurfaceVariant else palette.primary,
                    false, ClientRenderPipeline.POPUP_TEXT)
            }
        }

        Fonts.google_sans_regular.drawString(matrix, "Activation", x + 14f, y + 74f, 9f,
            palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        for ((index, option) in SettingBindMode.entries.withIndex()) {
            val buttonX = x + 12f + index % 2 * (modeWidth + 4f)
            val buttonY = y + 89f + index / 2 * 24f
            val selected = mode == option
            val progress = modeAnims[index].update(if (selected) 1f else 0f, dt)
            val hovered = interactive && over(mx, my, buttonX, buttonY, modeWidth, 20f)
            val baseColor = if (hovered) palette.surfaceContainer else palette.surfaceContainerLow
            RenderUtils.roundedRectWithBorder(matrix, buttonX, buttonY, modeWidth, 20f,
                baseColor.interpolateTo(palette.primaryContainer, progress.toDouble()), 5f,
                0.6f, palette.outlineVariant.interpolateTo(palette.primary, progress.toDouble())
                    .alpha((45f + 55f * progress).toInt()), ClientRenderPipeline.POPUP_RECT)
            ScissorUtil.withScissor(buttonX + 2f, buttonY, (modeWidth - 4f).coerceAtLeast(1f), 20f) {
                Fonts.google_sans_medium.drawCenteredString(matrix, option.displayName,
                    buttonX + modeWidth * 0.5f, buttonY + 5f, 9f,
                    palette.onSurfaceVariant.interpolateTo(palette.onPrimaryContainer, progress.toDouble()),
                    false, ClientRenderPipeline.POPUP_TEXT)
            }
        }

        val visibleHovered = interactive && over(mx, my, x + 12f, y + 140f, width - 24f, 26f)
        if (visibleHovered) {
            RenderUtils.roundedRect(matrix, x + 12f, y + 140f, width - 24f, 26f,
                palette.stateHover, 6f, ClientRenderPipeline.POPUP_RECT)
        }
        ScissorUtil.withScissor(x + 20f, y + 140f, (width - 72f).coerceAtLeast(1f), 26f) {
            Fonts.google_sans_regular.drawString(matrix, "Visible", x + 20f, y + 147.5f, 10f,
                palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        }
        ToggleSwitch.render(matrix, x + width - 20f - ToggleSwitch.WIDTH, y + 146f,
            visibleAnim.update(if (visible) 1f else 0f, dt), ClientRenderPipeline.POPUP_RECT)

        RenderUtils.rect(matrix, x + 14f, y + 169f, width - 28f, 0.6f,
            palette.outlineVariant.alpha(65), ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_regular.drawString(matrix, "Active value", x + 14f, y + 178f, 9f,
            palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        RenderUtils.roundedRect(matrix, x + 12f, contentY - 4f, width - 24f, contentHeightVisible + 8f,
            palette.surfaceContainerLow, 7f, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.currentGraphics?.nextStratum()
        ScissorUtil.withScissor(contentX, contentY, contentWidth, contentHeightVisible) {
            val hovered = interactive && !isListening && overContent(mx, my)
            contentHeight = editor.render(matrix, contentX, contentY - scroll, contentWidth,
                if (hovered) mouseX else Int.MIN_VALUE, if (hovered) mouseY else Int.MIN_VALUE, dt)
        }
        if (maxScroll() > 0f) {
            val thumbHeight = (contentHeightVisible * contentHeightVisible / contentHeight).coerceIn(minOf(12f, contentHeightVisible), contentHeightVisible)
            val thumbY = contentY + (contentHeightVisible - thumbHeight) * scroll / maxScroll()
            RenderUtils.roundedRect(matrix, x + width - 10f, thumbY, 2f, thumbHeight,
                palette.outline.alpha(150), 1f, ClientRenderPipeline.POPUP_RECT)
        }

        val doneHovered = interactive && over(mx, my, doneX, footerY, 52f, 20f)
        RenderUtils.roundedRect(matrix, doneX, footerY, 52f, 20f,
            if (doneHovered) palette.secondaryContainer else palette.surfaceContainerHigh, 6f,
            ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_medium.drawCenteredString(matrix, "Done", doneX + 26f, footerY + 5f, 9f,
            if (doneHovered) palette.onSecondaryContainer else palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
        val removeHovered = interactive && setting.binding != null &&
            over(mx, my, x + 12f, footerY, width - 80f, 20f)
        if (removeHovered) {
            RenderUtils.roundedRect(matrix, x + 12f, footerY, width - 80f, 20f,
                palette.error.alpha(12), 6f, ClientRenderPipeline.POPUP_RECT)
        }
        ScissorUtil.withScissor(x + 20f, footerY, (width - 96f).coerceAtLeast(1f), 20f) {
            Fonts.google_sans_regular.drawString(matrix, "Remove bind", x + 20f, footerY + 5f, 9f,
                when {
                    removeHovered -> palette.error
                    setting.binding == null -> palette.onSurfaceVariant.alpha(100)
                    else -> palette.onSurfaceVariant
                }, false, ClientRenderPipeline.POPUP_TEXT)
        }
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
        if (button == 0 && over(event.x(), event.y(), doneX, footerY, 52f, 20f)) {
            close()
            return
        }
        if (button == 0 && setting.binding != null && over(event.x(), event.y(), x + 12f, footerY, width - 80f, 20f)) {
            releaseEditorInput(applyNumber = false)
            dirty = false
            SettingBindingManager.clear(setting)
            ConfigManager.saveCurrentConfig()
            closed = true
            return
        }
        if (button == 0 && over(event.x(), event.y(), x + 12f, y + 140f, width - 24f, 26f)) {
            releaseEditorInput()
            visible = !visible
            dirty = true
            commit()
            return
        }
        if (button == 0) {
            for ((index, option) in SettingBindMode.entries.withIndex()) {
                if (over(event.x(), event.y(), x + 12f + index % 2 * (modeWidth + 4f), y + 89f + index / 2 * 24f, modeWidth, 20f)) {
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
        SettingBindingManager.configure(setting, SettingBinding(key, mode, valueSetting.serialize(), visible))
        ConfigManager.saveCurrentConfig()
        dirty = false
    }

    fun close(applyNumber: Boolean = true) {
        if (closed) return
        releaseEditorInput(applyNumber = applyNumber)
        commit()
        closed = true
    }

    private fun maxScroll(): Float = (contentHeight - contentHeightVisible).coerceAtLeast(0f)

    private fun overContent(mouseX: Double, mouseY: Double): Boolean = over(mouseX, mouseY, contentX, contentY, contentWidth, contentHeightVisible)

    private fun over(mouseX: Double, mouseY: Double, x: Float, y: Float, width: Float, height: Float): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
}
