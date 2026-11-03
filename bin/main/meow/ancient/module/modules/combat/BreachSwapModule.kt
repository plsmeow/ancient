package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import sweetie.evaware.flora.api.Commando

object BreachSwapModule : Module(
    name = "Breach Swap",
    category = COMBAT,
    description = "Swaps to Breach mace right before attack"
) {
    private const val MACE_ATTACK_SPEED = 0.6f
    private var ticksSinceAttack = 1000

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        if (ticksSinceAttack < 1000) ticksSinceAttack++
    }

    fun onAttackExecuted() {
        ticksSinceAttack = 0
    }

    fun isActive(): Boolean =
        isEnabled() && mc.player != null && mc.level != null && findBreachMaceSlot() != -1

    fun isVirtualCooldownReady(ticksAhead: Int = 0): Boolean {
        val fullChargeTicks = 20.0f / MACE_ATTACK_SPEED
        val progress = Mth.clamp((ticksSinceAttack + ticksAhead + 0.5f) / fullChargeTicks, 0.0f, 1.0f)
        return progress >= 0.98f
    }

    fun findBreachMaceSlot(): Int {
        val player = mc.player ?: return -1
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            val isMace = stack.`is`(Items.MACE) || stack.hoverName.string.contains("1.21 Mace")
            if (!isMace) continue
            val enchantments = stack.get(DataComponents.ENCHANTMENTS) ?: continue
            if (enchantments.keySet().any { it.`is`(Enchantments.BREACH) }) {
                return slot
            }
        }
        return -1
    }

    fun swapToBreachMace(): Int {
        if (!isActive()) return -1

        val maceSlot = findBreachMaceSlot()
        val player = mc.player ?: return -1
        val previousSlot = player.inventory.selectedSlot
        if (previousSlot == maceSlot) return -1

        selectSlot(maceSlot)
        return previousSlot
    }

    fun swapBack(previousSlot: Int) {
        if (previousSlot in 0..8) {
            selectSlot(previousSlot)
        }
    }

    private fun selectSlot(slot: Int) {
        val player = mc.player ?: return
        if (slot in 0..8) {
            player.inventory.selectedSlot = slot
            player.connection.send(ServerboundSetCarriedItemPacket(slot))
        }
    }

    override fun onEnable() {
        ticksSinceAttack = 1000
    }
}
