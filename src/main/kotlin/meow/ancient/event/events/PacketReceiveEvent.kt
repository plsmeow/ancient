package meow.ancient.event.events

import meow.ancient.event.types.EventCancellable
import net.minecraft.network.protocol.Packet

class PacketReceiveEvent(
    val packet: Packet<*>
) : EventCancellable()
