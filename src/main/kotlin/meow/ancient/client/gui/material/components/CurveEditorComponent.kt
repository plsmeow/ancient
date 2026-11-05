package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.Curve
import meow.ancient.module.setting.CurveSetting
import org.joml.Matrix4f

class CurveEditorComponent(private val curveSetting: CurveSetting) : SettingComponent(curveSetting) {
    private val graph = CurveGraph(curveSetting)
    private var cachedCurve: Curve? = null
    private var caption = ""

    override fun render(matrix: Matrix4f, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, dt: Float): Float {
        this.x = x
        this.y = y
        this.width = width
        height = 101f
        val palette = M3Theme.current
        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        RenderUtils.roundedRect(matrix, x + 2f, y + 1f, width - 4f, height - 3f,
            if (hovered) palette.surfaceContainerHigh else palette.surfaceContainer, 8f, ClientRenderPipeline.WINDOW_RECT)
        val title = fitText(setting.name, width - 26f, 10f, 8f)
        Fonts.google_sans_medium.drawString(matrix, title.text, x + 8f, y + 7f, title.size, palette.onSurface, false, ClientRenderPipeline.WINDOW_TEXT)
        Fonts.google_sans_medium.drawString(matrix, "›", x + width - 17f, y + 5f, 14f, palette.primary, false, ClientRenderPipeline.WINDOW_TEXT)
        graph.render(matrix, x + 14f, y + 31f, width - 28f, 43f, mouseX, mouseY, false)
        if (cachedCurve !== curveSetting.value) {
            cachedCurve = curveSetting.value
            caption = "${curveSetting.value.interpolation.displayName} · ${curveSetting.value.points.size} точек"
        }
        Fonts.google_sans_regular.drawCenteredString(matrix, caption, x + width * 0.5f, y + 86f, 8f, palette.onSurfaceVariant, false, ClientRenderPipeline.WINDOW_TEXT)
        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !isHovered(mouseX, mouseY, x, y, width, height)) return false
        ClickGuiScreen.openEditor(CurveEditorWindow(curveSetting))
        return true
    }
}
