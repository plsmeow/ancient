package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.combat.SurroundGeometry
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.floor

object SurroundModule : Module("Surround", COMBAT, "Surrounds your feet with obsidian and repairs broken walls") {
    private val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1)
    private val delay by number("Delay", 0.0, 0.0, 10.0, 1.0, 0)
    private val rotations by boolean("Rotations", true)
    private val silentSlots by boolean("Silent Slots", true)
    private val wallCheck by boolean("WallCheck", true)
    private val onlyGround by boolean("Only Ground", true)
    private val center by boolean("Center", false, "Moves to the center of the current block once on enable")
    private val supports by boolean("Supports", true, "Places support blocks below exposed surround positions")
    private val render by boolean("Render", true)
    private val actions = AuraActionQueue(this)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { 14.0 })
    private data class Site(val pos: BlockPos, val root: BlockPos, val hit: BlockHitResult)
    private val pending = HashMap<BlockPos, Int>()
    private var queued = false
    private var tick = 0
    private var lastPlace = -100
    private var centerRequested = false
    private var playerContext: LocalPlayer? = null
    private var levelContext: ClientLevel? = null

    fun hasPendingAction() = isEnabled() && (queued || pending.isNotEmpty())

    private fun reset() {
        queued = false
        pending.clear()
        tick = 0
        lastPlace = -100
        centerRequested = false
        playerContext = null
        levelContext = null
        actions.reset()
        visual.reset()
        SilentSwapUtil.releasePreparedSlot(this)
    }

    override fun onEnable() { reset(); centerRequested = center }
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando(priority = 120)
    fun onUpdate(event: PlayerUpdateEvent) {
        queued = false
        actions.beginTick()
        visual.beginTick()
        val player = mc.player
        val level = mc.level
        if (player == null || level == null || !player.connection.connection.isConnected) { reset(); return }
        if (playerContext !== player || levelContext !== level) {
            reset()
            playerContext = player
            levelContext = level
            centerRequested = center
            actions.beginTick()
        }
        tick++
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        val timeout = (ping.coerceAtLeast(0) / 50 + 5).coerceIn(5, 80)
        pending.entries.removeIf { level.getBlockState(it.key).`is`(Blocks.OBSIDIAN) || tick - it.value >= timeout }
        if (!ActionCoordinator.canAct(this) || !player.isAlive || player.isSpectator || player.isUsingItem
            || mc.gui.screen() != null || player.containerMenu !== player.inventoryMenu
            || !player.containerMenu.carried.isEmpty || InventoryMoveModule.isInventoryActionSlowed()
            || onlyGround && !player.onGround()) {
            SilentSwapUtil.releasePreparedSlot(this)
            return
        }
        if (centerRequested && player.onGround()) {
            val point = Vec3(floor(player.x) + 0.5, player.y, floor(player.z) + 0.5)
            if (level.noCollision(player, player.boundingBox.move(point.subtract(player.position())))) player.setPos(point)
            centerRequested = false
        }
        if (tick - lastPlace < delay.toInt()) return
        val hand = if (player.offhandItem.`is`(Items.OBSIDIAN)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val slot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else InventoryUtil.findHotbarSlot(Items.OBSIDIAN)
        if (slot !in 0..8) { SilentSwapUtil.releasePreparedSlot(this); return }
        val site = findSite(player, level) ?: run { SilentSwapUtil.releasePreparedSlot(this); return }
        if (silentSlots || hand == InteractionHand.OFF_HAND) {
            if (!SilentSwapUtil.prepareSlot(slot, 2, this)) return
        } else {
            InventoryUtil.swapToSlot(slot)
            if (player.inventory.selectedSlot != slot || SilentSwapUtil.serverSlot != slot) return
        }
        visual.show(AABB(site.pos), AuraTargetRenderer.Kind.BASE)
        queued = true
        val preparedTick = tick
        val aimed = actions.block(site.hit, range, rotations, MoveFixMode.SILENT, exactFace = true, wallCheck = wallCheck) { hit ->
            queued = false
            if (preparedTick != tick || mc.player !== player || mc.level !== level || !ActionCoordinator.canAct(this)
                || player.isUsingItem || mc.gui.screen() != null || player.containerMenu !== player.inventoryMenu
                || !player.containerMenu.carried.isEmpty || InventoryMoveModule.isInventoryActionSlowed()
                || onlyGround && !player.onGround() || SilentSwapUtil.serverSlot != slot
                || !validSite(player, level, site, hit, hand, slot)) return@block
            if (InventoryUtil.useItemOnPacket(hand, hit)) {
                pending[site.pos] = tick
                lastPlace = tick
                visual.pulse()
            }
        }
        if (!aimed) queued = false
    }

    private fun replaceable(player: LocalPlayer, level: ClientLevel, pos: BlockPos): Boolean {
        val gameMode = mc.gameMode ?: return false
        return !PacketMineModule.targetsPosition(pos) && level.worldBorder.isWithinBounds(pos) && level.getBlockState(pos).canBeReplaced()
            && !player.blockActionRestricted(level, pos, gameMode.playerMode)
            && !AABB(pos).intersects(player.boundingBox) && level.getEntities(null, AABB(pos)).none { !it.isRemoved && it.blocksBuilding }
    }

    private fun findSite(player: LocalPlayer, level: ClientLevel): Site? {
        val ring = SurroundGeometry.ring(player.boundingBox).sortedBy { player.eyePosition.distanceToSqr(Vec3.atCenterOf(it)) }
        for (root in ring) {
            if (root in pending || !replaceable(player, level, root)) continue
            val hit = actions.placementHit(root, range, wallCheck, rotations) ?: continue
            return Site(root, root, hit)
        }
        if (supports) for (root in ring) {
            if (root in pending || !replaceable(player, level, root)) continue
            for (depth in 1..2) {
                val pos = root.below(depth)
                if (pos in pending || !replaceable(player, level, pos)) break
                val hit = actions.placementHit(pos, range, wallCheck, rotations) ?: continue
                return Site(pos, root, hit)
            }
        }
        return null
    }

    private fun validSite(player: LocalPlayer, level: ClientLevel, site: Site, hit: BlockHitResult,
                          hand: InteractionHand, slot: Int): Boolean {
        if (site.root !in SurroundGeometry.ring(player.boundingBox) || !level.getBlockState(site.root).canBeReplaced()
            || !replaceable(player, level, site.pos) || hit.blockPos.relative(hit.direction) != site.pos) return false
        val stack = if (hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(slot)
        if (!stack.`is`(Items.OBSIDIAN)) return false
        val context = BlockPlaceContext(player, hand, stack, hit)
        return context.clickedPos == site.pos && context.canPlace()
    }
}
