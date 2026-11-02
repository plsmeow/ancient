package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.NumberSetting
import net.minecraft.client.input.CharacterEvent
import org.joml.Matrix4f
import java.util.Locale

class NumberComponent(private val numSetting: NumberSetting) : SettingComponent(numSetting) {
    private var isDragging = false
    private val thumbAnim = AnimFloat(0f, 0f, 16f)
    private val labelMarquee = MarqueeText()


    var isEditing = false
        private set
    private var currentInput = ""
    private var lastKeyActionTime = 0L

    private fun appendDigit(c: Char) {
        if (currentInput.length < 12) {
            currentInput += c
            lastKeyActionTime = System.currentTimeMillis()
        }
    }

    private fun appendDot() {
        if (numSetting.precision > 0 && !currentInput.contains('.')) {
            currentInput += if (currentInput.isEmpty() || currentInput == "-") "0." else "."
            lastKeyActionTime = System.currentTimeMillis()
        }
    }

    private fun appendMinus() {
        if (numSetting.min < 0 && currentInput.isEmpty()) {
            currentInput = "-"
            lastKeyActionTime = System.currentTimeMillis()
        }
    }

    fun stopEditing(apply: Boolean = true) {
        if (!isEditing) return
        if (apply && currentInput.isNotEmpty()) {
            val parsed = currentInput.toDoubleOrNull()
            if (parsed != null) {
                numSetting.set(parsed)
                ConfigManager.saveConfig("default")
            }
        }
        isEditing = false
        currentInput = ""
    }

    override fun render(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        mouseX: Int,
        mouseY: Int,
        dt: Float
    ): Float {
        this.x = x
        this.y = y
        this.width = width
        this.height = 26f

        val palette = M3Theme.current
        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        val expandThumb = thumbAnim.update(if (hovered || isDragging) 1f else 0f, dt)

        val valText = if (isEditing) {
            val blink = (System.currentTimeMillis() / 450) % 2 == 0L
            if (currentInput.isEmpty()) {
                if (blink) "_" else ""
            } else {
                currentInput + if (blink) "_" else ""
            }
        } else {
            numSetting.getFormattedValue()
        }

        val rightX = x + width - 2f
        val valW = Fonts.google_sans_medium.width(if (isEditing && currentInput.isEmpty()) "_" else valText, 9.5f)
        val maxTextW = (rightX - (x + 2f) - valW - 8f).coerceAtLeast(10f)

        labelMarquee.render(
            matrix = matrix,
            text = numSetting.name,
            x = x + 2f,
            y = y + 2.5f,
            maxWidth = maxTextW,
            size = 9.5f,
            color = palette.onSurface,
            bgColor = palette.surfaceContainerLowest,
            isHovered = hovered,
            dt = dt,
            pipeline = ClientRenderPipeline.WINDOW_TEXT
        )
        if (isEditing) {
            val editPad = 5f
            val editW = (valW + editPad * 2).coerceAtLeast(22f)
            val editH = 14f
            val editX = rightX - editW
            val editY = y + 1f
            RenderUtils.roundedRect(
                matrix = matrix,
                x = editX,
                y = editY,
                width = editW,
                height = editH,
                color = palette.primary,
                radius = 3.5f,
                pipeline = ClientRenderPipeline.WINDOW_RECT
            )
            Fonts.google_sans_medium.drawCenteredString(
                matrix = matrix,
                text = valText,
                centerX = editX + editW * 0.5f,
                y = editY + 2f,
                size = 10f,
                color = palette.onPrimary,
                shadow = false,
                pipeline = ClientRenderPipeline.WINDOW_TEXT
            )
        } else {
            Fonts.google_sans_medium.drawRightString(
                matrix = matrix,
                text = valText,
                rightX = rightX,
                y = y + 2.5f,
                size = 9.5f,
                color = palette.primary,
                shadow = false,
                pipeline = ClientRenderPipeline.WINDOW_TEXT
            )
        }

        val trackPad = 5f
        val trackX = x + trackPad
        val trackY = y + 17f
        val trackW = width - trackPad * 2f
        val trackH = 3.5f
        val trackRadius = 1.75f

        val range = (numSetting.max - numSetting.min).coerceAtLeast(0.0001)
        val percentage = ((numSetting.value - numSetting.min) / range).coerceIn(0.0, 1.0).toFloat()
        val fillW = trackW * percentage
        RenderUtils.roundedRect(
            matrix = matrix,
            x = trackX,
            y = trackY,
            width = trackW,
            height = trackH,
            color = palette.surfaceContainerHighest,
            radius = trackRadius,
            pipeline = ClientRenderPipeline.WINDOW_RECT
        )

        if (fillW > 0.5f) {
            RenderUtils.roundedRect(
                matrix = matrix,
                x = trackX,
                y = trackY,
                width = fillW,
                height = trackH,
                color = palette.primary,
                radius = trackRadius,
                pipeline = ClientRenderPipeline.WINDOW_RECT
            )
        }

        val thumbBaseSize = 8f
        val thumbSize = thumbBaseSize + expandThumb * 2f
        val thumbX = trackX + fillW - thumbSize * 0.5f
        val thumbY = trackY + (trackH - thumbSize) * 0.5f
        RenderUtils.roundedRect(
            matrix = matrix,
            x = thumbX,
            y = thumbY,
            width = thumbSize,
            height = thumbSize,
            color = palette.primary,
            radius = thumbSize * 0.5f,
            pipeline = ClientRenderPipeline.WINDOW_RECT
        )

        if (isDragging) {
            updateValueFromMouse(mouseX.toDouble(), trackX, trackW)
        }

        return height
    }

