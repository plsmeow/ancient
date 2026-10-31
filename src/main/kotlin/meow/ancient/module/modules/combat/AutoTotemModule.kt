package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.other.TimerUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

object AutoTotemModule : Module(
    name = "AutoTotem",
    category = COMBAT,
    description = "Automatically equips a totem in the offhand"
) {
    val health by number("Health", 4.0, 1.0, 20.0, step = 0.5, precision = 1, unit = "HP")
    val healthOnElytra by number("Health on Elytra", 11.0, 1.0, 20.0, step = 0.5, precision = 1, unit = "HP")
    val crystalsCheck by boolean("Crystals Check", false)

    private var swapBackItem = -1
    private var cooldownTicks = 0
    private val totemTimer = TimerUtil()

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return

        if (cooldownTicks > 0) {
            cooldownTicks--
        }

        updateSwap()
    }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        val packet = event.packet
        if (packet is ClientboundEntityEventPacket && packet.eventId == 35.toByte()) {
            val player = mc.player ?: return
            val level = mc.level ?: return
            if (packet.getEntity(level) === player) {
                cooldownTicks = 5
            }
        }
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        swapBackItem = -1
        cooldownTicks = 0
    }

    override fun onDisable() {
        swapBackItem = -1
        cooldownTicks = 0
    }

    private fun condition(): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false

        if (player.isCreative || player.isSpectator) {
            return false
        }

        var crystalNearby = false
        if (crystalsCheck) {
            for (entity in level.entitiesForRendering()) {
                if (entity !is EndCrystal) continue
                if (player.y >= entity.y && player.eyePosition.distanceTo(entity.boundingBox.center) <= 8.0) {
                    crystalNearby = true
                    break
                }
            }
        }

        val totalHealth = player.health + player.absorptionAmount
        val onElytra = player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)

        return totalHealth <= health
            || (onElytra && totalHealth <= healthOnElytra)
            || (crystalsCheck && crystalNearby)
    }

    private fun updateSwap() {
        val player = mc.player ?: return
        if (player.containerMenu !== player.inventoryMenu) return

        val offhand = player.offhandItem
        val totemSlot = InventoryUtil.findItem(Items.TOTEM_OF_UNDYING)

        if (condition()
            && totemTimer.isComplete(300)
            && totemSlot != -1
            && !offhand.`is`(Items.TOTEM_OF_UNDYING)
        ) {
            if (swapBackItem == -1 && !offhand.isEmpty) {
                swapBackItem = totemSlot
            }

            InventoryUtil.swapToOffhand(totemSlot)
            totemTimer.reset()
        }

        if (!condition() && swapBackItem != -1 && cooldownTicks == 0) {
            val backSlot = swapBackItem
            InventoryUtil.swapToOffhand(backSlot)
            swapBackItem = -1
        }
    }
}
