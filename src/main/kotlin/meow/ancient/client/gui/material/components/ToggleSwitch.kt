package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import org.joml.Matrix4f

internal object ToggleSwitch {
    const val WIDTH = 24f
    const val HEIGHT = 14f

    fun render(matrix: Matrix4f, x: Float, y: Float, progress: Float, pipeline: ClientRenderPipeline) {
        val palette = M3Theme.current
        val value = progress.coerceIn(0f, 1f)
        val trackColor = palette.surfaceContainerHighest.interpolateTo(palette.primary, value.toDouble())
        RenderUtils.roundedRect(matrix, x, y, WIDTH, HEIGHT, trackColor, HEIGHT * 0.5f, pipeline)

        val diameter = 8f + 2f * value
        val centerX = x + HEIGHT * 0.5f + (WIDTH - HEIGHT) * value
        val centerY = y + HEIGHT * 0.5f
        val thumbColor = palette.outline.interpolateTo(palette.onPrimary, value.toDouble())
        RenderUtils.roundedRect(matrix, centerX - diameter * 0.5f, centerY - diameter * 0.5f,
            diameter, diameter, thumbColor, diameter * 0.5f, pipeline)
    }
}
