package meow.ancient.module.modules.player

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryActionSource
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.modules.render.FreeCameraModule
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
    private var actionGeneration = 0L

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        reset()
    }

    private fun reset() {
        pearlUsed = false
        ticksExisted = 0
        actionGeneration++
        InventoryMoveModule.cancelInventoryActions(this)
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando
    fun onKey(event: KeyPressEvent) {
        if (event.cancel || event.action != InputConstants.PRESS || event.key == InputConstants.UNKNOWN.value) return
        if (event.key != throwKey) return
        val player = mc.player ?: return
        if (currentScreen != null || !player.isAlive || player.isSpectator || !player.connection.connection.isConnected
            || player.containerMenu !== player.inventoryMenu || !player.inventoryMenu.carried.isEmpty) return
        if (player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)) return
        if (InventoryUtil.findItem(Items.ENDER_PEARL) == -1 && !player.offhandItem.`is`(Items.ENDER_PEARL)) return
        event.cancel = true

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

        val hotbarSlot = InventoryUtil.findHotbarSlot(Items.ENDER_PEARL)
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
        val world = mc.level ?: return
        if (player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)) return
        val slot = if (player.mainHandItem.`is`(Items.ENDER_PEARL) || player.offhandItem.`is`(Items.ENDER_PEARL))
            player.inventory.selectedSlot else InventoryUtil.findItem(Items.ENDER_PEARL)
        if (slot < 0) return
        val generation = actionGeneration
        val connection = player.connection
        // Queue the whole swap/use/restore transaction, as FTHelper does for WindCharge.
        InventoryMoveModule.runInventoryAction(InventoryActionSource.fromInventorySlot(slot), Runnable {
            if (!isEnabled() || actionGeneration != generation || mc.player !== player || mc.level !== world
                || player.connection !== connection || !connection.connection.isConnected || currentScreen != null
                || !player.isAlive || player.isSpectator || player.containerMenu !== player.inventoryMenu
                || !player.inventoryMenu.carried.isEmpty) return@Runnable
            val savedYaw = player.yRot
            val savedPitch = player.xRot
            if (FreeCameraModule.isEnabled()) {
                val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
                player.yRot = FreeCameraModule.getYaw(delta)
                player.xRot = FreeCameraModule.getPitch(delta)
            }
            try {
                InventoryUtil.swapAndUse(Items.ENDER_PEARL, legit)
            } finally {
                player.yRot = savedYaw
                player.xRot = savedPitch
            }
        }, this)
    }
}
