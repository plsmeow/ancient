package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.GamePacketTypes
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import sweetie.evaware.flora.api.Commando

object SilentSwapUtil {
    private class PacketState(val connection: Connection) {
        var actionLocked = false
        var sentMovement = false
        @Volatile var teleportPending = false
            private set
        private var teleportId = 0
        private var teleportConfirmed = false

        @Synchronized
        fun receiveTeleport(id: Int) {
            teleportId = id
            teleportPending = true
            teleportConfirmed = false
        }

        @Synchronized
        fun confirmTeleport(id: Int) {
            if (teleportPending && teleportId == id) teleportConfirmed = true
        }

        @Synchronized
        fun finishTeleportTick() {
            if (teleportConfirmed) {
                teleportPending = false
                teleportConfirmed = false
            }
        }
    }

    private class SwapState(val player: LocalPlayer, val world: ClientLevel, val packets: PacketState) {
        val connection = packets.connection
        var serverSlot = -1
        var expectedSlot = player.inventory.selectedSlot
        var pendingSlot = -1
        var temporaryDepth = 0
        var scopeDepth = 0
        var scopeSlot = -1
        var sendDepth = 0

        fun isCurrent(): Boolean = mc.player === player && mc.level === world
            && player.connection.connection === connection && connection.isConnected && !player.isSpectator

        fun isManaged(): Boolean = AnticheatManager.isSilentSwapNew || pendingSlot >= 0
            || temporaryDepth > 0 || scopeDepth > 0
    }

    @Volatile private var state: SwapState? = null
    @Volatile private var packets: PacketState? = null
    private val HOTBAR = 0..8

    val serverSlot: Int
        get() {
            val player = mc.player ?: return -1
            val current = state
            return if (AnticheatManager.isSilentSwapNew && current != null && current.isCurrent()
                && current.serverSlot in HOTBAR) current.serverSlot else player.inventory.selectedSlot
        }

    init {
        EventManager.register(this)
        ClientTickEvents.START_CLIENT_TICK.register { updateSelection() }
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        state = null
    }

    fun withSlot(slot: Int, action: () -> Unit) {
        if (slot !in HOTBAR) return
        InventoryMoveModule.runInventoryAction(InventoryActionSource.HOTBAR, Runnable {
            val player = mc.player ?: return@Runnable
            if (!canSwap(player)) return@Runnable
            if (!AnticheatManager.isSilentSwapNew) {
                val previous = player.inventory.selectedSlot
                if (previous == slot) {
                    action()
                    return@Runnable
                }
                player.connection.send(ServerboundSetCarriedItemPacket(slot))
                try {
                    action()
                } finally {
                    restoreSimple(player, previous, false)
                }
                return@Runnable
            }
            val current = currentState(player) ?: return@Runnable
            observeSelection(current)
            if (current.scopeDepth > 0 && current.scopeSlot != slot) return@Runnable
            val swapped = swapTo(current, slot) ?: return@Runnable
            current.scopeDepth++
            current.scopeSlot = slot
            try {
                action()
            } finally {
                if (state === current && current.isCurrent()) {
                    observeSelection(current)
                    current.scopeDepth--
                    if (current.scopeDepth == 0) {
                        current.scopeSlot = -1
                        if (swapped) releaseTemporary(current)
                    }
                }
            }
        })
    }

    fun selectSlot(slot: Int): Boolean {
        if (slot !in HOTBAR) return false
        if (AnticheatManager.isSilentSwapNew && !mc.isSameThread) return false
        var selected = false
        InventoryMoveModule.runInventoryAction(InventoryActionSource.HOTBAR, Runnable {
            val player = mc.player ?: return@Runnable
            if (!canSwap(player)) return@Runnable
            if (!AnticheatManager.isSilentSwapNew) {
                if (player.inventory.selectedSlot == slot) {
                    selected = true
                    return@Runnable
                }
                player.connection.send(ServerboundSetCarriedItemPacket(slot))
                player.inventory.selectedSlot = slot
                selected = true
                return@Runnable
            }
            val current = currentState(player) ?: return@Runnable
            observeSelection(current)
            if (swapTo(current, slot) == null) return@Runnable
            selected = true
        })
        return selected
    }

