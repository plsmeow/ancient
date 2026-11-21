package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.combat.SurroundGeometry
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object AutoMineModule : Module("AutoMine", COMBAT, "Selects enemy feet and surround blocks for PacketMine") {
    private val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1)
    private val targetRange by number("Target Range", 6.0, 1.0, 12.0, 0.5, 1)
    private val feet by boolean("Feet", true)
    private val surround by boolean("Surround", true)
    private var targetId = -1
    private var world: Any? = null
    private fun reset() { PacketMineModule.releaseAutomaticTargets(this); targetId = -1; world = null }
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando(priority = 122) fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return reset()
        val level = mc.level ?: return reset()
        if (world !== level) { reset(); world = level }
        if (!PacketMineModule.isEnabled()) { reset(); return }
        // Clicking a block always takes ownership; automation does not evict manual targets.
        if (PacketMineModule.hasManualTarget()) { PacketMineModule.releaseAutomaticTargets(this); return }
        val enemy = level.entitiesForRendering().filterIsInstance<Player>().filter {
            it !== player && it.isAlive && !it.isSpectator && !it.isCreative && !FriendRepository.isFriend(it)
                && player.distanceToSqr(it) <= targetRange * targetRange
        }.sortedWith(compareByDescending<Player> { TargetRepository.isTarget(it) }.thenBy { player.distanceToSqr(it) }).firstOrNull()
        if (enemy == null) { reset(); return }
        if (targetId != enemy.id) { PacketMineModule.releaseAutomaticTargets(this); targetId = enemy.id }
        if (!AnticheatManager.canUseContext(player) || !player.containerMenu.carried.isEmpty) return
        val box = enemy.boundingBox
        val footBlocks = if (feet) BlockPos.betweenClosed(AABB(box.minX + 0.001, box.minY + 0.001, box.minZ + 0.001,
            box.maxX - 0.001, box.minY + 0.01, box.maxZ - 0.001)).map { it.immutable() } else emptyList()
        val tactical = (footBlocks + if (surround) SurroundGeometry.ring(box) else emptyList()).distinct()
        var active = PacketMineModule.automaticTargets(this)
        if (active.any { it.primary && it.pos !in tactical }) {
            PacketMineModule.releaseAutomaticTargets(this)
            active = emptyList()
        }
        if (active.any { it.retained } || active.size >= PacketMineModule.automaticCapacity()) return
        val candidates = tactical.filter { pos ->
            val state = level.getBlockState(pos)
            pos !in active.map { it.pos } && !state.isAir && !state.canBeReplaced()
                && state.getDestroySpeed(level, pos) > 0f && level.worldBorder.isWithinBounds(pos)
                && player.isWithinBlockInteractionRange(pos, 0.0)
                && AuraActionQueue.visibleHit(player, pos, range) != null
                && (0..8).any { player.inventory.getItem(it).canDestroyBlock(state, level, pos, player) }
        }.sortedWith(compareByDescending<BlockPos> { it in footBlocks }
            .thenBy { player.eyePosition.distanceToSqr(Vec3.atCenterOf(it)) })
            .take(PacketMineModule.automaticCapacity() - active.size)
        // Start the delayed secondary first and keep the preferred block as newest primary.
        val ordered = if (active.isEmpty()) candidates.asReversed() else candidates
        for (pos in ordered) {
            val hit = AuraActionQueue.visibleHit(player, pos, range) ?: continue
            PacketMineModule.selectAutomaticBlock(this, pos, hit.direction)
        }
    }
}
