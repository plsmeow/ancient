package meow.ancient.client.util.staff

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import meow.ancient.client.extensions.mc
import net.minecraft.world.scores.Scoreboard
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object StaffRepository {
    private val dir = File(mc.gameDirectory, "ancient")
    private val file = File(dir, "staff.json")
    private val legacyFile = File(".options/staff.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val staffList: MutableList<String> = CopyOnWriteArrayList()

    val VANISH_KEYWORDS = setOf(
        "supp", "ꜱupp", "mod", "der", "adm", "wne",
        "мод", "помо", "адм", "владе", "отри",
        "таф", "taf", "curat", "курато",
        "dev", "раз", "сапп", "yt", "ютуб",
        "стажер", "сотрудник"
    )

    private val vanishDetections = ConcurrentHashMap<String, Long>()
    private const val VANISH_TIMEOUT_MS = 5000L

    init {
        load()
    }

    fun addStaff(name: String) {
        if (!isStaff(name)) {
            staffList.add(name)
        }
    }

    fun removeStaff(name: String) {
        staffList.removeIf { it.equals(name, ignoreCase = true) }
    }

    fun isStaff(name: String): Boolean {
        return staffList.any { it.equals(name, ignoreCase = true) }
    }

    fun clear() {
        staffList.clear()
    }

    fun detectVanish(playerName: String) {
        vanishDetections[playerName.lowercase(Locale.ROOT)] = System.currentTimeMillis()
    }

    fun isVanishDetectedRecently(name: String): Boolean {
        val timestamp = vanishDetections[name.lowercase(Locale.ROOT)] ?: return false
        return System.currentTimeMillis() - timestamp < VANISH_TIMEOUT_MS
    }

    fun containsVanishKeyword(text: String?): Boolean {
        if (text == null) return false
        val lower = text.lowercase(Locale.ROOT)
        for (keyword in VANISH_KEYWORDS) {
            if (lower.contains(keyword)) return true
        }
        return false
    }

    fun checkScoreboardTeams(scoreboard: Scoreboard) {
        for (team in scoreboard.playerTeams) {
            val prefix = team.playerPrefix.string
            if (containsVanishKeyword(prefix)) {
                for (member in team.players) {
                    val clean = member.replace("[", "").replace("]", "")
                    detectVanish(clean)
                }
            }
        }
    }

    fun save() {
        try {
            dir.mkdirs()
            file.writeText(gson.toJson(staffList))
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
                staffList.clear()
                staffList.addAll(loaded)
            }
            if (targetFile === legacyFile && !file.exists()) {
                save()
            }
        } catch (_: Exception) {
        }
    }
}
