package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.combat.BlockPlacementController
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import sweetie.evaware.flora.api.Commando

object AutoWebModule : Module("AutoWeb", COMBAT, "Places cobwebs at enemy feet and predicted positions") {
    private val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1)
    private val targetRange by number("Target Range", 6.0, 1.0, 12.0, 0.5, 1)
    private val predict by number("Predict Ticks", 1.0, 0.0, 3.0, 0.5, 1)
    private val head by boolean("Head", false)
    private val delay by number("Delay", 1.0, 0.0, 10.0, 1.0, 0, unit = "ticks")
    private val rotations by boolean("Rotations", true)
    private val controller = BlockPlacementController(this)
    fun hasPendingAction(): Boolean = isEnabled() && controller.queued
    override fun onDisable() = controller.reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = controller.reset()

    private fun target(): Player? {
        val player = mc.player ?: return null
        return mc.level?.entitiesForRendering()?.filterIsInstance<Player>()?.filter {
            it !== player && it.isAlive && !it.isSpectator && !it.isCreative
                && !FriendRepository.isFriend(it) && player.distanceToSqr(it) <= targetRange * targetRange
        }?.sortedWith(compareByDescending<Player> { TargetRepository.isTarget(it) }.thenBy { player.distanceToSqr(it) })?.firstOrNull()
    }

    private fun positions(target: Player): List<BlockPos> {
        val player = mc.player ?: return emptyList()
        val velocity = target.deltaMovement
        val offset = velocity.scale(predict).let { if (it.lengthSqr() > 4.0) it.normalize().scale(2.0) else it }
        val boxes = listOf(target.boundingBox.move(offset), target.boundingBox)
        return boxes.flatMap { box ->
            val feet = BlockPos.betweenClosed(AABB(box.minX + 0.001, box.minY, box.minZ + 0.001,
                box.maxX - 0.001, box.minY + 0.01, box.maxZ - 0.001)).map { it.immutable() }
            if (head) feet + feet.map { it.above() } else feet
        }.distinct().filter { !AABB(it).intersects(player.boundingBox) }
    }

    @Commando(priority = 80) fun onUpdate(event: PlayerUpdateEvent) {
        val enemy = target()
        val candidates = enemy?.let(::positions).orEmpty()
        controller.update(candidates, Items.COBWEB, range, delay.toInt(), rotations) { pos ->
            enemy != null && enemy.isAlive && !enemy.isRemoved && !FriendRepository.isFriend(enemy)
                && pos in positions(enemy)
        }
    }
}
