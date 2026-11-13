package meow.ancient.module.modules.player

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.render.FreeCameraModule
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

object ClickPearlModule : Module(
    name = "ClickPearl",
    category = PLAYER,
    description = "Throws ender pearl with a single key press"
) {
    enum class ThrowMode(val displayName: String) {
        NORMAL("Normal"),
        LEGIT("Legit");

        companion object {
            val names: List<String> = entries.map { it.displayName }
            fun fromName(name: String): ThrowMode =
                entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: NORMAL
        }
    }

    val mode by enum("Mode", ThrowMode.NORMAL, nameMapper = { it.displayName })
    val throwKey by keybind("Throw Key", -102)

    private var pearlUsed = false
    private var ticksExisted = 0

    override fun onEnable() {
        pearlUsed = false
        ticksExisted = 0
    }

    override fun onDisable() {
        pearlUsed = false
        ticksExisted = 0
    }

    @Commando
    fun onKey(event: KeyPressEvent) {
        if (event.action != InputConstants.PRESS || event.key == InputConstants.UNKNOWN.value) return
        if (event.key != throwKey) return
        val player = mc.player ?: return
        if (player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)) return
        if (findPearlSlot() == -1 && !player.offhandItem.`is`(Items.ENDER_PEARL)) return

        if (mode == ThrowMode.NORMAL) {
            executeThrow(false)
        } else {
            pearlUsed = true
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return

        if (!pearlUsed && ticksExisted > 0) ticksExisted--
        if (pearlUsed || ticksExisted > 0) player.isSprinting = false

        if (!pearlUsed) return

        val hotbarSlot = findPearlHotbarSlot()
        if (hotbarSlot != -1) {
            executeThrow(true)
            pearlUsed = false
        } else {
            if (ticksExisted == 0) {
                ticksExisted++
                return
            }
            executeThrow(true)
            ticksExisted = 2
            pearlUsed = false
        }
    }

    private fun executeThrow(legit: Boolean) {
        val player = mc.player ?: return
        val savedYaw = player.yRot
        val savedPitch = player.xRot

        if (FreeCameraModule.isEnabled()) {
            player.yRot = FreeCameraModule.yaw
            player.xRot = FreeCameraModule.pitch
        }

        try {
            swapAndUsePearl(legit)
        } finally {
            player.yRot = savedYaw
            player.xRot = savedPitch
        }
    }

    private fun swapAndUsePearl(legit: Boolean) {
        val player = mc.player ?: return
        if (player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)) return

        if (player.mainHandItem.`is`(Items.ENDER_PEARL)) {
            if (legit) InventoryUtil.useItemLegit(InteractionHand.MAIN_HAND)
            else InventoryUtil.useItem(InteractionHand.MAIN_HAND)
            return
        }

        if (player.offhandItem.`is`(Items.ENDER_PEARL)) {
            if (legit) InventoryUtil.useItemLegit(InteractionHand.OFF_HAND)
            else InventoryUtil.useItem(InteractionHand.OFF_HAND)
            return
        }

        val hotbarSlot = findPearlHotbarSlot()
        if (hotbarSlot != -1) {
            if (legit) {
                InventoryUtil.useItemLegit(InteractionHand.MAIN_HAND, hotbarSlot)
            } else {
                InventoryUtil.withSilentSlot(hotbarSlot, Runnable {
                    InventoryUtil.useItem(InteractionHand.MAIN_HAND)
                })
            }
            return
        }

        val inventorySlot = findPearlInventorySlot()
        if (inventorySlot == -1) return

        val swapSlot = findUsableHotbarSlot()
        val menuSlot = inventorySlot

        if (InventoryUtil.clickSlot(menuSlot, swapSlot)) {
            try {
                if (legit) {
                    InventoryUtil.useItemLegit(InteractionHand.MAIN_HAND, swapSlot)
                } else {
                    SilentSwapUtil.withSlot(swapSlot) { InventoryUtil.useItem(InteractionHand.MAIN_HAND) }
                }
            } finally {
                InventoryUtil.clickSlot(menuSlot, swapSlot)
                InventoryUtil.closeInventory()
            }
        }
    }

    private fun findPearlHotbarSlot(): Int {
        val player = mc.player ?: return -1
        for (i in 0 until 9) {
            if (player.inventory.getItem(i).`is`(Items.ENDER_PEARL)) return i
        }
        return -1
    }

    private fun findPearlInventorySlot(): Int {
        val player = mc.player ?: return -1
        for (i in 9 until 36) {
            if (player.inventory.getItem(i).`is`(Items.ENDER_PEARL)) return i
        }
        return -1
    }

    private fun findPearlSlot(): Int {
        val hotbar = findPearlHotbarSlot()
        if (hotbar != -1) return hotbar
        return findPearlInventorySlot()
    }

    private fun findUsableHotbarSlot(): Int {
        val player = mc.player ?: return 8
        val current = player.inventory.selectedSlot
        for (i in 0 until 9) {
            if (i != current && player.inventory.getItem(i).isEmpty) return i
        }
        for (i in 0 until 9) {
            if (i != current) return i
        }
        return 8
    }
}
