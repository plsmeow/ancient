package meow.ancient.client.util.action

enum class ActionGroup { MANUAL, COMBAT, MINING, UTILITY }

data class ActionOwner(val name: String, val group: ActionGroup, val priority: Int, val helper: Boolean = false) {
    companion object {
        internal fun forModule(className: String): ActionOwner {
            val name = className.substringAfterLast('.').substringBefore('$').removeSuffix("Module")
            val helper = name in setOf("AutoMace", "BreachSwap", "MaceKill", "TpAura")
            return when {
                // Item-use binds must work even if an aura has a target or already attacked this tick.
                name in setOf("ClickPearl", "ElytraHelper", "Phase", "FtHelper", "HwHelper", "AutoLunge") ->
                    ActionOwner(name, ActionGroup.MANUAL, 1000)
                name == "SpearKill" -> ActionOwner(name, ActionGroup.COMBAT, 330)
                name == "Surround" -> ActionOwner(name, ActionGroup.COMBAT, 450)
                name == "CrystalAura" -> ActionOwner(name, ActionGroup.COMBAT, 323)
                name == "AnchorAura" -> ActionOwner(name, ActionGroup.COMBAT, 322)
                name == "CrystalTerrain" -> ActionOwner(name, ActionGroup.COMBAT, 321)
                name == "CrystalTerrainBoost" -> ActionOwner(name, ActionGroup.COMBAT, 324)
                name == "HitAura" -> ActionOwner(name, ActionGroup.COMBAT, 320)
                name == "TriggerBot" -> ActionOwner(name, ActionGroup.COMBAT, 300)
                className.contains(".combat.") -> ActionOwner(name, ActionGroup.COMBAT, 310, helper)
                name == "PacketMine" -> ActionOwner(name, ActionGroup.MINING, 900)
                else -> ActionOwner(name, ActionGroup.UTILITY, 100)
            }
        }
    }
}

/** A committed action cannot be preempted by a different automation halfway through its tick. */
internal class ActionArbiter {
    var committed: ActionOwner? = null
        private set
    var slotOwner: ActionOwner? = null
        private set
    var lastBlocked = ""
        private set
    var blockedCount = 0L
        private set

    fun beginTick() { committed = null }

    fun blockReason(owner: ActionOwner, preferred: ActionOwner?): String? {
        val active = committed
        return when {
            owner.group == ActionGroup.MANUAL -> null
            active != null && active.group != owner.group -> "${active.name} already acted"
            preferred != null && preferred.group != owner.group && preferred.priority > owner.priority -> "${preferred.name} has priority"
            preferred != null && preferred.group == owner.group && preferred.priority > owner.priority && !owner.helper -> "${preferred.name} has priority"
            else -> null
        }
    }

    fun canAct(owner: ActionOwner, preferred: ActionOwner?): Boolean {
        val reason = blockReason(owner, preferred)
        if (reason == null) return true
        lastBlocked = "${owner.name}: $reason"
        blockedCount++
        return false
    }

    fun commit(owner: ActionOwner) {
        if (committed == null || owner.group == ActionGroup.MANUAL) committed = owner
    }

    fun claimSlot(owner: ActionOwner) { slotOwner = owner }
    fun releaseSlot() { slotOwner = null }

    fun reset() {
        committed = null
        slotOwner = null
        lastBlocked = ""
        blockedCount = 0
    }
}
