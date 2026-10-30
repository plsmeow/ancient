package meow.ancient.event.events

import meow.ancient.event.types.Event
import net.minecraft.client.player.ClientInput

class MovementInputEvent(val input: ClientInput) : Event()
