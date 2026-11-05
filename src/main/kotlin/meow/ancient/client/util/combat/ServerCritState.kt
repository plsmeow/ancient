package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSendEvent
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import sweetie.evaware.flora.api.Commando

object ServerCritState {
    private var hasPosition = false
    private var lastY = 0.0
    private var onGround = true
    private var fallDistance = 0.0
    private var sprinting = false

    fun reset() {
        hasPosition = false
        lastY = 0.0
        onGround = true
        fallDistance = 0.0
        sprinting = false
    }

    val serverFallDistance: Double get() = fallDistance
    val serverOnGround: Boolean get() = onGround
    val serverSprinting: Boolean get() = sprinting

    fun wouldCritNow(): Boolean =
        hasPosition && fallDistance > 0.0 && !onGround && !sprinting

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando
    fun onPacketSend(event: PacketSendEvent) {
        when (val packet = event.packet) {
            is ServerboundPlayerCommandPacket -> when (packet.action) {
                ServerboundPlayerCommandPacket.Action.START_SPRINTING -> sprinting = true
                ServerboundPlayerCommandPacket.Action.STOP_SPRINTING -> sprinting = false
                else -> {}
            }

            is ServerboundMovePlayerPacket -> {
                val player = mc.player
                val canAccumulateFall = player == null ||
                    !(player.isInWater || player.isInLava || player.isFallFlying || player.onClimbable())
                if (packet.hasPosition()) {
                    recordPositionPacket(packet.getY(lastY), packet.isOnGround, canAccumulateFall)
                } else {
                    recordStatusPacket(packet.isOnGround)
                }
            }
        }
    }

    fun recordStatusPacket(packetOnGround: Boolean) {
        if (packetOnGround) fallDistance = 0.0
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
        if (canAccumulateFall && deltaY < 0.0) fallDistance -= deltaY
        if (packetOnGround) fallDistance = 0.0
        onGround = packetOnGround
    }
}
