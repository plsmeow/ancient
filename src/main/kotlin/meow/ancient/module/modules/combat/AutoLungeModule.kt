package meow.ancient.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments
import sweetie.evaware.flora.api.Commando

object AutoLungeModule : Module(
    name = "Auto Lunge",
    category = COMBAT,
    description = "Swaps to a Lunge spear in the hotbar and sends the STAB attack"
) {
    enum class LungeMode(val displayName: String) {
        BIND("Bind"),
        AUTO("Auto")
    }

    val mode by enum("Mode", LungeMode.BIND, nameMapper = { it.displayName })
    val bindKey by keybind("Bind Key", InputConstants.UNKNOWN.value, isVisible = { mode == LungeMode.BIND })
    val delay by number(
        "Delay", 100.0, 0.0, 2000.0, step = 10.0, precision = 0, unit = "ms",
        isVisible = { mode == LungeMode.AUTO }
    )

    private var lastLunge = 0L

    override fun onEnable() {
        lastLunge = 0L
    }

    override fun onDisable() {
        lastLunge = 0L
    }

    @Commando
    fun onKey(event: KeyPressEvent) {
        if (event.cancel || event.action != InputConstants.PRESS || event.key == InputConstants.UNKNOWN.value) return
        if (mode != LungeMode.BIND || event.key != bindKey) return
        val player = mc.player ?: return
        if (!canAct(player)) return
        event.cancel = true
        lunge(player)
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (mode != LungeMode.AUTO) return
        val player = mc.player ?: return
        if (!canAct(player)) return
        val now = System.currentTimeMillis()
        if (now - lastLunge < delay.toLong()) return
        if (lunge(player)) lastLunge = now
    }

    private fun canAct(player: LocalPlayer): Boolean {
        if (mc.player !== player || !player.connection.connection.isConnected || player.isSpectator) return false
        return mc.gui.screen() == null
    }

    private fun lunge(player: LocalPlayer): Boolean {
        val slot = findSpearSlot(player)
        if (slot == -1) return false
        val animation = player.inventory.getItem(slot).attackAnimation
        var executed = false
        SilentSwapUtil.withSlot(slot) {
            player.connection.send(
                ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STAB, BlockPos.ZERO, Direction.DOWN)
            )
            player.swing(InteractionHand.MAIN_HAND, animation, false)
            executed = true
        }
        return executed
    }

    private fun findSpearSlot(player: LocalPlayer): Int {
        for (slot in 0..8) {
            if (hasLunge(player.inventory.getItem(slot))) return slot
        }
        return -1
    }

    private fun hasLunge(stack: ItemStack): Boolean {
        if (stack.isEmpty || !stack.`is`(ItemTags.SPEARS)) return false
        val enchantments = stack.get(DataComponents.ENCHANTMENTS) ?: return false
        return enchantments.keySet().any { it.`is`(Enchantments.LUNGE) }
    }
}
