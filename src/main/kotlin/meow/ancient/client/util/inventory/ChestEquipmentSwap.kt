package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

class ChestEquipmentSwap {
    private var owner: LocalPlayer? = null
    private var connection: ClientPacketListener? = null
    private var sourceSlot = -1
    private var originalElytra = ItemStack.EMPTY
    private var equippedChestplate = ItemStack.EMPTY
    private var restorePending = false
    private var bridgeSlot = -1
    private var originalBridge = ItemStack.EMPTY
    private var preparingInventory = false

    init {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (restorePending) restoreElytra()
            else if (owner != null && !hasSameOwner()) clear()
        }
        EventManager.register(this)
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        val player = owner
        if (player != null && mc.player === player && player.connection === connection
            && bridgeSlot >= 0 && ItemStack.matches(player.inventory.getItem(sourceSlot), originalBridge)) {
            val displaced = player.inventory.getItem(bridgeSlot)
            if (matches(displaced, originalElytra) || matches(displaced, equippedChestplate)) {
                player.inventory.setItem(bridgeSlot, player.inventory.getItem(sourceSlot))
                player.inventory.setItem(sourceSlot, displaced)
            }
        }
        if (player != null && mc.player === player && player.connection === connection
            && sourceSlot in 0 until 36
            && matches(player.getItemBySlot(EquipmentSlot.CHEST), equippedChestplate)
            && matches(player.inventory.getItem(sourceSlot), originalElytra)) {
            val elytra = player.inventory.getItem(sourceSlot)
            val chestplate = player.getItemBySlot(EquipmentSlot.CHEST)
            player.inventory.setItem(sourceSlot, chestplate)
            player.setItemSlot(EquipmentSlot.CHEST, elytra)
        }
        clear()
    }

    fun equipChestplate(slot: Int = InventoryUtil.findBestChestplateSlot()): Boolean {
        if (owner != null) {
            if (hasSameOwner()) return false
            clear()
        }
        val player = mc.player ?: return false
        if (!canSwap(player)) return false
        val chest = player.getItemBySlot(EquipmentSlot.CHEST)
        if (!chest.`is`(Items.ELYTRA)) return false
        if (slot !in 0 until 36) return false
        val replacement = player.inventory.getItem(slot)
        val chestSlot = player.inventoryMenu.getSlot(6)
        if (!chestSlot.mayPickup(player) || !chestSlot.mayPlace(replacement)) return false

        val source = InventoryActionSource.INVENTORY
        if (!InventoryMoveModule.beginInventoryAction(source)) {
            preparingInventory = true
            return false
        }
        preparingInventory = false
        try {
            owner = player
            connection = player.connection
            sourceSlot = slot
            originalElytra = chest.copy()
            equippedChestplate = replacement.copy()
            try {
                exchangeChest(player, slot)
                if (matches(player.getItemBySlot(EquipmentSlot.CHEST), equippedChestplate)
                    && matches(player.inventory.getItem(slot), originalElytra)) {
                    return true
                }
                reset()
                return false
            } catch (failure: Throwable) {
                reset()
                throw failure
            }
        } finally {
            InventoryMoveModule.endInventoryAction(source)
        }
    }

    fun restoreElytra(): Boolean {
        val player = owner ?: return false
        if (!hasSameOwner()) {
            clear()
            return false
        }
        if (!canSwap(player)) return false
        if (bridgeSlot < 0) {
            if (matches(player.getItemBySlot(EquipmentSlot.CHEST), originalElytra)
                && matches(player.inventory.getItem(sourceSlot), equippedChestplate)) {
                clear()
                return true
            }
            if (!matches(player.getItemBySlot(EquipmentSlot.CHEST), equippedChestplate)
                || !matches(player.inventory.getItem(sourceSlot), originalElytra)) {
                clear()
                return false
            }
        }
        val source = InventoryActionSource.INVENTORY
        if (!InventoryMoveModule.beginInventoryAction(source)) {
            preparingInventory = true
            return false
        }
        preparingInventory = false
        try {
            if (bridgeSlot >= 0 && !recoverBridge(player)) return false
            if (matches(player.getItemBySlot(EquipmentSlot.CHEST), originalElytra)
                && matches(player.inventory.getItem(sourceSlot), equippedChestplate)) {
                clear()
                return true
            }
            if (!matches(player.getItemBySlot(EquipmentSlot.CHEST), equippedChestplate)
                || !matches(player.inventory.getItem(sourceSlot), originalElytra)) {
                clear()
                return false
            }
            val chestSlot = player.inventoryMenu.getSlot(6)
            if (!chestSlot.mayPickup(player) || !chestSlot.mayPlace(player.inventory.getItem(sourceSlot))) return false
            exchangeChest(player, sourceSlot)
            if (!matches(player.getItemBySlot(EquipmentSlot.CHEST), originalElytra)
                || !matches(player.inventory.getItem(sourceSlot), equippedChestplate)) return false
            clear()
            return true
        } finally {
            InventoryMoveModule.endInventoryAction(source)
        }
    }

    fun reset(restore: Boolean = true) {
        cancelPreparation()
        if (!restore) {
            clear()
            return
        }
        restorePending = owner != null
        if (restorePending) restoreElytra()
    }

    private fun hasSameOwner(): Boolean = mc.player === owner
        && mc.player?.connection === connection
        && connection?.connection?.isConnected == true

    private fun canSwap(player: LocalPlayer): Boolean = player.containerMenu === player.inventoryMenu
        && player.inventoryMenu.carried.isEmpty
        && player.connection.connection.isConnected
        && mc.gameMode != null

    private fun matches(actual: ItemStack, expected: ItemStack): Boolean {
        if (!ItemStack.isSameItem(actual, expected) || actual.count != expected.count) return false
        if (expected.isDamageableItem) expected.damageValue = actual.damageValue
        return ItemStack.isSameItemSameComponents(actual, expected)
    }

    private fun exchangeChest(player: LocalPlayer, slot: Int) {
        if (!hasSameOwner() || !canSwap(player)) return
        if (slot in 0..8) {
            try {
                InventoryUtil.clickSlot(6, slot)
            } finally {
                if (hasSameOwner() && canSwap(player)) InventoryUtil.closeInventory()
            }
            return
        }

        val bridge = 8
        originalBridge = player.inventory.getItem(bridge).copy()
        bridgeSlot = bridge
        try {
            try {
                InventoryUtil.clickSlot(slot, bridge)
            } finally {
                if (!ItemStack.matches(player.inventory.getItem(slot), originalBridge)) bridgeSlot = -1
            }
            if (bridgeSlot >= 0 && hasSameOwner() && canSwap(player)) {
                InventoryUtil.clickSlot(6, bridge)
            }
        } finally {
            try {
                recoverBridge(player)
            } finally {
                if (hasSameOwner() && canSwap(player)) InventoryUtil.closeInventory()
            }
        }
    }

    private fun recoverBridge(player: LocalPlayer): Boolean {
        if (bridgeSlot < 0) return true
        if (!hasSameOwner() || !canSwap(player)) return false
        val bridge = bridgeSlot
        if (ItemStack.matches(player.inventory.getItem(bridge), originalBridge)) {
            bridgeSlot = -1
            originalBridge = ItemStack.EMPTY
            return true
        }
        if (!ItemStack.matches(player.inventory.getItem(sourceSlot), originalBridge)) return false
        val displaced = player.inventory.getItem(bridge)
        if (!matches(displaced, originalElytra) && !matches(displaced, equippedChestplate)) return false
        if (mc.gameMode == null) return false
        try {
            InventoryUtil.clickSlot(sourceSlot, bridge)
        } finally {
            if (ItemStack.matches(player.inventory.getItem(bridge), originalBridge)) {
                bridgeSlot = -1
                originalBridge = ItemStack.EMPTY
            }
        }
        return bridgeSlot < 0
    }


    private fun cancelPreparation() {
        if (!preparingInventory) return
        preparingInventory = false
        InventoryMoveModule.cancelInventoryPreparation()
    }

    private fun clear() {
        cancelPreparation()
        owner = null
        connection = null
        sourceSlot = -1
        originalElytra = ItemStack.EMPTY
        equippedChestplate = ItemStack.EMPTY
        restorePending = false
        bridgeSlot = -1
        originalBridge = ItemStack.EMPTY
    }
}
