package meow.ancient.client.util.combat

import meow.ancient.module.modules.movement.FlightModule
import meow.ancient.module.modules.combat.hitaura.AttackTiming
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects

object CritTimings {
    private val blockingEffects = arrayOf(MobEffects.LEVITATION, MobEffects.BLINDNESS, MobEffects.SLOW_FALLING)

    fun isCritBlocked(player: LocalPlayer): Boolean =
        player.isInLiquid
            || player.isFallFlying
            || player.isPassenger
            || player.onClimbable()
            || player.isNoGravity
            || player.isUsingItem
            || player.abilities.flying
            || player.isAutoSpinAttack
            || player.isMobilityRestricted
            || blockingEffects.any(player::hasEffect)
            || FlightModule.isEnabled()

    fun critNow(player: LocalPlayer, sprintWillStop: Boolean): Boolean =
        !isCritBlocked(player) && !player.onGround() && player.fallDistance > 0.0
            && player.deltaMovement.y < 0.0 && AttackTiming.fallingCrit(ServerCritState.serverFallDistance,
            ServerCritState.serverOnGround, ServerCritState.serverSprinting || player.isSprinting, sprintWillStop)

    fun shouldStopSprinting(player: LocalPlayer, willClickNextTick: Boolean): Boolean {
        if (!willClickNextTick || player.isFallFlying) return false
        if (isCritBlocked(player)) return false
        return !player.onGround() && player.deltaMovement.y <= 0.0
    }
}
