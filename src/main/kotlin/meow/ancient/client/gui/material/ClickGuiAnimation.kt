package meow.ancient.client.gui.material

import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.components.CategoryPanel
import meow.ancient.client.gui.material.components.SearchBarComponent
import meow.ancient.client.gui.material.components.ThemePickerComponent
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.module.modules.render.ClickGuiModule
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

internal class PanelFlight(
    val startX: Float,
    val startY: Float,
    val destX: Float,
    val destY: Float,
    val durationMult: Float,
    val delay: Float,
    val arcAmount: Float
)

object ClickGuiAnimation {
    const val BASE_OPEN_DURATION = 0.38f
    const val BASE_CLOSE_DURATION = 0.32f

    var isOpening = false
        private set
    var isClosing = false
        private set

    private var openStartTime = 0L
    private var closeStartTime = 0L
    private val openFlights = HashMap<CategoryPanel, PanelFlight>()
    private val closeFlights = HashMap<CategoryPanel, PanelFlight>()
    private var savedSearchBar: SearchBarComponent? = null
    private var savedThemePicker: ThemePickerComponent? = null

    fun startOpen(panels: List<CategoryPanel>, screenW: Float, screenH: Float) {
        isOpening = true
        isClosing = false
        openStartTime = System.currentTimeMillis()
        openFlights.clear()
        closeFlights.clear()
        savedSearchBar = null
        savedThemePicker = null

        val count = panels.size
        val maxDim = maxOf(screenW, screenH)
        for (i in 0 until count) {
            val panel = panels[i]
            val angle = Random.nextFloat() * 6.2831853f
            val dist = maxDim * (1.1f + Random.nextFloat() * 0.5f)
            val startX = panel.x + cos(angle) * dist
            val startY = panel.y + sin(angle) * dist
            val durationMult = 0.85f + Random.nextFloat() * 0.3f
            val delay = Random.nextFloat() * 0.05f
            val arcAmount = (Random.nextFloat() - 0.5f) * 160f
            openFlights[panel] = PanelFlight(startX, startY, panel.x, panel.y, durationMult, delay, arcAmount)
        }
    }

    fun finishOpen() {
        isOpening = false
        openFlights.clear()
    }

    fun startClose(
        panels: List<CategoryPanel>,
        searchBar: SearchBarComponent,
        themePicker: ThemePickerComponent,
        screenW: Float,
        screenH: Float
    ) {
        if (isClosing) return
        isClosing = true
        isOpening = false
        closeStartTime = System.currentTimeMillis()
        openFlights.clear()
        closeFlights.clear()
        savedSearchBar = searchBar
        savedThemePicker = themePicker

        val count = panels.size
        val maxDim = maxOf(screenW, screenH)
        for (i in 0 until count) {
            val panel = panels[i]
            val angle = Random.nextFloat() * 6.2831853f
            val dist = maxDim * (1.1f + Random.nextFloat() * 0.5f)
            val destX = panel.x + cos(angle) * dist
            val destY = panel.y + sin(angle) * dist
            val durationMult = 0.85f + Random.nextFloat() * 0.3f
            val delay = Random.nextFloat() * 0.05f
            val arcAmount = (Random.nextFloat() - 0.5f) * 160f
            closeFlights[panel] = PanelFlight(panel.x, panel.y, destX, destY, durationMult, delay, arcAmount)
        }
    }

    fun finishClose() {
        isClosing = false
        closeFlights.clear()
        savedSearchBar = null
        savedThemePicker = null
    }

    fun reset() {
        isOpening = false
        isClosing = false
        openFlights.clear()
        closeFlights.clear()
        savedSearchBar = null
        savedThemePicker = null
    }

    fun getOpenProgress(): Float {
        if (!isOpening) return 1f
        val elapsed = (System.currentTimeMillis() - openStartTime) / 1000f
        val maxDuration = BASE_OPEN_DURATION * 1.25f
        if (elapsed >= maxDuration) {
            isOpening = false
            return 1f
        }
        return (elapsed / maxDuration).coerceIn(0f, 1f)
    }

    fun getCloseProgress(): Float {
        if (!isClosing) return 0f
        val elapsed = (System.currentTimeMillis() - closeStartTime) / 1000f
        val maxDuration = BASE_CLOSE_DURATION * 1.25f
        return (elapsed / maxDuration).coerceIn(0f, 1f)
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 0.45f
        val c3 = c1 + 1f
        val inv = t - 1f
        return 1f + c3 * inv * inv * inv + c1 * inv * inv
    }

