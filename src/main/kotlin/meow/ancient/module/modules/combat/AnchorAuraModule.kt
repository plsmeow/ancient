package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.combat.ExplosionDamageUtil
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.client.util.inventory.InventoryActionSource
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object AnchorAuraModule : Module(
    name = "AnchorAura",
    category = COMBAT,
    description = "Automatically places, charges, and detonates respawn anchors against opponents"
) {
    val targetRange by number("Target Range", 10.0, 3.0, 16.0, 0.5, 1)
    val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1)
    val minDamage by number("Min Damage", 4.0, 1.0, 20.0, 0.5, 1)
    val maxSelfDamage by number("Max Self Damage", 8.0, 0.0, 20.0, 0.5, 1)
    val delay by number("Delay", 0.0, 0.0, 10.0, 1.0, 0)

    val moveFixSetting = mode("MoveFix", "Silent", listOf("Silent", "Strict", "Free", "Off"))
    val moveFix by moveFixSetting

    val rotations by boolean("Rotations", true)
    val wallCheck by boolean("WallCheck", true, "Requires a clear view to place, charge and detonate anchors")
    val silentSlots by boolean("Silent Slots", true)
    val ignoreTerrain by boolean("Ignore Terrain", false, "Treats all blocks as indestructible when calculating explosion damage")
    val lethalOverride by boolean("Lethal Override", true)
    val targetNPCs by boolean("Target NPCs", true, "Includes practice NPCs reported as creative players")
    val render by boolean("Render", true, "Animated placement, charge and detonation targets")
    val renderSlideSpeed by number("Render Slide Speed", 14.0, 5.0, 30.0, 1.0, 0, isVisible = { render })

    private enum class SequenceStage { PLACE_SENT, CHARGE_SENT, DETONATE_SENT }
    private data class AnchorSequence(val pos: BlockPos, val stage: SequenceStage, val sentTick: Int, val charges: Int) {
        fun confirmed(state: BlockState): Boolean {
            val anchor = state.`is`(Blocks.RESPAWN_ANCHOR)
            return when (stage) {
                SequenceStage.PLACE_SENT -> anchor
                SequenceStage.CHARGE_SENT -> anchor && state.getValue(RespawnAnchorBlock.CHARGE) > charges
                SequenceStage.DETONATE_SENT -> !anchor
            }
        }
    }
    private data class Plan(val pos: BlockPos, val hit: BlockHitResult, val stage: SequenceStage)
    private val actions = AuraActionQueue(this)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { renderSlideSpeed })
    private var tickTimer = 0
    private var sequence: AnchorSequence? = null
    private var sequencePlayer: LocalPlayer? = null
    private var sequenceLevel: Level? = null
    private var queuedPlan: Plan? = null
    private var preferredPos: BlockPos? = null
    var currentTarget: Player? = null
        private set

    private fun reset() {
        tickTimer = 0
        sequence = null
        queuedPlan = null
        preferredPos = null
        sequencePlayer = null
        sequenceLevel = null
        currentTarget = null
        actions.beginTick()
        visual.reset()
        SilentSwapUtil.releasePreparedSlot(this)
    }

    private fun awaitingConfirmation(player: LocalPlayer, level: Level): Boolean {
        val current = sequence ?: return false
        val state = level.getBlockState(current.pos)
        val confirmed = current.confirmed(state)
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        val timeout = (ping.coerceAtLeast(0) / 50 + 5).coerceIn(5, 80)
        if (confirmed || player.tickCount - current.sentTick >= timeout) {
            sequence = null
            return false
        }
        visual.show(AABB(current.pos), renderKind(current.stage))
        return true
    }

    private fun getMoveFixMode() = MoveFixMode.fromName(moveFix)
    fun hasPendingAction() = isEnabled() && (queuedPlan != null || sequence != null)
    fun hasInFlightAction() = isEnabled() && sequence != null

    /** Read-only comparison against the action the anchor aura can actually perform. */
    fun availableDamage(): Float {
        if (!isEnabled()) return 0f
        val player = mc.player ?: return 0f
        if (mc.level?.dimension() == Level.NETHER || hasInFlightAction()) return 0f
        val target = findTarget(player) ?: return 0f
        val hp = AnticheatManager.getHealth(target)
        val plan = findPlan(player, target, hp, SequenceStage.DETONATE_SENT)
            ?: findPlan(player, target, hp, SequenceStage.CHARGE_SENT)
            ?: findPlan(player, target, hp, SequenceStage.PLACE_SENT) ?: return 0f
        if (plan.stage == SequenceStage.DETONATE_SENT && detonationSlot(player) == null) return 0f
        return anchorDamage(plan.pos, target)
    }

    @Commando(priority = 90)
    fun onUpdate(event: PlayerUpdateEvent) {
        queuedPlan = null
        actions.beginTick()
        visual.beginTick()
        val player = mc.player
        val level = mc.level
        if (!inGame || player == null || level == null || !player.connection.connection.isConnected) {
            reset()
            return
        }
        if (sequencePlayer !== player || sequenceLevel !== level) {
            reset()
            sequencePlayer = player
            sequenceLevel = level
            actions.beginTick()
        }
        currentTarget = findTarget(player)
        if (level.dimension() == Level.NETHER) { currentTarget = null; return }
        tickTimer++
        if (awaitingConfirmation(player, level) || !ActionCoordinator.canAct(this)) return
        if (player.isUsingItem || player.isSpectator || mc.gui.screen() != null
            || player.containerMenu !== player.inventoryMenu || !player.containerMenu.carried.isEmpty
            || InventoryMoveModule.isInventoryActionSlowed()) return
        val target = currentTarget ?: return
        if (tickTimer < delay.toInt()) return
        val targetHp = AnticheatManager.getHealth(target)
        val selected = findPlan(player, target, targetHp, SequenceStage.DETONATE_SENT)
            ?: findPlan(player, target, targetHp, SequenceStage.CHARGE_SENT)
            ?: findPlan(player, target, targetHp, SequenceStage.PLACE_SENT)
            ?: return
        val refreshed = if (selected.stage == SequenceStage.PLACE_SENT)
            actions.placementHit(selected.pos, range, wallCheck, rotations)
            else actions.visibleHit(selected.pos, range, wallCheck, selected.hit.location)
        if (refreshed == null) return
        val plan = selected.copy(hit = refreshed)
        if (!preparePlan(player, plan)) return
        queuedPlan = plan
        visual.show(AABB(plan.pos), renderKind(plan.stage))
        val aimed = actions.block(plan.hit, range, rotations, getMoveFixMode(), exactFace = plan.stage == SequenceStage.PLACE_SENT,
            wallCheck = wallCheck) { hit ->
            if (!validPlan(player, target, plan, hit)) return@block
            val state = level.getBlockState(plan.pos)
            val charges = if (state.`is`(Blocks.RESPAWN_ANCHOR)) state.getValue(RespawnAnchorBlock.CHARGE) else 0
            val sent = when (plan.stage) {
                SequenceStage.PLACE_SENT -> placeAnchor(player, hit)
                SequenceStage.CHARGE_SENT -> chargeAnchor(player, hit)
                SequenceStage.DETONATE_SENT -> detonateAnchor(player, hit)
            }
            if (sent) {
                queuedPlan = null
                preferredPos = plan.pos
                sequence = AnchorSequence(plan.pos, plan.stage, player.tickCount, charges)
                tickTimer = 0
                visual.pulse()
            }
        }
        if (!aimed) queuedPlan = null
    }

    private fun renderKind(stage: SequenceStage) = when (stage) {
        SequenceStage.PLACE_SENT -> AuraTargetRenderer.Kind.PLACE
        SequenceStage.CHARGE_SENT -> AuraTargetRenderer.Kind.CHARGE
        SequenceStage.DETONATE_SENT -> AuraTargetRenderer.Kind.BREAK
    }

    private fun detonationSlot(player: LocalPlayer): Int? =
        player.inventory.selectedSlot.takeIf { !player.inventory.getItem(it).`is`(Items.GLOWSTONE) }
            ?: (0..8).firstOrNull { !player.inventory.getItem(it).`is`(Items.GLOWSTONE) }

    private fun preparePlan(player: LocalPlayer, plan: Plan): Boolean {
        val item = when (plan.stage) {
            SequenceStage.PLACE_SENT -> Items.RESPAWN_ANCHOR
            SequenceStage.CHARGE_SENT -> Items.GLOWSTONE
            SequenceStage.DETONATE_SENT -> null
        }
        val slot = if (item == null) detonationSlot(player) ?: return false
            else if (player.offhandItem.`is`(item)) player.inventory.selectedSlot
            else InventoryUtil.findHotbarSlot(item)
        if (slot !in 0..8) return false
        if (silentSlots || item != null && player.offhandItem.`is`(item))
            return SilentSwapUtil.prepareSlot(slot, 1, this)
        InventoryUtil.swapToSlot(slot)
        return player.inventory.selectedSlot == slot && SilentSwapUtil.serverSlot == slot
    }

    private fun findTarget(player: Player): Player? {
        val level = mc.level ?: return null
        var best: Player? = null
        var bestDist = targetRange * targetRange
        var bestIsTarget = false
        val maxReachSq = targetRange * targetRange
        for (entity in level.entitiesForRendering()) {
            if (entity !is Player || entity === player || !entity.isAlive || entity.isRemoved
                || entity.isCreative && !targetNPCs || entity.isSpectator) continue
            if (FriendRepository.isFriend(entity)) continue
            val dist = player.distanceToSqr(entity)
            if (dist > maxReachSq) continue
            val isTarget = TargetRepository.isTarget(entity)
            if (best == null || (isTarget && !bestIsTarget) || (isTarget == bestIsTarget && dist < bestDist)) {
                best = entity
                bestDist = dist
                bestIsTarget = isTarget
            }
        }
        return best
    }

    private fun damageAllowed(player: LocalPlayer, target: Player, pos: BlockPos): Boolean {
        if (!target.isAlive || anchorDamage(pos, player) > maxSelfDamage.toFloat()) return false
        val damage = anchorDamage(pos, target)
        return damage >= minDamage.toFloat() || lethalOverride && damage >= AnticheatManager.getHealth(target)
    }

    private fun anchorDamage(pos: BlockPos, target: Player): Float {
        return ExplosionDamageUtil.anchorDamage(pos, target, ignoreTerrain, ignoreCreative = target !== mc.player && targetNPCs)
    }

    private fun validPlan(player: LocalPlayer, target: Player, plan: Plan, hit: BlockHitResult): Boolean {
        val level = mc.level ?: return false
        if (mc.player !== player || currentTarget !== target || !target.isAlive || target.isRemoved
            || FriendRepository.isFriend(target) || player.distanceToSqr(target) > targetRange * targetRange) return false
        if (!damageAllowed(player, target, plan.pos)) return false
        val state = level.getBlockState(plan.pos)
        return when (plan.stage) {
            SequenceStage.PLACE_SENT -> InventoryUtil.findHotbarSlot(Items.GLOWSTONE) >= 0
                && state.canBeReplaced() && hit.blockPos.relative(hit.direction) == plan.pos
                && level.getEntities(null, AABB(plan.pos)).none { it.isAlive }
            SequenceStage.CHARGE_SENT -> state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) == 0
            SequenceStage.DETONATE_SENT -> state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0
        }
    }

    private fun findPlan(player: LocalPlayer, target: Player, targetHp: Float, stage: SequenceStage): Plan? {
        val level = mc.level ?: return null
        if (stage == SequenceStage.PLACE_SENT && InventoryUtil.findHotbarSlot(Items.GLOWSTONE) < 0) return null
        if (stage == SequenceStage.PLACE_SENT && !player.offhandItem.`is`(Items.RESPAWN_ANCHOR)
            && InventoryUtil.findHotbarSlot(Items.RESPAWN_ANCHOR) < 0) return null
        if (stage == SequenceStage.CHARGE_SENT && !player.offhandItem.`is`(Items.GLOWSTONE)
            && InventoryUtil.findHotbarSlot(Items.GLOWSTONE) < 0) return null
        val r = range.toInt() + 1
        val p = player.blockPosition()
        var best: Plan? = null
        var bestDamage = 0f
        val candidates = sequence {
            preferredPos?.let { yield(it) }
            yieldAll(BlockPos.betweenClosed(p.x - r, maxOf(p.y - r, level.minY), p.z - r,
                p.x + r, minOf(p.y + r, level.maxY - 1), p.z + r).asSequence().filter { it != preferredPos })
        }
        for (mutablePos in candidates) {
            val pos = mutablePos.immutable()
            val state = level.getBlockState(pos)
            when (stage) {
                SequenceStage.PLACE_SENT -> if (!state.canBeReplaced() || level.getEntities(null, AABB(pos)).any { it.isAlive }) continue
                SequenceStage.CHARGE_SENT -> if (!state.`is`(Blocks.RESPAWN_ANCHOR) || state.getValue(RespawnAnchorBlock.CHARGE) != 0) continue
                SequenceStage.DETONATE_SENT -> if (!state.`is`(Blocks.RESPAWN_ANCHOR) || state.getValue(RespawnAnchorBlock.CHARGE) == 0) continue
            }
            if (player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) continue
            val damage = anchorDamage(pos, target)
            if (damage <= bestDamage || damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)) continue
            if (anchorDamage(pos, player) > maxSelfDamage.toFloat()) continue
            val hit = if (stage == SequenceStage.PLACE_SENT) AuraActionQueue.placementHit(player, pos, range, wallCheck)
                else AuraActionQueue.visibleHit(player, pos, range, wallCheck = wallCheck)
            if (hit == null) continue
            val plan = Plan(pos, hit, stage)
            if (pos == preferredPos) return plan
            best = plan
            bestDamage = damage
        }
        return best
    }

    private fun interact(player: LocalPlayer, hand: InteractionHand, hitResult: BlockHitResult, slot: Int? = null): Boolean {
        if (!mc.isSameThread || mc.player !== player || mc.level !== sequenceLevel || mc.gameMode == null
            || !player.connection.connection.isConnected || !player.containerMenu.carried.isEmpty
            || player.isSpectator || slot != null && slot !in 0..8) return false
        // Packet-only interaction: advance the sequence from actual server block updates, not prediction.
        if (hand == InteractionHand.MAIN_HAND && silentSlots) {
            var sent = false
            InventoryUtil.withSilentSlot(slot ?: player.inventory.selectedSlot) {
                sent = InventoryUtil.useItemOnPacket(hand, hitResult)
            }
            return sent
        }
        if (slot != null) {
            InventoryUtil.swapToSlot(slot)
            if (player.inventory.selectedSlot != slot) return false
        }
        return InventoryUtil.useItemOnPacket(hand, hitResult)
    }

    private fun placeAnchor(player: LocalPlayer, hit: BlockHitResult): Boolean {
        if (player.offhandItem.`is`(Items.RESPAWN_ANCHOR)) return interact(player, InteractionHand.OFF_HAND, hit)
        val slot = InventoryUtil.findHotbarSlot(Items.RESPAWN_ANCHOR)
        if (slot !in 0..8) return false
        return interact(player, InteractionHand.MAIN_HAND, hit, slot)
    }

    private fun chargeAnchor(player: LocalPlayer, hit: BlockHitResult): Boolean {
        if (player.isSecondaryUseActive) return false
        if (player.offhandItem.`is`(Items.GLOWSTONE)) return interact(player, InteractionHand.OFF_HAND, hit)
        val slot = InventoryUtil.findHotbarSlot(Items.GLOWSTONE)
        if (slot !in 0..8) return false
        return interact(player, InteractionHand.MAIN_HAND, hit, slot)
    }

    private fun detonateAnchor(player: LocalPlayer, hitResult: BlockHitResult): Boolean {
        val slot = detonationSlot(player) ?: return false
        if (player.isSecondaryUseActive && (!player.inventory.getItem(slot).isEmpty || !player.offhandItem.isEmpty)) return false
        if (!player.offhandItem.`is`(Items.GLOWSTONE)) {
            return interact(player, InteractionHand.MAIN_HAND, hitResult, slot)
        }

        val menu = player.containerMenu
        if (menu !== player.inventoryMenu || !menu.carried.isEmpty || mc.gameMode == null) return false
        val offhandSlot = menu.getSlot(45)
        val swapSlot = (0..35).firstOrNull {
            if (it == slot || player.inventory.getItem(it).`is`(Items.GLOWSTONE)) return@firstOrNull false
            val candidate = menu.getSlot(if (it in 0..8) it + 36 else it)
            candidate.mayPickup(player) && candidate.mayPlace(player.offhandItem)
                && offhandSlot.mayPickup(player) && offhandSlot.mayPlace(candidate.item)
        } ?: return false
        val menuSlot = if (swapSlot in 0..8) swapSlot + 36 else swapSlot
        if (!InventoryMoveModule.beginInventoryAction(InventoryActionSource.INVENTORY)) return false
        try {
            if (mc.player !== player || player.containerMenu !== menu || !InventoryUtil.clickSlotDirect(menuSlot, 40)) return false
            try {
                if (player.offhandItem.`is`(Items.GLOWSTONE)) return false
                return interact(player, InteractionHand.MAIN_HAND, hitResult, slot)
            } finally {
                if (mc.player === player && mc.level === sequenceLevel && player.containerMenu === menu
                    && player.connection.connection.isConnected) {
                    InventoryUtil.clickSlotDirect(menuSlot, 40)
                }
            }
        } finally {
            InventoryMoveModule.endInventoryAction(InventoryActionSource.INVENTORY)
        }
    }

    override fun onEnable() = reset()
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()
}
