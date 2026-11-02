package meow.ancient.module.modules.other

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.OTHER
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.InventoryActionSource
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.render.FreeCameraModule
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

object HwHelperModule : Module(
    name = "HWHelper",
    category = OTHER,
    description = "Helper binds for HolyWorld items"
) {
    val vzrKey by keybind("Взрывная штучка", InputConstants.UNKNOWN.value)
    val stanKey by keybind("Стан", InputConstants.UNKNOWN.value)
    val trapKey by keybind("Взрывная Трапка", InputConstants.UNKNOWN.value)
    val trappKey by keybind("Трапка", InputConstants.UNKNOWN.value)
    val snowKey by keybind("Ком снега", InputConstants.UNKNOWN.value)

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
    fun onKeyPress(event: KeyPressEvent) {
        if (event.cancel || event.action != InputConstants.PRESS || event.key == InputConstants.UNKNOWN.value) return
        val player = mc.player ?: return
        if (currentScreen != null || player.isSpectator || !player.connection.connection.isConnected
            || player.containerMenu !== player.inventoryMenu || !player.inventoryMenu.carried.isEmpty) return
        val item = getItemByKey(event.key) ?: return
        val slot = if (player.mainHandItem.`is`(item) || player.offhandItem.`is`(item)) player.inventory.selectedSlot
            else InventoryUtil.findItem(item)
        if (slot < 0) return
        event.cancel = true
        val generation = actionGeneration
        val connection = player.connection
        InventoryMoveModule.runInventoryAction(InventoryActionSource.fromInventorySlot(slot), Runnable {
            if (!isEnabled() || actionGeneration != generation || mc.player !== player
                || player.connection !== connection || !connection.connection.isConnected || currentScreen != null
                || player.containerMenu !== player.inventoryMenu || !player.inventoryMenu.carried.isEmpty) return@Runnable
            val savedYaw = player.yRot
            val savedPitch = player.xRot
            if (FreeCameraModule.isEnabled()) {
                val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
                player.yRot = FreeCameraModule.getYaw(delta)
                player.xRot = FreeCameraModule.getPitch(delta)
            }
            try {
                InventoryUtil.swapAndUse(item)
            } finally {
                player.yRot = savedYaw
                player.xRot = savedPitch
            }
        }, this)
    }

    private fun getItemByKey(key: Int): Item? {
        if (key == vzrKey) return Items.FIRE_CHARGE
        if (key == stanKey) return Items.NETHER_STAR
        if (key == trapKey) return Items.PRISMARINE_SHARD
        if (key == trappKey) return Items.POPPED_CHORUS_FRUIT
        if (key == snowKey) return Items.SNOWBALL
        return null
    }
}
