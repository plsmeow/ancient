package meow.ancient.client.util.target

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import meow.ancient.client.extensions.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

object TargetRepository {
    private val dir = File(mc.gameDirectory, "ancient")
    private val file = File(dir, "targets.json")
    private val legacyFile = File(".options/targets.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val targets: MutableList<String> = CopyOnWriteArrayList()

    init {
        load()
    }

    fun addTarget(name: String) {
        if (!isTarget(name)) {
            targets.add(name)
        }
    }

    fun removeTarget(name: String) {
        targets.removeIf { it.equals(name, ignoreCase = true) }
    }

    fun isTarget(name: String): Boolean {
        return targets.any { it.equals(name, ignoreCase = true) }
    }

    fun isTarget(player: Player): Boolean {
        return isTarget(player.name.string) || isTarget(player.scoreboardName)
    }

    fun isTarget(entity: Entity): Boolean {
        if (entity is Player) {
            return isTarget(entity)
        }
        return isTarget(entity.name.string)
    }

    fun clear() {
        targets.clear()
    }

    fun save() {
        try {
            dir.mkdirs()
            file.writeText(gson.toJson(targets))
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
            val listType = object : TypeToken<ArrayList<String>>() {}.type
            val loaded: List<String>? = gson.fromJson(targetFile.readText(), listType)
            if (loaded != null) {
                targets.clear()
                targets.addAll(loaded)
            }
            if (targetFile === legacyFile && !file.exists()) {
                save()
            }
        } catch (_: Exception) {
        }
    }
}
