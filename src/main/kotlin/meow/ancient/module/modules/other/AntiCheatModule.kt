package meow.ancient.module.modules.other

import meow.ancient.client.extensions.OTHER
import meow.ancient.module.Module

object AntiCheatModule : Module(
    name = "AntiCheat",
    category = OTHER,
    description = "Global anticheat bypass settings"
) {
    val sprintReset by mode(
        name = "Sprint Reset",
        value = "Packet",
        modes = listOf("Legit", "Packet", "LB"),
        description = "Legit holds sprint off for two ticks after a hit; Packet prepares the stop and resumes next tick; LB holds for one tick"
    )

    val swapBypass by mode(
        name = "Swap Bypass",
        value = "Grim",
        modes = listOf("Off", "Grim", "GrimFast"),
        description = "GrimFast pauses input for 50 ms before swaps and 60 ms on inventory or container close"
    )

    val silentSwap by mode(
        name = "Silent Swap",
        value = "Simple",
        modes = listOf("Simple", "New"),
        description = "Simple restores immediately; New swaps only the server slot, keeps the visible item unchanged and coalesces restores before a clean real tick packet"
    )

    val healthSource by mode(
        name = "Health Source",
        value = "Entity",
        modes = listOf("Entity", "Scoreboard"),
        description = "Source used for player health information"
    )

    init {
        super.setEnabled(true)
    }

    override fun isEnabled(): Boolean = true

    override fun setEnabled(value: Boolean) {
        super.setEnabled(true)
    }

    override fun toggle() {
        super.setEnabled(true)
    }
}
