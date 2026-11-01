package meow.ancient.client.command

import meow.ancient.client.command.impl.*
import meow.ancient.client.interfaces.ILoadable
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.event.EventManager
import meow.ancient.event.events.PacketSendEvent
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundChatPacket
import sweetie.evaware.flora.api.Commando

object CommandManager : ILoadable {
    var prefix: String = "."

    val commands: MutableList<Command> = ArrayList()

    override fun load() {
        commands.clear()
        add(
            HelpCommand,
            BindCommand,
            PresetCommand,
            ToggleCommand,
            ListCommand,
            PrefixCommand,
            VClipCommand,
            HClipCommand,
            SayCommand,
            MacroCommand,
            FriendCommand,
            TargetCommand,
            StaffCommand
        )
        EventManager.register(this)
    }

    private fun add(vararg command: Command) {
        commands.addAll(command)
    }

    fun getCommand(name: String): Command? {
        val lower = name.lowercase()
        return commands.firstOrNull { cmd ->
            cmd.name.equals(lower, ignoreCase = true) || cmd.aliases.any { it.equals(lower, ignoreCase = true) }
        }
    }

    fun parseTokens(input: String): List<String> {
        val tokens = ArrayList<String>()
        val matcher = Regex("\"([^\"]*)\"|(\\S+)").findAll(input)
        for (match in matcher) {
            tokens.add(match.groups[1]?.value ?: match.groups[2]!!.value)
        }
        return tokens
    }

    fun execute(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed == "?" || trimmed == "??") {
            HelpCommand.execute(emptyArray())
            return
        }

        val tokens = parseTokens(trimmed)
        if (tokens.isEmpty()) return

        val cmdName = tokens[0]
        val args = if (tokens.size > 1) tokens.subList(1, tokens.size).toTypedArray() else emptyArray()

        val command = getCommand(cmdName)
        if (command != null) {
            try {
                command.execute(args)
            } catch (e: Exception) {
                ChatUtil.error("Error executing command '$cmdName': ${e.message}")
                e.printStackTrace()
            }
        } else {
            ChatUtil.error("Unknown command '$cmdName'. Type '${prefix}help' or '${prefix}?' for available commands.")
        }
    }

    @Commando
    fun onPacketSend(event: PacketSendEvent) {
        val packet = event.packet
        if (packet is ServerboundChatPacket) {
            val message = packet.message
            if (message.startsWith(prefix)) {
                event.cancel = true
                val raw = message.substring(prefix.length)
                execute(raw)
            }
        } else if (packet is ServerboundChatCommandPacket) {
            val command = packet.command
            if (command.startsWith(prefix)) {
                event.cancel = true
                val raw = command.substring(prefix.length)
                execute(raw)
            }
        }
    }
}
