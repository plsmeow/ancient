package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.module.modules.movement.AirStuckModule
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import sweetie.evaware.flora.api.Commando

object CriticalsModule : Module(
    name = "Criticals",
    category = COMBAT,
    description = "Packet-level critical hits"
) {
    val modeSetting = mode("Mode", "Grim", listOf("Grim", "Packet", "UpdatedNCP", "Strict"),
        description = "Grim prepares attacks only in webs or while descending with Slow Falling")
    val mode by modeSetting

    @JvmField
    var killAuraTriggered = false

    @Commando
    fun onPacketSend(event: PacketSendEvent) {
        if (!inGame || !isEnabled() || killAuraTriggered) return
        val packet = event.packet
        if (packet is ServerboundAttackPacket) {
            val level = mc.level ?: return
            val target = level.getEntity(packet.entityId())
            if (target != null && target !is LivingEntity) return
            doCrit()
        }
    }


    fun doCrit(): Boolean {
        if (!canPrepareCrit()) return false
        val player = mc.player ?: return false

        if (player.isSprinting) {
            player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
            player.isSprinting = false
        }

        val x = player.x
        val y = player.y
        val z = player.z

        when (mode) {
            "Grim" -> sendPos(x, y - 1.0E-9, z, false)
            "Packet" -> {
                sendPos(x, y + 0.0625, z, false)
                sendPos(x, y, z, false)
            }
            "UpdatedNCP" -> {
                sendPos(x, y + 0.0000008, z, false)
                sendPos(x, y, z, false)
            }
            "Strict" -> {
                sendPos(x, y + 0.062600301692775, z, false)
                sendPos(x, y + 0.07260029960661, z, false)
                sendPos(x, y, z, false)
                sendPos(x, y, z, false)
            }
        }
        return true

    }

    fun canPrepareCrit(): Boolean {
        if (!isEnabled() || !inGame || AirStuckModule.isEnabled()) return false
        val player = mc.player ?: return false
        if (!player.isAlive || player.isPassenger || player.abilities.flying || player.isFallFlying
            || player.isInWater || player.isInLava || player.onClimbable()
            || player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.BLINDNESS)) return false
        return when (mode) {
            "Grim" -> MoveUtil.isInWeb() || (player.hasEffect(MobEffects.SLOW_FALLING)
                && !player.onGround() && player.deltaMovement.y < 0.0)
            "Packet", "UpdatedNCP", "Strict" -> player.onGround()
                && !player.hasEffect(MobEffects.SLOW_FALLING) && !MoveUtil.isInWeb()
            else -> false
        }
    }

    private fun sendPos(x: Double, y: Double, z: Double, onGround: Boolean) {
        val player = mc.player ?: return
        player.connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, onGround, player.horizontalCollision))
        player.connection.send(ServerboundClientTickEndPacket.INSTANCE)
    }
}
