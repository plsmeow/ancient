package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.HitboxPointsSetting
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.joml.Matrix4f

class HitboxPointsWindow(private val setting: HitboxPointsSetting) : EditorWindow {
    override var closed = false
        private set
    private val editor = HitboxPointsComponent(setting)
    private var x = Float.NaN
    private var y = Float.NaN
    private var width = 640f
    private var height = 470f
    private var scroll = 0f
    private var draggingWindow = false
    private var resizing = false
    private var draggingScrollbar = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var resizeStartX = 0.0
    private var resizeStartY = 0.0
    private var resizeStartWidth = 0f
    private var resizeStartHeight = 0f
    private var scrollbarOffset = 0f
    private var screenWidth = 0f
    private var screenHeight = 0f
    private val viewportX get() = x + 12f
    private val viewportY get() = y + 39f
    private val viewportWidth get() = (width - 33f).coerceAtLeast(1f)
    private val viewportHeight get() = (height - 55f).coerceAtLeast(1f)
    private val contentHeight get() = editor.measureHeight(viewportWidth, true)
    private val maxScroll get() = (contentHeight - viewportHeight).coerceAtLeast(0f)
    private val scrollbarX get() = x + width - 14f
    private val thumbHeight get() = minOf(viewportHeight, (viewportHeight * viewportHeight / contentHeight).coerceAtLeast(22f))
    private val thumbY get() = viewportY + if (maxScroll == 0f) 0f else scroll / maxScroll * (viewportHeight - thumbHeight)

