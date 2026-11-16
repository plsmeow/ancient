package meow.ancient.module.modules.player

import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryAutomation
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.world.item.ItemStack
import sweetie.evaware.flora.api.Commando

object ReplenishModule : Module("Replenish", PLAYER, "Refills remembered hotbar stacks from inventory") {
    private val threshold by number("Threshold", 16.0, 1.0, 64.0, 1.0, 0)
    private val delay by number("Delay", 250.0, 0.0, 2000.0, 50.0, 0, unit = "ms")
    private val templates = Array(9) { ItemStack.EMPTY }
    private var world: Any? = null
    private var next = 0L
    private fun reset() { InventoryAutomation.cancel(this); templates.fill(ItemStack.EMPTY); world = null; next = 0L }
    override fun onEnable() = reset()
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando(priority = 20) fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        if (world !== mc.level) { reset(); world = mc.level }
        if (InventoryAutomation.busy() || !InventoryAutomation.available()) return
        val serverSlot = SilentSwapUtil.serverSlot
        val held = SilentSwapUtil.debugSnapshot().holdTicks > 0
        for (slot in 0..8) {
            if (held && slot == serverSlot) continue
            val stack = player.inventory.getItem(slot)
            if (!stack.isEmpty) templates[slot] = stack.copyWithCount(1)
        }
        if (System.nanoTime() < next) return
        for (slot in 0..8) {
            if (slot == serverSlot && (held || PacketMineModule.hasPendingAction())) continue
            val current = player.inventory.getItem(slot)
            val template = templates[slot]
            if (template.isEmpty || !template.isStackable || current.count >= minOf(threshold.toInt(), template.maxStackSize)) continue
            val source = (9..35).filter {
                ItemStack.isSameItemSameComponents(player.inventory.getItem(it), template)
                    && player.inventory.getItem(it).count > current.count
            }.maxByOrNull { player.inventory.getItem(it).count } ?: continue
            val expected = current.copy()
            val replacement = player.inventory.getItem(source).copy()
            if (InventoryAutomation.submit(this, {
                    InventoryAutomation.matches(slot, expected) && InventoryAutomation.matches(source, replacement)
                        && InventoryAutomation.hotbarAvailable(slot)
                }, { InventoryUtil.clickSlotDirect(source, slot) })) next = System.nanoTime() + (delay * 1e6).toLong()
            return
        }
    }
}
