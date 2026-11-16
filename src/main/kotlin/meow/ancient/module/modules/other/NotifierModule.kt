package meow.ancient.module.modules.other

import meow.ancient.client.extensions.OTHER
import meow.ancient.client.gui.hud.HudNotificationCenter
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.event.events.PlayerCombatEvent
import meow.ancient.module.Module
import sweetie.evaware.flora.api.Commando

object NotifierModule : Module("Notifier", OTHER, "Reports player totem pops and deaths") {
    private val output by mode("Output", "Notifications", listOf("Notifications", "Chat", "Both"))
    private val totems by boolean("Totem Pops", true)
    private val self by boolean("Self", false, isVisible = { totems })
    private val deaths by boolean("Death", true)

    @Commando fun onCombat(event: PlayerCombatEvent) {
        val text = when (event.kind) {
            PlayerCombatEvent.Kind.TOTEM -> {
                if (!totems || event.self && !self) return
                "${event.name} popped ${event.pops} totem${if (event.pops == 1) "" else "s"}"
            }
            PlayerCombatEvent.Kind.DEATH -> {
                if (!deaths || event.self) return
                "Death ${event.name}"
            }
        }
        if (output != "Chat") HudNotificationCenter.push(if (event.kind == PlayerCombatEvent.Kind.TOTEM) "Totem Pop" else "Death", text, true)
        if (output != "Notifications") ChatUtil.info(text)
    }
}
