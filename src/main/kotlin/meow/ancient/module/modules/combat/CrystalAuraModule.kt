package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.combat.AuraTerrainMiner
import meow.ancient.client.util.combat.CrystalTerrainPlan
import meow.ancient.client.util.combat.CrystalTerrainPriority
import meow.ancient.client.util.combat.ExplosionDamageUtil
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.item.Item
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object CrystalAuraModule : Module(
    name = "CrystalAura",
    category = COMBAT,
    description = "Automatically places and detonates end crystals against opponents"
) {
    val targetRange by number("Target Range", 10.0, 3.0, 16.0, 0.5, 1)
    val placeRange by number("Place Range", 4.5, 1.0, 6.0, 0.1, 1)
    val breakRange by number("Break Range", 4.5, 1.0, 6.0, 0.1, 1)
    val minDamage by number("Min Damage", 4.0, 1.0, 20.0, 0.5, 1)
    val maxSelfDamage by number("Max Self Damage", 8.0, 0.0, 20.0, 0.5, 1)
    val placeDelay by number("Place Delay", 0.0, 0.0, 10.0, 1.0, 0)
    val breakDelay by number("Break Delay", 0.0, 0.0, 10.0, 1.0, 0)

    val moveFixSetting = mode("MoveFix", "Silent", listOf("Silent", "Strict", "Free", "Off"))
    val moveFix by moveFixSetting

    val rotations by boolean("Rotations", true)
    val wallCheck by boolean("WallCheck", true, "Requires a clear view to place and break crystals")
    val silentSlots by boolean("Silent Slots", true)
    val ignoreTerrain by boolean("Ignore Terrain", false, "Treats all blocks as indestructible when calculating explosion damage")
    val basePlace by boolean("Base Place", true, "Places obsidian as base for crystals if none available")
    val lethalOverride by boolean("Lethal Override", true)
    val targetNPCs by boolean("Target NPCs", true, "Includes practice NPCs reported as creative players")

    val autoMineTerrain by boolean("AutoMine Terrain", false, "Clears soft terrain for Base Place with a suitable hotbar tool",
        isVisible = { basePlace })
    val terrainPacketMine by boolean("Terrain PacketMine", false, "Uses packet mining instead of vanilla block breaking",
        isVisible = { basePlace && autoMineTerrain })
    val terrainDamageGain by number("Terrain Damage Gain", 3.0, 1.0, 12.0, 0.5, 1,
        description = "Minimum damage gain to prioritize quick terrain mining; also requires at least 25% more damage",
        isVisible = { basePlace && autoMineTerrain })
    val render by boolean("Render", true, "Animated placement, crystal and terrain targets")
    val renderSlideSpeed by number("Render Slide Speed", 14.0, 5.0, 30.0, 1.0, 0, isVisible = { render })

    private enum class PendingKind { CRYSTAL, BASE, BREAK }
    private data class Pending(val kind: PendingKind, val pos: BlockPos, val sentTick: Int, val entityId: Int = -1,
                               val box: AABB = AABB(pos))
    private data class Site(val pos: BlockPos, val hit: BlockHitResult, val damage: Float = 0f)
    private data class TerrainCandidate(val plan: CrystalTerrainPlan, val damage: Float, val miningTicks: Int)
    private data class Prepared(val kind: PendingKind, val pos: BlockPos, val targetId: Int,
        val since: Int, val box: AABB, val hit: BlockHitResult? = null, val entityId: Int = -1,
        val hand: InteractionHand = InteractionHand.MAIN_HAND, val slot: Int = -1, val terrainBase: Boolean = false)
    private val actions = AuraActionQueue(this)
    private var terrainOwner = ActionCoordinator.crystalTerrainOwner()
    private val terrainActions = AuraActionQueue(terrainOwner)
    private val miner = AuraTerrainMiner(terrainOwner, terrainActions, ::allowedTerrainStart)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { renderSlideSpeed })
    private var preferredPos: BlockPos? = null
    private var terrainPlan: CrystalTerrainPlan? = null
    private var nextTerrainComparison = 0
    private var terrainSearchTick = -1
    private var terrainSearchResult: TerrainCandidate? = null
    private var terrainSearchQuickResult: TerrainCandidate? = null
    private var pending: Pending? = null
    private var prepared: Prepared? = null
    private var sending: Packet<*>? = null
    private var accepted = false
    private var tick = 0
    private val failedSites = HashMap<BlockPos, Int>()
    var currentTarget: Player? = null
        private set
    private var world: ClientLevel? = null
    private var localPlayer: LocalPlayer? = null
    private var placeTimer = 0
    private var breakTimer = 0

    private fun getMoveFixMode() = MoveFixMode.fromName(moveFix)

    fun shouldKeepTerrainMining() = isEnabled() && basePlace && autoMineTerrain && miner.keepVanillaMining()
    fun hasPendingAction() = isEnabled() && (prepared != null || pending != null || miner.active)
    fun hasCombatAction() = isEnabled() && (prepared != null || pending != null)
    fun hasTerrainAction() = isEnabled() && miner.active
    fun hasBoostedTerrainAction() = isEnabled() && basePlace && autoMineTerrain
        && terrainPlan?.priorityBoosted == true && terrainPlan?.stage == CrystalTerrainPlan.Stage.MINE
        && prepared == null && pending == null && !AnchorAuraModule.hasInFlightAction()

    fun yieldToManualMining(pos: BlockPos) {
        miner.cancel()
        clearPrepared()
        actions.beginTick()
        terrainActions.beginTick()
        // A manual selection of the same block replaces the automatic operation entirely.
        if (terrainPlan?.pos == pos) terrainPlan = null
    }

    @Commando(priority = 100)
    fun onUpdate(event: PlayerUpdateEvent) {
        actions.beginTick()
        terrainActions.beginTick()
        visual.beginTick()
        val player = mc.player
        val level = mc.level
        if (player == null || level == null || !player.connection.connection.isConnected) {
            reset()
            return
        }
        if (world !== level || localPlayer !== player) {
            reset()
            world = level
            localPlayer = player
            actions.beginTick()
        }
        placeTimer++
        breakTimer++
        tick++
        failedSites.entries.removeIf { tick >= it.value }
        currentTarget = findTarget(player)
        val updateOwner = if (hasBoostedTerrainAction()) ActionCoordinator.boostedCrystalTerrainOwner() else this
        if (!ActionCoordinator.canAct(updateOwner) || player.isUsingItem || player.isSpectator || mc.gui.screen() != null
            || player.containerMenu !== player.inventoryMenu || !player.containerMenu.carried.isEmpty
            || InventoryMoveModule.isInventoryActionSlowed()) {
            miner.cancel()
            clearPrepared()
            return
        }
        val target = currentTarget
        // Never spend another crystal while spawn, base placement or destruction is unconfirmed.
        if (awaitingConfirmation(player, level)) return

        val crystal = target?.let { findCrystal(player, it, AnticheatManager.getHealth(it)) }
        if (crystal != null) {
            if (miner.active) miner.cancel()
            // A qualifying crystal retains priority even while its break delay/rotation is pending.
            if (prepared?.entityId != crystal.id || prepared?.targetId != target.id) {
                clearPrepared()
                prepared = Prepared(PendingKind.BREAK, crystal.blockPosition(), target.id, tick,
                    crystal.boundingBox, entityId = crystal.id, slot = player.inventory.selectedSlot)
            }
            prepare(player)
            return
        }
        if (prepared?.kind == PendingKind.BREAK) clearPrepared()
        // The approved replacement survives movement of the opponent, failed clicks and retries.
        if (continueTerrain(player, level)) return
        if (target == null) { miner.cancel(); clearPrepared(); return }
        val targetHp = AnticheatManager.getHealth(target)
        val existing = prepared
        if (existing != null) {
            if (existing.targetId == target.id && validPrepared(player, target, existing) && tick - existing.since < 6) {
                if (maybeImproveTerrain(player, target, targetHp)) return
                prepare(player)
                return
            }
            if (tick - existing.since >= 6) failedSites[existing.pos] = tick + 2
            clearPrepared()
        }
        miner.cancel()
        if (placeTimer < placeDelay.toInt()) return
        val hasCrystal = player.offhandItem.`is`(Items.END_CRYSTAL) || InventoryUtil.findHotbarSlot(Items.END_CRYSTAL) >= 0
        if (!hasCrystal) return
        val compareTerrain = canCompareTerrain(player) && tick >= nextTerrainComparison
        val crystalSite = findSite(player, target, targetHp, base = false, preferCached = !compareTerrain)
        val hasObsidian = basePlace && (player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0)
        val baseSite = if (hasObsidian && (crystalSite == null || compareTerrain))
            findSite(player, target, targetHp, base = true, preferCached = !compareTerrain) else null
        if (maybeImproveTerrain(player, target, targetHp, maxOf(crystalSite?.damage ?: 0f, baseSite?.damage ?: 0f))) return
        if (crystalSite != null) {
            schedulePlace(player, crystalSite, Items.END_CRYSTAL, PendingKind.CRYSTAL)
            return
        }
        if (baseSite != null) {
            schedulePlace(player, baseSite, Items.OBSIDIAN, PendingKind.BASE)
            return
        }
        if (basePlace && autoMineTerrain) {
            val site = findTerrainSite(player, target, targetHp) ?: return
            startTerrain(site.plan)
        }
    }

    private fun canCompareTerrain(player: LocalPlayer): Boolean = basePlace && autoMineTerrain
        && (player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0)
        && (player.offhandItem.`is`(Items.END_CRYSTAL) || InventoryUtil.findHotbarSlot(Items.END_CRYSTAL) >= 0)

    private fun availableCrystalDamage(player: LocalPlayer, target: Player, hp: Float): Float = maxOf(
        findSite(player, target, hp, base = false, preferCached = false)?.damage ?: 0f,
        findSite(player, target, hp, base = true, preferCached = false)?.damage ?: 0f)

    private fun gainJustifiesMining(damage: Float, immediateDamage: Float, hp: Float, miningTicks: Int): Boolean {
        if (AnchorAuraModule.hasInFlightAction() || immediateDamage >= hp || miningTicks > 20) return false
        if (immediateDamage > 0f && !CrystalTerrainPriority.shouldBoost(damage, immediateDamage, hp,
                terrainDamageGain.toFloat(), miningTicks)) return false
        val availableDamage = maxOf(immediateDamage, AnchorAuraModule.availableDamage())
        return CrystalTerrainPriority.shouldBoost(damage, availableDamage, hp, terrainDamageGain.toFloat(), miningTicks)
    }

    private fun maybeImproveTerrain(player: LocalPlayer, target: Player, hp: Float, immediateDamage: Float? = null): Boolean {
        if (!canCompareTerrain(player) || tick < nextTerrainComparison || AnchorAuraModule.hasInFlightAction()) return false
        nextTerrainComparison = tick + 4
        val damage = immediateDamage ?: availableCrystalDamage(player, target, hp)
        if (damage >= hp) return false
        val candidate = findTerrainSite(player, target, hp, quickOnly = true) ?: return false
        if (!gainJustifiesMining(candidate.damage, damage, hp, candidate.miningTicks)) return false
        clearPrepared()
        candidate.plan.boostPriority()
        startTerrain(candidate.plan)
        return true
    }

    private fun refreshTerrainPriority(player: LocalPlayer, plan: CrystalTerrainPlan) {
        if (!plan.priorityBoosted && canCompareTerrain(player) && tick >= nextTerrainComparison
            && !AnchorAuraModule.hasInFlightAction()) {
            nextTerrainComparison = tick + 4
            val target = mc.level?.getEntity(plan.targetId) as? Player
            if (target != null && target.isAlive && !target.isRemoved) {
                val hp = AnticheatManager.getHealth(target)
                val immediate = availableCrystalDamage(player, target, hp)
                if (gainJustifiesMining(terrainDamage(plan.pos, target, listOf(plan.pos)), immediate, hp,
                        miner.estimatedMiningTicks(player, plan.pos))) plan.boostPriority()
            }
        }
        // A promoted operation keeps its cell and progress; never restart it to change priority.
        terrainOwner = if (plan.priorityBoosted && !AnchorAuraModule.hasInFlightAction())
            ActionCoordinator.boostedCrystalTerrainOwner() else ActionCoordinator.crystalTerrainOwner()
        miner.changeOwner(terrainOwner)
    }

    private fun startTerrain(plan: CrystalTerrainPlan) {
        preferredPos = plan.pos
        terrainPlan = plan
        val player = mc.player ?: return
        val level = mc.level ?: return
        continueTerrain(player, level)
    }

    private fun continueTerrain(player: LocalPlayer, level: ClientLevel): Boolean {
        val plan = terrainPlan ?: return false
        val state = level.getBlockState(plan.pos)
        if (state.`is`(Blocks.OBSIDIAN)) plan.observeServerState(state)
        if (plan.stage == CrystalTerrainPlan.Stage.DONE) {
            miner.cancel()
            if (prepared?.terrainBase == true) clearPrepared()
            terrainPlan = null
            preferredPos = plan.pos
            return false
        }
        if (!basePlace || !autoMineTerrain) { miner.cancel(); clearPrepared(); return true }
        val hasObsidian = player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0
        if (!hasObsidian) {
            miner.cancel()
            clearPrepared()
            visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.BASE)
            return true
        }
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        plan.retry(tick, (ping.coerceAtLeast(0) / 50 + 5).coerceIn(5, 80))
        if (plan.stage == CrystalTerrainPlan.Stage.CONFIRM) {
            visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.BASE)
            return true
        }
        if (plan.stage == CrystalTerrainPlan.Stage.MINE) {
            clearPrepared()
            if (!plan.miningStarted && !allowedTerrainStart(plan.pos)) {
                miner.cancel()
                terrainPlan = null
                return false
            }
            refreshTerrainPriority(player, plan)
            if (!ActionCoordinator.canAct(terrainOwner)) {
                miner.cancel()
                return true
            }
            if (!state.isAir && miner.canMine(player, plan.pos, placeRange) && !miner.active) miner.select(plan.pos)
            if (miner.active) miner.update(placeRange, terrainPacketMine, rotations, getMoveFixMode())
            visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.MINE, if (state.isAir) 1f else miner.progress)
            return true
        }
        miner.cancel()
        if (!state.canBeReplaced()) {
            clearPrepared()
            // A server correction may restore the original terrain. Retry this cell alone.
            if (miner.canMine(player, plan.pos, placeRange)) {
                plan.restartMining()
                return continueTerrain(player, level)
            }
            visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.BASE)
            return true
        }
        val existing = prepared
        if (existing?.terrainBase == true && existing.pos == plan.pos && validPrepared(player, currentTarget, existing)) {
            prepare(player)
            return true
        }
        clearPrepared()
        visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.BASE)
        val hit = actions.placementHit(plan.pos, placeRange, wallCheck, rotations) ?: return true
        schedulePlace(player, Site(plan.pos, hit), Items.OBSIDIAN, PendingKind.BASE, plan.targetId, terrainBase = true)
        return true
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

    private fun awaitingConfirmation(player: LocalPlayer, level: ClientLevel): Boolean {
        val current = pending ?: return false
        val confirmed = when (current.kind) {
            PendingKind.BREAK -> level.getEntity(current.entityId) == null
            PendingKind.BASE -> level.getBlockState(current.pos).`is`(Blocks.OBSIDIAN)
            PendingKind.CRYSTAL -> level.getEntities(null, crystalBox(current.pos)).any { it is EndCrystal && it.isAlive }
        }
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        val timeout = (ping.coerceAtLeast(0) / 50 + 5).coerceIn(5, 80)
        if (confirmed || tick - current.sentTick >= timeout) {
            pending = null
            if (current.kind == PendingKind.BASE && terrainPlan?.pos == current.pos) {
                if (confirmed) terrainPlan?.observeServerState(level.getBlockState(current.pos))
                else terrainPlan?.retry(tick, timeout)
            } else if (!confirmed && current.kind != PendingKind.BREAK) failedSites[current.pos] = tick + 2
            return false
        }
        visual.show(current.box, when (current.kind) {
            PendingKind.BREAK -> AuraTargetRenderer.Kind.BREAK
            PendingKind.BASE -> AuraTargetRenderer.Kind.BASE
            PendingKind.CRYSTAL -> AuraTargetRenderer.Kind.PLACE
        })
        return true
    }

    private fun qualifies(pos: Vec3, player: LocalPlayer, target: Player, targetHp: Float, base: BlockPos? = null): Boolean {
        if (!target.isAlive) return false
        if (ExplosionDamageUtil.crystalDamage(pos, player, base, ignoreTerrain) > maxSelfDamage.toFloat()) return false
        val damage = ExplosionDamageUtil.crystalDamage(pos, target, base, ignoreTerrain, ignoreCreative = targetNPCs)
        return damage >= minDamage.toFloat() || lethalOverride && damage >= targetHp
    }

    private fun findCrystal(player: LocalPlayer, target: Player, targetHp: Float): EndCrystal? {
        val level = mc.level ?: return null
        var best: EndCrystal? = null
        var bestDamage = 0f
        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal || !entity.isAlive) continue
            if (AuraActionQueue.visibleCrystalPoint(player, entity, breakRange, wallCheck) == null) continue
            val damage = ExplosionDamageUtil.crystalDamage(entity.position(), target, ignoreTerrain = ignoreTerrain, ignoreCreative = targetNPCs)
            if (damage <= bestDamage || damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)) continue
            if (ExplosionDamageUtil.crystalDamage(entity.position(), player, ignoreTerrain = ignoreTerrain) > maxSelfDamage.toFloat()) continue
            best = entity
            bestDamage = damage
        }
        return best
    }

    private fun crystalBox(pos: BlockPos) = AABB(pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), pos.x + 1.0, pos.y + 3.0, pos.z + 1.0)
    private fun explosionPos(pos: BlockPos) = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)

    private fun positions(player: LocalPlayer): Iterable<BlockPos> {
        val level = mc.level ?: return emptyList()
        val r = placeRange.toInt() + 1
        val p = player.blockPosition()
        return BlockPos.betweenClosed(p.x - r, maxOf(p.y - r, level.minY), p.z - r,
            p.x + r, minOf(p.y + r, level.maxY - 1), p.z + r)
    }

    private fun findSite(player: LocalPlayer, target: Player, targetHp: Float, base: Boolean, onlyPos: BlockPos? = null,
                         preferCached: Boolean = true): Site? {
        val level = mc.level ?: return null
        var best: Site? = null
        var bestDamage = 0f
        val attackReach = minOf(breakRange, player.entityInteractionRange())
        val candidates = sequence {
            if (onlyPos != null) yield(onlyPos) else {
                preferredPos?.let { yield(it) }
                yieldAll(positions(player).asSequence().filter { it != preferredPos })
            }
        }
        for (mutablePos in candidates) {
            val pos = mutablePos.immutable()
            if (pos in failedSites) continue
            if (base && PacketMineModule.targetsPosition(pos)) continue
            val state = level.getBlockState(pos)
            if (base) {
                if (pos.y >= target.blockPosition().y || !state.canBeReplaced()) continue
            } else if (!state.`is`(Blocks.OBSIDIAN) && !state.`is`(Blocks.BEDROCK)) continue
            if (!level.getBlockState(pos.above()).isAir) continue
            if (player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > placeRange * placeRange) continue
            val futureCrystal = AABB(pos.x - 0.5, pos.y + 1.0, pos.z - 0.5, pos.x + 1.5, pos.y + 3.0, pos.z + 1.5)
            if (player.eyePosition.distanceToSqr(RotationUtil.getNearestPoint(player.eyePosition, futureCrystal)) > attackReach * attackReach) continue
            if (level.getEntities(null, crystalBox(pos)).any { it.isAlive }) continue
            if (base && level.getEntities(null, AABB(pos)).any { !it.isRemoved && it.blocksBuilding }) continue
            val damage = ExplosionDamageUtil.crystalDamage(explosionPos(pos), target, if (base) pos else null, ignoreTerrain, ignoreCreative = targetNPCs)
            if (damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)) continue
            if (damage <= bestDamage && pos != preferredPos) continue
            if (ExplosionDamageUtil.crystalDamage(explosionPos(pos), player, if (base) pos else null, ignoreTerrain) > maxSelfDamage.toFloat()) continue
            val hit = if (base) AuraActionQueue.placementHit(player, pos, placeRange, wallCheck)
                else AuraActionQueue.visibleHit(player, pos, placeRange, wallCheck = wallCheck)
            if (hit == null) continue
            val site = Site(pos, hit, damage)
            // Reuse a validated site immediately; a full scan is only needed when it stops working.
            if (preferCached && pos == preferredPos) return site
            if (damage > bestDamage) { best = site; bestDamage = damage }
        }
        return best
    }

    private fun schedulePlace(player: LocalPlayer, site: Site, item: Item, kind: PendingKind,
                              targetId: Int? = currentTarget?.id, terrainBase: Boolean = false) {
        val hand = if (player.offhandItem.`is`(item)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val slot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else InventoryUtil.findHotbarSlot(item)
        if (slot !in 0..8) return
        if (targetId == null) return
        preferredPos = site.pos
        prepared = Prepared(kind, site.pos, targetId, tick,
            AABB(if (kind == PendingKind.CRYSTAL) site.pos.above() else site.pos),
            hit = site.hit, hand = hand, slot = slot, terrainBase = terrainBase)
        prepare(player)
    }

    private var readyTick = -1

    private fun clearPrepared() {
        prepared = null
        readyTick = -1
        SilentSwapUtil.releasePreparedSlot(this)
    }

    private fun prepare(player: LocalPlayer) {
        val action = prepared ?: return
        readyTick = -1
        // Lease the item before vanilla physics/sprint and other pre-movement actions lock swaps.
        if (silentSlots || action.hand == InteractionHand.OFF_HAND || action.kind == PendingKind.BREAK) {
            if (!SilentSwapUtil.prepareSlot(action.slot, 1, this)) return
        } else {
            InventoryUtil.swapToSlot(action.slot)
            if (player.inventory.selectedSlot != action.slot || SilentSwapUtil.serverSlot != action.slot) return
        }
        val aimed = if (action.kind == PendingKind.BREAK) {
            val crystal = mc.level?.getEntity(action.entityId) as? EndCrystal ?: return
            actions.prepareCrystal(crystal, breakRange, rotations, getMoveFixMode(), wallCheck)
        } else {
            val hit = if (action.kind == PendingKind.BASE) actions.placementHit(action.pos, placeRange, wallCheck, rotations)
                else actions.visibleHit(action.pos, placeRange, wallCheck, action.hit?.location)
            if (hit == null) { clearPrepared(); return }
            // A moving player can expose another support or another part of the same face.
            prepared = action.copy(hit = hit)
            actions.prepareBlock(hit, placeRange, rotations, getMoveFixMode(),
                exactFace = action.kind == PendingKind.BASE, wallCheck = wallCheck)
        }
        if (!aimed) return
        readyTick = tick
        visual.show(action.box, renderKind(action.kind))
    }

    private fun renderKind(kind: PendingKind) = when (kind) {
        PendingKind.BREAK -> AuraTargetRenderer.Kind.BREAK
        PendingKind.BASE -> AuraTargetRenderer.Kind.BASE
        PendingKind.CRYSTAL -> AuraTargetRenderer.Kind.PLACE
    }

    private fun validPrepared(player: LocalPlayer, target: Player?, action: Prepared): Boolean {
        val level = mc.level ?: return false
        if (action.terrainBase) {
            val stack = if (action.hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(action.slot)
            return action.kind == PendingKind.BASE && terrainPlan?.pos == action.pos
                && terrainPlan?.stage == CrystalTerrainPlan.Stage.BASE && basePlace && autoMineTerrain
                && stack.`is`(Items.OBSIDIAN) && level.worldBorder.isWithinBounds(action.pos)
                && level.getBlockState(action.pos).canBeReplaced()
                && level.getEntities(null, AABB(action.pos)).none { !it.isRemoved && it.blocksBuilding }
        }
        if (target == null) return false
        if (!target.isAlive || target.isRemoved || FriendRepository.isFriend(target)
            || player.distanceToSqr(target) > targetRange * targetRange) return false
        if (action.kind == PendingKind.BREAK) {
            val crystal = level.getEntity(action.entityId) as? EndCrystal ?: return false
            return crystal.isAlive && qualifies(crystal.position(), player, target, AnticheatManager.getHealth(target))
        }
        val item = if (action.kind == PendingKind.BASE) Items.OBSIDIAN else Items.END_CRYSTAL
        val stack = if (action.hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(action.slot)
        if (!stack.`is`(item) || !level.worldBorder.isWithinBounds(action.pos)) return false
        if (!qualifies(explosionPos(action.pos), player, target, AnticheatManager.getHealth(target),
                action.pos.takeIf { action.kind == PendingKind.BASE })) return false
        val state = level.getBlockState(action.pos)
        if (action.kind == PendingKind.BASE) {
            if (!basePlace || !state.canBeReplaced() || level.getEntities(null, AABB(action.pos)).any { !it.isRemoved && it.blocksBuilding }) return false
        } else if (!state.`is`(Blocks.OBSIDIAN) && !state.`is`(Blocks.BEDROCK)) return false
        return level.getBlockState(action.pos.above()).isAir
            && level.getEntities(null, crystalBox(action.pos)).none { it.isAlive }
    }

    @Commando(priority = -128)
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val action = prepared ?: return
        val player = mc.player ?: return
        val level = mc.level ?: return
        val target = level.getEntity(action.targetId) as? Player
        if (readyTick != tick || player !== localPlayer || level !== world || pending != null
            || !ActionCoordinator.canAct(this) || player.isUsingItem || player.isSpectator
            || mc.gui.screen() != null || player.containerMenu !== player.inventoryMenu
            || !player.containerMenu.carried.isEmpty || InventoryMoveModule.isInventoryActionSlowed()
            || SilentSwapUtil.serverSlot != action.slot) return
        if (!validPrepared(player, target, action)) { clearPrepared(); return }
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(this)) {
            accepted = false
            try {
                if (action.kind == PendingKind.BREAK) {
                    if (breakTimer < breakDelay.toInt()) return@withOwner
                    val crystal = level.getEntity(action.entityId) as? EndCrystal ?: return@withOwner
                    if (!actions.crystalReady(crystal, breakRange, wallCheck)) return@withOwner
                    val packet = ServerboundAttackPacket(crystal.id)
                    sending = packet
                    player.connection.send(packet)
                    if (accepted) {
                        player.resetAttackStrengthTicker()
                        player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
                        player.connection.send(ServerboundPunchPacket.INSTANCE)
                        breakTimer = 0
                    }
                } else {
                    if (!action.terrainBase && placeTimer < placeDelay.toInt()) return@withOwner
                    // A crystal can spawn between selection and sending. It still wins over placement.
                    if (!action.terrainBase && target != null && findCrystal(player, target, AnticheatManager.getHealth(target)) != null) {
                        clearPrepared()
                        return@withOwner
                    }
                    val hit = actions.blockHit(action.hit ?: return@withOwner, placeRange,
                        exactFace = action.kind == PendingKind.BASE, wallCheck = wallCheck) ?: return@withOwner
                    if (action.kind == PendingKind.BASE && hit.blockPos.relative(hit.direction) != action.pos) return@withOwner
                    val gameMode = mc.gameMode ?: return@withOwner
                    gameMode.startPrediction(level) { sequence ->
                        ServerboundUseItemOnPacket(action.hand, hit, sequence).also { sending = it }
                    }
                    if (accepted) {
                        player.swing(action.hand, SwingAnimation.DEFAULT, false)
                        placeTimer = 0
                    }
                }
                if (accepted) {
                    if (action.terrainBase) terrainPlan?.placementSent(tick)
                    pending = Pending(action.kind, action.pos, tick, action.entityId, action.box)
                    visual.pulse()
                    clearPrepared()
                }
            } finally { sending = null }
        }
    }

    @Commando
    fun onPacketSent(event: PacketSentEvent) {
        if (!mc.isSameThread) return
        if (event.packet === sending) accepted = true
        val packet = event.packet as? ServerboundPlayerActionPacket ?: return
        if (packet.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
            && packet.pos == terrainPlan?.pos && ActionCoordinator.resolveOwner() == terrainOwner)
            terrainPlan?.miningStartAccepted()
    }

    private fun allowedTerrainStart(pos: BlockPos): Boolean {
        val plan = terrainPlan ?: return false
        if (plan.pos != pos) return false
        if (plan.miningStarted) return true
        val player = mc.player ?: return false
        val target = mc.level?.getEntity(plan.targetId) as? Player ?: return false
        return target.isAlive && !target.isRemoved && !FriendRepository.isFriend(target)
            && player.distanceToSqr(target) <= targetRange * targetRange
            && qualifies(explosionPos(pos), player, target, AnticheatManager.getHealth(target), base = pos)
    }

    @Commando(priority = -128)
    fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.cancel) return
        val plan = terrainPlan ?: return
        val player = localPlayer ?: return
        val level = world ?: return
        fun block(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
            if (pos != plan.pos) return
            mc.execute {
                if (!event.cancel && terrainPlan === plan && mc.player === player && mc.level === level)
                    plan.observeServerState(state)
            }
        }
        when (val packet = event.packet) {
            is ClientboundBlockUpdatePacket -> block(packet.pos, packet.blockState)
            is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, state -> block(pos.immutable(), state) }
        }
    }

    private fun findTerrainSite(player: LocalPlayer, target: Player, targetHp: Float, quickOnly: Boolean = false): TerrainCandidate? {
        if (terrainSearchTick == tick) return if (quickOnly) terrainSearchQuickResult else terrainSearchResult
        terrainSearchTick = tick
        terrainSearchResult = null
        terrainSearchQuickResult = null
        val level = mc.level ?: return null
        var best: TerrainCandidate? = null
        var bestQuick: TerrainCandidate? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var bestQuickScore = Double.NEGATIVE_INFINITY
        val canBase = basePlace && (player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0)
        val attackReach = minOf(breakRange, player.entityInteractionRange())
        val candidates = sequence {
            preferredPos?.let { yield(it) }
            yieldAll(positions(player).asSequence().filter { it != preferredPos })
        }
        for (mutablePos in candidates) {
            val pos = mutablePos.immutable()
            if (pos in failedSites) continue
            if (PacketMineModule.targetsPosition(pos)) continue
            if (pos.y >= target.blockPosition().y || player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > placeRange * placeRange) continue
            val state = level.getBlockState(pos)
            if (!canBase || state.canBeReplaced() || !level.getBlockState(pos.above()).isAir
                || !miner.canMine(player, pos, placeRange)) continue
            val clear = listOf(pos)
            val futureCrystal = AABB(pos.x - 0.5, pos.y + 1.0, pos.z - 0.5, pos.x + 1.5, pos.y + 3.0, pos.z + 1.5)
            if (player.eyePosition.distanceToSqr(RotationUtil.getNearestPoint(player.eyePosition, futureCrystal)) > attackReach * attackReach) continue
            if (level.getEntities(null, crystalBox(pos)).any { it.isAlive }
                || level.getEntities(null, AABB(pos)).any { !it.isRemoved && it.blocksBuilding }) continue
            val damage = terrainDamage(pos, target, clear)
            if (damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)
                || terrainDamage(pos, player, clear) > maxSelfDamage.toFloat()) continue
            val miningTicks = miner.estimatedMiningTicks(player, pos)
            if (damage <= (best?.damage ?: 0f) && (miningTicks > 20 || damage <= (bestQuick?.damage ?: 0f))) continue
            val cleared = clear.associateWith { Blocks.AIR.defaultBlockState() }
            if (AuraActionQueue.placementHit(player, pos, placeRange, wallCheck, cleared) == null) continue
            if (AuraActionQueue.visibleHit(player, pos, placeRange, wallCheck = wallCheck,
                    overrides = cleared + (pos to Blocks.OBSIDIAN.defaultBlockState())) == null) continue
            if (AuraActionQueue.visibleHit(player, pos, placeRange) == null) continue
            val score = damage - player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) * 0.01
            val site = TerrainCandidate(CrystalTerrainPlan(pos, target.id), damage, miningTicks)
            if (score > bestScore) { best = site; bestScore = score }
            if (miningTicks <= 20 && score > bestQuickScore) { bestQuick = site; bestQuickScore = score }
        }
        terrainSearchResult = best
        terrainSearchQuickResult = bestQuick
        return if (quickOnly) bestQuick else best
    }

    private fun terrainDamage(pos: BlockPos, target: Player, clear: List<BlockPos>): Float {
        val baseFactory = ExplosionDamageUtil.getOverridingHitFactory(pos, Blocks.OBSIDIAN.defaultBlockState(), ignoreTerrain)
        val factory = ExplosionDamageUtil.RaycastFactory { context, block ->
            if (block in clear && block != pos) null else baseFactory.apply(context, block)
        }
        return ExplosionDamageUtil.explosionDamage(target, explosionPos(pos), 12f, false, factory,
            ignoreCreative = target !== mc.player && targetNPCs)
    }

    private fun reset() {
        placeTimer = 0
        breakTimer = 0
        pending = null
        clearPrepared()
        sending = null
        accepted = false
        tick = 0
        failedSites.clear()
        currentTarget = null
        preferredPos = null
        terrainPlan = null
        nextTerrainComparison = 0
        terrainSearchTick = -1
        terrainSearchResult = null
        terrainSearchQuickResult = null
        world = null
        localPlayer = null
        actions.beginTick()
        terrainActions.beginTick()
        visual.reset()
        miner.cancel()
        terrainOwner = ActionCoordinator.crystalTerrainOwner()
        miner.changeOwner(terrainOwner)
    }

    override fun onEnable() = reset()
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()
}
