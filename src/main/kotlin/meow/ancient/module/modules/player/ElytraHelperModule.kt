package meow.ancient.module.modules.player

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryActionSource
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

object ElytraHelperModule : Module(
    name = "Elytra Helper",
    category = PLAYER,
    description = "Swaps chest equipment, launches fireworks and automatically takes off with an elytra"
) {
    enum class LaunchMode(val displayName: String) {
        NORMAL("Normal"),
        LEGIT("Legit")
    }

    val swapKey by keybind("Swap Key", InputConstants.UNKNOWN.value)
    val hotbar by boolean("Hotbar", true)
    val fireworkKey by keybind("Firework Key", InputConstants.UNKNOWN.value)
    val launchMode by enum("Launch Mode", LaunchMode.NORMAL, nameMapper = { it.displayName })
    val autoTakeoff by boolean("Auto Takeoff", true)

    private var actionGeneration = 0L

    override fun onDisable() {
        actionGeneration++
        InventoryMoveModule.cancelInventoryActions(this)
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        actionGeneration++
        InventoryMoveModule.cancelInventoryActions(this)
    }

    @Commando
    fun onKey(event: KeyPressEvent) {
        if (event.cancel || event.action != InputConstants.PRESS || event.key == InputConstants.UNKNOWN.value) return
        val player = mc.player ?: return
        if (!canAct(player)) return

        val swapRequested = event.key == swapKey
        val rocketRequested = event.key == fireworkKey && player.isFallFlying
        if (!swapRequested && !rocketRequested) return
        event.cancel = true

        if (swapRequested) swapChestEquipment(player)
        if (rocketRequested) launchFirework(player)
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!autoTakeoff || InventoryMoveModule.isInventoryActionSlowed()) return
        val player = mc.player ?: return
        if (!player.connection.connection.isConnected) return
        val chest = player.getItemBySlot(EquipmentSlot.CHEST)
        if (!chest.`is`(Items.ELYTRA) || !LivingEntity.canGlideUsing(chest, EquipmentSlot.CHEST)
            || player.isInLava || player.isInWater || player.isPassenger || player.isFallFlying
            || player.isSpectator) return

        if (player.onGround()) {
            if (!mc.options.keyJump.isDown && !MoveUtil.isKeyPressed(mc.options.keyJump)
                && !player.input.keyPresses.jump() && !player.isJumping) {
                player.jumpFromGround()
            }
        } else {
            player.connection.send(
                ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            )
            player.startFallFlying()
        }
    }

    private fun queueAction(source: InventoryActionSource, action: (LocalPlayer) -> Unit) {
        val player = mc.player ?: return
        val connection = player.connection
        val generation = actionGeneration
        InventoryMoveModule.runInventoryAction(source, Runnable {
            if (!isEnabled() || generation != actionGeneration || mc.player !== player
                || player.connection !== connection || !canAct(player)) return@Runnable
            action(player)
        }, this)
    }

    private fun canAct(player: LocalPlayer): Boolean {
        if (mc.player !== player || !player.connection.connection.isConnected || mc.gameMode == null || player.isSpectator
            || player.containerMenu !== player.inventoryMenu || !player.inventoryMenu.carried.isEmpty) return false
        val screen = mc.gui.screen() ?: return true
        return screen is AbstractContainerScreen<*> && InventoryMoveModule.canMoveInScreen(screen)
    }

    fun swapChestEquipment(player: LocalPlayer) {
        val chestplate = player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)
        if (hotbar) {
            val slot = if (chestplate) InventoryUtil.findBestChestplateSlot(0, 9)
                else InventoryUtil.findBestElytraSlot(0, 9)
            if (slot >= 0 && canEquipByUse(player, player.inventory.getItem(slot))) {
                queueAction(InventoryActionSource.HOTBAR) { currentPlayer ->
                    if (canEquipByUse(currentPlayer, currentPlayer.inventory.getItem(slot))) {
                        InventoryUtil.withSilentSlot(slot, Runnable { InventoryUtil.useItem(InteractionHand.MAIN_HAND) })
                    }
                }
                return
            }
        }

        val slot = if (chestplate) InventoryUtil.findBestChestplateSlot() else InventoryUtil.findBestElytraSlot()
        if (slot < 0) return
        val selected = player.inventory.getItem(slot).copy()
        queueAction(InventoryActionSource.INVENTORY) { currentPlayer ->
            if (ItemStack.matches(currentPlayer.inventory.getItem(slot), selected)
                && currentPlayer.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA) == chestplate) {
                InventoryUtil.swapChest(slot)
            }
        }
    }

    private fun canEquipByUse(player: LocalPlayer, stack: ItemStack): Boolean {
        val equippable = stack.get(DataComponents.EQUIPPABLE) ?: return false
        val chestSlot = player.inventoryMenu.getSlot(6)
        return equippable.swappable() && equippable.slot() == EquipmentSlot.CHEST
            && chestSlot.mayPickup(player) && chestSlot.mayPlace(stack)
            && !ItemStack.isSameItemSameComponents(stack, player.getItemBySlot(EquipmentSlot.CHEST))
    }

    fun launchFirework(player: LocalPlayer) {
        if (isEatingWithMainHand(player)) {
            launchFromOffhand(player)
            return
        }
        if (player.mainHandItem.`is`(Items.FIREWORK_ROCKET)) {
            queueAction(InventoryActionSource.HOTBAR) { useRocket(it, InteractionHand.MAIN_HAND) }
            return
        }
        if (player.offhandItem.`is`(Items.FIREWORK_ROCKET)) {
            queueAction(InventoryActionSource.HOTBAR) { useRocket(it, InteractionHand.OFF_HAND) }
            return
        }

        val hotbarSlot = InventoryUtil.findItem(Items.FIREWORK_ROCKET, true)
        if (hotbarSlot >= 0) {
            queueAction(InventoryActionSource.HOTBAR) { useRocket(it, InteractionHand.MAIN_HAND, hotbarSlot) }
            return
        }
        val inventorySlot = InventoryUtil.findItem(Items.FIREWORK_ROCKET, false)
        if (inventorySlot < 0) {
            missingFirework()
            return
        }
        if (player.cooldowns.isOnCooldown(player.inventory.getItem(inventorySlot))) return
        val bridge = findRocketBridge(player)
        if (!canExchange(player, inventorySlot, bridge + 36)) return
        queueAction(InventoryActionSource.INVENTORY) { currentPlayer ->
            val stack = currentPlayer.inventory.getItem(inventorySlot)
            if (!currentPlayer.isFallFlying || !stack.`is`(Items.FIREWORK_ROCKET)
                || currentPlayer.cooldowns.isOnCooldown(stack)
                || !canExchange(currentPlayer, inventorySlot, bridge + 36)) return@queueAction
            if (!InventoryUtil.clickSlot(inventorySlot, bridge)) return@queueAction
            try {
                useRocket(currentPlayer, InteractionHand.MAIN_HAND, bridge)
            } finally {
                try {
                    if (canAct(currentPlayer)) InventoryUtil.clickSlot(inventorySlot, bridge)
                } finally {
                    if (canAct(currentPlayer)) InventoryUtil.closeInventory()
                }
            }
        }
    }

    private fun useRocket(player: LocalPlayer, hand: InteractionHand, hotbarSlot: Int? = null) {
        if (!canAct(player)) return
        val stack = if (hotbarSlot != null) player.inventory.getItem(hotbarSlot) else player.getItemInHand(hand)
        if (!stack.`is`(Items.FIREWORK_ROCKET) || player.cooldowns.isOnCooldown(stack)) return
        when (launchMode) {
            LaunchMode.NORMAL -> {
                if (hotbarSlot == null) InventoryUtil.useItem(hand)
                else InventoryUtil.withSilentSlot(hotbarSlot, Runnable { InventoryUtil.useItem(hand) })
            }
            LaunchMode.LEGIT -> InventoryUtil.useItemLegit(hand, hotbarSlot)
        }
    }

    private fun isEatingWithMainHand(player: LocalPlayer): Boolean {
        if (!player.isUsingItem || player.usedItemHand != InteractionHand.MAIN_HAND) return false
        val animation = player.useItem.useAnimation
        return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK
    }

    private fun launchFromOffhand(player: LocalPlayer) {
        if (player.offhandItem.`is`(Items.FIREWORK_ROCKET)) {
            queueAction(InventoryActionSource.HOTBAR) { currentPlayer ->
                val stack = currentPlayer.offhandItem
                if (stack.`is`(Items.FIREWORK_ROCKET) && !currentPlayer.cooldowns.isOnCooldown(stack)) {
                    InventoryUtil.useItem(InteractionHand.OFF_HAND)
                }
            }
            return
        }
        val hotbarSlot = InventoryUtil.findItem(Items.FIREWORK_ROCKET, true)
        val inventorySlot = if (hotbarSlot >= 0) hotbarSlot else InventoryUtil.findItem(Items.FIREWORK_ROCKET, false)
        if (inventorySlot < 0) {
            missingFirework()
            return
        }
        if (player.cooldowns.isOnCooldown(player.inventory.getItem(inventorySlot))) return
        val menuSlot = if (inventorySlot in 0..8) inventorySlot + 36 else inventorySlot
        if (!canExchange(player, menuSlot, 45)) return
        queueAction(InventoryActionSource.INVENTORY) { currentPlayer ->
            val stack = currentPlayer.inventory.getItem(inventorySlot)
            if (!currentPlayer.isFallFlying || !stack.`is`(Items.FIREWORK_ROCKET)
                || currentPlayer.cooldowns.isOnCooldown(stack)
                || !canExchange(currentPlayer, menuSlot, 45)) return@queueAction
            if (!InventoryUtil.clickSlot(menuSlot, 40)) return@queueAction
            try {
                if (canAct(currentPlayer)) InventoryUtil.useItem(InteractionHand.OFF_HAND)
            } finally {
                try {
                    if (canAct(currentPlayer)) InventoryUtil.clickSlot(menuSlot, 40)
                } finally {
                    if (canAct(currentPlayer)) InventoryUtil.closeInventory()
                }
            }
        }
    }

    private fun canExchange(player: LocalPlayer, firstMenuSlot: Int, secondMenuSlot: Int): Boolean {
        val first = player.inventoryMenu.getSlot(firstMenuSlot)
        val second = player.inventoryMenu.getSlot(secondMenuSlot)
        return first.mayPickup(player) && second.mayPickup(player)
            && first.mayPlace(second.item) && second.mayPlace(first.item)
    }

    private fun findRocketBridge(player: LocalPlayer): Int {
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty || stack.useAnimation == ItemUseAnimation.NONE) return slot
        }
        return 8
    }

    private fun missingFirework() {
        ChatUtil.send("Firework rocket not found in inventory.")
    }
}
