package meow.ancient.client.command

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import meow.ancient.client.config.ConfigManager
import meow.ancient.module.ModuleManager
import meow.ancient.client.extensions.Category
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.macro.MacroRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.module.setting.KeybindSetting

object CommandSuggestionProvider {
    private val COMMON_KEYS = ('a'..'z').map(Char::toString) + ('0'..'9').map(Char::toString) +
        (1..24).map { "f$it" } + (1..8).map { "mouse$it" } + listOf(
            "none", "space", "rshift", "lshift", "rctrl", "lctrl", "ralt", "lalt",
            "tab", "enter", "caps", "up", "down", "left", "right", "del", "back", "grave"
        )

    fun getUsage(input: String): String? {
        val prefix = CommandManager.prefix
        if (!input.startsWith(prefix)) return null
        val commandName = CommandManager.parseTokens(input.substring(prefix.length)).firstOrNull() ?: return null
        val cmd = CommandManager.getCommand(commandName) ?: return null
        return "Usage: $prefix${cmd.syntax}"
    }

    fun getSuggestions(input: String, cursor: Int): Suggestions? {
        val prefix = CommandManager.prefix
        if (prefix.isEmpty() || !input.startsWith(prefix)) return null
        val position = cursor.coerceIn(0, input.length)
        if (position < prefix.length) return Suggestions(StringRange.at(position), emptyList())

        val previous = ArrayList<String>()
        var start = prefix.length
        while (start < position) {
            if (input[start].isWhitespace()) {
                start++
                continue
            }
            val quoted = input[start] == '"'
            var end = start + if (quoted) 1 else 0
            while (end < input.length && if (quoted) input[end] != '"' else !input[end].isWhitespace()) end++
            val tokenEnd = end + if (quoted && end < input.length) 1 else 0
            if (position <= tokenEnd) break
            previous.add(input.substring(start + if (quoted) 1 else 0, end))
            start = tokenEnd
        }

        val quoted = start < position && input[start] == '"'
        val wordStart = start + if (quoted) 1 else 0
        val currentWord = input.substring(wordStart.coerceAtMost(position), position).removeSuffix("\"")
        var end = position
        if (quoted && position > start + 1 && input[position - 1] == '"') {
            end = position
        } else {
            while (end < input.length && if (quoted) input[end] != '"' else !input[end].isWhitespace()) end++
            if (quoted && end < input.length) end++
        }
        val range = StringRange.between(start, end)
        val candidates = if (previous.isEmpty()) {
            CommandManager.commands.flatMap { listOf(it.name) + it.aliases }
        } else {
            val command = CommandManager.getCommand(previous[0])
            val args = previous.drop(1)
            when (command?.name) {
                "help" -> if (args.isEmpty()) CommandManager.commands.flatMap { listOf(it.name) + it.aliases } else emptyList()
                "toggle" -> when (args.size) {
                    0 -> ModuleManager.modules.map { it.name }
                    1 -> listOf("on", "off")
                    else -> emptyList()
                }
                "list" -> if (args.isEmpty()) Category.entries.map { it.displayName } else emptyList()
                "bind" -> when (args.size) {
                    0 -> listOf("add", "set", "remove", "del", "delete", "clear", "list")
                    1 -> if (args[0].lowercase() in listOf("add", "set", "remove", "del", "delete")) {
                        ModuleManager.modules.map { it.name }
                    } else emptyList()
                    2 -> if (args[0].equals("add", true) || args[0].equals("set", true)) COMMON_KEYS else emptyList()
                    else -> emptyList()
                }
                "preset" -> when (args.size) {
                    0 -> listOf("load", "save", "create", "delete", "del", "remove", "clear", "list", "dir", "folder", "open")
                    1 -> if (args[0].lowercase() in listOf("load", "save", "create", "delete", "del", "remove")) {
                        ConfigManager.listConfigs()
                    } else emptyList()
                    else -> emptyList()
                }
                "macro" -> when (args.size) {
                    0 -> listOf("add", "remove", "del", "delete", "clear", "list")
                    1 -> when (args[0].lowercase()) {
                        "add" -> COMMON_KEYS
                        "remove", "del", "delete" -> MacroRepository.macros.map { KeybindSetting.formatKey(it.key).lowercase() }
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
                "friend" -> when (args.size) {
                    0 -> listOf("add", "remove", "del", "delete", "clear", "list")
                    1 -> when (args[0].lowercase()) {
                        "add" -> mc.level?.players()?.map { it.name.string } ?: emptyList()
                        "remove", "del", "delete" -> FriendRepository.friends.map { it.name }
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
                "target" -> when (args.size) {
                    0 -> listOf("add", "remove", "del", "delete", "clear", "list")
                    1 -> when (args[0].lowercase()) {
                        "add" -> mc.level?.players()?.map { it.name.string } ?: emptyList()
                        "remove", "del", "delete" -> TargetRepository.targets
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
                else -> emptyList()
            }
        }
        val matches = candidates.distinct().filter { it.startsWith(currentWord, ignoreCase = true) }.map {
            Suggestion(range, if (quoted || it.any(Char::isWhitespace)) "\"$it\"" else it)
        }
        return Suggestions.create(input, matches)
    }
}
