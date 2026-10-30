package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object ElytraJumpModule : Module(
    name = "Elytra Jump",
    category = MOVEMENT,
    description = "Flies upwards continuously with elytra"
) {
    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return

        val chestItem = player.getItemBySlot(EquipmentSlot.CHEST)
        if (!chestItem.`is`(Items.ELYTRA)) return

        if (!player.abilities.flying && player.onGround() && !player.isInWater && !player.isInLava) {
            player.jumpFromGround()
        }

        if (!player.abilities.flying && !player.onGround() && !player.isInWater && !player.isFallFlying) {
            player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING))
            player.startFallFlying()
        }

        val vel = player.deltaMovement
        player.deltaMovement = Vec3(0.0, vel.y, 0.0)

        if (player.isFallFlying) {
            player.deltaMovement = Vec3(player.deltaMovement.x, player.deltaMovement.y + 0.06, player.deltaMovement.z)
        }

        val target = HitAuraModule.currentTarget
        if (target != null && player.isFallFlying) {
            val targetYaw = target.yRot
            player.yRot = targetYaw
            player.xRot = 0.0f
            player.yHeadRot = targetYaw
            player.yBodyRot = targetYaw
        }
    }
}
