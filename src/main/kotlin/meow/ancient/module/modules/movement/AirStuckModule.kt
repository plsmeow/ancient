package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.ChestEquipmentSwap
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.event.events.PlayerTickEndEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object AirStuckModule : Module(
    name = "AirStuck",
    category = MOVEMENT,
    description = "Stops movement in mid-air without sending movement packets"
) {
    val autoSwapChest by boolean("Auto Swap Chest", true)
    val backElytra by boolean("Back Elytra", true, isVisible = { autoSwapChest })
    val fallCheck by boolean("Fall Check", true)

    private val chestSwap = ChestEquipmentSwap()
    private var frozenPlayer: LocalPlayer? = null
    private var frozenConnection: ClientPacketListener? = null
    private var savedVelocity = Vec3.ZERO
    private var savedNoGravity = false
    private var lockX = 0.0
    private var lockZ = 0.0
    private var swappedChest = false
    private var chestSwapPending = false

    override fun onEnable() {
        val player = mc.player
        if (player == null || mc.level == null || !player.connection.connection.isConnected) {
            setEnabled(false)
            return
        }
        if (fallCheck && player.fallDistance <= 0.0) {
            ChatUtil.send("You need to be falling to enable AirStuck")
            setEnabled(false)
            return
        }

        frozenPlayer = player
        frozenConnection = player.connection
        savedVelocity = player.deltaMovement
        savedNoGravity = player.isNoGravity
        lockX = player.x
        lockZ = player.z
        player.setNoGravity(true)
        player.deltaMovement = Vec3.ZERO
        chestSwapPending = autoSwapChest && player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)
        tryEquipChest(player)
    }

    override fun onDisable() {
        release(restoreElytra = autoSwapChest && backElytra && swappedChest)
    }

    @Commando
    fun onPacket(event: PacketSendEvent) {
        if (event.packet !is ServerboundMovePlayerPacket) return
        if (activePlayer() == null) return
        event.cancel = true
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = activePlayer() ?: return
        tryEquipChest(player)
        player.deltaMovement = Vec3.ZERO
        player.setNoGravity(true)
    }

    @Commando
    fun onTickEnd(event: PlayerTickEndEvent) {
        val player = activePlayer() ?: return
        player.deltaMovement = Vec3.ZERO
        player.setPos(lockX, player.y, lockZ)
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        release(restoreElytra = false)
        setEnabled(false)
    }

    private fun activePlayer(): LocalPlayer? {
        val player = frozenPlayer
        if (player == null || mc.player !== player || mc.level == null
            || player.connection !== frozenConnection || !player.connection.connection.isConnected) {
            release(restoreElytra = false)
            setEnabled(false)
            return null
        }
        return player
    }

    private fun tryEquipChest(player: LocalPlayer) {
        if (!chestSwapPending) return
        if (!autoSwapChest || !player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)) {
            chestSwapPending = false
            return
        }
        if (chestSwap.equipChestplate()) {
            swappedChest = true
            chestSwapPending = false
        }
    }

    private fun release(restoreElytra: Boolean) {
        val player = frozenPlayer
        val connection = frozenConnection
        frozenPlayer = null
        frozenConnection = null
        if (player != null) {
            player.deltaMovement = savedVelocity
            player.setNoGravity(savedNoGravity)
        }
        if (swappedChest) {
            chestSwap.reset(restore = restoreElytra && player != null && mc.player === player
                && player.connection === connection && mc.level != null && player.connection.connection.isConnected)
        }
        savedVelocity = Vec3.ZERO
        savedNoGravity = false
        lockX = 0.0
        lockZ = 0.0
        swappedChest = false
        chestSwapPending = false
    }
}