    private fun easeInBack(t: Float): Float {
        val c1 = 0.45f
        val c3 = c1 + 1f
        return c3 * t * t * t - c1 * t * t
    }

    fun getPanelOpenOffset(panel: CategoryPanel): Pair<Float, Float> {
        if (!isOpening) return Pair(0f, 0f)
        val flight = openFlights[panel] ?: return Pair(0f, 0f)
        val elapsed = (System.currentTimeMillis() - openStartTime) / 1000f
        val panelDuration = BASE_OPEN_DURATION * flight.durationMult
        val p = ((elapsed - flight.delay) / panelDuration).coerceIn(0f, 1f)
        val eased = easeOutBack(p)

        val dx = (flight.destX - flight.startX) * (1f - eased)
        val dy = (flight.destY - flight.startY) * (1f - eased)
        val arc = sin((1f - p) * Math.PI.toFloat()) * flight.arcAmount
        val len = hypot(flight.destX - flight.startX, flight.destY - flight.startY).coerceAtLeast(1f)
        val nx = -(flight.destY - flight.startY) / len
        val ny = (flight.destX - flight.startX) / len

        return Pair(dx + nx * arc, dy + ny * arc)
    }

    fun getBarOffset(): Float {
        if (!isOpening) return 0f
        val elapsed = (System.currentTimeMillis() - openStartTime) / 1000f
        val maxDuration = BASE_OPEN_DURATION * 1.15f
        if (elapsed >= maxDuration) return 0f
        val progress = (elapsed / maxDuration).coerceIn(0f, 1f)
        val inv = 1f - progress
        val eased = 1f - inv * inv * inv * inv
        return -60f * (1f - eased)
    }

    fun renderClosing(gfx: GuiGraphicsExtractor, dt: Float = 0.016f) {
        if (!isClosing) return
        val elapsed = (System.currentTimeMillis() - closeStartTime) / 1000f
        val maxDuration = BASE_CLOSE_DURATION * 1.25f
        if (elapsed >= maxDuration) {
            finishClose()
            return
        }

        val matrix = MatrixControl.matrix4fStack
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()
        val progress = (elapsed / maxDuration).coerceIn(0f, 1f)
        val scrimAlphaRatio = 1f - progress

        if (ClickGuiModule.blur && mc.options.getMenuBackgroundBlurriness() == 0) {
            val scrimAlpha = (80 * scrimAlphaRatio).toInt().coerceIn(0, 255)
            if (scrimAlpha > 0) {
                RenderUtils.rect(
                    matrix = matrix,
                    x = 0f,
                    y = 0f,
                    width = screenW,
                    height = screenH,
                    color = Color4b(0, 0, 0, scrimAlpha),
                    pipeline = ClientRenderPipeline.LOW
                )
            }
        }

        val barOffset = -70f * (progress * progress * progress)
        savedSearchBar?.let { sb ->
            sb.animOffsetY = barOffset
            gfx.nextStratum()
            sb.render(matrix, -1, -1, dt)
        }

        savedThemePicker?.let { tp ->
            val origY = tp.y
            tp.y += barOffset
            try {
                gfx.nextStratum()
                tp.render(matrix, -1, -1, dt)
            } finally {
                tp.y = origY
            }
        }

        for ((panel, flight) in closeFlights) {
            val panelDuration = BASE_CLOSE_DURATION * flight.durationMult
            val p = ((elapsed - flight.delay) / panelDuration).coerceIn(0f, 1f)
            val eased = easeInBack(p)

            val dx = (flight.destX - flight.startX) * eased
            val dy = (flight.destY - flight.startY) * eased
            val arc = sin(p * Math.PI.toFloat()) * flight.arcAmount
            val len = hypot(flight.destX - flight.startX, flight.destY - flight.startY).coerceAtLeast(1f)
            val nx = -(flight.destY - flight.startY) / len
            val ny = (flight.destX - flight.startX) / len

            val origX = panel.x
            val origY = panel.y
            panel.x = flight.startX + dx + nx * arc
            panel.y = flight.startY + dy + ny * arc
            try {
                gfx.nextStratum()
                panel.render(matrix, -1, -1, dt)
            } finally {
                panel.x = origX
                panel.y = origY
            }
        }
    }
}
