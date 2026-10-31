package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.module.Module
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

object HitBoxesModule : Module(
    name = "HitBoxes",
    category = COMBAT,
    description = ""
) {
    @JvmField
    val size = number("Size", 0.3, 0.1, 1.5, 0.05, 2)

    @JvmStatic
    fun expandAmount(entity: Entity): Double {
        if (!isEnabled()) return 0.0
        if (entity !is Player || entity === mc.player) return 0.0
        if (FriendRepository.isFriend(entity)) return 0.0
        return size.value
    }
}
