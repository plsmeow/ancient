package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Font
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.TextSetting
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.InputQuirks
import org.joml.Matrix4f

class CustomTextField(
    initialValue: String = "",
    private val maxLength: Int = 256,
    private val hint: String = "",
    private val onChanged: (String) -> Unit = {}
) {
    var text: String = TextSetting.normalize(initialValue, maxLength)
        private set
    val isFocused: Boolean get() = focusedField === this
    private var x = 0f
    private var y = 0f
    private var width = 0f
    private var height = 0f
    private var cursor = text.length
    private var anchor = cursor
    private var dragging = false
    private var advances = FloatArray(text.length + 1)
    private var metricsFont: Font? = null
    private val size = 9.5f
    private val caretAnim = AnimFloat(0f, 0f, 24f)
    private val scrollAnim = AnimFloat(0f, 0f, 20f)
    private val focusAnim = AnimFloat(0f, 0f, 18f)
    private val typingAnim = AnimFloat(0f, 0f, 10f)
    private var scrollTarget = 0f
    private var blinkStart = System.nanoTime()

    fun setBounds(x: Float, y: Float, width: Float, height: Float) {
        this.x = x
        this.y = y
        this.width = width.coerceAtLeast(1f)
        this.height = height.coerceAtLeast(1f)
    }

    fun setText(value: String) {
        if (value == text) return
        val normalized = TextSetting.normalize(value, maxLength)
        if (normalized == text) return
        text = normalized
        cursor = text.length
        anchor = cursor
        metricsFont = null
        feedback()
    }

    fun focus() {
        if (isFocused) return
        ClickGuiScreen.clearAllListening()
        clearFocus()
        focusedField = this
        blinkStart = System.nanoTime()
        mc.textInputManager().startTextInput(this)
    }

    fun blur() {
        if (isFocused) {
            mc.textInputManager().stopTextInput(this)
            focusedField = null
        }
        dragging = false
    }

    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private fun ensureMetrics() {
        val font = Fonts.google_sans_regular
        if (metricsFont === font) return
        if (advances.size < text.length + 1) advances = FloatArray(text.length + 1)
        advances[0] = 0f
        var index = 0
        var advance = 0f
        while (index < text.length) {
            val codepoint = text.codePointAt(index)
            val next = index + Character.charCount(codepoint)
            advance += font.advance(codepoint) * size
            advances[next] = advance
            index = next
        }
        metricsFont = font
    }

    fun render(matrix: Matrix4f, dt: Float, popup: Boolean = false) {
        ensureMetrics()
        if (isFocused) {
            mc.textInputManager().setTextInputArea(x.toInt(), y.toInt(), width.toInt(), height.toInt())
        }
        val palette = M3Theme.current
        val rectPipeline = if (popup) ClientRenderPipeline.POPUP_RECT else ClientRenderPipeline.WINDOW_RECT
        val textPipeline = if (popup) ClientRenderPipeline.POPUP_TEXT else ClientRenderPipeline.WINDOW_TEXT
        val focus = focusAnim.update(if (isFocused) 1f else 0f, dt)
        val typing = typingAnim.update(0f, dt)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surfaceContainerHigh, 6f, rectPipeline)
        if (focus > 0.001f) {
            RenderUtils.roundedRect(matrix, x, y, width, height, palette.primary.with(a = (focus * (18f + typing * 18f)).toInt()), 6f, rectPipeline)
            RenderUtils.rect(matrix, x + 6f, y + height - 1.5f, (width - 12f).coerceAtLeast(1f), 1.5f, palette.primary.with(a = (focus * 255f).toInt()), rectPipeline)
        }
        val innerWidth = (width - 14f).coerceAtLeast(1f)
        val maxScroll = (advances[text.length] - innerWidth + 2f).coerceAtLeast(0f)
        val caretTarget = advances[cursor]
        if (caretTarget < scrollTarget) scrollTarget = caretTarget
        if (caretTarget > scrollTarget + innerWidth - 2f) scrollTarget = caretTarget - innerWidth + 2f
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll)
        val scroll = scrollAnim.update(scrollTarget, dt)
        val caret = caretAnim.update(caretTarget, dt)
        val textY = y + (height - size) * 0.5f - 0.5f
        ScissorUtil.withScissor(x + 6f, y + 2f, (width - 12f).coerceAtLeast(1f), (height - 4f).coerceAtLeast(1f)) {
            val textX = x + 7f - scroll
            if (isFocused && anchor != cursor) {
                val left = advances[minOf(anchor, cursor)]
                val right = advances[maxOf(anchor, cursor)]
                RenderUtils.rect(matrix, textX + left, textY - 1f, right - left, size + 3f, palette.primary.with(a = 72), rectPipeline)
            }
            Fonts.google_sans_regular.drawString(matrix, if (text.isEmpty()) hint else text, if (text.isEmpty()) x + 7f else textX, textY, size, if (text.isEmpty()) palette.onSurfaceVariant else palette.onSurface, false, textPipeline)
            val elapsed = (System.nanoTime() - blinkStart) / 1_000_000_000.0
            if (isFocused && (elapsed % 1.0 < 0.6 || typing > 0.1f)) {
                RenderUtils.rect(matrix, textX + caret, textY - 1f, 1f, size + 3f, palette.primary, rectPipeline)
            }
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !contains(mouseX, mouseY)) return false
        focus()
        ensureMetrics()
        val position = positionAt(mouseX)
        if (!mc.hasShiftDown()) anchor = position
        cursor = position
        dragging = true
        feedback()
        return true
    }

    fun mouseDragged(mouseX: Double, button: Int): Boolean {
        if (button != 0 || !dragging || !isFocused) return false
        ensureMetrics()
        cursor = positionAt(mouseX)
        feedback()
        return true
    }

    fun mouseReleased(button: Int) {
        if (button == 0) dragging = false
    }

    private fun positionAt(mouseX: Double): Int {
        val target = (mouseX - x - 7f + scrollAnim.value).toFloat()
        var index = 0
        while (index < text.length) {
            val next = text.offsetByCodePoints(index, 1)
            if (target < (advances[index] + advances[next]) * 0.5f) return index
            index = next
        }
        return text.length
    }

    fun keyPressed(keyCode: Int, shortcutKey: Int, modifiers: Int): Boolean {
        if (!isFocused) return false
        val control = modifiers and InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER != 0
        val shift = modifiers and InputConstants.MOD_SHIFT != 0
        val shortcut = control && !shift && modifiers and InputConstants.MOD_ALT == 0
        when {
            keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER -> blur()
            shortcut && shortcutKey == InputConstants.KEYCODE_A -> {
                anchor = 0
                cursor = text.length
                feedback()
            }
            shortcut && shortcutKey == InputConstants.KEYCODE_C -> copySelection()
            shortcut && shortcutKey == InputConstants.KEYCODE_X -> {
                copySelection()
                if (anchor != cursor) replaceSelection("")
            }
            shortcut && shortcutKey == InputConstants.KEYCODE_V -> replaceSelection(mc.keyboardHandler.clipboard)
            keyCode == InputConstants.KEY_LEFT -> moveCursor(if (!shift && anchor != cursor) minOf(anchor, cursor) else previous(cursor, control), shift)
            keyCode == InputConstants.KEY_RIGHT -> moveCursor(if (!shift && anchor != cursor) maxOf(anchor, cursor) else next(cursor, control), shift)
            keyCode == InputConstants.KEY_HOME -> moveCursor(0, shift)
            keyCode == InputConstants.KEY_END -> moveCursor(text.length, shift)
            keyCode == InputConstants.KEY_BACKSPACE -> {
                if (anchor == cursor) anchor = previous(cursor, control)
                replaceSelection("")
            }
            keyCode == InputConstants.KEY_DELETE -> {
                if (anchor == cursor) anchor = next(cursor, control)
                replaceSelection("")
            }
        }
        return true
    }

    fun charTyped(event: CharacterEvent): Boolean {
        if (!isFocused) return false
        if (event.isAllowedChatCharacter()) replaceSelection(event.codepointAsString())
        return true
    }

    private fun copySelection() {
        if (anchor != cursor) mc.keyboardHandler.clipboard = text.substring(minOf(anchor, cursor), maxOf(anchor, cursor))
    }

    private fun previous(position: Int, word: Boolean): Int {
        var result = position
        if (!word) return if (result > 0) text.offsetByCodePoints(result, -1) else 0
        while (result > 0 && Character.isWhitespace(text.codePointBefore(result))) result = text.offsetByCodePoints(result, -1)
        while (result > 0 && !Character.isWhitespace(text.codePointBefore(result))) result = text.offsetByCodePoints(result, -1)
        return result
    }

    private fun next(position: Int, word: Boolean): Int {
        var result = position
        if (!word) return if (result < text.length) text.offsetByCodePoints(result, 1) else text.length
        while (result < text.length && !Character.isWhitespace(text.codePointAt(result))) result = text.offsetByCodePoints(result, 1)
        while (result < text.length && Character.isWhitespace(text.codePointAt(result))) result = text.offsetByCodePoints(result, 1)
        return result
    }

    private fun moveCursor(position: Int, selecting: Boolean) {
        cursor = position
        if (!selecting) anchor = cursor
        feedback()
    }

    private fun replaceSelection(value: String) {
        val start = minOf(anchor, cursor)
        val end = maxOf(anchor, cursor)
        val remaining = maxLength - text.codePointCount(0, text.length) + text.codePointCount(start, end)
        val inserted = TextSetting.normalize(value, remaining)
        val updated = text.substring(0, start) + inserted + text.substring(end)
        cursor = start + inserted.length
        anchor = cursor
        if (updated != text) {
            text = updated
            metricsFont = null
            onChanged(text)
        }
        feedback()
    }

    private fun feedback() {
        blinkStart = System.nanoTime()
        typingAnim.setImmediate(1f)
    }

    companion object {
        private var focusedField: CustomTextField? = null
        val hasFocus: Boolean get() = focusedField != null

        fun clearFocus() {
            focusedField?.let {
                mc.textInputManager().stopTextInput(it)
                it.blur()
            }
        }

        fun blurOutside(mouseX: Double, mouseY: Double) {
            focusedField?.let { if (!it.contains(mouseX, mouseY)) it.blur() }
        }

        fun keyPressedFocused(keyCode: Int, shortcutKey: Int, modifiers: Int): Boolean = focusedField?.keyPressed(keyCode, shortcutKey, modifiers) ?: false
        fun charTypedFocused(event: CharacterEvent): Boolean = focusedField?.charTyped(event) ?: false
    }
}
