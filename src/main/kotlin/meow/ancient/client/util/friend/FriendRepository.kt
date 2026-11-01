package meow.ancient.client.util.friend

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import meow.ancient.client.extensions.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class Friend(val name: String)

object FriendRepository {
    private val dir = File(mc.gameDirectory, "ancient")
    private val file = File(dir, "friends.json")
    private val legacyFile = File(".options/friends.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val friends: MutableList<Friend> = CopyOnWriteArrayList()

    init {
        load()
    }

    fun addFriend(name: String) {
        if (!isFriend(name)) {
            friends.add(Friend(name))
        }
    }

    fun removeFriend(name: String) {
        friends.removeIf { it.name.equals(name, ignoreCase = true) }
    }

    fun shouldAttack(player: Player): Boolean {
        return !isFriend(player)
    }

    fun isFriend(friend: String): Boolean {
        return friends.any { it.name.equals(friend, ignoreCase = true) }
    }

    fun isFriend(player: Player): Boolean {
        return isFriend(player.name.string) || isFriend(player.scoreboardName)
    }

    fun isFriend(entity: Entity): Boolean {
        if (entity is Player) {
            return isFriend(entity)
        }
        return isFriend(entity.name.string)
    }

    fun getFriend(name: String): Friend? {
        return friends.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun clear() {
        friends.clear()
    }

    fun save() {
        try {
            dir.mkdirs()
            file.writeText(gson.toJson(friends))
        } catch (_: Exception) {
        }
    }

    fun load() {
        val targetFile = when {
            file.exists() -> file
            legacyFile.exists() -> legacyFile
            else -> null
        } ?: return
        try {
            val listType = object : TypeToken<ArrayList<Friend>>() {}.type
            val loaded: List<Friend>? = gson.fromJson(targetFile.readText(), listType)
            if (loaded != null) {
                friends.clear()
                friends.addAll(loaded)
            }
            if (targetFile === legacyFile && !file.exists()) {
                save()
            }
        } catch (_: Exception) {
        }
    }
}
