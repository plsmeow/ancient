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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

object AutoTotemModule : Module(
    name = "AutoTotem",
    category = COMBAT,
    description = "Automatically equips a totem in the offhand"
) {
    val mode by mode("Mode", "Always", listOf("Always", "Health"))
    val health by number("Health", 6.0, 1.0, 20.0, step = 0.5, precision = 1, unit = "HP", isVisible = { mode == "Health" })
    val healthOnElytra by number("Elytra HP", 12.0, 1.0, 20.0, step = 0.5, precision = 1, unit = "HP", isVisible = { mode == "Health" })
    val crystalsCheck by boolean("Crystals Check", true, isVisible = { mode == "Health" })
    val crystalRange by number("Crystal Range", 8.0, 2.0, 16.0, 0.5, 1, unit = "m", isVisible = { mode == "Health" && crystalsCheck })
    val swapBack by boolean("Swap Back", true, isVisible = { mode == "Health" })
    val delay by number("Delay", 0.0, 0.0, 300.0, step = 10.0, precision = 0, unit = "ms")

    private var swapBackSlot = -1
    private var swapBackItem: Item? = null
    private var popCooldown = 0
    private val timer = TimerUtil()

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        if (popCooldown > 0) popCooldown--
        updateSwap(player)
    }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        val packet = event.packet
        if (packet is ClientboundEntityEventPacket && packet.eventId == 35.toByte()) {
            val player = mc.player ?: return
            val level = mc.level ?: return
            if (packet.getEntity(level) === player) {
                popCooldown = 10
                swapBackSlot = -1
                swapBackItem = null
                timer.reset()
            }
        }
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        swapBackSlot = -1
        swapBackItem = null
        popCooldown = 0
    }

    override fun onDisable() {
        swapBackSlot = -1
        swapBackItem = null
        popCooldown = 0
    }
    private fun condition(player: Player): Boolean {
        if (player.isCreative || player.isSpectator || !player.isAlive) return false
        if (mode == "Always") return true

        val level = mc.level ?: return false
        val currentHealth = player.health + player.absorptionAmount
        val onElytra = player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)

        if (currentHealth <= health) return true
        if (onElytra && currentHealth <= healthOnElytra) return true

        if (crystalsCheck) {
            val rSq = crystalRange * crystalRange
            for (entity in level.entitiesForRendering()) {
                if (entity !is EndCrystal || !entity.isAlive) continue
                if (player.eyePosition.distanceToSqr(entity.boundingBox.center) <= rSq) {
                    return true
                }
            }
        }

        return false
    }

    private fun findTotemSlot(player: Player): Int {
        for (i in 0..8) {
            if (player.inventory.getItem(i).`is`(Items.TOTEM_OF_UNDYING)) return i
        }
        for (i in 9..35) {
            if (player.inventory.getItem(i).`is`(Items.TOTEM_OF_UNDYING)) return i
        }
        return -1
    }

    private fun updateSwap(player: Player) {
        if (player.containerMenu !== player.inventoryMenu) return

        val offhand = player.offhandItem
        val hasTotem = offhand.`is`(Items.TOTEM_OF_UNDYING)
        val needTotem = condition(player)

        if (needTotem) {
            if (hasTotem) return
            if (!timer.isComplete(delay.toLong())) return

            val totemSlot = findTotemSlot(player)
            if (totemSlot == -1) return

            if (swapBack && swapBackSlot == -1 && !offhand.isEmpty) {
                swapBackSlot = totemSlot
                swapBackItem = offhand.item
            }

            InventoryUtil.swapToOffhand(totemSlot, forceGrim = true)
            timer.reset()
        } else if (swapBack && swapBackSlot != -1 && popCooldown == 0) {
            if (!timer.isComplete(delay.toLong())) return

            val slot = swapBackSlot
            val expected = swapBackItem
            if (slot in 0..35 && expected != null && player.inventory.getItem(slot).`is`(expected)) {
                InventoryUtil.swapToOffhand(slot, forceGrim = true)
            }
            swapBackSlot = -1
            swapBackItem = null
            timer.reset()
        }
    }
}
