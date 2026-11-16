package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.ProjectionUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.OverlayRenderEvent
import meow.ancient.event.events.WorldRenderEvent
import meow.ancient.module.Module
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import java.util.UUID

object BreakEspModule : Module("BreakESP", RENDER, "Shows enemy mining positions and player names") {
    private val doubleBreak by boolean("DoubleBreak", true, "Retains the previous observed block as a delayed second break")
    private val range by number("Range", 32.0, 4.0, 128.0, 1.0, 0)
    private val timeout by number("Timeout", 5.0, 1.0, 15.0, 0.5, 1, unit = "s")
    private data class Break(val pos: BlockPos, val name: String, val uuid: UUID, var progress: Int, var updated: Long)
    private val breaks = HashMap<Int, MutableList<Break>>()
    private var world: ClientLevel? = null

    private fun clear() { breaks.clear(); world = null }
    override fun onEnable() = clear()
    override fun onDisable() = clear()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = clear()

    @Commando fun onPacket(event: PacketReceiveEvent) {
        if (event.cancel) return
        val packet = event.packet as? ClientboundBlockDestructionPacket ?: return
        val level = mc.level ?: return
        mc.execute {
            if (!isEnabled() || mc.level !== level) return@execute
            if (world !== level) { clear(); world = level }
            val enemy = level.getEntity(packet.id) as? Player ?: return@execute
            if (enemy === mc.player) return@execute
            val list = breaks.getOrPut(enemy.id) { ArrayList() }
            list.removeAll { it.uuid != enemy.uuid }
            val previous = list.firstOrNull { it.pos == packet.pos }
            if (packet.progress !in 0..9) {
                // The protocol only broadcasts one active position per entity. In DoubleBreak
                // mode this may be a delayed destroy; bound its life instead of inventing blocks.
                if (!doubleBreak) list.removeAll { it.pos == packet.pos }
                return@execute
            }
            val now = System.nanoTime()
            if (previous != null) {
                previous.progress = packet.progress; previous.updated = now
                list.remove(previous); list.add(previous)
            }
            else list.add(Break(packet.pos.immutable(), enemy.scoreboardName, enemy.uuid, packet.progress, now))
            while (list.size > if (doubleBreak) 2 else 1) list.removeAt(0)
        }
    }

    private fun visible(): List<Break> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        if (world !== level) { clear(); return emptyList() }
        val now = System.nanoTime()
        breaks.entries.removeIf { (id, list) ->
            val enemy = level.getEntity(id) as? Player
            list.removeAll { now - it.updated > (timeout * 1e9).toLong() || level.getBlockState(it.pos).isAir
                || enemy == null || !enemy.isAlive || enemy.uuid != it.uuid }
            while (list.size > if (doubleBreak) 2 else 1) list.removeAt(0)
            list.isEmpty()
        }
        return breaks.values.flatten().filter { player.eyePosition.distanceToSqr(Vec3.atCenterOf(it.pos)) <= range * range }
    }

    @Commando fun onWorld(event: WorldRenderEvent) {
        val palette = M3Theme.current
        for (entry in visible()) {
            val alpha = 30 + entry.progress * 7
            Gizmos.cuboid(AABB(entry.pos), GizmoStyle.strokeAndFill(palette.primary.argb, 1.5f,
                palette.primary.alpha(alpha).argb)).setAlwaysOnTop()
        }
    }
    @Commando fun onOverlay(event: OverlayRenderEvent) {
        val font = Fonts.google_sans_medium
        for (entry in visible()) {
            val screen = ProjectionUtil.worldToScreen(Vec3.atCenterOf(entry.pos)) ?: continue
            font.drawCenteredString(MatrixControl.matrix4fStack, entry.name, screen.x, screen.y - 4f,
                8f, M3Theme.current.onSurface, shadow = true, pipeline = ClientRenderPipeline.HUD_TEXT)
        }
    }
}
