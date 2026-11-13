package meow.ancient.event

import sweetie.evaware.flora.Flora

object EventManager {
    fun post(any: Any) {
        Flora.post(any)
    }

    fun register(any: Any) {
        Flora.register(any)
    }

    fun unregister(any: Any) {
        Flora.unregister(any)
    }
}
