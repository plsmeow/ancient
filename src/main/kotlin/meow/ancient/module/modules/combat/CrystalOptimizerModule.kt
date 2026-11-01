package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.module.Module
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal

object CrystalOptimizerModule : Module(
    name = "CrystalOptimizer",
    category = COMBAT,
    description = "Optimizes crystal explosions by instantly removing broken crystals on client"
) {
    val fastBreak by boolean("Fast Break", true, "Immediately removes crystals on attack to allow instant placement")

    fun onCrystalAttacked(crystal: Entity) {
        if (!isEnabled() || !inGame || !fastBreak) return
        if (crystal is EndCrystal) {
            crystal.discard()
            mc.level?.removeEntity(crystal.id, Entity.RemovalReason.DISCARDED)
        }
    }
}
