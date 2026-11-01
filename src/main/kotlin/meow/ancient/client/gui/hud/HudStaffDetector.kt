package meow.ancient.client.gui.hud

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.staff.StaffRepository
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.world.level.GameType
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
internal class HudStaffDetector {
    private val seen = HashSet<String>()
    private val listed = HashSet<UUID>()
    private val namePattern = Pattern.compile("^\\w{3,16}$")
    private val prefixPattern = Pattern.compile(".*(ꔷ|ꔳ|ꔩ|ꔥ|ꔡ|ꔗ|ꔓ|\\bmod\\b|\\badm\\b|\\bhelp\\b|\\bwne\\b|модер|мод|хелп|помощ|помо|админ|адм|владел|владе|отриц|отри|\\btaf\\b|\\bcurat\\b|куратор|курато|\\bdev\\b|разраб|раз|\\bsupp\\b|\\bꜱupp\\b|саппорт|сапп|\\bder\\b|\\byt\\b|\\[yt\\]|ютуб|стажер|сотрудник).*")
    private val serverNpcs = arrayOf("auction", "exp_smith", "shop_balls", "shop_grief", "free", "shop_kits", "siege", "rwplus", "bossfight", "guide", "shop_smith", "shop_spawners", "colliseum", "battlepass", "buyer", "huckster", "buff_brewer", "killer", "shop_mage")
    private val rowComparator = Comparator<HudRow> { a, b -> a.text.compareTo(b.text, ignoreCase = true) }

    fun reset() {
        seen.clear()
        listed.clear()
    }

    fun update(level: ClientLevel, connection: ClientPacketListener, card: HudCard) {
        seen.clear()
        listed.clear()

        StaffRepository.checkScoreboardTeams(level.scoreboard)

        for (info in connection.listedOnlinePlayers) {
            listed.add(info.profile.id())
        }

        val self = mc.player?.name?.string ?: ""
        val address = connection.serverData?.ip ?: ""
        val isRwOrCake = address.contains("mc.rwdonat.pw") || address.contains("mc.cakeworld.pw")

        for (info in connection.onlinePlayers) {
            val name = info.profile.name().replace("[", "").replace("]", "")
            val notSelf = !name.equals(self, ignoreCase = true)
            val isValidName = namePattern.matcher(name).matches()

            val display = info.tabListDisplayName?.string ?: ""
            val displayLower = display.lowercase(Locale.ROOT)
            val teamPrefix = info.team?.playerPrefix?.string ?: ""
            val teamPrefixLower = teamPrefix.lowercase(Locale.ROOT)

            val matchesPrefix = prefixPattern.matcher(displayLower).matches()
                || prefixPattern.matcher(teamPrefixLower).matches()
                || StaffRepository.containsVanishKeyword(displayLower)
                || StaffRepository.containsVanishKeyword(teamPrefixLower)

            val vanish = !listed.contains(info.profile.id())
            val isGM3 = info.gameMode == GameType.SPECTATOR
            val vanishByPacket = StaffRepository.isVanishDetectedRecently(name)
            val isStaffMember = StaffRepository.isStaff(name)

            if ((isValidName && notSelf && matchesPrefix) || (isValidName && notSelf && vanish) || (isValidName && notSelf && vanishByPacket) || isStaffMember) {
                if (isRwOrCake && isNpc(name)) continue

                val nickIndex = display.indexOf(name)
                val cleanPrefix = when {
                    nickIndex > 0 -> display.substring(0, nickIndex).trim()
                    teamPrefix.isNotBlank() -> teamPrefix.trim()
                    else -> ""
                }

                val isVanished = vanish || vanishByPacket || isGM3
                val status = if (isVanished) "Vanished" else "Online"
                append(card, name, cleanPrefix, status, isVanished)
            }
        }

        for (team in level.scoreboard.playerTeams) {
            val teamPrefix = team.playerPrefix.string
            val isStaffTeam = StaffRepository.containsVanishKeyword(teamPrefix) || prefixPattern.matcher(teamPrefix.lowercase(Locale.ROOT)).matches()
            for (member in team.players) {
                val clean = member.replace("[", "").replace("]", "")
                if (!namePattern.matcher(clean).matches() || clean.equals(self, ignoreCase = true)) continue
                if (isRwOrCake && isNpc(clean)) continue

                val isStaffMember = StaffRepository.isStaff(clean)
                if (isStaffTeam || isStaffMember || StaffRepository.isVanishDetectedRecently(clean)) {
                    val info = connection.getPlayerInfo(clean)
                    val vanish = info == null || !listed.contains(info.profile.id()) || info.gameMode == GameType.SPECTATOR || StaffRepository.isVanishDetectedRecently(clean)
                    val status = if (vanish) "Vanished" else "Online"
                    append(card, clean, teamPrefix.trim(), status, vanish)
                }
            }
        }

        for (staffName in StaffRepository.staffList) {
            if (staffName.equals(self, ignoreCase = true) || !namePattern.matcher(staffName).matches()) continue
            if (isRwOrCake && isNpc(staffName)) continue
            val info = connection.getPlayerInfo(staffName)
            if (info != null) {
                val vanish = !listed.contains(info.profile.id()) || info.gameMode == GameType.SPECTATOR || StaffRepository.isVanishDetectedRecently(staffName)
                val teamPrefix = info.team?.playerPrefix?.string?.trim() ?: ""
                val status = if (vanish) "Vanished" else "Online"
                append(card, staffName, teamPrefix, status, vanish)
            } else if (StaffRepository.isVanishDetectedRecently(staffName)) {
                append(card, staffName, "", "Vanished", true)
            }
        }

        if (card.rowCount > 1) {
            card.rows.subList(0, card.rowCount).sortWith(rowComparator)
        }
    }

    private fun isNpc(name: String): Boolean {
        var i = 0
        while (i < serverNpcs.size) {
            if (name.contains(serverNpcs[i++])) return true
        }
        return false
    }

    private fun append(card: HudCard, name: String, prefix: String, status: String, vanished: Boolean) {
        if (!seen.add(name.lowercase(Locale.ROOT))) return
        val label = if (prefix.isBlank()) name else "$prefix $name"
        card.add(label, status, vanished, key = "staff_$name")
    }
}

