package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.module.modules.combat.MaceKillModule
import meow.ancient.module.modules.movement.AirStuckModule
import meow.ancient.module.modules.movement.FlightModule
import meow.ancient.module.modules.movement.GroundSpoofModule
import net.minecraft.tags.FluidTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

object IdealHitUtils {
    fun cooldownIsReached(toSprinting: Boolean): Boolean {
        val player = mc.player ?: return false
        var cooldown = 0f
        if (player.mainHandItem.isEmpty) cooldown = 0.9f
        if (player.mainHandItem.`is`(ItemTags.SWORDS)) cooldown = 0.52f
        if (toSprinting) cooldown -= 0.15f
        return player.firstPersonHandsAndItems().mainHandHeight > cooldown
    }

    fun canCritical(): Boolean {
        val player = mc.player ?: return false
        val stepHeight = player.maxUpStep().toDouble()
        val allowedMovement = player.collide(Vec3(0.0, stepHeight, 0.0))

        if (GroundSpoofModule.isSpoofingAir()) return true

        val jumpHeld = mc.options.keyJump.isDown

        val critImpossible = player.isInLava
            || player.onClimbable()
            || player.isEyeInFluid(FluidTags.WATER)
            || player.hasEffect(MobEffects.LEVITATION)
            || player.hasEffect(MobEffects.SLOW_FALLING)
            || player.hasEffect(MobEffects.BLINDNESS)
            || MoveUtil.isInWeb()
            || player.isFallFlying
            || player.isPassenger
            || player.abilities.flying
            || (allowedMovement.y < stepHeight - 0.5 && player.onGround())
            || (player.deltaMovement.y == -0.005 && player.isEyeInFluid(FluidTags.WATER))
            || AirStuckModule.isEnabled()
            || FlightModule.isEnabled()
            || MaceKillModule.isEnabled()
            || (player.onGround() && !jumpHeld)

        val serverConfirmedFall = !player.onGround()
            && player.fallDistance > 0f
            && player.deltaMovement.y < 0.0

        return critImpossible || serverConfirmedFall
    }
}
