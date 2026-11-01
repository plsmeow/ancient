package meow.ancient.client.command.impl

import meow.ancient.client.command.Command
import meow.ancient.client.command.CommandManager
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.other.ChatUtil

object FriendCommand : Command(
    name = "friend",
    description = "Manage friends list",
    syntax = "friend <add <name> | remove <name> | clear | list>",
    aliases = listOf("f", "friends")
) {
    override fun execute(args: Array<String>) {
        val action = if (args.isEmpty()) "list" else args[0].lowercase()

        when (action) {
            "add" -> {
                if (args.size < 2) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}friend add <name>")
                    return
                }
                val name = args[1]
                if (FriendRepository.isFriend(name)) {
                    ChatUtil.send("Этот игрок уже в списке друзей")
                    return
                }
                FriendRepository.addFriend(name)
                FriendRepository.save()
                ChatUtil.success("Игрок с именем §f$name §7успешно добавлен в друзья")
            }

            "remove", "del", "delete" -> {
                if (args.size < 2) {
                    ChatUtil.error("Usage: ${CommandManager.prefix}friend remove <name>")
                    return
                }
                val name = args[1]
                if (!FriendRepository.isFriend(name)) {
                    ChatUtil.error("Такой друг не найден")
                    return
                }
                FriendRepository.removeFriend(name)
                FriendRepository.save()
                ChatUtil.success("Игрок с именем §f$name §7успешно удален из друзей")
            }

            "clear" -> {
                FriendRepository.clear()
                FriendRepository.save()
                ChatUtil.success("Все друзья удалены")
            }

            "list" -> {
                val list = FriendRepository.friends
                if (list.isEmpty()) {
                    ChatUtil.send("Список друзей пуст.")
                    return
                }
                ChatUtil.send("Список друзей:")
                for (friend in list) {
                    ChatUtil.raw(" §7- §f${friend.name}")
                }
            }

            else -> {
                ChatUtil.error("Используй: add / remove / list / clear")
            }
        }
    }
}
