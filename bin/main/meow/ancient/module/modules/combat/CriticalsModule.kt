package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.AirStuckModule
import net.minecraft.world.effect.MobEffects

object CriticalsModule : Module(
    name = "Criticals",
    category = COMBAT,
    description = "Packet-level critical hits"
) {
    val modeSetting = mode("Mode", "Grim", listOf("Grim", "Packet", "UpdatedNCP", "Strict"),
        description = "Grim prepares crits in webs or descending with Slow Falling; other modes prepare grounded crits")
    val mode by modeSetting

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
}
