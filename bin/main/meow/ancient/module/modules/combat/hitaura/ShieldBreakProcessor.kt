package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryUtil
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items

object ShieldBreakProcessor {
    fun isTargetBlocking(target: LivingEntity?): Boolean =
        target != null && target.isBlocking && target.useItem.`is`(Items.SHIELD)

    fun swapToAxe(target: LivingEntity): Int {
        if (!isTargetBlocking(target)) return -1
        val player = mc.player ?: return -1
        val axeSlot = findAxeInHotbar()
        val previousSlot = player.inventory.selectedSlot
        if (axeSlot == -1 || previousSlot == axeSlot) return -1
        InventoryUtil.swapToSlot(axeSlot)
        return if (mc.player === player && player.inventory.selectedSlot == axeSlot) previousSlot else -1
    }

    fun swapBack(slot: Int) {
        if (slot !in 0..8) return
        val player = mc.player ?: return
        if (player.connection.connection.isConnected) {
            InventoryUtil.swapToSlot(slot)
        } else {
            player.inventory.selectedSlot = slot
        }
    }

    fun findAxeInHotbar(): Int {
        val player = mc.player ?: return -1
        for (slot in 0..8) {
            if (player.inventory.getItem(slot).`is`(ItemTags.AXES)) return slot
        }
        return -1
    }
}