    fun restoreSlot(slot: Int, syncClientSlot: Boolean = true) {
        if (slot !in HOTBAR) return
        val player = mc.player ?: return
        if (!canSwap(player)) return
        if (!AnticheatManager.isSilentSwapNew) {
            restoreSimple(player, slot, syncClientSlot)
            return
        }
        if (!mc.isSameThread) return
        val current = currentState(player) ?: return
        observeSelection(current)
        if (current.scopeDepth > 0 || current.temporaryDepth <= 0) return
        releaseTemporary(current)
    }

    @JvmStatic
    fun canSendCarriedItem(connection: Connection, slot: Int, lastServerSlot: Int): Boolean {
        val player = mc.player ?: return true
        if (player.connection.connection !== connection) return true
        val current = currentState(player) ?: return true
        if (lastServerSlot in HOTBAR) current.serverSlot = lastServerSlot
        if (AnticheatManager.isSilentSwapNew) mc.gameMode?.carriedIndex = player.inventory.selectedSlot
        if (!current.isManaged()) return true
        if (current.sendDepth == 0) observeSelection(current)
        if (slot != current.serverSlot && (current.packets.actionLocked || current.packets.sentMovement
                || current.sendDepth == 0 && current.pendingSlot >= 0
                || current.scopeDepth > 0 && slot != current.scopeSlot)) {
            if (current.sendDepth == 0 && slot in HOTBAR) current.pendingSlot = slot
            return false
        }
        return true
    }

    @JvmStatic
    fun onPacketSent(connection: Connection, packet: Packet<*>, lastServerSlot: Int) {
        val player = mc.player ?: return
        if (player.connection.connection !== connection) return
        val current = currentState(player) ?: return
        if (lastServerSlot in HOTBAR) current.serverSlot = lastServerSlot
        when (packet) {
            is ServerboundSetCarriedItemPacket -> {
                if (current.sendDepth == 0) current.pendingSlot = -1
                if (AnticheatManager.isSilentSwapNew) mc.gameMode?.carriedIndex = player.inventory.selectedSlot
            }
            is ServerboundMovePlayerPacket -> {
                if (isTickPacket(current.packets, packet)) current.packets.actionLocked = false
                current.packets.sentMovement = true
            }
            is ServerboundClientTickEndPacket -> {
                if (isTickPacket(current.packets, packet)) current.packets.actionLocked = false
                current.packets.sentMovement = false
                current.packets.finishTeleportTick()
            }
            is ServerboundPlayerCommandPacket -> {
                if (!player.isPassenger || packet.action != ServerboundPlayerCommandPacket.Action.START_SPRINTING
                    && packet.action != ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                    current.packets.actionLocked = true
                }
            }
            is ServerboundAcceptTeleportationPacket -> current.packets.confirmTeleport(packet.id())
            else -> when (packet.type()) {
                GamePacketTypes.SERVERBOUND_ATTACK, GamePacketTypes.SERVERBOUND_PUNCH, GamePacketTypes.SERVERBOUND_INTERACT,
                GamePacketTypes.SERVERBOUND_SPECTATOR_ACTION, GamePacketTypes.SERVERBOUND_USE_ITEM,
                GamePacketTypes.SERVERBOUND_USE_ITEM_ON, GamePacketTypes.SERVERBOUND_PLAYER_ACTION -> {
                    current.packets.actionLocked = true
                }
            }
        }
    }

    @JvmStatic
    fun onPacketSending(connection: Connection, packet: Packet<*>, lastServerSlot: Int) {
        if (!mc.isSameThread) return
        val current = state ?: return
        if (!current.isCurrent() || current.connection !== connection || !current.isManaged()) return
        if (lastServerSlot in HOTBAR) current.serverSlot = lastServerSlot
        observeSelection(current)
    }

