package meow.ancient.event.events

import meow.ancient.event.types.EventCancellable
import net.minecraft.client.gui.screens.Screen

class ScreenCloseEvent(
    val screen: Screen
) : EventCancellable()
