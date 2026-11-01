package meow.ancient.event.events

import meow.ancient.event.types.EventCancellable

class KeyPressEvent(
    val key: Int,
    val scancode: Int,
    val action: Int,
    val modifiers: Int
) : EventCancellable()
