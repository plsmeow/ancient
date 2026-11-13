package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.module.setting.BooleanSetting
import org.joml.Matrix4f

class BooleanComponent(private val boolSetting: BooleanSetting) : SettingComponent(boolSetting) {
    private val toggleAnim = AnimFloat(if (boolSetting.value) 1f else 0f, if (boolSetting.value) 1f else 0f, 16f)
    private val labelMarquee = MarqueeText()

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
        this.height = 18f

        val palette = M3Theme.current
        val toggleProgress = toggleAnim.update(if (boolSetting.value) 1f else 0f, dt)
        val switchW = ToggleSwitch.WIDTH
        val switchH = ToggleSwitch.HEIGHT
        val switchX = x + width - switchW - 2f
        val switchY = y + (height - switchH) * 0.5f
        val maxTextW = switchX - (x + 2f) - 6f

        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        labelMarquee.render(
            matrix = matrix,
            text = boolSetting.name,
            x = x + 2f,
            y = switchY + (switchH - 10f) * 0.5f - 1.5f,
            maxWidth = maxTextW,
            size = 10f,
            color = palette.onSurface,
            bgColor = palette.surfaceContainerLowest,
            isHovered = hovered,
            dt = dt,
            pipeline = ClientRenderPipeline.WINDOW_TEXT
        )

        ToggleSwitch.render(matrix, switchX, switchY, toggleProgress, ClientRenderPipeline.WINDOW_RECT)

        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isHovered(mouseX, mouseY, x, y, width, height)) {
            boolSetting.toggle()
            return true
        }
        return false
    }
}
