package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.inventory.SilentSwapUtil
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
        val previousSlot = SilentSwapUtil.serverSlot
        if (axeSlot == -1 || previousSlot == axeSlot) return -1
        if (!SilentSwapUtil.selectSlot(axeSlot) || mc.player !== player) return -1
        return previousSlot
    }

    fun swapBack(slot: Int) {
        if (slot !in 0..8) return
        val player = mc.player ?: return
        if (player.connection.connection.isConnected) {
            SilentSwapUtil.restoreSlot(slot)
        } else if (!AnticheatManager.isSilentSwapNew) {
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
