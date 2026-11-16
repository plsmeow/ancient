package meow.ancient.client.gui.material.components

import meow.ancient.client.util.other.color.Color4b
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
        private var scrollTime = 0f
        private var lastText = ""
        private var lastSize = 0f

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
            if (text != lastText || size != lastSize) {
                scrollTime = 0f
                lastText = text
                lastSize = size
            }
            if (maxWidth <= 0f || size <= 0f) {
                scrollTime = 0f
                return
            }
            val totalW = font.width(text, size)
            if (totalW <= maxWidth) {
                font.drawString(matrix, text, x, y, size, color, false, pipeline)
                scrollTime = 0f
                return
            }

            if (!isHovered) {
                scrollTime = 0f
                val trimmed = fitStart(font, text, maxWidth, size)
                font.drawString(matrix, trimmed, x, y, size, color, false, pipeline)
                return
            }

            val overflow = totalW - maxWidth
            val pause = 0.7f
            val travel = maxOf(1.25f, overflow / 32f)
            val cycle = 2f * (pause + travel)
            val delta = if (dt.isFinite()) dt.coerceIn(0f, 0.1f) else 0f
            scrollTime = (scrollTime + delta) % cycle
            val offset = when {
                scrollTime < pause -> 0f
                scrollTime < pause + travel -> overflow * ease((scrollTime - pause) / travel)
                scrollTime < 2f * pause + travel -> overflow
                else -> overflow * (1f - ease((scrollTime - 2f * pause - travel) / travel))
            }

            // Move the complete label at subpixel precision; clip instead of replacing whole characters.
            ScissorUtil.withScissor(x, y - 2f, maxWidth, font.getLineHeight(size) + 4f) {
                font.drawString(matrix, text, x - offset, y, size, color, false, pipeline)
            }
        }

        private fun ease(progress: Float): Float {
            val t = progress.coerceIn(0f, 1f)
            return t * t * t * (t * (t * 6f - 15f) + 10f)
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

    }
}
