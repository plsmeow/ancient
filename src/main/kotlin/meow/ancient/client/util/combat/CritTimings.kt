package meow.ancient.client.util.combat

import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.mixin.LivingEntityAccessor
import meow.ancient.module.modules.movement.FlightModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

object CritTimings {
    enum class SelectionMode { IGNORE, SMART, ALWAYS }

    private val blockingEffects = arrayOf(MobEffects.LEVITATION, MobEffects.BLINDNESS, MobEffects.SLOW_FALLING)

    fun allowsCriticalHit(player: LocalPlayer, ignoreOnGround: Boolean = false): Boolean {
        val blockingConditions = booleanArrayOf(
            FlightModule.isEnabled(),
            player.isInLiquid,
            player.isPassenger,
            MoveUtil.isInWeb(),
            blockingEffects.any(player::hasEffect),
            player.onClimbable(),
            player.isNoGravity,
            player.isUsingItem,
            player.abilities.flying,
            player.onGround() && !ignoreOnGround
        )
        return blockingConditions.none { it }
    }

    fun canDoCriticalHit(player: LocalPlayer, ignoreOnGround: Boolean = false, ignoreSprint: Boolean = false): Boolean =
        allowsCriticalHit(player, ignoreOnGround)
            && player.getAttackStrengthScale(0.5f) > 0.9f
            && (!player.isSprinting || ignoreSprint)

    fun wouldDoCriticalHit(player: LocalPlayer, ignoreSprint: Boolean = false): Boolean =
        canDoCriticalHit(player, false, ignoreSprint) && player.fallDistance > 0.0

    fun isCriticalHit(player: LocalPlayer, mode: SelectionMode): Boolean = when (mode) {
        SelectionMode.IGNORE -> true
        SelectionMode.SMART -> !shouldWaitForCrit(player, ignoreState = true)
        SelectionMode.ALWAYS -> wouldDoCriticalHit(player)
    }

    fun shouldStopSprinting(player: LocalPlayer, mode: SelectionMode, hasTarget: Boolean, willClickNextTick: Boolean): Boolean {
        if (mode == SelectionMode.IGNORE) return false
        if (player.onGround()) return false
        return hasTarget && willClickNextTick
    }

    fun shouldWaitForCrit(player: LocalPlayer, ignoreState: Boolean = false): Boolean {
        if (player.isFallFlying) return false
        if (!allowsCriticalHit(player, ignoreOnGround = true)) return false

        val onGround = player.onGround()
        val isJumping = player.input.keyPresses.jump

        if (onGround && !isJumping) return false

        val nextPossibleCrit = calculateTicksUntilNextCrit(player)

        if (!onGround && player.deltaMovement.y <= 0.0) {
            if (player.fallDistance > 0.0 && player.getAttackStrengthScale(0.5f) > 0.9f) return false
            val collision = FallingPlayer.fromPlayer(player).findCollision((nextPossibleCrit + 1.0f).toInt())
            return collision == null || collision.tick >= nextPossibleCrit.toInt()
        }

        val initialMotionY = if (onGround) LivingEntity.BASE_JUMP_POWER.toDouble() else player.deltaMovement.y
        val gravity = 0.08
        val ticksTillFall = (initialMotionY / gravity).toFloat()
        val ticksTillCrit = maxOf(nextPossibleCrit, ticksTillFall)

        val simulatedFallingPlayer = if (onGround) {
            FallingPlayer(
                player,
                player.x,
                player.y,
                player.z,
                player.deltaMovement.x,
                player.deltaMovement.y + initialMotionY,
                player.deltaMovement.z,
                player.yRot
            )
        } else {
            FallingPlayer.fromPlayer(player)
        }

        val collision = simulatedFallingPlayer.findCollision((ticksTillCrit + 5.0f).toInt())
        if (collision != null && collision.tick < ticksTillFall.toInt()) return false

        return true
    }

    fun calculateTicksUntilNextCrit(player: LocalPlayer): Float {
        val durationToWait = player.getCurrentItemAttackStrengthDelay() * 0.9f - 0.5f
        val waitedDuration = (player as LivingEntityAccessor).`ancient$getAttackStrengthTicker`().toFloat()
        return (durationToWait - waitedDuration).coerceAtLeast(0.0f)
    }
}
