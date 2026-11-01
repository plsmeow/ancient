package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AntiBotModule : Module(
    name = "AntiBot",
    category = COMBAT,
    description = "Detects server bots and excludes them from targets"
) {
    val mode by mode("Mode", "Normal", listOf("Normal", "Test"))
    val ignoreNpc by boolean("Ignore NPC", true)

    private val bots: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val level = mc.level ?: return
        val player = mc.player ?: return

        for (entity in level.players()) {
            if (entity === player) continue

            var isBot = false

            when (mode) {
                "Normal" -> {
                    val boots = entity.getItemBySlot(EquipmentSlot.FEET)
                    val leggings = entity.getItemBySlot(EquipmentSlot.LEGS)
                    val chest = entity.getItemBySlot(EquipmentSlot.CHEST)
                    val helmet = entity.getItemBySlot(EquipmentSlot.HEAD)
                    val armor = listOf(boots, leggings, chest, helmet)

                    val hasMatchingItem = boots.`is`(Items.LEATHER_BOOTS)
                        || leggings.`is`(Items.LEATHER_LEGGINGS)
                        || chest.`is`(Items.LEATHER_CHESTPLATE)
                        || helmet.`is`(Items.LEATHER_HELMET)
                        || boots.`is`(Items.IRON_BOOTS)
                        || leggings.`is`(Items.IRON_LEGGINGS)
                        || chest.`is`(Items.IRON_CHESTPLATE)
                        || helmet.`is`(Items.IRON_HELMET)

                    for (armorPiece in armor) {
                        if (!armorPiece.isEmpty
                            && armorPiece.isEnchantable
                            && !armorPiece.isDamaged
                            && hasMatchingItem
                            && !entity.mainHandItem.isEmpty
                            && entity.offhandItem.isEmpty
                            && entity.foodData.foodLevel == 20
                        ) {
                            isBot = true
                            break
                        }
                    }
                }
                "Test" -> {
                    val connection = mc.connection
                    if (connection != null) {
                        val playerEntry = connection.getPlayerInfo(entity.uuid)
                        if (playerEntry == null) {
                            isBot = true
                        }
                    }
                }
            }

            if (isBot) {
                bots.add(entity.uuid)
            } else {
                bots.remove(entity.uuid)
            }
        }
    }

    override fun onDisable() {
        bots.clear()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        bots.clear()
    }

    fun isBot(player: Player): Boolean {
        if (!isEnabled()) return false
        if (ignoreNpc) {
            val name = player.name.string
            if (name.startsWith("CIT-")) return true
        }
        return bots.contains(player.uuid)
    }

    fun isBot(entity: Entity): Boolean {
        if (entity !is Player) return false
        return isBot(entity)
    }
}
