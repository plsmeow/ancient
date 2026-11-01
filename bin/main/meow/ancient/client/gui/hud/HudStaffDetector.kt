package meow.ancient.client.gui.hud

import meow.ancient.client.extensions.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.level.GameType
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

internal class HudStaffDetector {
    private val seen = HashSet<String>()
    private val listed = HashSet<UUID>()
    private val namePattern = Pattern.compile("^\\w{3,16}$")
    private val prefixPattern = Pattern.compile(".*(ꔷ|ꔳ|ꔩ|ꔥ|ꔡ|ꔗ|ꔓ|\\bmod\\b|\\badm\\b|\\bhelp\\b|\\bwne\\b|модер|мод|хелп|помощ|помо|админ|адм|владел|владе|отриц|отри|\\btaf\\b|\\bcurat\\b|куратор|курато|\\bdev\\b|разраб|раз|\\bsupp\\b|\\bꜱupp\\b|саппорт|сапп|\\bder\\b|\\byt\\b|\\[yt\\]|ютуб|стажер|сотрудник).*")
    private val vanishKeywords = arrayOf("supp", "ꜱupp", "mod", "der", "adm", "wne", "мод", "помо", "адм", "владе", "отри", "таф", "taf", "curat", "курато", "dev", "раз", "сапп", "yt", "ютуб", "стажер", "сотрудник")
    private val serverNpcs = arrayOf("auction", "exp_smith", "shop_balls", "shop_grief", "free", "shop_kits", "siege", "rwplus", "bossfight", "guide", "shop_smith", "shop_spawners", "colliseum", "battlepass", "buyer", "huckster", "buff_brewer", "killer", "shop_mage")
    private val rowComparator = Comparator<HudRow> { a, b -> a.text.compareTo(b.text, ignoreCase = true) }

    fun reset() {
        seen.clear()
        listed.clear()
    }

    fun update(level: ClientLevel, connection: ClientPacketListener, card: HudCard) {
        seen.clear()
        listed.clear()
        for (info in connection.listedOnlinePlayers) listed.add(info.profile.id())
        val self = mc.player?.name?.string ?: ""
        val address = connection.serverData?.ip ?: ""
        val excludeNpcs = address.contains("mc.rwdonat.pw") || address.contains("mc.cakeworld.pw")
        for (team in level.scoreboard.playerTeams) {
            val prefix = team.playerPrefix.string
            if (!isStaffPrefix(prefix)) continue
            for (member in team.players) {
                val name = member.replace("[", "").replace("]", "")
                val info = connection.getPlayerInfo(name)
                append(card, name, prefix, info, self, excludeNpcs)
            }
        }
        for (info in connection.onlinePlayers) {
            val name = info.profile.name().replace("[", "").replace("]", "")
            val display = info.tabListDisplayName?.string ?: ""
            val teamPrefix = info.team?.playerPrefix?.string ?: ""
            if (!isStaffPrefix(display) && !isStaffPrefix(teamPrefix)) continue
            val nickIndex = display.indexOf(name)
            val prefix = if (nickIndex >= 0) display.substring(0, nickIndex) else teamPrefix
            append(card, name, prefix, info, self, excludeNpcs)
        }
        if (card.rowCount > 1) {
            card.rows.subList(0, card.rowCount).sortWith(rowComparator)
        }
    }

    private fun isStaffPrefix(prefix: String): Boolean {
        val lower = prefix.lowercase(Locale.ROOT)
        if (prefixPattern.matcher(lower).matches()) return true
        var i = 0
        while (i < vanishKeywords.size) {
            if (lower.contains(vanishKeywords[i++])) return true
        }
        return false
    }

    private fun append(card: HudCard, name: String, prefix: String, info: PlayerInfo?, self: String, excludeNpcs: Boolean) {
        if (!namePattern.matcher(name).matches() || name.equals(self, ignoreCase = true)) return
        if (excludeNpcs) {
            var i = 0
            while (i < serverNpcs.size) if (name.contains(serverNpcs[i++])) return
        }
        if (!seen.add(name.lowercase(Locale.ROOT))) return
        val vanished = info == null || !listed.contains(info.profile.id()) || info.gameMode == GameType.SPECTATOR
        val label = if (prefix.isBlank()) name else "${prefix.trim()} $name"
        card.add(label, if (vanished) "Vanished" else "Online", vanished)
    }
}
