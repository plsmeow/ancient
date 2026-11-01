package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.module.modules.movement.AirStuckModule
import meow.ancient.module.modules.movement.GroundSpoofModule
import net.minecraft.world.effect.MobEffects

class CriticalProcessor {
    fun isReadyForAttack(onlyCrits: Boolean, smartCrits: Boolean): Boolean {
        if (!inGame) return false
        if (!onlyCrits) return true
        if (GroundSpoofModule.isSpoofingAir()) return true

        val localPlayer = mc.player ?: return false

        if (!canPerformCrit()) {
            return true
        }

        if (smartCrits) {
            val isJumpingOrAirborne = !localPlayer.onGround() || mc.options.keyJump.isDown || MoveUtil.isKeyPressed(mc.options.keyJump)
            if (!isJumpingOrAirborne) {
                return true
            }
        }

        return isFallingForCrit()
    }

    fun isFallingForCrit(): Boolean {
        if (!inGame) return false
        val localPlayer = mc.player ?: return false
        if (GroundSpoofModule.isSpoofingAir()) return true
        return !localPlayer.onGround() && localPlayer.fallDistance > 0.0 && localPlayer.deltaMovement.y < 0.0
    }

    private fun canPerformCrit(): Boolean {
        val localPlayer = mc.player ?: return false
        if (AirStuckModule.isEnabled()) return false
        if (localPlayer.isInWater || localPlayer.isInLava || localPlayer.onClimbable() || localPlayer.isPassenger) {
            return false
        }
        if (localPlayer.abilities.flying || localPlayer.isFallFlying) {
            return false
        }
        if (MoveUtil.isInWeb() || MoveUtil.isAboveWater()) {
            return false
        }
        if (localPlayer.hasEffect(MobEffects.BLINDNESS)
            || localPlayer.hasEffect(MobEffects.LEVITATION)
            || localPlayer.hasEffect(MobEffects.SLOW_FALLING)
        ) {
            return false
        }
        return true
    }
}
