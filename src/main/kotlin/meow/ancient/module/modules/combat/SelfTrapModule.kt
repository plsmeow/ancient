package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.combat.BlockPlacementController
import meow.ancient.client.util.combat.SurroundGeometry
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import sweetie.evaware.flora.api.Commando
import kotlin.math.ceil

object SelfTrapModule : Module("SelfTrap", COMBAT, "Builds an obsidian roof and head protection") {
    private val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1)
    private val delay by number("Delay", 1.0, 0.0, 10.0, 1.0, 0, unit = "ticks")
    private val rotations by boolean("Rotations", true)
    private val onlyGround by boolean("Only Ground", true)
    private val supports by boolean("Supports", true)
    private val controller = BlockPlacementController(this)
    fun hasPendingAction(): Boolean = isEnabled() && controller.queued
    override fun onDisable() = controller.reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = controller.reset()

    private fun positions(): List<BlockPos> {
        val player = mc.player ?: return emptyList()
        if (onlyGround && !player.onGround()) return emptyList()
        val box = player.boundingBox
        val roofY = ceil(box.maxY).toInt()
        val roof = BlockPos.betweenClosed(AABB(box.minX + 0.001, roofY.toDouble(), box.minZ + 0.001,
            box.maxX - 0.001, roofY + 0.01, box.maxZ - 0.001)).map { it.immutable() }
        val ring = SurroundGeometry.ring(box)
        val head = ring.map { BlockPos(it.x, roofY - 1, it.z) }
        return (roof + head + if (supports) ring else emptyList()).distinct()
            .filter { !AABB(it).intersects(box) }
    }

    @Commando(priority = 115) fun onUpdate(event: PlayerUpdateEvent) {
        controller.update(positions(), Items.OBSIDIAN, range, delay.toInt(), rotations) { it in positions() }
    }
}
