package meow.ancient.client.command.impl

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.command.Command
import meow.ancient.client.command.CommandManager
import meow.ancient.client.util.macro.MacroRepository
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.module.setting.KeybindSetting

object MacroCommand : Command(
    name = "macro",
    description = "Manage key macros",
    syntax = "macro <add <key> <command> | remove <key> | clear | list>",
    aliases = listOf("macros", "m")
) {
    override fun execute(args: Array<String>) {
        val action = if (args.isEmpty()) "list" else args[0].lowercase()

        when (action) {
            "add", "set" -> {
                if (args.size < 3) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}macro add <key> <command>")
                    return
                }
                val keyName = args[1]
                val key = KeybindSetting.parseKey(keyName)
                if (key == InputConstants.UNKNOWN.value) {
                    ChatUtil.error("Invalid key '$keyName'!")
                    return
                }
                val command = args.drop(2).joinToString(" ")
                MacroRepository.addMacro(command, key)
                MacroRepository.save()
                val keyDisplay = KeybindSetting.formatKey(key).lowercase()
                ChatUtil.success("Макрос добавлен: §f$keyDisplay §7→ §f$command")
            }

            "remove", "del", "delete" -> {
                if (args.size < 2) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}macro remove <key>")
                    return
                }
                val keyName = args[1]
                val key = KeybindSetting.parseKey(keyName)
                if (key == InputConstants.UNKNOWN.value) {
                    ChatUtil.error("Invalid key '$keyName'!")
                    return
                }
                if (MacroRepository.removeByKey(key)) {
                    MacroRepository.save()
                    val keyDisplay = KeybindSetting.formatKey(key).lowercase()
                    ChatUtil.success("Макрос на клавише §f$keyDisplay §7удалён")
                } else {
                    ChatUtil.error("Макрос на этой клавише не найден")
                }
            }

            "clear" -> {
                MacroRepository.clear()
                MacroRepository.save()
                ChatUtil.success("Все макросы очищены")
            }

            "list" -> {
                val list = MacroRepository.macros
                if (list.isEmpty()) {
                    ChatUtil.send("Список макросов пуст.")
                    return
                }
                ChatUtil.send("Список макросов:")
                for (macro in list) {
                    val keyDisplay = KeybindSetting.formatKey(macro.key).lowercase()
                    ChatUtil.raw(" §7$keyDisplay §8→ §f${macro.message}")
                }
            }

            else -> {
                ChatUtil.error("Используй: add / remove / list / clear")
            }
        }
    }
}
