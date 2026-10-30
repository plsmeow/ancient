package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments

object InventoryUtil {
    private val Hotbar_rad = 0 until 9
    private val invent_rad = 9 until 36
    private val allslots = 0 until 36

    fun swapToOffhand(slot: Int, closeInvPacket: Boolean = true) {
        val player = mc.player ?: return
        val menu = player.containerMenu
        if (!canModifyInventory(player) || slot !in menu.slots.indices) return
        val source = InventoryActionSource.fromMenuSlot(player, menu, slot, 40)
        InventoryMoveModule.runInventoryAction(source, Runnable {
            if (mc.player !== player || player.containerMenu !== menu) return@Runnable
            if (clickSlot(slot, 40) && closeInvPacket) closeInventory()
        })
    }

    fun swapSlots(from: Int, to: Int, closeInvPacket: Boolean = true) {
        val player = mc.player ?: return
        val menu = player.containerMenu
        if (menu !== player.inventoryMenu || from !in allslots || to !in allslots || !canModifyInventory(player)) return
        val swapSlot = when {
            to in Hotbar_rad -> to
            from in Hotbar_rad -> from
            else -> return
        }
        val inventorySlot = if (swapSlot == to) from else to
        val slot = if (inventorySlot in Hotbar_rad) inventorySlot + 36 else inventorySlot
        val source = InventoryActionSource.fromInventorySlot(inventorySlot)
        InventoryMoveModule.runInventoryAction(source, Runnable {
            if (mc.player !== player || player.containerMenu !== menu) return@Runnable
            if (clickSlot(slot, swapSlot) && closeInvPacket) closeInventory()
        })
    }

