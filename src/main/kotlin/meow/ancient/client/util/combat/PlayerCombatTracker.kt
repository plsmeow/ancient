package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerCombatEvent
import meow.ancient.event.events.PlayerUpdateEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.entity.player.Player
import sweetie.evaware.flora.api.Commando
import java.util.UUID

/** Independent of Tags/Notifier being enabled; entity unload is never treated as death. */
object PlayerCombatTracker {
    private var world: ClientLevel? = null
    private val pops = HashMap<UUID, Int>()
    private val dead = HashMap<UUID, Long>()
    private val lastPop = HashMap<UUID, Long>()

    init { EventManager.register(this) }
    fun initialize() { }
    fun count(player: Player): Int = if (world === mc.level) pops[player.uuid] ?: 0 else 0
    fun lastPopNanos(player: Player): Long = if (world === mc.level) lastPop[player.uuid] ?: 0L else 0L

    private fun sync() {
        if (world !== mc.level) { clear(); world = mc.level }
    }
    private fun clear() { world = null; pops.clear(); dead.clear(); lastPop.clear() }
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = clear()

    @Commando fun onPacket(event: PacketReceiveEvent) {
        if (event.cancel) return
        val packet = event.packet as? ClientboundEntityEventPacket ?: return
        if (packet.eventId != 35.toByte() && packet.eventId != 3.toByte()) return
        val level = mc.level ?: return
        mc.execute {
            if (mc.level !== level) return@execute
            sync()
            val player = packet.getEntity(level) as? Player ?: return@execute
            if (packet.eventId == 35.toByte()) {
                dead.remove(player.uuid)
                val count = (pops[player.uuid] ?: 0) + 1
                pops[player.uuid] = count
                lastPop[player.uuid] = System.nanoTime()
                EventManager.post(PlayerCombatEvent(player.uuid, player.scoreboardName, player === mc.player,
                    PlayerCombatEvent.Kind.TOTEM, count))
            } else death(player)
        }
    }

    @Commando fun onUpdate(event: PlayerUpdateEvent) {
        sync()
        val level = mc.level ?: return
        for (entity in level.entitiesForRendering()) {
            if (entity !is Player) continue
            if (entity.isDeadOrDying) death(entity)
            else if (dead[entity.uuid]?.let { System.nanoTime() - it > 3_000_000_000L } == true) dead.remove(entity.uuid)
        }
    }

    private fun death(player: Player) {
        if (player.uuid in dead) return
        dead[player.uuid] = System.nanoTime()
        val count = pops.remove(player.uuid) ?: 0
        EventManager.post(PlayerCombatEvent(player.uuid, player.scoreboardName, player === mc.player,
            PlayerCombatEvent.Kind.DEATH, count))
    }
}
