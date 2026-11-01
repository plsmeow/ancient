package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import sweetie.evaware.flora.api.Commando

object CriticalsModule : Module(
    name = "Criticals",
    category = COMBAT,
    description = "Packet-level critical hits"
) {
    val modeSetting = mode("Mode", "Grim", listOf("Grim", "Packet", "UpdatedNCP", "Strict"))
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


    fun doCrit() {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!canPacketCrit() && mode != "Grim") return

        if (player.isSprinting) {
            player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
            player.isSprinting = false
        }

        val x = player.x
        val y = player.y
        val z = player.z

        when (mode) {
            "Grim" -> {
                val inWeb = level.getBlockState(player.blockPosition()).`is`(Blocks.COBWEB)
                if (inWeb) {
                    sendPos(x, y - 1.0E-9, z, false)
                }
            }
            "Packet" -> {
                if (canPacketCrit()) {
                    sendPos(x, y + 0.0625, z, false)
                    sendPos(x, y, z, false)
                }
            }
            "UpdatedNCP" -> {
                if (canPacketCrit()) {
                    sendPos(x, y + 0.0000008, z, false)
                    sendPos(x, y, z, false)
                }
            }
            "Strict" -> {
                if (canPacketCrit()) {
                    sendPos(x, y + 0.062600301692775, z, false)
                    sendPos(x, y + 0.07260029960661, z, false)
                    sendPos(x, y, z, false)
                    sendPos(x, y, z, false)
                }
            }
        }

    }

    private fun canPacketCrit(): Boolean {
        val player = mc.player ?: return false
        return player.onGround()
            && !player.abilities.flying
            && !player.hasEffect(MobEffects.LEVITATION)
            && !player.hasEffect(MobEffects.BLINDNESS)
            && !player.isInLava
            && !player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
            && !player.onClimbable()
    }

    private fun sendPos(x: Double, y: Double, z: Double, onGround: Boolean) {
        val player = mc.player ?: return
        player.connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, onGround, player.horizontalCollision))
        player.connection.send(ServerboundClientTickEndPacket.INSTANCE)
    }
}
