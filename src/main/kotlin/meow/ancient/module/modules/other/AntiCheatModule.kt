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
        description = "Legit stops sprint before movement and resumes naturally; Packet stops before attack and restarts after; LB stops sprint at the exact attack moment"
    )

    val swapBypass by mode(
        name = "Swap Bypass",
        value = "Grim",
        modes = listOf("Off", "Grim", "GrimFast"),
        description = "GrimFast pauses input for 50 ms before swaps and 60 ms on inventory or container close"
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
