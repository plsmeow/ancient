package meow.ancient.client.command.impl

import meow.ancient.client.command.Command
import meow.ancient.client.command.CommandManager
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.client.util.target.TargetRepository

object TargetCommand : Command(
    name = "target",
    description = "Manage targets list",
    syntax = "target <add <name> | remove <name> | clear | list>",
    aliases = listOf("t", "targets")
) {
    override fun execute(args: Array<String>) {
        val action = if (args.isEmpty()) "list" else args[0].lowercase()

        when (action) {
            "add" -> {
                if (args.size < 2) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}target add <name>")
                    return
                }
                val name = args[1]
                if (TargetRepository.isTarget(name)) {
                    ChatUtil.send("Этот игрок уже в списке таргетов")
                    return
                }
                TargetRepository.addTarget(name)
                TargetRepository.save()
                ChatUtil.success("Игрок §f$name §7добавлен в таргеты")
            }

            "remove", "del", "delete" -> {
                if (args.size < 2) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}target remove <name>")
                    return
                }
                val name = args[1]
                if (!TargetRepository.isTarget(name)) {
                    ChatUtil.error("Этот игрок не найден в списке таргетов")
                    return
                }
                TargetRepository.removeTarget(name)
                TargetRepository.save()
                ChatUtil.success("Игрок §f$name §7удалён из таргетов")
            }

            "clear" -> {
                TargetRepository.clear()
                TargetRepository.save()
                ChatUtil.success("Все таргеты очищены")
            }

            "list" -> {
                val list = TargetRepository.targets
                if (list.isEmpty()) {
                    ChatUtil.send("Список таргетов пуст.")
                    return
                }
                ChatUtil.send("Список таргетов:")
                for (target in list) {
                    ChatUtil.raw(" §7- §c$target")
                }
            }

            else -> {
                ChatUtil.error("Используй: add / remove / list / clear")
            }
        }
    }
}
