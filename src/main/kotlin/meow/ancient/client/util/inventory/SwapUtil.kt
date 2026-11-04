package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket

object SwapUtil {
    fun isSlownessActive(): Boolean =
        AnticheatManager.isSwapBypassActive || SlownessManager.isSlowed()

    fun clickWithBypass(
        closeAfter: Boolean = true,
        durationMs: Long = 75L,
        forceGrim: Boolean = false,
        action: () -> Unit
    ) {
        val player = mc.player ?: return
        val connection = player.connection
        val menu = player.containerMenu
        val world = mc.level
        if (!mc.isSameThread) {
            mc.execute {
                if (mc.player === player && mc.level === world && player.connection === connection
                    && player.containerMenu === menu && connection.connection.isConnected) {
                    clickWithBypass(closeAfter, durationMs, forceGrim, action)
                }
            }
            return
        }

        if (!AnticheatManager.isSwapBypassActive) {
            action()
            if (closeAfter) {
                connection.send(ServerboundContainerClosePacket(player.containerMenu.containerId))
            }
            return
        }

        if (AnticheatManager.isSwapBypassGrimFast && !forceGrim) {
            clickGrimFast(closeAfter, action)
            return
        }
        SlownessManager.addImmediateTask(durationMs) {
            action()
            if (closeAfter && mc.player === player && mc.level === world
                && player.connection === connection && player.containerMenu === menu
                && connection.connection.isConnected) {
                connection.send(ServerboundContainerClosePacket(menu.containerId))
            }
        }
    }
    private fun clickGrimFast(closeAfter: Boolean, action: () -> Unit) {
        val player = mc.player ?: return
        val world = mc.level ?: return
        val connection = player.connection
        val menu = player.containerMenu

        SlownessManager.addTask(50L) {
            action()
            if (closeAfter && mc.player === player && mc.level === world
                && player.connection === connection && player.containerMenu === menu
                && connection.connection.isConnected) {
                connection.send(ServerboundContainerClosePacket(menu.containerId))
            }
        }
    }
}
