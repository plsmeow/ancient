package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando
import java.util.Random
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object BowBombModule : Module(
    name = "BowBomb",
    category = COMBAT,
    description = "Bow and projectile velocity exploit"
) {
    val rotation = boolean("Rotation", false)
    val modeSetting = mode("Mode", "Maximum", listOf("Normal", "Maximum", "Factorised"))
    val mode by modeSetting
    val factor = number("Factor", 1.0, 1.0, 20.0, 1.0, 0)
    val exploitSetting = mode("Exploit", "Strong", listOf("Strong", "Fast", "Strict", "Phobos", "WB"))
    val exploit by exploitSetting
    val minimize = boolean("Minimize", false)
    val delay = number("Delay", 5.0, 0.0, 10.0, 0.5, 1)

    val selection = multiMode(
        "Selection",
        setOf("Bows", "EPearls", "XP", "Eggs", "SplashPotions", "Snowballs"),
        listOf("Bows", "EPearls", "XP", "Eggs", "SplashPotions", "Snowballs")
    )

    private var lastActionTime = 0L
    private val rnd = Random()

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

        val yawRad = Math.toRadians(player.yRot.toDouble())
        val strictDirection = doubleArrayOf(
            100.0 * -sin(yawRad),
            100.0 * cos(yawRad)
        )

        val runs = getRuns()
        when (exploit) {
            "Fast" -> {
                for (i in 0 until runs) {
                    spoof(player.x, if (minimize.value) player.y else player.y - 1e-10, player.z, true)
                    spoof(player.x, player.y + 1e-10, player.z, false)
                }
            }
            "Strong" -> {
                for (i in 0 until runs) {
                    spoof(player.x, player.y + 1e-10, player.z, false)
                    spoof(player.x, if (minimize.value) player.y else player.y - 1e-10, player.z, true)
                }
            }
            "Phobos" -> {
                for (i in 0 until runs) {
                    spoof(player.x, player.y + 0.00000000000013, player.z, true)
                    spoof(player.x, player.y + 0.00000000000027, player.z, false)
                }
            }
            "Strict" -> {
                for (i in 0 until runs) {
                    if (rnd.nextBoolean()) {
                        spoof(player.x - strictDirection[0], player.y, player.z - strictDirection[1], false)
                    } else {
                        spoof(player.x + strictDirection[0], player.y, player.z + strictDirection[1], true)
                    }
                }
            }
            "WB" -> {
                for (i in 0 until runs) {
                    spoof(player.x + getWorldBorderRnd(), player.y, player.z + getWorldBorderRnd(), false)
                }
            }
        }

        lastActionTime = now
    }

    private fun spoof(x: Double, y: Double, z: Double, ground: Boolean) {
        val player = mc.player ?: return
        if (rotation.value) {
            player.connection.send(ServerboundMovePlayerPacket.PosRot(x, y, z, player.yRot, player.xRot, ground, player.horizontalCollision))
        } else {
            player.connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, ground, player.horizontalCollision))
        }
    }

    private fun getRuns(): Int = when (mode) {
        "Normal" -> floor(factor.value).toInt().coerceAtLeast(1)
        "Factorised" -> (10 + (factor.value.toInt() - 1)).coerceAtLeast(1)
        else -> (30.0f * factor.value.toFloat()).toInt().coerceAtLeast(1)
    }

    private fun getWorldBorderRnd(): Double {
        if (mc.hasSingleplayerServer()) return 1.0
        val n = rnd.nextInt(29000000).toDouble()
        return if (rnd.nextBoolean()) n else -n
    }
}
