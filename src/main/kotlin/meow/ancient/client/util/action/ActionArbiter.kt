package meow.ancient.client.util.action

enum class ActionGroup { MANUAL, COMBAT, MINING, UTILITY }

data class ActionOwner(val name: String, val group: ActionGroup, val priority: Int, val helper: Boolean = false)

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
