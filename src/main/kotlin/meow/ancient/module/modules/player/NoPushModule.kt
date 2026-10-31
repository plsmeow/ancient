package meow.ancient.module.modules.player

import meow.ancient.client.extensions.PLAYER
import meow.ancient.module.Module

object NoPushModule : Module(
    name = "NoPush",
    category = PLAYER,
    description = "Prevents player from being pushed by blocks and entities"
) {
    val players by boolean("Players", true)
    val blocks by boolean("Blocks", true)

    fun shouldCancelBlockPush(): Boolean = isEnabled() && blocks

    fun shouldCancelEntityPush(): Boolean = isEnabled() && players
}
