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

object FtHelperModule : Module(
    name = "FTHelper",
    category = OTHER,
    description = "Helper binds for FunTime items"
) {
    val snowballKey by keybind("Снежок Заморозка", InputConstants.UNKNOWN.value)
    val godsAuraKey by keybind("Божья Аура", InputConstants.UNKNOWN.value)
    val trapKey by keybind("Трапка", InputConstants.UNKNOWN.value)
    val plastKey by keybind("Пласт", InputConstants.UNKNOWN.value)
    val clearDustKey by keybind("Явная пыль", InputConstants.UNKNOWN.value)
    val fireChargeKey by keybind("Огненный заряд", InputConstants.UNKNOWN.value)
    val disorientationKey by keybind("Дезориентация", InputConstants.UNKNOWN.value)
    val windChargeKey by keybind("Заряд ветра", InputConstants.UNKNOWN.value)

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
        if (key == snowballKey) return Items.SNOWBALL
        if (key == godsAuraKey) return Items.PHANTOM_MEMBRANE
        if (key == trapKey) return Items.NETHERITE_SCRAP
        if (key == plastKey) return Items.DRIED_KELP
        if (key == clearDustKey) return Items.SUGAR
        if (key == fireChargeKey) return Items.FIRE_CHARGE
        if (key == disorientationKey) return Items.ENDER_EYE
        if (key == windChargeKey) return Items.WIND_CHARGE
        return null
    }
}
