package meow.ancient.module.modules.other

import meow.ancient.client.extensions.OTHER
import meow.ancient.client.gui.hud.HudNotificationCenter
import meow.ancient.event.events.PlayerCombatEvent
import meow.ancient.module.Module
import sweetie.evaware.flora.api.Commando

object NotifierModule : Module("Notifier", OTHER, "Reports player totem pops and deaths") {
    private val totemsSetting = boolean("Totem Pops", true)
    private val totems by totemsSetting
    private val selfSetting = boolean("Self", false, isVisible = { totems })
    private val self by selfSetting
    private val deathsSetting = boolean("Death", true)
    private val deaths by deathsSetting
    val notificationSettings = listOf(totemsSetting, selfSetting, deathsSetting)

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
        HudNotificationCenter.push(if (event.kind == PlayerCombatEvent.Kind.TOTEM) "Totem Pop" else "Death", text, true)
    }
}
