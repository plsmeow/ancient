package meow.ancient.client.command.impl

import meow.ancient.client.command.Command
import meow.ancient.client.extensions.safeRun
import meow.ancient.client.util.other.ChatUtil

object SayCommand : Command(
    name = "say",
    description = "Send a chat message to the server without command interception",
    syntax = "say <message>",
    aliases = emptyList()
) {
    override fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            sendUsage()
            return
        }

        val message = args.joinToString(" ")
        safeRun {
            player.connection.sendChat(message)
        } ?: run {
            ChatUtil.error("Cannot send message: not in game!")
        }
    }
}
