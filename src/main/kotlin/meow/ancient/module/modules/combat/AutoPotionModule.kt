package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.inventory.InventoryAutomation
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

object AutoPotionModule : Module("AutoPotion", COMBAT, "Throws useful splash potions at your feet") {
    private val effects by multiMode("Effects", setOf("Strength", "Speed", "Fire Resistance", "Healing"),
        listOf("Strength", "Speed", "Fire Resistance", "Healing", "Regeneration", "Resistance"))
    private val health by number("Health", 12.0, 1.0, 20.0, 0.5, 1, unit = "HP")
    private val refresh by number("Refresh", 3.0, 0.0, 15.0, 0.5, 1, unit = "s")
    private val delay by number("Delay", 750.0, 250.0, 3000.0, 50.0, 0, unit = "ms")
    private val onlyHotbar by boolean("Only Hotbar", true)
    private val onlyGround by boolean("Only Ground", true)
    private val onlyCombat by boolean("Only Combat", false)
    private val actions = AuraActionQueue(this)
    private var queued = false
    private var next = 0L
    fun hasPendingAction(): Boolean = isEnabled() && queued
    private fun reset() {
        queued = false; next = 0L; actions.reset()
        InventoryAutomation.cancel(this); SilentSwapUtil.releasePreparedSlot(this)
    }
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

    private fun priority(stack: ItemStack): Int {
        val player = mc.player ?: return -1
        if (!stack.`is`(Items.SPLASH_POTION) || player.cooldowns.isOnCooldown(stack)) return -1
        val contents = stack.get(DataComponents.POTION_CONTENTS) ?: return -1
        var score = -1
        for (effect in contents.allEffects) {
            val type = effect.effect
            val name = when (type) {
                MobEffects.STRENGTH -> "Strength"
                MobEffects.SPEED -> "Speed"
                MobEffects.FIRE_RESISTANCE -> "Fire Resistance"
                MobEffects.INSTANT_HEALTH -> "Healing"
                MobEffects.REGENERATION -> "Regeneration"
                MobEffects.RESISTANCE -> "Resistance"
                else -> return -1 // Do not throw mixed harmful potions on yourself.
            }
            if (name !in effects) continue
            if (name == "Healing" || name == "Regeneration") {
                if (player.health + player.absorptionAmount > health) continue
            }
            val current = player.getEffect(type)
            if (name != "Healing" && current != null && current.amplifier >= effect.amplifier
                && (current.isInfiniteDuration || current.duration > refresh * 20)) continue
            score = maxOf(score, (if (name == "Healing") 1000 else if (name == "Strength") 500 else 100) + effect.amplifier)
        }
        return score
    }

    @Commando(priority = 125) fun onUpdate(event: PlayerUpdateEvent) {
        queued = false
        actions.beginTick()
        val player = mc.player ?: return
        if (!InventoryAutomation.available() || InventoryAutomation.busy() || InventoryMoveModule.isInventoryActionSlowed()
            || System.nanoTime() < next || onlyGround && !player.onGround()
            || onlyCombat && HitAuraModule.currentTarget == null && CrystalAuraModule.currentTarget == null
                && AnchorAuraModule.currentTarget == null) { SilentSwapUtil.releasePreparedSlot(this); return }
        val offhand = priority(player.offhandItem)
        val slot = (0..8).maxByOrNull { priority(player.inventory.getItem(it)) } ?: return
        if (offhand < 0 && priority(player.inventory.getItem(slot)) < 0) {
            if (!onlyHotbar && !InventoryAutomation.busy()) {
                val source = (9..35).filter { priority(player.inventory.getItem(it)) >= 0 }
                    .maxByOrNull { priority(player.inventory.getItem(it)) } ?: return
                val bridge = (8 downTo 0).firstOrNull { InventoryAutomation.hotbarAvailable(it) } ?: return
                val original = player.inventory.getItem(bridge).copy()
                val potion = player.inventory.getItem(source).copy()
                InventoryAutomation.submit(this, {
                    InventoryAutomation.matches(bridge, original) && InventoryAutomation.matches(source, potion)
                        && priority(potion) >= 0 && InventoryAutomation.hotbarAvailable(bridge)
                }, { InventoryUtil.clickSlotDirect(source, bridge) })
            }
            return
        }
        queued = true
        if (!ActionCoordinator.canAct(this)) { queued = false; return }
        val hand = if (offhand >= priority(player.inventory.getItem(slot))) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val heldSlot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else slot
        if (!SilentSwapUtil.prepareSlot(heldSlot, 2, this)) return
        val expected = player.getItemInHand(hand).takeIf { hand == InteractionHand.OFF_HAND }
            ?: player.inventory.getItem(heldSlot)
        val template = expected.copy()
        actions.item(90f, MoveFixMode.SILENT) {
            if (mc.player !== player || !InventoryAutomation.available() || onlyGround && !player.onGround()
                || SilentSwapUtil.serverSlot != heldSlot || InventoryMoveModule.isInventoryActionSlowed()) return@item
            val stack = if (hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(heldSlot)
            if (!ItemStack.matches(stack, template) || priority(stack) < 0) return@item
            if (InventoryUtil.useItemPacket(hand)) {
                next = System.nanoTime() + (delay * 1e6).toLong()
                queued = false
                SilentSwapUtil.releasePreparedSlot(this)
            }
        }
    }
}
