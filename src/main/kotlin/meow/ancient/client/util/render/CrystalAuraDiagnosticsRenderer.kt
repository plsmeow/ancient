package meow.ancient.client.util.render

import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.combat.CrystalAuraDiagnostics
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.client.util.render.rect.RectStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.Locale

internal class CrystalAuraDiagnosticsRenderer(private val enabled: () -> Boolean,
                                             private val snapshot: () -> CrystalAuraDiagnostics.Snapshot) {
    private var refreshAt = 0L
    private var lines = emptyList<String>()

    fun render(graphics: GuiGraphicsExtractor) {
        if (!enabled() || mc.player == null || mc.level == null || mc.gui.hud.isHidden) { refreshAt = 0L; return }
        val now = System.nanoTime()
        if (now >= refreshAt) {
            val state = snapshot()
            lines = buildList {
                val progress = if (state.reason == CrystalAuraDiagnostics.Reason.MINING) " · ${(state.progress * 100).toInt()}%" else ""
                add(state.reason.text + progress)
                if (state.detail.isNotEmpty()) add(state.detail)
                state.pos?.let { pos ->
                    add("Блок ${pos.x} ${pos.y} ${pos.z}" + if (state.miningTicks > 0) " · копание ~${state.miningTicks} т" else "")
                    if (state.damage > 0f || state.predictionTicks > 0.0)
                        add(String.format(Locale.ROOT, "Урон %.1f → %.1f · прогноз %.1f т",
                            state.damage, state.predictedDamage, state.predictionTicks))
                }
            }
            refreshAt = now + 100_000_000L
        }
        val width = minOf(300f, (graphics.guiWidth() - 16f).coerceAtLeast(0f))
        val height = 27f + lines.size * 12f
        val x = 8f
        val y = minOf(80f, (graphics.guiHeight() - height - 8f).coerceAtLeast(0f))
        val palette = M3Theme.current
        val matrix = MatrixControl.matrix4fStack
        RenderUtils.rect(matrix, x, y, width, height, RectStyle.rounded(palette.surfaceContainerLow.alpha(230), 8f))
        Fonts.google_sans_medium.drawString(matrix, "CrystalAura", x + 9f, y + 8f, 9f,
            palette.primary, false, ClientRenderPipeline.HUD_TEXT)
        val font = Fonts.google_sans_regular
        for ((index, line) in lines.withIndex()) {
            var text = line
            while (text.isNotEmpty() && font.getWidth(text, 8f) > width - 20f) text = text.dropLast(1)
            font.drawString(matrix, text, x + 9f, y + 25f + index * 12f, 8f,
                palette.onSurface, false, ClientRenderPipeline.HUD_TEXT)
        }
    }
}