    fun onTickStart() {
        if (!mc.isSameThread) return
        val current = state ?: return
        if (!current.isCurrent() || !current.isManaged() || current.sendDepth > 0) return
        if (current.packets.teleportPending || current.packets.actionLocked) return
        observeSelection(current)
        flushPendingRestore(current)
    }

    @JvmStatic
    fun onPacketReceived(connection: Connection, packet: Packet<*>) {
        val current = packets ?: return
        if (current.connection !== connection) return
        if (packet is ClientboundPlayerPositionPacket) current.receiveTeleport(packet.id())
    }

    @JvmStatic
    fun onConnectionClosed(connection: Connection) {
        mc.execute {
            if (packets?.connection === connection) {
                state = null
                packets = null
            }
        }
    }

    private fun isTickPacket(current: PacketState, packet: Packet<*>): Boolean {
        if (current.teleportPending) return false
        return when (packet) {
            is ServerboundMovePlayerPacket -> true
            is ServerboundClientTickEndPacket -> !current.sentMovement
            else -> false
        }
    }

    private fun swapTo(current: SwapState, slot: Int): Boolean? {
        val before = current.serverSlot
        if (!sendSlot(current, slot)) return null
        if (before == slot) return false
        current.temporaryDepth++
        if (current.pendingSlot < 0) current.pendingSlot = current.player.inventory.selectedSlot
        return true
    }

    private fun releaseTemporary(current: SwapState) {
        current.temporaryDepth--
        if (current.temporaryDepth == 0) current.pendingSlot = current.player.inventory.selectedSlot
    }

    private fun sendSlot(current: SwapState, slot: Int): Boolean {
        if (!current.isCurrent()) return false
        if ((current.packets.actionLocked || current.packets.sentMovement)
            && slot != current.serverSlot) return false
        if (current.scopeDepth > 0 && slot != current.scopeSlot) return false
        if (AnticheatManager.isSilentSwapNew) mc.gameMode?.carriedIndex = current.player.inventory.selectedSlot
        if (current.serverSlot == slot) return true
        current.sendDepth++
        try {
            current.player.connection.send(ServerboundSetCarriedItemPacket(slot))
        } finally {
            current.sendDepth--
        }
        if (state !== current || !current.isCurrent()) return false
        return current.serverSlot == slot
    }

    private fun updateSelection() {
        val current = state ?: return
        if (!current.isCurrent()) {
            state = null
            return
        }
        if (current.isManaged()) observeSelection(current)
    }

    private fun flushPendingRestore(current: SwapState) {
        if (!current.isCurrent()) {
            state = null
            return
        }
        if (!current.isManaged()) return
        observeSelection(current)
        if (current.pendingSlot !in HOTBAR) return
        val slot = current.pendingSlot
        if (!sendSlot(current, slot)) return
        current.pendingSlot = -1
    }

    private fun observeSelection(current: SwapState) {
        val selected = current.player.inventory.selectedSlot
        if (selected == current.expectedSlot) return
        current.expectedSlot = selected
        current.pendingSlot = selected.takeIf { it in HOTBAR } ?: -1
        if (AnticheatManager.isSilentSwapNew) mc.gameMode?.carriedIndex = selected
    }

    private fun currentState(player: LocalPlayer): SwapState? {
        val world = mc.level ?: return null
        if (!canSwap(player)) return null
        val connection = player.connection.connection
        val interval = packets?.takeIf { it.connection === connection }
            ?: PacketState(connection).also { packets = it }
        return state?.takeIf { it.isCurrent() } ?: SwapState(player, world, interval).also { state = it }
    }

    private fun restoreSimple(player: LocalPlayer, slot: Int, syncClientSlot: Boolean) {
        player.connection.send(ServerboundSetCarriedItemPacket(slot))
        if (syncClientSlot && mc.player === player) player.inventory.selectedSlot = slot
    }

    private fun canSwap(player: LocalPlayer): Boolean =
        mc.player === player && player.connection.connection.isConnected && !player.isSpectator
}
