package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.mixin.ServerboundMovePlayerPacketAccessor
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import sweetie.evaware.flora.api.Commando

object GroundSpoofModule : Module(
    name = "GroundSpoof",
    category = MOVEMENT,
    description = "Spoofs the on-ground flag in outgoing movement packets"
) {
    val mode by mode("Mode", "False", listOf("True", "False"))

    fun isSpoofingAir(): Boolean = isEnabled() && mode == "False"

    @Commando
    fun onPacket(event: PacketSendEvent) {
        if (!inGame) return
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return
        val onGround = mode == "True"
        if (packet.isOnGround == onGround) return
        (packet as ServerboundMovePlayerPacketAccessor).`ancient$setOnGround`(onGround)
    }
}
