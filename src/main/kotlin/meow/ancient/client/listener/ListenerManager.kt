package meow.ancient.client.listener

import meow.ancient.client.interfaces.ILoadable
import meow.ancient.client.listener.listeners.InputListener
import meow.ancient.client.listener.listeners.RenderListener
import meow.ancient.client.util.rotation.RotationManager

object ListenerManager : ILoadable {
    val listeners: MutableList<Listener> = ArrayList()

    override fun load() {
        listeners.clear()
        add(
            InputListener,
            RenderListener,
            RotationManager
        )
        listeners.forEach(Listener::init)
    }

    private fun add(vararg listener: Listener) {
        listeners.addAll(listener)
    }
}