    fun clickSlot(menuSlot: Int, hotbarButton: Int): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val menu = player.containerMenu
        if (!canModifyInventory(player) || menuSlot !in menu.slots.indices
            || hotbarButton !in Hotbar_rad && hotbarButton != 40) return false
        val slot = menu.getSlot(menuSlot)
        if (!slot.mayPickup(player) || !slot.mayPlace(player.inventory.getItem(hotbarButton))) return false
        val source = InventoryActionSource.fromMenuSlot(player, menu, menuSlot, hotbarButton)
        if (!InventoryMoveModule.beginInventoryAction(source)) return false
        try {
            if (mc.player !== player || player.containerMenu !== menu || !canModifyInventory(player)) return false
            gameMode.handleContainerInput(menu.containerId, menuSlot, hotbarButton, ContainerInput.SWAP, player)
            return true
        } finally {
            InventoryMoveModule.endInventoryAction(source)
        }
    }

    fun closeInventory() {
        InventoryMoveModule.runInventoryAction(InventoryActionSource.HOTBAR, Runnable {
            val player = mc.player ?: return@Runnable
            if (!canModifyInventory(player)) return@Runnable
            player.connection.send(ServerboundContainerClosePacket(player.containerMenu.containerId))
        })
    }

    fun useItem(hand: InteractionHand) {
        InventoryMoveModule.runInventoryAction(InventoryActionSource.HOTBAR, Runnable {
            val player = mc.player ?: return@Runnable
            val gameMode = mc.gameMode ?: return@Runnable
            val level = mc.level ?: return@Runnable
            if (!canModifyInventory(player)) return@Runnable
            gameMode.startPrediction(level) { sequence ->
                ServerboundUseItemPacket(hand, sequence, player.yRot, player.xRot)
            }
            player.swing(hand, SwingAnimation.DEFAULT, false)
        })
    }

    fun withSilentSlot(slot: Int, action: Runnable) {
        if (slot !in Hotbar_rad) return
        InventoryMoveModule.runInventoryAction(InventoryActionSource.HOTBAR, Runnable {
            val player = mc.player ?: return@Runnable
            if (!canModifyInventory(player)) return@Runnable
            val previous = player.inventory.selectedSlot
            val switched = previous != slot
            if (switched) player.connection.send(ServerboundSetCarriedItemPacket(slot))
            try {
                action.run()
            } finally {
                if (switched && mc.player === player && player.connection.connection.isConnected) {
                    player.connection.send(ServerboundSetCarriedItemPacket(previous))
                }
            }
        })
    }

    fun useItemLegit(hand: InteractionHand, hotbarSlot: Int? = null) {
        if (hotbarSlot != null && hotbarSlot !in Hotbar_rad) return
        InventoryMoveModule.runInventoryAction(InventoryActionSource.HOTBAR, Runnable {
            val player = mc.player ?: return@Runnable
            val gameMode = mc.gameMode ?: return@Runnable
            if (!canModifyInventory(player)) return@Runnable
            val previous = player.inventory.selectedSlot
            val selected = hotbarSlot ?: previous
            val switched = selected != previous
            if (switched) player.inventory.selectedSlot = selected
            try {
                val result = gameMode.useItem(player, hand)
                if (result is net.minecraft.world.InteractionResult.Success && result.shouldSwing()) {
                    player.swing(hand, SwingAnimation.DEFAULT, false)
                }
            } finally {
                if (switched && mc.player === player && player.connection.connection.isConnected) {
                    player.inventory.selectedSlot = previous
                    gameMode.tick()
                }
            }
        })
    }

    fun swapToSlot(slot: Int) {
        if (slot !in Hotbar_rad || mc.player?.inventory?.selectedSlot == slot) return
        InventoryMoveModule.runInventoryAction(InventoryActionSource.HOTBAR, Runnable {
            val player = mc.player ?: return@Runnable
            if (!canModifyInventory(player) || player.inventory.selectedSlot == slot) return@Runnable
            player.connection.send(ServerboundSetCarriedItemPacket(slot))
            player.inventory.selectedSlot = slot
        })
    }

    fun swapChest(slot: Int): Boolean {
        val player = mc.player ?: return false
        if (slot !in allslots || player.containerMenu !== player.inventoryMenu || !canModifyInventory(player)) return false
        val chestSlot = player.inventoryMenu.getSlot(6)
        if (!chestSlot.mayPickup(player) || !chestSlot.mayPlace(player.inventory.getItem(slot))) return false
        val source = InventoryActionSource.fromInventorySlot(slot)
        if (!InventoryMoveModule.beginInventoryAction(source)) return false
        try {
            if (slot in Hotbar_rad) return clickSlot(6, slot)
            val bridge = 8
            val originalBridge = player.inventory.getItem(bridge).copy()
            if (!clickSlot(slot, bridge)) return false
            try {
                if (mc.player !== player || player.containerMenu !== player.inventoryMenu) return false
                return clickSlot(6, bridge)
            } finally {
                if (mc.player === player && player.containerMenu === player.inventoryMenu
                    && ItemStack.matches(player.inventory.getItem(slot), originalBridge)) {
                    clickSlot(slot, bridge)
                }
            }
        } finally {
            try {
                if (mc.player === player && player.containerMenu === player.inventoryMenu) closeInventory()
            } finally {
                InventoryMoveModule.endInventoryAction(source)
            }
        }
    }

    private fun canModifyInventory(player: LocalPlayer): Boolean = mc.player === player
        && player.connection.connection.isConnected && player.containerMenu.carried.isEmpty && mc.gameMode != null

    fun findBestChestplateSlot(start: Int = 0, end: Int = 36): Int {
        val player = mc.player ?: return -1
        val registry = mc.level?.registryAccess()?.lookup(Registries.ENCHANTMENT)?.orElse(null)
        val protection = registry?.get(Enchantments.PROTECTION)?.orElse(null)
        val unbreaking = registry?.get(Enchantments.UNBREAKING)?.orElse(null)
        val mending = registry?.get(Enchantments.MENDING)?.orElse(null)
        var bestSlot = -1
        var bestScore = -1.0
        for (slot in start.coerceAtLeast(0) until end.coerceAtMost(36)) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty || stack.count != 1) continue
            val equippable = stack.get(DataComponents.EQUIPPABLE) ?: continue
            if (equippable.slot() != EquipmentSlot.CHEST || stack.`is`(Items.ELYTRA)) continue
            val priority = when (stack.item) {
                Items.NETHERITE_CHESTPLATE -> 6
                Items.DIAMOND_CHESTPLATE -> 5
                Items.IRON_CHESTPLATE -> 4
                Items.CHAINMAIL_CHESTPLATE -> 3
                Items.GOLDEN_CHESTPLATE -> 2
                Items.LEATHER_CHESTPLATE -> 1
                else -> 0
            }
            if (priority == 0 || !player.inventoryMenu.getSlot(6).mayPlace(stack)) continue
            val protectionLevel = protection?.let { EnchantmentHelper.getItemEnchantmentLevel(it, stack) } ?: 0
            val unbreakingLevel = unbreaking?.let { EnchantmentHelper.getItemEnchantmentLevel(it, stack) } ?: 0
            val mendingLevel = mending?.let { EnchantmentHelper.getItemEnchantmentLevel(it, stack) } ?: 0
            val durability = if (stack.maxDamage > 0) (stack.maxDamage - stack.damageValue).toDouble() / stack.maxDamage else 1.0
            val score = priority * 10000.0 + protectionLevel * 100 + unbreakingLevel * 10
                + (if (mendingLevel > 0) 1 else 0) + durability * 10
            if (score > bestScore) {
                bestScore = score
                bestSlot = slot
            }
        }
        return bestSlot
    }

    fun findBestElytraSlot(start: Int = 0, end: Int = 36): Int {
        val player = mc.player ?: return -1
        var bestSlot = -1
        var bestDurability = -1
        for (slot in start.coerceAtLeast(0) until end.coerceAtMost(36)) {
            val stack = player.inventory.getItem(slot)
            if (!stack.`is`(Items.ELYTRA) || stack.count != 1
                || !player.inventoryMenu.getSlot(6).mayPlace(stack)) continue
            val durability = stack.maxDamage - stack.damageValue
            if (durability > bestDurability) {
                bestSlot = slot
                bestDurability = durability
            }
        }
        return bestSlot
    }

    fun findItem(item: Item, inHotbar: Boolean): Int {
        val player = mc.player ?: return -1
        val items = player.inventory.nonEquipmentItems
        val range = if (inHotbar) Hotbar_rad else invent_rad
        var finalSlot = -1

        for (i in range) {
            if (items[i].item == item) {
                finalSlot = i
            }
        }
        return finalSlot
    }

    fun findItem(input: Item): Int {
        val slot = findFirstSlot(allslots) { _, stack -> stack.item == input }
        return if (slot in Hotbar_rad) slot + 36 else slot
    }

    fun hasElytraEquipped(): Boolean {
        val player = mc.player ?: return false
        return player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)
    }

    fun findAxeInInventory(inHotbar: Boolean): Int {
        val range = if (inHotbar) Hotbar_rad else invent_rad
        return findFirstSlot(range) { _, stack -> stack.`is`(ItemTags.AXES) }
    }

    fun findBestSlotInHotBar(): Int {
        val emptySlot = findEmptySlot()
        return if (emptySlot != -1) emptySlot else findNonSwordSlot()
    }

    fun findEmptySlot(): Int {
        val selected = mc.player?.inventory?.selectedSlot ?: return -1
        return findFirstSlot(Hotbar_rad) { idx, stack -> stack.isEmpty && idx != selected }
    }

    private fun findNonSwordSlot(): Int {
        val selected = mc.player?.inventory?.selectedSlot ?: return -1
        return findFirstSlot(Hotbar_rad) { idx, stack -> !stack.`is`(ItemTags.SWORDS) && idx != selected }
    }

    fun countNonEnchantedTotems(): Int {
        val player = mc.player ?: return 0
        val items = player.inventory.nonEquipmentItems
        var count = 0

        for (i in allslots) {
            val stack = items[i]
            if (stack.`is`(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted) {
                count++
            }
        }
        return count
    }

    fun findBestTotemSlot(saveEnchanted: Boolean): Int {
        val slot = findFirstSlot(allslots) { _, stack ->
            stack.`is`(Items.TOTEM_OF_UNDYING) && (!saveEnchanted || !stack.isEnchanted)
        }
        return if (slot in Hotbar_rad) slot + 36 else slot
    }

    private inline fun findFirstSlot(range: IntRange, predicate: (Int, ItemStack) -> Boolean): Int {
        val items = mc.player?.inventory?.nonEquipmentItems ?: return -1
        for (i in range) {
            if (predicate(i, items[i])) {
                return i
            }
        }
        return -1
    }
}
