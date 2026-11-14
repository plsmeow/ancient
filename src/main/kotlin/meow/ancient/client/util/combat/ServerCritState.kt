package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.event.events.PacketReceiveEvent
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import sweetie.evaware.flora.api.Commando

object ServerCritState {
    private var hasPosition = false
    private var lastY = 0.0
    private var onGround = true
    private var fallDistance = 0.0
    private var sprinting = false
    private var movementSprinting = false
    private var sentMovement = false
    private var player: net.minecraft.client.player.LocalPlayer? = null
    private var world: net.minecraft.client.multiplayer.ClientLevel? = null

    fun reset() {
        hasPosition = false
        lastY = 0.0
        onGround = true
        fallDistance = 0.0
        sprinting = false
        movementSprinting = false
        sentMovement = false
        player = null
        world = null
    }

    val serverFallDistance: Double get() = fallDistance
    val serverOnGround: Boolean get() = onGround
    val serverSprinting: Boolean get() = sprinting
    val serverMovementSprinting: Boolean get() = movementSprinting

    fun wouldCritNow(): Boolean =
        hasPosition && fallDistance > 0.0 && !onGround && !sprinting

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) { syncContext() }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.cancel || event.packet !is ClientboundPlayerPositionPacket) return
        val expectedPlayer = mc.player
        mc.execute {
            if (!event.cancel && mc.player === expectedPlayer) {
                reset()
                syncContext()
            }
        }
    }

    private fun syncContext() {
        if (player === mc.player && world === mc.level) return
        reset()
        player = mc.player
        world = mc.level
    }

    @Commando
    fun onPacketSent(event: PacketSentEvent) {
        if (!mc.isSameThread) return
        syncContext()
        when (val packet = event.packet) {
            is ServerboundPlayerCommandPacket -> when (packet.action) {
                ServerboundPlayerCommandPacket.Action.START_SPRINTING -> recordSprintState(true)
                ServerboundPlayerCommandPacket.Action.STOP_SPRINTING -> recordSprintState(false)
                else -> {}
            }

            is ServerboundMovePlayerPacket -> {
                recordMovementState()
                sentMovement = true
                val player = mc.player
                val canAccumulateFall = player == null ||
                    !(player.isInWater || player.isInLava || player.isFallFlying || player.onClimbable())
                if (packet.hasPosition()) {
                    recordPositionPacket(packet.getY(lastY), packet.isOnGround, canAccumulateFall)
                } else {
                    recordStatusPacket(packet.isOnGround, canAccumulateFall)
                }
            }
            is ServerboundClientTickEndPacket -> {
                if (!sentMovement) recordMovementState()
                sentMovement = false
            }
        }
    }

    fun recordSprintState(value: Boolean) { sprinting = value }
    fun recordMovementState() { movementSprinting = sprinting }

    fun recordStatusPacket(packetOnGround: Boolean, canAccumulateFall: Boolean = true) {
        if (packetOnGround || !canAccumulateFall) fallDistance = 0.0
        onGround = packetOnGround
    }

    fun recordPositionPacket(y: Double, packetOnGround: Boolean, canAccumulateFall: Boolean) {
        if (!hasPosition) {
            hasPosition = true
            lastY = y
            onGround = packetOnGround
            fallDistance = 0.0
            return
        }
        val deltaY = y - lastY
        lastY = y
        if (!canAccumulateFall) fallDistance = 0.0
        else if (deltaY < 0.0) fallDistance -= deltaY
        if (packetOnGround) fallDistance = 0.0
        onGround = packetOnGround
    }
}