    private fun bounds() {
        val nextWidth = mc.window.guiScaledWidth.toFloat()
        val nextHeight = mc.window.guiScaledHeight.toFloat()
        if (screenWidth != nextWidth || screenHeight != nextHeight) {
            editor.releaseInput()
            draggingWindow = false
            resizing = false
            draggingScrollbar = false
            screenWidth = nextWidth
            screenHeight = nextHeight
        }
        val availableWidth = (screenWidth - 16f).coerceAtLeast(1f)
        val availableHeight = (screenHeight - 16f).coerceAtLeast(1f)
        width = width.coerceIn(minOf(280f, availableWidth), availableWidth)
        height = height.coerceIn(minOf(200f, availableHeight), availableHeight)
        if (!x.isFinite()) x = (screenWidth - width) * 0.5f
        if (!y.isFinite()) y = (screenHeight - height) * 0.5f
        x = x.coerceIn(0f, (screenWidth - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenHeight - height).coerceAtLeast(0f))
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    override fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        if (closed) return
        bounds()
        val palette = M3Theme.current
        RenderUtils.currentGraphics?.nextStratum()
        RenderUtils.rect(matrix, 0f, 0f, screenWidth, screenHeight, palette.scrim.alpha(145), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surface, 11f, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.currentGraphics?.nextStratum()
        ScissorUtil.withScissor(x + 13f, y + 7f, (width - 51f).coerceAtLeast(1f), 23f) {
            Fonts.google_sans_medium.drawString(matrix, "${setting.name} · Point Editor", x + 13f, y + 13f,
                11f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        }
        val closeHovered = over(mouseX.toDouble(), mouseY.toDouble(), x + width - 33f, y + 5f, 27f, 27f)
        if (closeHovered) RenderUtils.roundedRect(matrix, x + width - 33f, y + 5f, 27f, 27f, palette.surfaceContainerHigh, 6f, ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_medium.drawCenteredString(matrix, "×", x + width - 19.5f, y + 8f, 17f,
            palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        RenderUtils.rect(matrix, x + 10f, y + 34f, width - 20f, 0.7f, palette.outlineVariant, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.currentGraphics?.nextStratum()
        ScissorUtil.start(viewportX, viewportY, viewportWidth, viewportHeight)
        try {
            val hovered = inViewport(mouseX.toDouble(), mouseY.toDouble())
            editor.renderEditor(matrix, viewportX, viewportY - scroll, viewportWidth,
                if (hovered) mouseX else Int.MIN_VALUE, if (hovered) mouseY else Int.MIN_VALUE, dt, forceExpanded = true, popup = true)
        } finally {
            ScissorUtil.end()
        }
        RenderUtils.currentGraphics?.nextStratum()
        if (maxScroll > 0f) {
            RenderUtils.roundedRect(matrix, scrollbarX, viewportY, 5f, viewportHeight, palette.surfaceContainerHighest, 2.5f, ClientRenderPipeline.POPUP_RECT)
            RenderUtils.roundedRect(matrix, scrollbarX, thumbY, 5f, thumbHeight,
                if (draggingScrollbar || over(mouseX.toDouble(), mouseY.toDouble(), scrollbarX - 3f, thumbY, 11f, thumbHeight)) palette.primary else palette.outline,
                2.5f, ClientRenderPipeline.POPUP_RECT)
        }
        for (i in 0..2) {
            RenderUtils.rect(matrix, x + width - 13f + i * 3f, y + height - 4f - i * 3f, 2f, 2f, palette.outline, ClientRenderPipeline.POPUP_RECT)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, button: Int) {
        if (closed) return
        bounds()
        val mx = event.x()
        val my = event.y()
        if (!over(mx, my, x, y, width, height)) { close(); return }
        if (button == 0 && over(mx, my, x + width - 33f, y + 5f, 27f, 27f)) { close(); return }
        if (button == 0 && over(mx, my, x + width - 18f, y + height - 18f, 18f, 18f)) {
            editor.releaseInput()
            resizing = true
            resizeStartX = mx
            resizeStartY = my
            resizeStartWidth = width
            resizeStartHeight = height
            return
        }
        if (button == 0 && maxScroll > 0f && over(mx, my, scrollbarX - 3f, viewportY, 11f, viewportHeight)) {
            editor.releaseInput()
            draggingScrollbar = true
            scrollbarOffset = if (over(mx, my, scrollbarX - 3f, thumbY, 11f, thumbHeight)) my.toFloat() - thumbY else thumbHeight * 0.5f
            dragScrollbar(my)
            return
        }
        if (button == 0 && over(mx, my, x, y, width - 35f, 34f)) {
            editor.releaseInput()
            draggingWindow = true
            dragOffsetX = mx.toFloat() - x
            dragOffsetY = my.toFloat() - y
            return
        }
        if (inViewport(mx, my)) editor.mouseClicked(mx, my, button)
        else editor.releaseInput()
    }

    override fun mouseDragged(event: MouseButtonEvent, button: Int, deltaX: Double, deltaY: Double) {
        if (closed || button != 0) return
        when {
            resizing -> {
                width = resizeStartWidth + (event.x() - resizeStartX).toFloat()
                height = resizeStartHeight + (event.y() - resizeStartY).toFloat()
                bounds()
            }
            draggingWindow -> {
                x = event.x().toFloat() - dragOffsetX
                y = event.y().toFloat() - dragOffsetY
                bounds()
            }
            draggingScrollbar -> dragScrollbar(event.y())
            else -> editor.mouseDragged(event.x(), event.y(), button, deltaX, deltaY)
        }
    }

    override fun mouseReleased(event: MouseButtonEvent, button: Int) {
        if (closed) return
        if (button == 0) {
            draggingWindow = false
            resizing = false
            draggingScrollbar = false
        }
        editor.mouseReleased(event.x(), event.y(), button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double) {
        if (closed || delta == 0.0 || !over(mouseX, mouseY, x, y, width, height)) return
        releaseInput()
        scroll = (scroll - delta.toFloat() * 28f).coerceIn(0f, maxScroll)
    }

    override fun keyPressed(event: KeyEvent) {
        if (closed) return
        if (editor.keyPressed(event.key(), event.keycode(), event.modifiers())) return
        if (event.key() == InputConstants.KEY_ESCAPE) close()
    }

    override fun charTyped(event: CharacterEvent) {
        if (!closed) editor.charTyped(event)
    }

    override fun releaseInput() {
        draggingWindow = false
        resizing = false
        draggingScrollbar = false
        editor.releaseInput()
    }

    override fun close() {
        if (closed) return
        releaseInput()
        closed = true
    }

    private fun dragScrollbar(mouseY: Double) {
        val travel = viewportHeight - thumbHeight
        scroll = if (travel <= 0f) 0f else (((mouseY - viewportY - scrollbarOffset) / travel) * maxScroll).toFloat().coerceIn(0f, maxScroll)
    }

    private fun inViewport(mouseX: Double, mouseY: Double): Boolean = over(mouseX, mouseY, viewportX, viewportY, viewportWidth, viewportHeight)

    private fun over(mouseX: Double, mouseY: Double, x: Float, y: Float, width: Float, height: Float): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

}
