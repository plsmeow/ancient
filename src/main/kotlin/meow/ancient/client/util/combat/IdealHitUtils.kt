package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.module.modules.combat.MaceKillModule
import meow.ancient.module.modules.movement.AirStuckModule
import meow.ancient.module.modules.movement.FlightModule
import meow.ancient.module.modules.movement.GroundSpoofModule
import net.minecraft.tags.FluidTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.client.player.LocalPlayer

object IdealHitUtils {
    private const val MIN_CRITICAL_FALL_DISTANCE = 0.05

    fun canAttack(smartCrits: Boolean, sentOnGround: Boolean, sentFallDistance: Double, hasSentMovement: Boolean): Boolean {
        val player = mc.player ?: return false
        if (critImpossible(player)) return true
        if (GroundSpoofModule.isEnabled()) return false
        if (!hasSentMovement) return false
        if (smartCrits && player.onGround() && sentOnGround && !mc.options.keyJump.isDown) return true
        return !sentOnGround && !player.onGround()
            && sentFallDistance >= MIN_CRITICAL_FALL_DISTANCE
            && player.fallDistance >= MIN_CRITICAL_FALL_DISTANCE && player.deltaMovement.y < 0.0
    }

    fun canPrepareAttack(smartCrits: Boolean): Boolean {
        val player = mc.player ?: return false
        if (critImpossible(player)) return true
        if (GroundSpoofModule.isEnabled()) return false
        if (smartCrits && player.onGround() && !mc.options.keyJump.isDown) return true
        if (player.onGround() || player.deltaMovement.y >= 0.0) return false
        return player.fallDistance - player.deltaMovement.y >= MIN_CRITICAL_FALL_DISTANCE
    }

    private fun critImpossible(player: LocalPlayer): Boolean = player.isInLava
        || player.onClimbable()
        || player.isEyeInFluid(FluidTags.WATER)
        || player.hasEffect(MobEffects.LEVITATION)
        || player.hasEffect(MobEffects.SLOW_FALLING)
        || player.hasEffect(MobEffects.BLINDNESS)
        || MoveUtil.isInWeb()
        || player.isFallFlying
        || player.isPassenger
        || player.abilities.flying
        || AirStuckModule.isEnabled()
        || FlightModule.isEnabled()
        || MaceKillModule.isEnabled()
}
