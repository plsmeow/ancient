package meow.ancient.client.command.impl

import meow.ancient.client.command.Command
import meow.ancient.client.command.CommandManager
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.client.util.staff.StaffRepository

object StaffCommand : Command(
    name = "staff",
    description = "Manage staff members",
    syntax = "staff <add <name> | remove <name> | clear | list>",
    aliases = listOf("st", "staffs")
) {
    override fun execute(args: Array<String>) {
        val action = if (args.isEmpty()) "list" else args[0].lowercase()

        when (action) {
            "add" -> {
                if (args.size < 2) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}staff add <name>")
                    return
                }
                val name = args[1]
                if (StaffRepository.isStaff(name)) {
                    ChatUtil.send("Этот игрок уже в списке модераторов")
                    return
                }
                StaffRepository.addStaff(name)
                StaffRepository.save()
                ChatUtil.success("Игрок §f$name §7успешно добавлен в список модераторов")
            }

            "remove", "del", "delete" -> {
                if (args.size < 2) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}staff remove <name>")
                    return
                }
                val name = args[1]
                if (!StaffRepository.isStaff(name)) {
                    ChatUtil.error("Такой игрок в списке модераторов не найден")
                    return
                }
                StaffRepository.removeStaff(name)
                StaffRepository.save()
                ChatUtil.success("Игрок §f$name §7успешно удален из списка модераторов")
            }

            "clear" -> {
                StaffRepository.clear()
                StaffRepository.save()
                ChatUtil.success("Список модераторов очищен")
            }

            "list" -> {
                val list = StaffRepository.staffList
                if (list.isEmpty()) {
                    ChatUtil.send("Список модераторов пуст.")
                    return
                }
                ChatUtil.send("Список модераторов (${list.size}):")
                list.forEachIndexed { i, staff ->
                    ChatUtil.send("§7${i + 1}. §f$staff")
                }
            }

            else -> {
                ChatUtil.error("Usage: ${CommandManager.prefix}$syntax")
            }
        }
    }
}
