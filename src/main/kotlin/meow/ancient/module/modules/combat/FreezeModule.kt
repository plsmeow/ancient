package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import sweetie.evaware.flora.api.Commando

object FreezeModule : Module(
    name = "Freeze",
    category = COMBAT,
    description = "Briefly freezes one enemy client-side without delaying your own packets."
) {
    private val targetMode by mode("Target", "Nearest", listOf("Nearest", "HitAura", "Crosshair"))
    private val duration by number("Freeze Time", 250.0, 50.0, 10000.0, 10.0, 0, "ms")
    private val interval by number("Interval", 250.0, 100.0, 1000.0, 10.0, 0, "ms")
    private val range by number("Range", 6.0, 1.0, 12.0, 0.1, 1)
    private val onlyOnAttack by boolean("Only On Attack", false)

    private val packets = ArrayDeque<Packet<ClientGamePacketListener>>()
    private var listener: ClientPacketListener? = null
    private var level: ClientLevel? = null
    private var target: Player? = null
    private var frozenUntil = 0L
    private var nextFreezeAt = 0L
    private var replaying = false
    private const val MAX_PACKETS = 2048

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        release()
        reset()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        reset()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val connection = mc.connection
        val world = mc.level
        val player = mc.player
        if (connection == null || world == null || player == null) {
            reset()
            return
        }
        if (listener !== connection || level !== world) {
            reset()
            listener = connection
            level = world
        }
        if (!player.isAlive || onlyOnAttack && !mc.options.keyAttack.isDown &&
            !(HitAuraModule.isEnabled() && HitAuraModule.currentTarget != null)) {
            release()
            return
        }

        val now = System.nanoTime()
        if (target != null && now >= frozenUntil) release()
        val selected = selectTarget(world)
        if (target != null && target !== selected) release()
        if (target == null && selected != null && now >= nextFreezeAt) {
            target = selected
            frozenUntil = now + duration.toLong() * 1_000_000L
        }
    }

    fun delayPacket(connection: ClientPacketListener, entity: Entity?, packet: Packet<ClientGamePacketListener>): Boolean {
        if (!isEnabled() || replaying || entity == null || target !== entity) return false
        if (listener !== connection || level !== mc.level) {
            reset()
            return false
        }
        if (!entity.isAlive || entity.isRemoved || entity.isPassenger || entity.isVehicle ||
            System.nanoTime() >= frozenUntil || packets.size >= MAX_PACKETS) {
            release()
            return false
        }
        packets.addLast(packet)
        return true
    }

    fun shouldFreeze(entity: Entity): Boolean {
        if (!isEnabled() || replaying || target !== entity) return false
        if (listener !== mc.connection || level !== mc.level) {
            reset()
            return false
        }
        if (!entity.isAlive || entity.isRemoved || entity.isPassenger || entity.isVehicle ||
            System.nanoTime() >= frozenUntil) {
            release()
            return false
        }
        return true
    }

    fun onRemoveEntities(connection: ClientPacketListener, ids: it.unimi.dsi.fastutil.ints.IntList) {
        if (listener === connection && target?.let { ids.contains(it.id) } == true) {
            packets.clear()
            target = null
            frozenUntil = 0L
            nextFreezeAt = System.nanoTime() + interval.toLong() * 1_000_000L
        }
    }

    fun onCorrection(connection: ClientPacketListener) {
        if (listener === connection) release()
    }

    fun onRespawn(connection: ClientPacketListener) {
        if (listener === connection) reset()
    }

    private fun selectTarget(world: ClientLevel): Player? {
        when (targetMode) {
            "HitAura" -> return if (HitAuraModule.isEnabled()) {
                (HitAuraModule.currentTarget as? Player)?.takeIf(::isValidTarget)
            } else null
            "Crosshair" -> return ((mc.hitResult as? EntityHitResult)?.entity as? Player)?.takeIf(::isValidTarget)
        }
        val player = mc.player ?: return null
        var nearest: Player? = null
        var distance = range * range
        for (candidate in world.players()) {
            if (!isValidTarget(candidate)) continue
            val candidateDistance = player.distanceToSqr(candidate)
            if (candidateDistance <= distance) {
                nearest = candidate
                distance = candidateDistance
            }
        }
        return nearest
    }

    private fun isValidTarget(entity: Entity): Boolean {
        val player = mc.player ?: return false
        return entity is Player && entity !== player && entity.isAlive && !entity.isRemoved &&
            !entity.isSpectator && !entity.isPassenger && !entity.isVehicle &&
            player.distanceToSqr(entity) <= range * range &&
            !FriendRepository.isFriend(entity) && !AntiBotModule.isBot(entity) && player.hasLineOfSight(entity)
    }

    private fun release() {
        if (target == null && packets.isEmpty()) return
        target = null
        frozenUntil = 0L
        nextFreezeAt = System.nanoTime() + interval.toLong() * 1_000_000L
        val connection = listener
        if (connection == null || connection !== mc.connection || level !== mc.level) {
            packets.clear()
            return
        }
        replaying = true
        try {
            while (packets.isNotEmpty()) packets.removeFirst().handle(connection)
        } finally {
            packets.clear()
            replaying = false
        }
    }

    private fun reset() {
        packets.clear()
        listener = null
        level = null
        target = null
        frozenUntil = 0L
        nextFreezeAt = 0L
        replaying = false
    }
}
