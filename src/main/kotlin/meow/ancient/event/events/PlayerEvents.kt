package meow.ancient.event.events

import meow.ancient.event.types.Event

class PlayerUpdateEvent : Event() {
    companion object {
        @JvmField
        val INSTANCE = PlayerUpdateEvent()
    }
}

class PlayerMovementSentEvent : Event() {
    companion object {
        @JvmField
        val INSTANCE = PlayerMovementSentEvent()
    }
}
