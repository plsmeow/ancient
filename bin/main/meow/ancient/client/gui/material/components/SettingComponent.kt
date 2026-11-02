package meow.ancient.client.gui.material.components

import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Font
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.Setting
import net.minecraft.client.input.CharacterEvent
import org.joml.Matrix4f

abstract class SettingComponent(val setting: Setting<*>) : GuiComponent {
    override var x: Float = 0f
    override var y: Float = 0f
    override var width: Float = 0f
    override var height: Float = 0f

    protected data class FittedText(val text: String, val size: Float)

    abstract fun render(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        mouseX: Int,
        mouseY: Int,
        dt: Float
    ): Float

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, modifiers: Int): Boolean = mouseClicked(mouseX, mouseY, button)
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {}
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double) {}
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false
    open fun charTyped(event: CharacterEvent): Boolean = false

    protected fun isHovered(mouseX: Double, mouseY: Double, x: Float, y: Float, w: Float, h: Float): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }

    protected fun fitText(
        text: String,
        maxWidth: Float,
        baseSize: Float = 10f,
        minSize: Float = 8f,
        font: Font = Fonts.google_sans_regular
    ): FittedText {
        if (maxWidth <= 0f || text.isEmpty()) return FittedText("", minSize)

        var textSize = baseSize
        val origW = font.width(text, textSize)
        if (origW > maxWidth) {
            textSize = (textSize * (maxWidth / origW)).coerceIn(minSize, baseSize)
        }

        if (font.width(text, textSize) <= maxWidth) {
            return FittedText(text, textSize)
        }

        var truncated = text
        while (truncated.isNotEmpty() && font.width("$truncated...", textSize) > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return FittedText("$truncated...", textSize)
    }

    class MarqueeText {
        private var charOffset = 0f
        private var forward = true
        private var pauseTimer = 0f

        fun render(
            matrix: Matrix4f,
            text: String,
            x: Float,
            y: Float,
            maxWidth: Float,
            size: Float,
            color: Color4b,
            isHovered: Boolean,
            dt: Float,
            bgColor: Color4b? = null,
            pipeline: ClientRenderPipeline = ClientRenderPipeline.WINDOW_TEXT
        ) {
            val font = Fonts.google_sans_regular
            val totalW = font.width(text, size)
            if (totalW <= maxWidth) {
                font.drawString(matrix, text, x, y, size, color, false, pipeline)
                charOffset = 0f
                pauseTimer = 0f
                forward = true
                return
            }

            if (!isHovered) {
                charOffset = 0f
                pauseTimer = 0f
                forward = true
                val trimmed = fitStart(font, text, maxWidth, size)
                font.drawString(matrix, trimmed, x, y, size, color, false, pipeline)
                return
            }

            val maxShift = (text.length - 3).coerceAtLeast(1)
            if (forward) {
                if (charOffset <= 0f && pauseTimer < 0.7f) {
                    pauseTimer += dt
                } else {
                    charOffset += 7f * dt
                    if (charOffset >= maxShift) {
                        charOffset = maxShift.toFloat()
                        forward = false
                        pauseTimer = 0f
                    }
                }
            } else {
                if (charOffset >= maxShift && pauseTimer < 0.7f) {
                    pauseTimer += dt
                } else {
                    charOffset -= 7f * dt
                    if (charOffset <= 0f) {
                        charOffset = 0f
                        forward = true
                        pauseTimer = 0f
                    }
                }
            }

            val startIndex = charOffset.toInt().coerceIn(0, text.length - 1)
            val display = fitWindow(font, text, startIndex, maxWidth, size)
            font.drawString(matrix, display, x, y, size, color, false, pipeline)
        }

        private fun fitStart(font: Font, text: String, maxWidth: Float, size: Float): String {
            var len = text.length
            while (len > 0) {
                val candidate = text.substring(0, len) + "…"
                if (font.width(candidate, size) <= maxWidth) return candidate
                len--
            }
            return ""
        }

        private fun fitWindow(font: Font, text: String, startIndex: Int, maxWidth: Float, size: Float): String {
            val sub = text.substring(startIndex)
            val prefix = if (startIndex > 0) "…" else ""
            if (font.width(prefix + sub, size) <= maxWidth) {
                return prefix + sub
             }
            var len = sub.length
            while (len > 0) {
                val candidate = prefix + sub.substring(0, len) + "…"
                if (font.width(candidate, size) <= maxWidth) return candidate
                len--
            }
            return ""
        }
    }
}
