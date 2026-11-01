package meow.ancient.client.command

import meow.ancient.client.util.other.ChatUtil

abstract class Command(
    val name: String,
    val description: String = "",
    val syntax: String = "",
    val aliases: List<String> = emptyList()
) {
    abstract fun execute(args: Array<String>)

    fun sendUsage() {
        ChatUtil.error("Usage: ${CommandManager.prefix}$syntax")
    }
}