    private fun updateValueFromMouse(mouseX: Double, trackX: Float, trackW: Float) {
        val pct = ((mouseX - trackX) / trackW).coerceIn(0.0, 1.0)
        val rawVal = numSetting.min + pct * (numSetting.max - numSetting.min)
        numSetting.set(rawVal)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = mouseClicked(mouseX, mouseY, button, 0)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, modifiers: Int): Boolean {
        if (isHovered(mouseX, mouseY, x, y, width, height)) {
            if (button == 0 && (modifiers and InputConstants.MOD_CONTROL) != 0) {
                if (isEditing) {
                    stopEditing(apply = true)
                } else {
                    ClickGuiScreen.clearAllListening(exceptNumber = this)
                    isDragging = false
                    isEditing = true
                    currentInput = if (numSetting.precision <= 0) {
                        "${numSetting.value.toInt()}"
                    } else if (numSetting.value % 1.0 == 0.0) {
                        "${numSetting.value.toInt()}"
                    } else {
                        String.format(Locale.US, "%.${numSetting.precision}f", numSetting.value).trimEnd('0').trimEnd('.')
                    }
                }
                return true
            }
            if (isEditing) {
                stopEditing(apply = true)
            }
            if (button == 0) {
                isDragging = true
                val trackPad = 5f
                updateValueFromMouse(mouseX, x + trackPad, width - trackPad * 2f)
                return true
            }
        } else if (isEditing) {
            stopEditing(apply = true)
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        if (button == 0) {
            isDragging = false
        }
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double) {
        if (isDragging && button == 0) {
            val trackPad = 5f
            updateValueFromMouse(mouseX, x + trackPad, width - trackPad * 2f)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isEditing) return false
        when (keyCode) {
            InputConstants.KEY_ESCAPE -> {
                stopEditing(apply = false)
                return true
            }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                stopEditing(apply = true)
                return true
            }
            InputConstants.KEY_BACKSPACE -> {
                if (currentInput.isNotEmpty()) {
                    currentInput = currentInput.dropLast(1)
                    lastKeyActionTime = System.currentTimeMillis()
                }
                return true
            }
            InputConstants.KEY_PERIOD, InputConstants.KEY_NUMPADCOMMA, InputConstants.KEY_COMMA -> {
                appendDot()
                return true
            }
            InputConstants.KEY_MINUS, 45 -> {
                appendMinus()
                return true
            }
            InputConstants.KEY_0, InputConstants.KEY_NUMPAD0 -> {
                appendDigit('0')
                return true
            }
            InputConstants.KEY_1, InputConstants.KEY_NUMPAD1 -> {
                appendDigit('1')
                return true
            }
            InputConstants.KEY_2, InputConstants.KEY_NUMPAD2 -> {
                appendDigit('2')
                return true
            }
            InputConstants.KEY_3, InputConstants.KEY_NUMPAD3 -> {
                appendDigit('3')
                return true
            }
            InputConstants.KEY_4, InputConstants.KEY_NUMPAD4 -> {
                appendDigit('4')
                return true
            }
            InputConstants.KEY_5, InputConstants.KEY_NUMPAD5 -> {
                appendDigit('5')
                return true
            }
            InputConstants.KEY_6, InputConstants.KEY_NUMPAD6 -> {
                appendDigit('6')
                return true
            }
            InputConstants.KEY_7, InputConstants.KEY_NUMPAD7 -> {
                appendDigit('7')
                return true
            }
            InputConstants.KEY_8, InputConstants.KEY_NUMPAD8 -> {
                appendDigit('8')
                return true
            }
            InputConstants.KEY_9, InputConstants.KEY_NUMPAD9 -> {
                appendDigit('9')
                return true
            }
        }
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!isEditing) return false
        if (System.currentTimeMillis() - lastKeyActionTime < 60L) return true
        val cp = event.codepoint()
        if (cp in '0'.code..'9'.code) {
            appendDigit(cp.toChar())
            return true
        }
        if ((cp == '.'.code || cp == ','.code) && numSetting.precision > 0) {
            appendDot()
            return true
        }
        if (cp == '-'.code && numSetting.min < 0) {
            appendMinus()
            return true
        }
        return true
    }
}
