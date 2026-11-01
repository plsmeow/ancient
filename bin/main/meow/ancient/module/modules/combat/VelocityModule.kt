package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import sweetie.evaware.flora.api.Commando

object VelocityModule : Module(
    name = "Velocity",
    category = COMBAT,
    description = "Controls incoming knockback and Grim ping cancellation."
) {
    val mode by mode(
        name = "Mode",
        value = "Grim",
        modes = listOf("Cancel", "Grim")
    )
    val fix by boolean(
        name = "Fix",
        value = false,
        isVisible = { mode == "Grim" }
    )
    val fixDelay by number(
        name = "Fix Delay",
        value = 100.0,
        min = 0.0,
        max = 1000.0,
        step = 10.0,
        precision = 0,
        unit = "ms",
        isVisible = { mode == "Grim" && fix }
    )

    @Volatile
    private var lastVelocityTime = 0L

    @Volatile
    private var velocityCanceled = false

    override fun onEnable() = reset()

    override fun onDisable() = reset()

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    private fun reset() {
        velocityCanceled = false
        lastVelocityTime = 0L
    }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        val player = mc.player
        if (player == null || mc.connection == null) {
            reset()
            return
        }

        when (val packet = event.packet) {
            is ClientboundSetEntityMotionPacket -> {
                if (packet.id != player.id) return
                event.cancel = true
                if (mode == "Grim") {
                    velocityCanceled = true
                    lastVelocityTime = System.currentTimeMillis()
                }
            }
            is ClientboundPingPacket -> {
                if (mode != "Grim") return
                if (!fix) {
                    event.cancel = true
                    return
                }
                if (!velocityCanceled) return
                if (System.currentTimeMillis() - lastVelocityTime < fixDelay.toLong()) {
                    event.cancel = true
                } else {
                    velocityCanceled = false
                }
            }
            is ClientboundExplodePacket -> {
                val knockback = packet.playerKnockback()
                if (knockback.isPresent) {
                    val vec = knockback.get()
                    vec.x = 0.0
                    vec.y = 0.0
                    vec.z = 0.0
                }
            }
        }
    }
}
