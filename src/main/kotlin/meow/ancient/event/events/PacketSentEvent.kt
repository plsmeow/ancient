package meow.ancient.event.events

import meow.ancient.event.types.Event
import net.minecraft.network.protocol.Packet

/** Emitted after all outgoing cancellation/ownership checks have accepted the packet. */
class PacketSentEvent(val packet: Packet<*>) : Event()
