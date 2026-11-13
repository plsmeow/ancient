package meow.ancient.client.util.action

import meow.ancient.client.extensions.mc
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerTickEndEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object ClientDiagnostics {
    data class Correction(val id: Int, val delta: Vec3, val timeNanos: Long)

    private var player: LocalPlayer? = null
    private var level: ClientLevel? = null
    private var lastPosition: Vec3? = null
    var speed = Vec3.ZERO
        private set
    var corrections = emptyList<Correction>()
        private set
    var correctionCount = 0L
        private set

    init { EventManager.register(this) }
    fun ensureInitialized() { }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) { reset() }

    private fun reset() {
        player = null
        level = null
        lastPosition = null
        speed = Vec3.ZERO
        corrections = emptyList()
        correctionCount = 0
    }

    @Commando
    fun onTickEnd(event: PlayerTickEndEvent) {
        val current = mc.player ?: return reset()
        if (player !== current || level !== mc.level) {
            reset()
            player = current
            level = mc.level
        }
        val position = current.position()
        speed = lastPosition?.let { position.subtract(it).scale(20.0) } ?: Vec3.ZERO
        lastPosition = position
    }

    @Commando
    fun onPacket(event: PacketReceiveEvent) {
        if (event.cancel) return
        val packet = event.packet as? ClientboundPlayerPositionPacket ?: return
        val expected = mc.player ?: return
        val world = mc.level
        mc.execute {
            if (mc.player !== expected || mc.level !== world) return@execute
            if (player !== expected || level !== world) {
                reset()
                player = expected
                level = world
            }
            val resolved = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(expected), packet.change(), packet.relatives())
            corrections = (listOf(Correction(packet.id(), resolved.position().subtract(expected.position()), System.nanoTime())) + corrections).take(3)
            correctionCount++
            lastPosition = null // Teleports are not flight speed measurements.
        }
    }
}
