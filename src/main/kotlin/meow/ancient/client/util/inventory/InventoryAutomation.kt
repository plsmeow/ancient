package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.world.item.ItemStack

/** One queued inventory edit at a time; revalidate snapshots after the movement pause. */
object InventoryAutomation {
    private var owner: Module? = null
    private var queuedAt = 0L
    private var settleUntil = 0L
    private var generation = 0
    private var world: Any? = null

    fun busy(): Boolean {
        if (world !== mc.level) { owner = null; settleUntil = 0L; generation++; world = mc.level }
        if (owner != null && System.nanoTime() - queuedAt > 3_000_000_000L) cancel(owner!!)
        return owner != null || System.nanoTime() < settleUntil
    }

    fun submit(module: Module, validate: () -> Boolean, edit: () -> Boolean): Boolean {
        if (busy() || !module.isEnabled()) return false
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        if (!available()) return false
        owner = module
        queuedAt = System.nanoTime()
        val version = ++generation
        val action = Runnable {
            if (version != generation || owner !== module) return@Runnable
            try {
                if (mc.player !== player || mc.level !== level || !module.isEnabled()
                    || !available() || !validate()) return@Runnable
                if (edit()) {
                    val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
                    settleUntil = System.nanoTime() + (ping.coerceIn(0, 1000) + 150) * 1_000_000L
                    InventoryUtil.closeInventory()
                }
            } finally { if (version == generation) owner = null }
        }
        if (AnticheatManager.isSwapBypassActive && !InventoryMoveModule.isEnabled()) {
            // Inventory helpers must still send idle input before CLICK_WINDOW when GUI movement is off.
            SlownessManager.addTask(100L) {
                if (version == generation && owner === module)
                    InventoryMoveModule.runInventoryAction(InventoryActionSource.INVENTORY, action, module)
            }
        } else InventoryMoveModule.runInventoryAction(InventoryActionSource.INVENTORY, action, module)
        return true
    }

    fun cancel(module: Module) {
        InventoryMoveModule.cancelInventoryActions(module)
        if (owner === module) { owner = null; generation++ }
    }

    fun available(): Boolean {
        val player = mc.player ?: return false
        return player.isAlive && !player.isSpectator && !player.isUsingItem && !mc.options.keyUse.isDown
            && mc.gui.screen() == null && player.containerMenu === player.inventoryMenu
            && player.containerMenu.carried.isEmpty && player.connection.connection.isConnected
    }

    fun menuSlot(slot: Int): Int = if (slot in 0..8) slot + 36 else slot
    fun hotbarAvailable(slot: Int): Boolean = slot in 0..8 && (SilentSwapUtil.serverSlot != slot
        || SilentSwapUtil.debugSnapshot().holdTicks == 0 && !PacketMineModule.hasPendingAction())
    fun matches(slot: Int, expected: ItemStack): Boolean =
        mc.player?.inventory?.getItem(slot)?.let { ItemStack.matches(it, expected) } == true

    /** Inventory armor slot -> bridge hotbar -> source, including the old equipped stack. */
    fun equip(source: Int, armorMenuSlot: Int, bridge: Int): Boolean {
        val player = mc.player ?: return false
        if (source in 0..8) return InventoryUtil.clickSlotDirect(armorMenuSlot, source)
        val originalBridge = player.inventory.getItem(bridge).copy()
        if (!InventoryUtil.clickSlotDirect(source, bridge)) return false
        try { return InventoryUtil.clickSlotDirect(armorMenuSlot, bridge) }
        finally {
            if (mc.player === player && player.containerMenu === player.inventoryMenu
                && ItemStack.matches(player.inventory.getItem(source), originalBridge))
                InventoryUtil.clickSlotDirect(source, bridge)
        }
    }
}
