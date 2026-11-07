package meow.ancient.client.util.combat

import meow.ancient.mixin.LivingEntityAccessor
import meow.ancient.module.modules.movement.FlightModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import kotlin.math.ceil

object CritTimings {
    private val blockingEffects = arrayOf(MobEffects.LEVITATION, MobEffects.BLINDNESS, MobEffects.SLOW_FALLING)

    private const val STRENGTH_SCALE = 0.9f
    private const val GRAVITY = 0.08
    private const val JUMP_POWER = LivingEntity.BASE_JUMP_POWER.toDouble()
    private const val LANDING_MARGIN_TICKS = 4

    fun isCritBlocked(player: LocalPlayer): Boolean =
        player.isInLiquid
            || player.isPassenger
            || player.onClimbable()
            || player.isNoGravity
            || player.isUsingItem
            || player.abilities.flying
            || player.isAutoSpinAttack
            || player.isMobilityRestricted
            || blockingEffects.any(player::hasEffect)
            || FlightModule.isEnabled()

    private fun strengthReady(player: LocalPlayer): Boolean =
        player.getAttackStrengthScale(0.5f) >= STRENGTH_SCALE

    private fun ticksUntilStrengthReady(player: LocalPlayer): Int {
        val required = player.getCurrentItemAttackStrengthDelay() * STRENGTH_SCALE
        val waited = (player as LivingEntityAccessor).`ancient$getAttackStrengthTicker`().toFloat() + 0.5f
        val remaining = required - waited
        return if (remaining <= 0.0f) 0 else ceil(remaining.toDouble()).toInt()
    }

    fun critNow(player: LocalPlayer, sprintWillStop: Boolean): Boolean =
        !player.isFallFlying
            && strengthReady(player)
            && !isCritBlocked(player)
            && ServerCritState.serverFallDistance > 0.0
            && !ServerCritState.serverOnGround
            && !ServerCritState.serverSprinting
            && !player.isSprinting

    fun critReachable(player: LocalPlayer): Boolean {
        if (player.isFallFlying) return false
        if (isCritBlocked(player)) return false

        val ticksToReady = ticksUntilStrengthReady(player)
        val ticksToAirborne = ticksUntilDescending(player)
        if (ticksToAirborne < 0) return false

        val horizon = maxOf(ticksToReady, ticksToAirborne) + LANDING_MARGIN_TICKS
        return descentWindowExists(player, horizon, ticksToAirborne)
    }

    private fun ticksUntilDescending(player: LocalPlayer): Int {
        if (!player.onGround()) {
            val motionY = player.deltaMovement.y
            if (motionY <= 0.0) return 0
            return ceil(motionY / GRAVITY).toInt()
        }
        if (!player.input.keyPresses.jump()) return -1
        return ceil(JUMP_POWER / GRAVITY).toInt()
    }

    private fun descentWindowExists(player: LocalPlayer, horizon: Int, ticksToAirborne: Int): Boolean {
        val simulation = if (player.onGround()) {
            FallingPlayer(
                player, player.x, player.y, player.z,
                player.deltaMovement.x, player.deltaMovement.y + JUMP_POWER, player.deltaMovement.z,
                player.yRot
            )
        } else {
            FallingPlayer.fromPlayer(player)
        }
        val collision = simulation.findCollision(horizon) ?: return true
        return collision.tick > ticksToAirborne
    }

    fun shouldStopSprinting(player: LocalPlayer, willClickNextTick: Boolean): Boolean {
        if (!willClickNextTick || player.isFallFlying) return false
        if (isCritBlocked(player)) return false
        return !player.onGround() && player.deltaMovement.y < 0.0 && ServerCritState.serverFallDistance > 0.0
    }
}
