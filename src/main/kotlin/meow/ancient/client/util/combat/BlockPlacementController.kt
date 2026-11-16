package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.InventoryAutomation
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.inventory.BlockPredictionUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import meow.ancient.client.util.render.AuraTargetRenderer

/** Shared by ordinary placements; uses the exact same acknowledged ray/face pipeline as CA. */
class BlockPlacementController(private val owner: Module) {
    private data class Site(val pos: BlockPos, val hit: BlockHitResult, val item: Item)
    private val actions = AuraActionQueue(owner)
    private val visual = AuraTargetRenderer({ owner.isEnabled() }, { 14.0 })
    private val pending = HashMap<BlockPos, Int>()
    private var world: Any? = null
    private var playerContext: Any? = null
    private var tick = 0
    private var lastPlace = -100
    private var aiming: Site? = null
    var queued = false
        private set

    fun placeBeforePhysics() { actions.placeBeforePhysics() }

    fun reset() {
        pending.clear(); actions.reset(); visual.reset(); queued = false
        world = null; playerContext = null; tick = 0; lastPlace = -100
        aiming = null
        PlacementReservations.release(owner)
        SilentSwapUtil.releasePreparedSlot(owner)
    }

    fun update(positions: List<BlockPos>, item: Item, range: Double, delay: Int,
               rotations: Boolean, retainTarget: Boolean = false, validPosition: (BlockPos) -> Boolean) {
        queued = false
        val player = mc.player ?: return reset()
        val level = mc.level ?: return reset()
        if (world !== level || playerContext !== player) {
            reset(); world = level; playerContext = player
        }
        tick++
        actions.beginTick(); visual.beginTick()
        val timeout = ((player.connection.getPlayerInfo(player.uuid)?.latency ?: 150).coerceAtLeast(0) / 50 + 5).coerceIn(5, 80)
        pending.entries.removeIf { !BlockPredictionUtil.serverState(level, it.key).canBeReplaced() || tick - it.value >= timeout }
        if (!player.isAlive || player.isSpectator || player.isUsingItem || mc.gui.screen() != null
            || player.containerMenu !== player.inventoryMenu || !player.containerMenu.carried.isEmpty
            || InventoryAutomation.busy() || InventoryMoveModule.isInventoryActionSlowed() || tick - lastPlace < delay) {
            SilentSwapUtil.releasePreparedSlot(owner); return
        }
        val hand = if (player.offhandItem.`is`(item)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val slot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else InventoryUtil.findHotbarSlot(item)
        if (slot !in 0..8) return
        fun available(pos: BlockPos): Boolean = pos !in pending && validPosition(pos)
            && !PacketMineModule.targetsPosition(pos) && !PlacementReservations.reserved(pos, owner)
            && level.worldBorder.isWithinBounds(pos) && !level.isOutsideBuildHeight(pos)
            && level.getBlockState(pos).canBeReplaced()
            && mc.gameMode?.let { !player.blockActionRestricted(level, pos, it.playerMode) } == true
        val retained = aiming?.takeIf { retainTarget && it.item === item && available(it.pos)
            && validPlacement(it.pos, it.hit, hand, slot, item) }
        val site = retained ?: positions.firstNotNullOfOrNull { candidate ->
            if (!available(candidate)) return@firstNotNullOfOrNull null
            val hit = actions.placementHit(candidate, range, rotations = rotations) ?: return@firstNotNullOfOrNull null
            if (!validPlacement(candidate, hit, hand, slot, item)) return@firstNotNullOfOrNull null
            Site(candidate.immutable(), hit, item)
        } ?: run { aiming = null; SilentSwapUtil.releasePreparedSlot(owner); return }
        aiming = site.takeIf { retainTarget }
        val pos = site.pos
        val hit = site.hit
        queued = true
        if (!ActionCoordinator.canAct(owner) || !SilentSwapUtil.prepareSlot(slot, 2, owner)) return
        visual.show(AABB(pos), AuraTargetRenderer.Kind.PLACE)
        val preparedTick = tick
        if (!actions.block(hit, range, rotations, MoveFixMode.SILENT, exactFace = true) { actual ->
                queued = false
                if (preparedTick != tick || mc.player !== player || mc.level !== level || !owner.isEnabled()
                    || !ActionCoordinator.canAct(owner) || player.isUsingItem || mc.gui.screen() != null
                    || player.containerMenu !== player.inventoryMenu || !player.containerMenu.carried.isEmpty
                    || InventoryAutomation.busy() || InventoryMoveModule.isInventoryActionSlowed() || SilentSwapUtil.serverSlot != slot
                    || !available(pos) || !validPlacement(pos, actual, hand, slot, item)) return@block
                if (InventoryUtil.useItemOn(hand, actual)) {
                    aiming = null
                    pending[pos] = tick; lastPlace = tick
                    PlacementReservations.claim(pos, owner, timeout)
                    visual.pulse()
                }
            }) {
            queued = false
            aiming = null
        }
    }

    private fun validPlacement(pos: BlockPos, hit: BlockHitResult, hand: InteractionHand, slot: Int, item: Item): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val stack = if (hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(slot)
        if (!stack.`is`(item) || hit.blockPos.relative(hit.direction) != pos) return false
        val block = (item as? BlockItem)?.block ?: return false
        val context = BlockPlaceContext(player, hand, stack, hit)
        if (context.clickedPos != pos || !context.canPlace()) return false
        val state = block.getStateForPlacement(context) ?: return false
        if (!state.canSurvive(level, pos)) return false
        // Cobweb has an empty collision shape: players inside it are valid placement targets.
        val boxes = state.getCollisionShape(level, pos).toAabbs().map { it.move(pos) }
        return boxes.none { box -> level.getEntities(null, box).any { !it.isRemoved && it.blocksBuilding } }
    }
}

object PlacementReservations {
    private data class Reservation(val owner: Any, val expires: Long)
    private var world: Any? = null
    private val entries = HashMap<BlockPos, Reservation>()
    private fun clean() {
        if (world !== mc.level) { entries.clear(); world = mc.level }
        val now = System.nanoTime()
        entries.entries.removeIf { now >= it.value.expires }
    }
    fun reserved(pos: BlockPos, owner: Any): Boolean { clean(); return entries[pos]?.owner?.let { it !== owner } == true }
    fun claim(pos: BlockPos, owner: Any, ticks: Int) { clean(); entries[pos.immutable()] = Reservation(owner, System.nanoTime() + ticks * 50_000_000L) }
    fun release(owner: Any) { entries.entries.removeIf { it.value.owner === owner } }
}
