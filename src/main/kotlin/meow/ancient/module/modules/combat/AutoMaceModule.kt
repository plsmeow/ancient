package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.inventory.ChestEquipmentSwap
import meow.ancient.client.util.inventory.InventoryActionSource
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.GroundSpoofModule
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import sweetie.evaware.flora.api.Commando

object AutoMaceModule : Module(
    name = "AutoMace",
    category = COMBAT,
    description = "Swaps to a hotbar mace for HitAura smash attacks"
) {
    enum class MacePriority(val displayName: String) {
        NONE("None"),
        DENSITY("Density"),
        BREACH("Breach"),
        WIND("Wind")
    }

    enum class AttackPreparation {
        NONE, READY, WAITING
    }

    val force by boolean("No Delay", true)
    val syncHurtTime by boolean("Sync HurtTime", false, isVisible = { force })
    val macePriority by enum("Mace Priority", MacePriority.NONE, nameMapper = { it.displayName })
    val elytraHandling by boolean("Elytra Handling", false)
    val returnElytra by boolean("Return Elytra", false, isVisible = { elytraHandling })
    val returnDelay by number(
        "Return Delay", 0.0, 0.0, 10.0, step = 1.0, precision = 0, unit = "t",
        isVisible = { elytraHandling && returnElytra }
    )

    private val chestSwap = ChestEquipmentSwap()
    private var attackPlayer: LocalPlayer? = null
    private var previousSlot = -1
    private var chestSwapped = false
    private var chestSwappedThisAttack = false
    private var returnPlayer: LocalPlayer? = null
    private var returnAtTick = -1
    private var inventoryTransaction: InventoryActionSource? = null
    private var preparingInventory = false

    fun isForceReady(target: LivingEntity): Boolean = force
        && (!syncHurtTime || target.hurtTime <= 1)
        && findReadyMaceSlot() != -1

    fun beforeAttack(): AttackPreparation {
        chestSwappedThisAttack = false
        val player = mc.player
        val maceSlot = findReadyMaceSlot()
        if (player == null || maceSlot == -1) {
            cancelPreparation()
            return AttackPreparation.NONE
        }
        val needsChestSwap = elytraHandling && player.isFallFlying
            && player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)
        val chestSlot = if (needsChestSwap) InventoryUtil.findBestChestplateSlot() else -1
        if (needsChestSwap && chestSlot == -1) {
            cancelPreparation()
            return AttackPreparation.WAITING
        }
        val selectedSlot = SilentSwapUtil.serverSlot
        if ((selectedSlot != maceSlot || needsChestSwap)
            && (player.containerMenu !== player.inventoryMenu || !player.inventoryMenu.carried.isEmpty)) {
            cancelPreparation()
            return AttackPreparation.WAITING
        }
        val source = if (needsChestSwap) InventoryActionSource.INVENTORY
            else InventoryActionSource.HOTBAR
        if (source == InventoryActionSource.HOTBAR) cancelPreparation()
        if (!InventoryMoveModule.beginInventoryAction(source)) {
            preparingInventory = source == InventoryActionSource.INVENTORY
            return AttackPreparation.WAITING
        }
        preparingInventory = false
        inventoryTransaction = source
        try {
            if (needsChestSwap) {
                if (!chestSwap.equipChestplate(chestSlot)) return AttackPreparation.WAITING
                chestSwapped = true
                chestSwappedThisAttack = true
            }
            attackPlayer = player
            previousSlot = if (selectedSlot != maceSlot) selectedSlot else -1
            if ((previousSlot >= 0 || AnticheatManager.isSilentSwapNew) && !SilentSwapUtil.selectSlot(maceSlot)) {
                previousSlot = -1
                return AttackPreparation.WAITING
            }
            return AttackPreparation.READY
        } catch (failure: Throwable) {
            afterAttack(false)
            throw failure
        }
    }

    fun afterAttack(executed: Boolean) {
        try {
            try {
                restoreSelectedSlot()
            } finally {
                if (chestSwappedThisAttack) {
                    if (!executed) {
                        chestSwap.reset()
                        chestSwapped = false
                        returnPlayer = null
                        returnAtTick = -1
                    } else if (returnElytra) {
                        val player = mc.player
                        returnPlayer = player
                        returnAtTick = (player?.tickCount ?: 0) + returnDelay.toInt()
                        if (returnDelay.toInt() == 0) restoreChest()
                    }
                }
                chestSwappedThisAttack = false
            }
        } finally {
            val source = inventoryTransaction
            if (source != null) {
                inventoryTransaction = null
                InventoryMoveModule.endInventoryAction(source)
            }
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player
        if (returnPlayer != null && returnPlayer !== player) {
            chestSwap.reset()
            chestSwapped = false
            returnPlayer = null
            returnAtTick = -1
            return
        }
        if (returnAtTick >= 0 && player != null && player.tickCount >= returnAtTick) restoreChest()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        val player = attackPlayer
        if (!AnticheatManager.isSilentSwapNew && player != null && player === mc.player && previousSlot in 0..8) {
            player.inventory.selectedSlot = previousSlot
        }
        attackPlayer = null
        previousSlot = -1
        chestSwapped = false
        chestSwappedThisAttack = false
        returnPlayer = null
        returnAtTick = -1
        inventoryTransaction = null
        preparingInventory = false
    }

    override fun onDisable() {
        cancelPreparation()
        try {
            afterAttack(false)
        } finally {
            chestSwap.reset()
            chestSwapped = false
            chestSwappedThisAttack = false
            returnPlayer = null
            returnAtTick = -1
        }
    }

    private fun cancelPreparation() {
        if (!preparingInventory) return
        preparingInventory = false
        InventoryMoveModule.cancelInventoryPreparation()
    }

    private fun restoreSelectedSlot() {
        val player = attackPlayer
        val slot = previousSlot
        attackPlayer = null
        previousSlot = -1
        if (player == null || player !== mc.player || slot !in 0..8) return
        if (player.connection.connection.isConnected) {
            SilentSwapUtil.restoreSlot(slot)
        } else if (!AnticheatManager.isSilentSwapNew) {
            player.inventory.selectedSlot = slot
        }
    }

    private fun restoreChest() {
        if (!chestSwapped || chestSwap.restoreElytra()) {
            chestSwapped = false
            returnPlayer = null
            returnAtTick = -1
        }
    }

    private fun findReadyMaceSlot(): Int {
        if (!isEnabled()) return -1
        val player = mc.player ?: return -1
        if (mc.level == null || !player.connection.connection.isConnected) return -1
        if (player.isFallFlying && !elytraHandling) return -1
        if (!GroundSpoofModule.isEnabled() && player.fallDistance <= 1.5) return -1
        return findBestMaceSlot(player)
    }

    private fun findBestMaceSlot(player: LocalPlayer): Int {
        val key = when (macePriority) {
            MacePriority.NONE -> null
            MacePriority.DENSITY -> Enchantments.DENSITY
            MacePriority.BREACH -> Enchantments.BREACH
            MacePriority.WIND -> Enchantments.WIND_BURST
        }
        val enchantment = key?.let { enchantmentKey ->
            mc.level?.registryAccess()?.lookup(Registries.ENCHANTMENT)?.orElse(null)?.get(enchantmentKey)?.orElse(null)
        }
        var bestSlot = -1
        var bestLevel = -1
        for (slot in 0 until 9) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty || (!stack.`is`(Items.MACE) && !stack.hoverName.string.contains("1.21 Mace"))) continue
            if (macePriority == MacePriority.NONE) return slot
            val level = enchantment?.let { EnchantmentHelper.getItemEnchantmentLevel(it, stack) } ?: 0
            if (level > bestLevel) {
                bestLevel = level
                bestSlot = slot
            }
        }
        return bestSlot
    }
}
