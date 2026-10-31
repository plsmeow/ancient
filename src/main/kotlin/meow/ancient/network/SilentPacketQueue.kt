package meow.ancient.network

import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import java.util.Collections
import java.util.IdentityHashMap

object SilentPacketQueue {
    private val packets = Collections.synchronizedMap(IdentityHashMap<Packet<*>, Boolean>())

    fun send(connection: Connection, packet: Packet<*>) {
        packets[packet] = true
        connection.send(packet, null, true)
    }

    @JvmStatic
    fun consume(packet: Packet<*>): Boolean = packets.remove(packet) != null
}
