package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.module.modules.movement.AirStuckModule
import meow.ancient.module.modules.movement.GroundSpoofModule
import meow.ancient.module.modules.combat.CriticalsModule
import net.minecraft.world.effect.MobEffects

class CriticalProcessor {
    fun isReadyForAttack(onlyCrits: Boolean, smartCrits: Boolean): Boolean {
        if (!inGame) return false
        if (!onlyCrits) return true
        val player = mc.player ?: return false
        if (isGrimPacketReady()) return true
        if (GroundSpoofModule.isSpoofingAir()) return true
        if (!canPerformCrit()) return true
        if (smartCrits && player.onGround()) return true
        return isFallingForCrit()
    }

    fun isFallingForCrit(): Boolean {
        if (!inGame) return false
        val player = mc.player ?: return false
        if (GroundSpoofModule.isSpoofingAir()) return true
        return !player.onGround() && player.fallDistance > 0.0 && player.deltaMovement.y < 0.0
    }

    fun isGrimPacketReady(): Boolean {
        if (!inGame) return false
        if (!CriticalsModule.isEnabled()) return false
        if (CriticalsModule.mode != "Grim") return false
        if (AirStuckModule.isEnabled()) return false
        val player = mc.player ?: return false
        if (!player.isAlive || player.isPassenger || player.abilities.flying || player.isFallFlying) return false
        if (player.isInWater || player.isInLava || player.onClimbable()) return false
        if (player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.BLINDNESS)) return false
        return MoveUtil.isInWeb()
            || (player.hasEffect(MobEffects.SLOW_FALLING) && !player.onGround() && player.deltaMovement.y < 0.0)
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
        if (MoveUtil.isInWeb()) {
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
