package meow.ancient.event.events

import java.util.UUID

class PlayerCombatEvent(val uuid: UUID, val name: String, val self: Boolean, val kind: Kind, val pops: Int) {
    enum class Kind { TOTEM, DEATH }
}
