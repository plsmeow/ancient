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
        var offset = 0f
        private var forward = true
        private var pauseTime = 0f

        fun update(isHovered: Boolean, maxScroll: Float, dt: Float): Float {
            if (maxScroll <= 0f) {
                offset = 0f
                forward = true
                pauseTime = 0f
                return 0f
            }

            if (isHovered) {
                if (forward) {
                    if (offset <= 0f && pauseTime < 0.6f) {
                        pauseTime += dt
                    } else {
                        offset += 28f * dt
                        if (offset >= maxScroll) {
                            offset = maxScroll
                            forward = false
                            pauseTime = 0f
                        }
                    }
                } else {
                    if (offset >= maxScroll && pauseTime < 0.7f) {
                        pauseTime += dt
                    } else {
                        offset -= 28f * dt
                        if (offset <= 0f) {
                            offset = 0f
                            forward = true
                            pauseTime = 0f
                        }
                    }
                }
            } else {
                if (offset > 0f) {
                    offset = (offset - 50f * dt).coerceAtLeast(0f)
                }
                forward = true
                pauseTime = 0f
            }
            return offset
        }

        fun render(
            matrix: Matrix4f,
            text: String,
            x: Float,
            y: Float,
            maxWidth: Float,
            size: Float,
            color: Color4b,
            bgColor: Color4b,
            isHovered: Boolean,
            dt: Float,
            pipeline: ClientRenderPipeline = ClientRenderPipeline.WINDOW_TEXT
        ) {
            val font = Fonts.google_sans_regular
            val totalW = font.width(text, size)
            if (totalW <= maxWidth) {
                font.drawString(matrix, text, x, y, size, color, false, pipeline)
                offset = 0f
                return
            }

            val maxScroll = totalW - maxWidth
            val curOffset = update(isHovered, maxScroll, dt)
            val fadeW = 12f

            ScissorUtil.withScissor(x, y - 2f, maxWidth, size + 5f) {
                font.drawString(matrix, text, x - curOffset, y, size, color, false, pipeline)
            }

            if (curOffset > 0.5f) {
                val leftProgress = (curOffset / fadeW).coerceIn(0f, 1f)
                val leftAlpha = (255 * leftProgress).toInt()
                RenderUtils.gradientRect(
                    matrix = matrix,
                    x = x,
                    y = y - 2f,
                    width = fadeW,
                    height = size + 5f,
                    topLeft = bgColor.alpha(leftAlpha),
                    topRight = bgColor.alpha(0),
                    bottomRight = bgColor.alpha(0),
                    bottomLeft = bgColor.alpha(leftAlpha),
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
            }

            if (curOffset < maxScroll - 0.5f) {
                val rightProgress = ((maxScroll - curOffset) / fadeW).coerceIn(0f, 1f)
                val rightAlpha = (255 * rightProgress).toInt()
                val rightX = x + maxWidth - fadeW
                RenderUtils.gradientRect(
                    matrix = matrix,
                    x = rightX,
                    y = y - 2f,
                    width = fadeW,
                    height = size + 5f,
                    topLeft = bgColor.alpha(0),
                    topRight = bgColor.alpha(rightAlpha),
                    bottomRight = bgColor.alpha(rightAlpha),
                    bottomLeft = bgColor.alpha(0),
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
            }
        }
    }
}
