package meow.ancient.event.events

import meow.ancient.event.types.Event

class ClientDisconnectEvent : Event() {
    companion object {
        @JvmField
        val INSTANCE = ClientDisconnectEvent()
    }
}
