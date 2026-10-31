package meow.ancient.client.util.anticheat

import meow.ancient.module.modules.other.AntiCheatModule
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.scores.DisplaySlot

object AnticheatManager {
    val sprintReset: String
        get() = AntiCheatModule.sprintReset

    val isPacketSprintReset: Boolean
        get() = sprintReset == "Packet"

    val swapBypass: String
        get() = AntiCheatModule.swapBypass

    val isSwapBypassActive: Boolean
        get() = swapBypass == "Grim"

    val healthSource: String
        get() = AntiCheatModule.healthSource

    val isScoreboardHealth: Boolean
        get() = healthSource == "Scoreboard"

    fun getHealth(entity: LivingEntity): Float {
        if (entity !is Player || !isScoreboardHealth) {
            return entity.health + entity.absorptionAmount
        }
        val level = entity.level()
        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME)
            ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (objective != null) {
            val score = scoreboard.getPlayerScoreInfo(entity, objective)
            if (score != null && score.value > 0) {
                return score.value.toFloat()
            }
        }
        return entity.health + entity.absorptionAmount
    }

    fun getBaseHealth(entity: LivingEntity): Float {
        if (entity !is Player || !isScoreboardHealth) {
            return entity.health
        }
        return getHealth(entity)
    }
}
