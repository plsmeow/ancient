package meow.ancient.event.events

import meow.ancient.event.types.Event

class PlayerTickEndEvent : Event() {
    companion object {
        @JvmField
        val INSTANCE = PlayerTickEndEvent()
    }
}
