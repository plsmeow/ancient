package meow.ancient.client.gui.debug

import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.action.ClientDiagnostics
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.client.util.render.rect.RectStyle
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.module.modules.other.TestModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.world.phys.Vec3
import org.lwjgl.sdl.SDLMouse
import java.util.Locale

object TestDiagnosticsPanel {
    private data class Row(val label: String, val value: String, val accent: Boolean = false)
    private var rows = emptyList<Row>()
    private var refreshAt = 0L
    private var dragging = false
    private var leftWasDown = false
    private var offsetX = 0f
    private var offsetY = 0f

    fun render(graphics: GuiGraphicsExtractor) {
        if (!TestModule.isEnabled() || !TestModule.debugPanel || mc.player == null || mc.level == null) {
            dragging = false
            leftWasDown = false
            refreshAt = 0L
            return
        }
        val now = System.nanoTime()
        if (now >= refreshAt) {
            rows = collectRows(now)
            refreshAt = now + 100_000_000L
        }
        val scale = minOf(TestModule.debugScale.toFloat(),
            ((graphics.guiHeight() - 16f) / (36f + rows.size * 12f)).coerceAtLeast(0.25f))
        val width = minOf(340f * scale, (graphics.guiWidth() - 16f).coerceAtLeast(0f))
        val rowHeight = 12f * scale
        val headerHeight = 26f * scale
        val height = headerHeight + rows.size * rowHeight + 10f * scale
        val maxX = (graphics.guiWidth() - width).coerceAtLeast(0f)
        val maxY = (graphics.guiHeight() - height).coerceAtLeast(0f)
        var x = TestModule.debugX.value.toFloat().coerceIn(0f, maxX)
        var y = TestModule.debugY.value.toFloat().coerceIn(0f, maxY)
        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        val down = (SDLMouse.SDL_GetMouseState(null, null) and SDLMouse.SDL_BUTTON_LMASK) != 0
        if (currentScreen is ChatScreen) {
            if (down && !leftWasDown && mouseX in x..(x + width) && mouseY in y..(y + headerHeight)) {
                dragging = true
                offsetX = mouseX - x
                offsetY = mouseY - y
            }
            if (!down) dragging = false
            if (dragging) {
                x = (mouseX - offsetX).coerceIn(0f, maxX)
                y = (mouseY - offsetY).coerceIn(0f, maxY)
                TestModule.debugX.value = x.toDouble()
                TestModule.debugY.value = y.toDouble()
            }
        } else dragging = false
        leftWasDown = down

        val matrix = MatrixControl.matrix4fStack
        val palette = M3Theme.current
        RenderUtils.rect(matrix, x, y, width, height,
            RectStyle.rounded(palette.surfaceContainerLow.alpha(235), 12f * scale))
        RenderUtils.rect(matrix, x + 10f * scale, y + headerHeight - 5f * scale,
            (width - 20f * scale).coerceAtLeast(0f), 1f,
            RectStyle.rounded(palette.outlineVariant.alpha(90), 0.5f))
        Fonts.google_sans_medium.drawString(matrix, "Client diagnostics", x + 11f * scale,
            y + 8f * scale, 9f * scale, palette.primary, false, ClientRenderPipeline.HUD_TEXT)
        val font = Fonts.google_sans_regular
        val size = 7.7f * scale
        val labelWidth = 87f * scale
        for ((index, row) in rows.withIndex()) {
            val ry = y + headerHeight + index * rowHeight
            font.drawString(matrix, row.label, x + 11f * scale, ry, size,
                palette.onSurfaceVariant, false, ClientRenderPipeline.HUD_TEXT)
            val available = (width - labelWidth - 22f * scale).coerceAtLeast(0f)
            font.drawString(matrix, trim(row.value, available, size), x + labelWidth, ry, size,
                if (row.accent) palette.tertiary else palette.onSurface,
                false, ClientRenderPipeline.HUD_TEXT)
        }
    }

    private fun collectRows(now: Long): List<Row> = buildList {
        val player = mc.player ?: return@buildList
        val actions = ActionCoordinator.snapshot()
        if ("Actions" in TestModule.debugSections) {
            add(Row("Priority", actions.preferred))
            add(Row("Acted this tick", actions.committed))
            add(Row("Slot owner", actions.slotOwner))
            val slot = SilentSwapUtil.debugSnapshot()
            add(Row("Server / client", "${slot(slot.serverSlot)} / ${slot(slot.clientSlot)}"))
            add(Row("Slot restore", if (slot.pendingSlot < 0) "None" else "${slot(slot.pendingSlot)} in ${slot.holdTicks} ticks"))
            add(Row("Packet state", when {
                slot.teleportPending -> "Teleport pending"
                slot.movementSent -> "Movement sent"
                slot.actionLocked -> "Action locked"
                else -> "Ready"
            }))
            add(Row("Blocked checks", actions.blockedCount.toString()))
            add(Row("Last conflict", actions.lastBlocked.ifEmpty { "None" }, actions.lastBlocked.isNotEmpty()))
        }
        if ("Rotation" in TestModule.debugSections) {
            add(Row("Rotation owner", RotationManager.rotationOwner ?: "Camera"))
            val rotation = RotationManager.getServerRotation()
            add(Row("Yaw / pitch", "${number(rotation.yaw.toDouble())} / ${number(rotation.pitch.toDouble())}"))
            add(Row("Rotation priority", RotationManager.rotationPriority.toString()))
        }
        if ("Mining" in TestModule.debugSections) {
            val targets = PacketMineModule.debugTargets()
            if (targets.isEmpty()) add(Row("PacketMine", "Idle"))
            for ((index, target) in targets.withIndex()) {
                add(Row("Block ${index + 1}", "${target.x} ${target.y} ${target.z}"))
                add(Row("Mine state", "${target.phase} · ${(target.progress * 100).toInt()}%${if (target.primed) " · primed" else ""}"))
                if (target.phase == "CONFIRM") add(Row("Server confirm", "${target.confirmationTicks} ticks waiting", true))
                add(Row("Tool slot", slot(target.toolSlot)))
            }
        }
        if ("Motion" in TestModule.debugSections) {
            val speed = ClientDiagnostics.speed
            add(Row("Speed XYZ", vector(speed) + " b/s"))
            add(Row("Horizontal / 3D", "${number(speed.horizontalDistance())} / ${number(speed.length())} b/s"))
            add(Row("Motion XYZ", vector(player.deltaMovement) + " b/t"))
        }
        if ("Corrections" in TestModule.debugSections) {
            add(Row("Corrections", ClientDiagnostics.correctionCount.toString()))
            if (ClientDiagnostics.corrections.isEmpty()) add(Row("Last correction", "None"))
            for (correction in ClientDiagnostics.corrections) {
                val age = (now - correction.timeNanos) / 1.0E9
                add(Row("TP #${correction.id}", "${number(correction.delta.length())} b · ${number(age)}s ago", age < 3.0))
                add(Row("Correction XYZ", vector(correction.delta)))
            }
        }
    }

    private fun slot(slot: Int): String = if (slot in 0..8) (slot + 1).toString() else "Unknown"
    private fun number(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
    private fun vector(value: Vec3): String = "${number(value.x)} / ${number(value.y)} / ${number(value.z)}"

    private fun trim(text: String, width: Float, size: Float): String {
        val font = Fonts.google_sans_regular
        if (font.getWidth(text, size) <= width) return text
        val budget = width - font.getWidth("…", size)
        var result = text
        while (result.isNotEmpty() && font.getWidth(result, size) > budget) result = result.dropLast(1)
        return if (budget < 0f) "" else result + "…"
    }
}
