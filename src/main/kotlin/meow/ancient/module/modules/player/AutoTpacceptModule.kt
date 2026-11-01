package meow.ancient.module.modules.player

import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import sweetie.evaware.flora.api.Commando

object AutoTpacceptModule : Module(
    name = "AutoTpaccept",
    category = PLAYER,
    description = "Automatically accepts teleport requests from players or friends"
) {
    val onlyFriends by boolean("Only Friends", true, "Only accept requests from friends")
    val delay by number("Delay", 0.0, 0.0, 5.0, 0.1, 1, unit = "s", description = "Delay in seconds before accepting")
    val command by text("Command", "tpaccept", 64, "Command to run when accepting request")

    private var acceptPending = false
    private var scheduledTime = 0L

    @Commando
    fun onPacket(event: PacketReceiveEvent) {
        if (!inGame) return
        val rawText = when (val packet = event.packet) {
            is ClientboundSystemChatPacket -> packet.content().string
            is ClientboundDisguisedChatPacket -> packet.message().string
            is ClientboundPlayerChatPacket -> packet.unsignedContent().orElse(null)?.string ?: packet.body().content()
            else -> null
        } ?: return

        val textLower = rawText.lowercase()
        val isTpRequest = textLower.contains("телепортироваться")
            || textLower.contains("has requested teleport")
            || textLower.contains("has requested to teleport")
            || textLower.contains("просит к вам телепортироваться")
            || textLower.contains("просит телепортироваться к вам")
            || textLower.contains("запрашивает телепорт к вам")
            || textLower.contains("запрашивает телепортацию")
            || textLower.contains("teleport request")
            || textLower.contains("/tpaccept")

        if (!isTpRequest) return

        if (onlyFriends) {
            var fromFriend = false
            for (friend in FriendRepository.friends) {
                if (rawText.contains(friend.name, ignoreCase = true)) {
                    fromFriend = true
                    break
                }
            }
            if (!fromFriend) return
        }

        if (delay <= 0.0) {
            mc.player?.connection?.sendCommand(command.removePrefix("/"))
        } else {
            scheduledTime = System.currentTimeMillis() + (delay * 1000.0).toLong()
            acceptPending = true
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame || !acceptPending) return
        if (System.currentTimeMillis() >= scheduledTime) {
            acceptPending = false
            mc.player?.connection?.sendCommand(command.removePrefix("/"))
        }
    }

    override fun onDisable() {
        acceptPending = false
        scheduledTime = 0L
    }
}
