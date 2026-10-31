package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando
import kotlin.math.floor

object BowBombModule : Module(
    name = "BowBomb",
    category = COMBAT,
    description = "Bow and projectile velocity exploit"
) {
    val rotation = boolean("Rotation", false)
    val modeSetting = mode("Mode", "Maximum", listOf("Normal", "Maximum", "Factorised"))
    val mode by modeSetting
    val factor = number("Factor", 1.0, 1.0, 20.0, 1.0, 0)
    val exploitSetting = mode("Exploit", "Strong", listOf("Strong", "Fast", "Phobos"))
    val exploit by exploitSetting
    val minimize = boolean("Minimize", false)
    val delay = number("Delay", 5.0, 0.0, 10.0, 0.5, 1)

    val selection = multiMode(
        "Selection",
        setOf("Bows", "EPearls", "XP", "Eggs", "SplashPotions", "Snowballs"),
        listOf("Bows", "EPearls", "XP", "Eggs", "SplashPotions", "Snowballs")
    )

    private var lastActionTime = 0L

    @Commando
    fun onPacketSend(event: PacketSendEvent) {
        if (!inGame) return
        val player = mc.player ?: return
        val now = System.currentTimeMillis()
        if (now - lastActionTime < (delay.value * 1000).toLong()) return

        val packet = event.packet

        val bowRelease = packet is ServerboundPlayerActionPacket
            && packet.action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
            && player.useItem.`is`(Items.BOW)
            && selection.isSelected("Bows")

        val interact = packet is ServerboundUseItemPacket
            && packet.hand == InteractionHand.MAIN_HAND
            && ((player.mainHandItem.`is`(Items.ENDER_PEARL) && selection.isSelected("EPearls"))
            || (player.mainHandItem.`is`(Items.EXPERIENCE_BOTTLE) && selection.isSelected("XP"))
            || (player.mainHandItem.`is`(Items.EGG) && selection.isSelected("Eggs"))
            || (player.mainHandItem.`is`(Items.SPLASH_POTION) && selection.isSelected("SplashPotions"))
            || (player.mainHandItem.`is`(Items.SNOWBALL) && selection.isSelected("Snowballs")))

        if (!bowRelease && !interact) return

        player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING))

        val runs = getRuns()
        when (exploit) {
            "Fast" -> {
                for (i in 0 until runs) {
                    spoof(if (minimize.value) player.y else player.y - 1e-10, true)
                    spoof(player.y + 1e-10, false)
                }
            }
            "Strong" -> {
                for (i in 0 until runs) {
                    spoof(player.y + 1e-10, false)
                    spoof(if (minimize.value) player.y else player.y - 1e-10, true)
                }
            }
            "Phobos" -> {
                for (i in 0 until runs) {
                    spoof(player.y + 0.00000000000013, true)
                    spoof(player.y + 0.00000000000027, false)
                }
            }
        }

        lastActionTime = now
    }

    private fun spoof(y: Double, ground: Boolean) {
        val player = mc.player ?: return
        val collision = player.horizontalCollision
        if (rotation.value) {
            player.connection.send(ServerboundMovePlayerPacket.PosRot(player.x, y, player.z, player.yRot, player.xRot, ground, collision))
        } else {
            player.connection.send(ServerboundMovePlayerPacket.Pos(player.x, y, player.z, ground, collision))
        }
        player.connection.send(ServerboundClientTickEndPacket.INSTANCE)
    }

    private fun getRuns(): Int = when (mode) {
        "Normal" -> floor(factor.value).toInt().coerceIn(1, 20)
        "Factorised" -> (10 + factor.value.toInt() - 1).coerceIn(1, 20)
        else -> (30.0f * factor.value.toFloat()).toInt().coerceIn(1, 20)
    }

}
