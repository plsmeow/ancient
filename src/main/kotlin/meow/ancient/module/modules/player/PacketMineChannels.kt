package meow.ancient.module.modules.player

/** Vanilla has one active destroy position and one early-stopped position completed by server ticks. */
internal class PacketMineChannels<T : Any> {
    var active: T? = null
        private set
    var delayed: T? = null
        private set
    fun canStart(): Boolean = active == null
    fun canHandoff(target: T): Boolean = active === target && delayed == null
    fun started(target: T) {
        check(canStart())
        active = target
    }
    fun handoff(target: T) {
        check(canHandoff(target))
        delayed = target
        active = null
    }
    fun confirmed(target: T) {
        if (active === target) active = null
        if (delayed === target) delayed = null
    }
    fun abortActive(target: T) {
        if (active === target) active = null
        // ABORT does not clear vanilla's delayed destroy slot.
    }
    fun reset() { active = null; delayed = null }
}
