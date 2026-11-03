package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.TriggerBotModule
import sweetie.evaware.flora.api.Commando

object AutoSprintModule : Module(
    name = "AutoSprint",
    category = MOVEMENT,
    description = ""
) {
    fun shouldSprint(): Boolean {
        if (!isEnabled() || !inGame) return false
        if (HitAuraModule.shouldPreventSprint() || TriggerBotModule.shouldPreventSprint()) return false
        if (InventoryMoveModule.shouldPreventSprint()) return false
        val screen = currentScreen
        if (screen != null && !InventoryMoveModule.canMoveInScreen(screen)) return false
        return true
    }

    override fun onDisable() {
        if (inGame) {
            mc.options.keySprint.isDown = !InventoryMoveModule.shouldPreventSprint()
                && MoveUtil.isKeyPressed(mc.options.keySprint)
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        safeRun {
            if (HitAuraModule.shouldPreventSprint() || TriggerBotModule.shouldPreventSprint() || InventoryMoveModule.shouldPreventSprint()) {
                player.isSprinting = false
                mc.options.keySprint.isDown = false
                return@safeRun
            }

            if (shouldSprint()) {
                mc.options.keySprint.isDown = true
            } else {
                mc.options.keySprint.isDown = MoveUtil.isKeyPressed(mc.options.keySprint)
            }
        }
    }
}
