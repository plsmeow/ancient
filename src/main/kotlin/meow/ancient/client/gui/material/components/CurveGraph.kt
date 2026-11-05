package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.module.setting.Curve
import meow.ancient.module.setting.CurveInterpolation
import meow.ancient.module.setting.CurvePoint
import meow.ancient.module.setting.CurveSetting
import org.joml.Matrix4f
import kotlin.math.abs

internal class CurveGraph(
    private val setting: CurveSetting,
    private val beginEdit: () -> Unit = {},
    private val changed: () -> Unit = {},
    private val endEdit: () -> Unit = {}
) {
    var selectedIndex = -1
        private set
    val selectedPoint: CurvePoint? get() = setting.value.points.getOrNull(selectedIndex)
    private var x = 0f
    private var y = 0f
    private var width = 0f
    private var height = 0f
    private var dragging = false
    private var cachedCurve: Curve? = null
    private var samples = FloatArray(0)

    fun clearSelection() {
        release()
        selectedIndex = -1
    }

    fun render(matrix: Matrix4f, x: Float, y: Float, width: Float, height: Float, mouseX: Int, mouseY: Int, interactive: Boolean) {
        this.x = x
        this.y = y
        this.width = width.coerceAtLeast(1f)
        this.height = height.coerceAtLeast(1f)
        val palette = M3Theme.current
        RenderUtils.roundedRect(matrix, x - 8f, y - 8f, width + 16f, height + 16f, palette.surfaceContainerLowest, 8f, ClientRenderPipeline.WINDOW_RECT)
        for (i in 0..4) {
            val gridColor = palette.outlineVariant.alpha(if (i == 0 || i == 4) 125 else 62)
            RenderUtils.rect(matrix, x + width * i / 4f, y, 0.6f, height, gridColor, ClientRenderPipeline.WINDOW_RECT)
            RenderUtils.rect(matrix, x, y + height * i / 4f, width, 0.6f, gridColor, ClientRenderPipeline.WINDOW_RECT)
        }
        val curve = setting.value
        if (cachedCurve !== curve) {
            samples = sample(curve)
            cachedCurve = curve
            if (selectedIndex !in curve.points.indices) selectedIndex = -1
        }
        RenderUtils.currentGraphics?.let { graphics ->
            graphics.guiRenderState.addGuiElement(
                CurveRenderState(graphics.pose(), samples, x, y, width, height,
                    palette.primary.argb, palette.primary.alpha(if (interactive) 36 else 22).argb,
                    if (interactive) 1.8f else 1.3f, graphics.scissorStack.peek())
            )
        }
        val hovered = if (interactive) pointAt(mouseX.toDouble(), mouseY.toDouble()) else -1
        for (i in curve.points.indices) {
            val point = curve.points[i]
            val px = x + point.x * width
            val py = y + (1f - point.y) * height
            val selected = interactive && i == selectedIndex
            val radius = if (selected || i == hovered) 4.1f else if (interactive) 3.2f else 2.1f
            if (selected) RenderUtils.roundedRect(matrix, px - 7f, py - 7f, 14f, 14f, palette.primary.alpha(38), 7f, ClientRenderPipeline.WINDOW_RECT)
            RenderUtils.roundedRect(matrix, px - radius, py - radius, radius * 2f, radius * 2f,
                if (selected) palette.tertiary else palette.primary, radius, ClientRenderPipeline.WINDOW_RECT)
            RenderUtils.roundedRect(matrix, px - 1.4f, py - 1.4f, 2.8f, 2.8f, palette.surface, 1.4f, ClientRenderPipeline.WINDOW_RECT)
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!contains(mouseX, mouseY)) return false
        val index = pointAt(mouseX, mouseY)
        if (button == 1) {
            if (index > 0 && index < setting.value.points.lastIndex) {
                beginEdit()
                setting.removePoint(index)
                selectedIndex = -1
                changed()
                endEdit()
            }
            return true
        }
        if (button != 0) return true
        beginEdit()
        selectedIndex = index
        if (selectedIndex < 0) {
            val nx = ((mouseX - x) / width).toFloat().coerceIn(0f, 1f)
            val ny = (1f - (mouseY - y) / height).toFloat().coerceIn(0f, 1f)
            val previousSize = setting.value.points.size
            setting.addPoint(CurvePoint(nx, ny))
            if (setting.value.points.size > previousSize) {
                selectedIndex = setting.value.points.indexOfFirst { abs(it.x - nx) < Curve.MIN_POINT_GAP }
                changed()
            }
        }
        dragging = selectedIndex >= 0
        if (!dragging) endEdit()
        return true
    }

    fun mouseDragged(mouseX: Double, mouseY: Double) {
        if (!dragging) return
        moveSelected(((mouseX - x) / width).toFloat(), (1f - (mouseY - y) / height).toFloat())
    }

    fun moveSelected(x: Float, y: Float) {
        if (selectedPoint == null) return
        val previous = setting.value
        setting.movePoint(selectedIndex, x, y)
        if (setting.value != previous) changed()
    }

    fun release() {
        if (!dragging) return
        dragging = false
        endEdit()
    }

    fun keyPressed(key: Int, modifiers: Int): Boolean {
        val point = selectedPoint ?: return false
        if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
            beginEdit()
            setting.removePoint(selectedIndex)
            selectedIndex = -1
            changed()
            endEdit()
            return true
        }
        val step = if (modifiers and InputConstants.MOD_SHIFT != 0) 0.05f else 0.005f
        val dx = when (key) { InputConstants.KEY_LEFT -> -step; InputConstants.KEY_RIGHT -> step; else -> 0f }
        val dy = when (key) { InputConstants.KEY_DOWN -> -step; InputConstants.KEY_UP -> step; else -> 0f }
        if (dx == 0f && dy == 0f) return false
        beginEdit()
        moveSelected(point.x + dx, point.y + dy)
        endEdit()
        return true
    }

    private fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x - 6f && mouseX <= x + width + 6f && mouseY >= y - 6f && mouseY <= y + height + 6f

    private fun pointAt(mouseX: Double, mouseY: Double): Int {
        var best = -1
        var bestDistance = 64.0
        for (i in setting.value.points.indices) {
            val point = setting.value.points[i]
            val dx = mouseX - (x + point.x * width)
            val dy = mouseY - (y + (1f - point.y) * height)
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    private fun sample(curve: Curve): FloatArray {
        if (curve.interpolation != CurveInterpolation.SMOOTH) {
            if (curve.interpolation == CurveInterpolation.LINEAR) {
                return FloatArray(curve.points.size * 2).also { result ->
                    for (i in curve.points.indices) {
                        result[i * 2] = curve.points[i].x
                        result[i * 2 + 1] = 1f - curve.points[i].y
                    }
                }
            }
            val result = FloatArray((curve.points.size * 2 - 1) * 2)
            result[0] = 0f
            result[1] = 1f - curve.points[0].y
            for (i in 1..curve.points.lastIndex) {
                val offset = (i * 2 - 1) * 2
                result[offset] = curve.points[i].x
                result[offset + 1] = 1f - curve.points[i - 1].y
                result[offset + 2] = curve.points[i].x
                result[offset + 3] = 1f - curve.points[i].y
            }
            return result
        }
        val result = FloatArray(258)
        for (i in 0..128) {
            val input = i / 128f
            result[i * 2] = input
            result[i * 2 + 1] = 1f - curve.evaluate(input)
        }
        return result
    }
}
