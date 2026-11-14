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
import meow.ancient.client.util.combat.AuraMotionPrediction
import meow.ancient.client.util.combat.AuraDamageCache
import meow.ancient.client.util.combat.AuraEntityOccupancy
import meow.ancient.client.util.combat.CrystalTerrainScoring
import meow.ancient.client.util.combat.CrystalTerrainRecovery
import meow.ancient.client.util.combat.CrystalAuraDiagnostics
import meow.ancient.client.util.combat.CrystalAuraDiagnostics.Reason
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.render.CrystalAuraDiagnosticsRenderer
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
import net.minecraft.client.gui.GuiGraphicsExtractor
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
import kotlin.math.ceil

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
    val extrapolation by boolean("Extrapolation", true, "Predicts positions at explosion time, including movement collisions")
    val extrapolationLimit by number("Extrapolation Limit", 20.0, 0.0, 40.0, 1.0, 0, isVisible = { extrapolation })
    val diagnostics by boolean("Diagnostics", false, "Shows why actions wait and the current AutoMine damage forecast")

    val autoMineTerrain by boolean("AutoMine Terrain", false, "Clears soft terrain for Base Place with a suitable hotbar tool",
        isVisible = { basePlace })
    val terrainPacketMine by boolean("Terrain PacketMine", false, "Uses packet mining instead of vanilla block breaking",
        isVisible = { basePlace && autoMineTerrain })
    val terrainDamageGain by number("Terrain Damage Gain", 3.0, 1.0, 12.0, 0.5, 1,
        description = "Minimum damage gain over another aura to prioritize quick terrain mining; ready crystal sites always win",
        isVisible = { basePlace && autoMineTerrain })
    val render by boolean("Render", true, "Animated placement, crystal and terrain targets")
    val renderSlideSpeed by number("Render Slide Speed", 14.0, 5.0, 30.0, 1.0, 0, isVisible = { render })

    private enum class PendingKind { CRYSTAL, BASE, BREAK }
    private data class Pending(val kind: PendingKind, val pos: BlockPos, val sentTick: Int, val entityId: Int = -1,
                               val box: AABB = AABB(pos))
    private data class Site(val pos: BlockPos, val hit: BlockHitResult, val damage: Float = 0f)
    private data class TerrainCandidate(val plan: CrystalTerrainPlan, val damage: Float, val predictedDamage: Float,
        val miningTicks: Int, val cycleTicks: Int, val predictionTicks: Double, val score: Double)
    private data class Prepared(val kind: PendingKind, val pos: BlockPos, val targetId: Int,
        val since: Int, val box: AABB, val hit: BlockHitResult? = null, val entityId: Int = -1,
        val hand: InteractionHand = InteractionHand.MAIN_HAND, val slot: Int = -1, val terrainBase: Boolean = false)
    private val actions = AuraActionQueue(this)
    private var terrainOwner = ActionCoordinator.crystalTerrainOwner()
    private val terrainActions = AuraActionQueue(terrainOwner)
    private val miner = AuraTerrainMiner(terrainOwner, terrainActions, ::allowedTerrainStart)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { renderSlideSpeed })
    private val motionPrediction = AuraMotionPrediction()
    private val damageCache = AuraDamageCache()
    private val occupancy = AuraEntityOccupancy()
    private val siteSearchMemo = HashMap<Boolean, Site?>()
    private val diagnosticState = CrystalAuraDiagnostics()
    private val diagnosticRenderer = CrystalAuraDiagnosticsRenderer({ isEnabled() && diagnostics }, { diagnosticState.snapshot })
    private var preferredPos: BlockPos? = null
    private var terrainPlan: CrystalTerrainPlan? = null
    private var nextTerrainComparison = 0
    private var terrainSearchTick = -1
    private var terrainSearchCandidates = emptyList<TerrainCandidate>()
    private var terrainAssessment: TerrainCandidate? = null
    private var terrainSearchReason = Reason.NO_TERRAIN
    private var siteSearchReason = Reason.DAMAGE
    private var terrainRetryAt = 0
    private var terrainAirSince: Int? = null
    private var terrainFailuresSeen = 0
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

    fun renderDiagnostics(graphics: GuiGraphicsExtractor) = diagnosticRenderer.render(graphics)

    private fun ping(player: LocalPlayer): Int = (player.connection.getPlayerInfo(player.uuid)?.latency ?: 150).coerceAtLeast(0)
    private fun predictionTicks(ticks: Double): Double = if (extrapolation) ticks.coerceIn(0.0, extrapolationLimit) else 0.0
    private fun placementLead(player: LocalPlayer, base: Boolean): Double = predictionTicks(
        (ceil(ping(player) / 50.0) * (if (base) 2 else 1) + (if (base) 2 else 1)
            + placeDelay * (if (base) 2 else 1) + breakDelay))
    private fun terrainCycle(player: LocalPlayer, miningTicks: Int, blocks: Int = 1): Int {
        val cycle = CrystalTerrainScoring.cycleTicks(miningTicks, ping(player), placeDelay.toInt(), breakDelay.toInt())
        if (cycle == Int.MAX_VALUE) return cycle
        return cycle + (blocks - 1).coerceAtLeast(0) * (ceil(ping(player).coerceAtMost(4000) / 50.0).toInt() + 1)
    }

    private fun remainingMiningTicks(player: LocalPlayer, plan: CrystalTerrainPlan): Int {
        val level = mc.level ?: return Int.MAX_VALUE
        var total = 0
        for (pos in plan.miningBlocks) {
            if (level.getBlockState(pos).isAir || pos == plan.pos && level.getBlockState(pos).`is`(Blocks.OBSIDIAN)) continue
            val ticks = miner.estimatedMiningTicks(player, pos)
            if (ticks !in 1..200) return Int.MAX_VALUE
            total += ticks
        }
        return total.coerceAtLeast(1)
    }

    private fun forecast(target: Player, lead: Double): AuraMotionPrediction.Snapshot = if (lead > 0.0)
        motionPrediction.predict(target, predictionTicks(lead)) else AuraMotionPrediction.Snapshot(target.position(), target.boundingBox)

    private fun damageAt(pos: Vec3, target: Player, base: BlockPos?, lead: Double, cleared: Set<BlockPos> = emptySet()): Float {
        if (target.isSpectator || target.isCreative && (target === mc.player || !targetNPCs)) return 0f
        val predicted = forecast(target, lead)
        return damageCache.damage(target, predicted, pos, base, cleared = cleared)
    }

    private fun selfDamageAt(pos: Vec3, player: LocalPlayer, base: BlockPos?, lead: Double): Float =
        maxOf(damageAt(pos, player, base, 0.0), damageAt(pos, player, base, lead))

    fun shouldKeepTerrainMining() = isEnabled() && basePlace && autoMineTerrain && miner.keepVanillaMining()
    fun hasPendingAction() = isEnabled() && (prepared != null || pending != null || miner.active)
    fun hasCombatAction() = isEnabled() && (prepared != null || pending != null)
    fun hasTerrainAction() = isEnabled() && miner.active
    fun hasBoostedTerrainAction() = isEnabled() && basePlace && autoMineTerrain
        && terrainPlan?.priorityBoosted == true && terrainPlan?.stage == CrystalTerrainPlan.Stage.MINE
        && terrainPlan?.recovery?.suspended == false && tick >= (terrainPlan?.recovery?.retryAt ?: 0)
        && prepared == null && pending == null && !AnchorAuraModule.hasInFlightAction()

    fun yieldToManualMining(pos: BlockPos) {
        miner.cancel()
        clearPrepared()
        actions.beginTick()
        terrainActions.beginTick()
        // A manual selection of the same block replaces the automatic operation entirely.
        if (terrainPlan?.let { pos == it.pos || pos in it.miningBlocks } == true) terrainPlan = null
    }

    @Commando(priority = 100)
    fun onUpdate(event: PlayerUpdateEvent) {
        diagnosticState.status(Reason.IDLE)
        actions.beginTick()
        terrainActions.beginTick()
        visual.beginTick()
        val player = mc.player
        val level = mc.level
        if (player == null || level == null || !player.connection.connection.isConnected) {
            reset()
            diagnosticState.status(Reason.NO_WORLD)
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
        damageCache.beginTick(level, ignoreTerrain)
        miner.beginTick()
        siteSearchMemo.clear()
        failedSites.entries.removeIf { tick >= it.value }
        currentTarget = findTarget(player)
        motionPrediction.update(player, tick)
        currentTarget?.let { motionPrediction.update(it, tick) }
        terrainPlan?.targetId?.takeIf { it != currentTarget?.id }?.let { id ->
            (level.getEntity(id) as? Player)?.let { motionPrediction.update(it, tick) }
        }
        val updateOwner = if (hasBoostedTerrainAction()) ActionCoordinator.boostedCrystalTerrainOwner() else this
        if (!ActionCoordinator.canAct(updateOwner) || player.isUsingItem || player.isSpectator
            || player.containerMenu !== player.inventoryMenu || !player.containerMenu.carried.isEmpty
            || InventoryMoveModule.isInventoryActionSlowed()) {
            miner.cancel()
            terrainPlan?.recovery?.pause(tick)
            clearPrepared()
            val conflict = ActionCoordinator.blockReason(updateOwner)
            diagnosticState.status(if (conflict != null) Reason.PRIORITY else Reason.CONTEXT,
                conflict ?: when {
                    player.isUsingItem -> "Использование предмета"
                    player.isSpectator -> "Режим наблюдателя"
                    InventoryMoveModule.isInventoryActionSlowed() -> "Ожидание действия инвентаря"
                    else -> "Инвентарь занят"
                })
            return
        }
        val target = currentTarget
        // Never spend another crystal while spawn, base placement or destruction is unconfirmed.
        if (awaitingConfirmation(player, level)) return
        if (target != null || terrainPlan != null)
            occupancy.update(level.entitiesForRendering(), AABB(player.blockPosition()).inflate(maxOf(placeRange, breakRange) + 4.0))

        val crystal = target?.let { findCrystal(player, it, AnticheatManager.getHealth(it)) }
        if (crystal != null) {
            terrainPlan?.recovery?.pause(tick)
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
        if (target != null && terrainPlan != null && useAvailableSite(player, target)) return
        // Keep the selected excavation only while no ready placement is available.
        if (continueTerrain(player, level)) return
        if (target == null) { miner.cancel(); clearPrepared(); diagnosticState.status(Reason.NO_TARGET); return }
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
        if (placeTimer < placeDelay.toInt()) { diagnosticState.status(Reason.DELAY); return }
        val hasCrystal = player.offhandItem.`is`(Items.END_CRYSTAL) || InventoryUtil.findHotbarSlot(Items.END_CRYSTAL) >= 0
        if (!hasCrystal) { diagnosticState.status(Reason.NO_CRYSTALS); return }
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
            if (terrainPlan != null) return
            if (tick < terrainRetryAt) { diagnosticState.status(Reason.DELAY, "Пауза после неудачного AutoMine"); return }
            if (!hasObsidian) { diagnosticState.status(Reason.NO_OBSIDIAN); return }
            val site = findTerrainSite(player, target, targetHp) ?: run {
                diagnosticState.status(terrainSearchReason)
                return
            }
            startTerrain(site.plan)
        } else diagnosticState.status(siteSearchReason)
    }

    private fun canCompareTerrain(player: LocalPlayer): Boolean = basePlace && autoMineTerrain
        && (player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0)
        && (player.offhandItem.`is`(Items.END_CRYSTAL) || InventoryUtil.findHotbarSlot(Items.END_CRYSTAL) >= 0)

    private fun availableCrystalDamage(player: LocalPlayer, target: Player, hp: Float): Float = maxOf(
        findSite(player, target, hp, base = false, preferCached = false)?.damage ?: 0f,
        findSite(player, target, hp, base = true, preferCached = false)?.damage ?: 0f)

    private fun gainJustifiesMining(damage: Float, immediateDamage: Float, hp: Float, miningTicks: Int): Boolean {
        if (AnchorAuraModule.hasInFlightAction() || immediateDamage > 0f || miningTicks > 20) return false
        val availableDamage = maxOf(immediateDamage, AnchorAuraModule.availableDamage())
        return CrystalTerrainPriority.shouldBoost(damage, availableDamage, hp, terrainDamageGain.toFloat(), miningTicks)
    }

    private fun maybeImproveTerrain(player: LocalPlayer, target: Player, hp: Float, immediateDamage: Float? = null): Boolean {
        if (terrainPlan != null || tick < terrainRetryAt || !canCompareTerrain(player)
            || tick < nextTerrainComparison || AnchorAuraModule.hasInFlightAction()) return false
        nextTerrainComparison = tick + 4
        val damage = immediateDamage ?: availableCrystalDamage(player, target, hp)
        if (damage > 0f) return false
        // Rank every eligible quick candidate; the fastest one need not meet the damage gain.
        findTerrainSite(player, target, hp)
        val quick = terrainSearchCandidates.filter { it.miningTicks <= 20 }
        if (quick.isEmpty() || AnchorAuraModule.hasInFlightAction()) return false
        val available = maxOf(damage, AnchorAuraModule.availableDamage())
        val candidate = quick.filter {
            CrystalTerrainPriority.shouldBoost(it.predictedDamage, available, hp, terrainDamageGain.toFloat(), it.miningTicks)
        }.maxByOrNull { it.score } ?: return false
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
                val miningTicks = remainingMiningTicks(player, plan)
                val lead = predictionTicks(terrainCycle(player, miningTicks, plan.miningBlocks.size).toDouble())
                if (gainJustifiesMining(terrainDamage(plan.pos, target, lead), immediate, hp,
                        miningTicks)) plan.boostPriority()
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
        terrainAssessment = terrainSearchCandidates.firstOrNull { it.plan === plan }
        terrainFailuresSeen = miner.confirmationFailures
        terrainAirSince = null
        val player = mc.player ?: return
        val level = mc.level ?: return
        continueTerrain(player, level)
    }

    private fun describeTerrain(plan: CrystalTerrainPlan) {
        terrainAssessment?.takeIf { it.plan === plan }?.let {
            diagnosticState.candidate(plan.miningPos ?: plan.pos, it.damage, it.predictedDamage, it.miningTicks, it.predictionTicks)
        }
    }

    private fun cancelTerrain() {
        miner.cancel()
        terrainActions.beginTick()
        if (prepared?.terrainBase == true) clearPrepared()
        terrainPlan = null
        terrainAssessment = null
        terrainAirSince = null
        terrainOwner = ActionCoordinator.crystalTerrainOwner()
        miner.changeOwner(terrainOwner)
    }

    private fun useAvailableSite(player: LocalPlayer, target: Player): Boolean {
        val hasCrystal = player.offhandItem.`is`(Items.END_CRYSTAL) || InventoryUtil.findHotbarSlot(Items.END_CRYSTAL) >= 0
        if (!hasCrystal) return false
        val hp = AnticheatManager.getHealth(target)
        val crystalSite = findSite(player, target, hp, base = false)
        val hasObsidian = basePlace && (player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0)
        val baseSite = if (crystalSite == null && hasObsidian) findSite(player, target, hp, base = true) else null
        val site = crystalSite ?: baseSite ?: return false
        cancelTerrain()
        clearPrepared()
        preferredPos = site.pos
        schedulePlace(player, site, if (crystalSite != null) Items.END_CRYSTAL else Items.OBSIDIAN,
            if (crystalSite != null) PendingKind.CRYSTAL else PendingKind.BASE)
        return true
    }

    private fun abandonTerrain(reason: Reason, detail: String) {
        val pos = terrainPlan?.pos
        cancelTerrain()
        pos?.let { failedSites[it] = tick + 60 }
        terrainRetryAt = tick + 20
        diagnosticState.status(reason, detail, pos)
    }

    private fun continueTerrain(player: LocalPlayer, level: ClientLevel): Boolean {
        val plan = terrainPlan ?: return false
        describeTerrain(plan)
        val state = level.getBlockState(plan.pos)
        if (state.`is`(Blocks.OBSIDIAN)) plan.observeServerState(plan.pos, state)
        if (plan.stage == CrystalTerrainPlan.Stage.DONE) {
            cancelTerrain()
            preferredPos = plan.pos
            return false
        }
        if (!basePlace || !autoMineTerrain) {
            abandonTerrain(Reason.CONTEXT, "AutoMine отключён")
            return false
        }
        if (miner.confirmationFailures != terrainFailuresSeen) {
            terrainFailuresSeen = miner.confirmationFailures
            plan.recovery.miningFailed(tick)
        }
        val minePos = plan.miningPos
        if (miner.active && miner.position != minePos) {
            miner.cancel()
            terrainActions.beginTick()
            terrainAirSince = null
        }
        val actionPos = minePos ?: plan.pos
        val reach = minOf(placeRange, player.blockInteractionRange())
        val reachable = level.worldBorder.isWithinBounds(actionPos) && level.hasChunk(actionPos.x shr 4, actionPos.z shr 4)
            && player.eyePosition.distanceToSqr(RotationUtil.getNearestPoint(player.eyePosition, AABB(actionPos))) <= reach * reach
        val hasObsidian = player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0
        val estimated = minePos?.let { miner.estimatedMiningTicks(player, it).coerceAtMost(60) } ?: 0
        val stallTimeout = if (!hasObsidian) 20 else if (plan.stage == CrystalTerrainPlan.Stage.MINE)
            (estimated * 2 + ping(player) / 50 + 20).coerceIn(40, 160) else 40
        when (plan.recovery.observe(tick, plan.stage, reachable, miner.progress, stallTimeout, minePos)) {
            CrystalTerrainRecovery.Decision.ABANDON -> {
                abandonTerrain(if (reachable) Reason.SERVER else Reason.UNREACHABLE,
                    if (reachable) "Лимит попыток или тайм-аут; позиция освобождена" else "Долго вне дистанции; позиция освобождена")
                return false
            }
            CrystalTerrainRecovery.Decision.SUSPEND -> {
                miner.cancel()
                if (prepared?.terrainBase == true) clearPrepared()
                diagnosticState.status(Reason.UNREACHABLE, "План временно приостановлен", plan.pos)
                return false
            }
            CrystalTerrainRecovery.Decision.BACKOFF -> {
                diagnosticState.status(Reason.SERVER, "Повтор копания после неудачного STOP", plan.pos)
                return true
            }
            else -> Unit
        }
        if (!hasObsidian) {
            miner.cancel()
            clearPrepared()
            visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.BASE)
            diagnosticState.status(Reason.NO_OBSIDIAN, pos = plan.pos)
            return true
        }
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        plan.retry(tick, (ping.coerceAtLeast(0) / 50 + 5).coerceIn(5, 80))
        if (plan.stage == CrystalTerrainPlan.Stage.CONFIRM) {
            diagnosticState.status(Reason.SERVER, "Подтверждение обсидиана", plan.pos)
            visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.BASE)
            return true
        }
        if (plan.stage == CrystalTerrainPlan.Stage.MINE) {
            val miningPos = minePos ?: return false
            val miningState = level.getBlockState(miningPos)
            clearPrepared()
            if (!plan.miningStarted && !allowedTerrainStart(miningPos)) {
                cancelTerrain()
                return false
            }
            refreshTerrainPriority(player, plan)
            if (!ActionCoordinator.canAct(terrainOwner)) {
                miner.cancel()
                plan.recovery.pause(tick)
                diagnosticState.status(Reason.PRIORITY, ActionCoordinator.blockReason(terrainOwner).orEmpty(), miningPos)
                return true
            }
            if (miningState.isAir) {
                if (terrainAirSince == null) terrainAirSince = tick
                if (tick - terrainAirSince!! >= (ping(player) / 50 + 5).coerceIn(5, 80)) {
                    plan.observeServerState(miningPos, miningState)
                    terrainAirSince = null
                    return continueTerrain(player, level)
                }
                diagnosticState.status(Reason.SERVER, "Подтверждение разрушения блока", miningPos)
                return true
            }
            terrainAirSince = null
            if (!miner.canMine(player, miningPos, placeRange)) {
                abandonTerrain(if (!miner.hasTool(player, miningPos)) Reason.NO_TOOL else Reason.NO_TERRAIN,
                    "Выбранный блок больше нельзя сломать")
                return false
            }
            if (!miner.active) miner.select(miningPos)
            if (miner.active) miner.update(placeRange, terrainPacketMine, rotations, getMoveFixMode(), wallCheck)
            diagnosticState.status(when (miner.status) {
                AuraTerrainMiner.Status.CONFIRM -> Reason.SERVER
                AuraTerrainMiner.Status.SLOT -> Reason.TOOL_SWAP
                AuraTerrainMiner.Status.FACE -> Reason.MINE_FACE
                AuraTerrainMiner.Status.ROTATION, AuraTerrainMiner.Status.START -> Reason.ROTATION
                else -> Reason.MINING
            }, pos = miningPos, progress = miner.progress)
            visual.show(AABB(miningPos), AuraTargetRenderer.Kind.MINE, miner.progress)
            return true
        }
        miner.cancel()
        if (!level.getBlockState(plan.pos.above()).isAir) {
            clearPrepared()
            if (miner.canMine(player, plan.pos.above(), placeRange)
                && !PacketMineModule.targetsPosition(plan.pos.above())) {
                plan.restartMining(plan.pos.above())
                return continueTerrain(player, level)
            }
            abandonTerrain(Reason.CRYSTAL_BLOCKED, "Место для кристалла перекрыто")
            return false
        }
        if (!state.canBeReplaced()) {
            clearPrepared()
            // A server correction may restore the original terrain. Retry this cell alone.
            if (miner.canMine(player, plan.pos, placeRange)) {
                plan.restartMining()
                return continueTerrain(player, level)
            }
            abandonTerrain(Reason.NO_TERRAIN, "В ячейке появился неподходящий блок")
            return false
        }
        val existing = prepared
        if (existing?.terrainBase == true && existing.pos == plan.pos && validPrepared(player, currentTarget, existing)) {
            prepare(player)
            return true
        }
        clearPrepared()
        visual.show(AABB(plan.pos), AuraTargetRenderer.Kind.BASE)
        val hit = actions.placementHit(plan.pos, placeRange, wallCheck, rotations) ?: run {
            diagnosticState.status(Reason.PLACE_FACE, "Повтор плейса в выбранную ячейку", plan.pos)
            return true
        }
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
                if (confirmed) terrainPlan?.observeServerState(current.pos, level.getBlockState(current.pos))
                else terrainPlan?.retry(tick, timeout)
            } else if (!confirmed && current.kind != PendingKind.BREAK) failedSites[current.pos] = tick + 2
            return false
        }
        visual.show(current.box, when (current.kind) {
            PendingKind.BREAK -> AuraTargetRenderer.Kind.BREAK
            PendingKind.BASE -> AuraTargetRenderer.Kind.BASE
            PendingKind.CRYSTAL -> AuraTargetRenderer.Kind.PLACE
        })
        diagnosticState.status(Reason.SERVER, when (current.kind) {
            PendingKind.BREAK -> "Подтверждение взрыва"
            PendingKind.BASE -> "Подтверждение обсидиана"
            PendingKind.CRYSTAL -> "Подтверждение появления кристалла"
        }, current.pos)
        if (current.kind != PendingKind.BASE || terrainPlan?.pos != current.pos) terrainPlan?.recovery?.pause(tick)
        terrainPlan?.let { describeTerrain(it) }
        return true
    }

    private fun qualifies(pos: Vec3, player: LocalPlayer, target: Player, targetHp: Float, base: BlockPos? = null,
                          lead: Double = 0.0): Boolean {
        if (!target.isAlive) return false
        if (selfDamageAt(pos, player, base, lead) > maxSelfDamage.toFloat()) return false
        val damage = damageAt(pos, target, base, lead)
        return damage >= minDamage.toFloat() || lethalOverride && damage >= targetHp
    }

    private fun findCrystal(player: LocalPlayer, target: Player, targetHp: Float, fresh: Boolean = false): EndCrystal? {
        val level = mc.level ?: return null
        var best: EndCrystal? = null
        var bestDamage = 0f
        val lead = predictionTicks(ping(player) / 100.0)
        val entities = if (fresh) level.entitiesForRendering() else occupancy.endCrystals
        for (entity in entities) {
            if (entity !is EndCrystal || !entity.isAlive) continue
            if (AuraActionQueue.visibleCrystalPoint(player, entity, breakRange, wallCheck) == null) continue
            val damage = damageAt(entity.position(), target, null, lead)
            if (damage <= bestDamage || damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)) continue
            if (selfDamageAt(entity.position(), player, null, lead) > maxSelfDamage.toFloat()) continue
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
        if (onlyPos == null && !preferCached && siteSearchMemo.containsKey(base)) return siteSearchMemo[base]
        var best: Site? = null
        var bestDamage = 0f
        var geometry = 0
        var clear = 0
        var damagePass = 0
        var selfPass = 0
        val lead = placementLead(player, base)
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
            // Locally predicted air in our excavation is not a ready site until mining is confirmed.
            if (terrainPlan?.let {
                    it.stage == CrystalTerrainPlan.Stage.MINE && (it.pos == pos || it.miningPos == pos || it.miningPos == pos.above())
                } == true) continue
            if (base && PacketMineModule.targetsPosition(pos)) continue
            val state = level.getBlockState(pos)
            if (base) {
                if (pos.y >= target.blockPosition().y || !state.canBeReplaced()) continue
            } else if (!state.`is`(Blocks.OBSIDIAN) && !state.`is`(Blocks.BEDROCK)) continue
            geometry++
            if (!level.getBlockState(pos.above()).isAir) continue
            if (player.eyePosition.distanceToSqr(RotationUtil.getNearestPoint(player.eyePosition, AABB(pos))) > placeRange * placeRange) continue
            val futureCrystal = AABB(pos.x - 0.5, pos.y + 1.0, pos.z - 0.5, pos.x + 1.5, pos.y + 3.0, pos.z + 1.5)
            if (player.eyePosition.distanceToSqr(RotationUtil.getNearestPoint(player.eyePosition, futureCrystal)) > attackReach * attackReach) continue
            if (occupancy.crystalBlocked(pos)) continue
            if (base && occupancy.baseBlocked(pos)) continue
            clear++
            val damage = damageAt(explosionPos(pos), target, if (base) pos else null, lead)
            if (damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)) continue
            damagePass++
            if (damage <= bestDamage && pos != preferredPos) continue
            if (selfDamageAt(explosionPos(pos), player, if (base) pos else null, lead) > maxSelfDamage.toFloat()) continue
            selfPass++
            val hit = if (base) AuraActionQueue.placementHit(player, pos, placeRange, wallCheck)
                else AuraActionQueue.visibleHit(player, pos, placeRange, wallCheck = wallCheck)
            if (hit == null) continue
            val site = Site(pos, hit, damage)
            // Reuse a validated site immediately; a full scan is only needed when it stops working.
            if (preferCached && pos == preferredPos) return site
            if (damage > bestDamage) { best = site; bestDamage = damage }
        }
        if (best == null) siteSearchReason = when {
            selfPass > 0 -> Reason.PLACE_FACE
            damagePass > 0 -> Reason.SELF_DAMAGE
            clear == 0 && geometry > 0 -> Reason.CRYSTAL_BLOCKED
            else -> Reason.DAMAGE
        }
        if (onlyPos == null && !preferCached) siteSearchMemo[base] = best
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
        diagnosticState.status(when (action.kind) {
            PendingKind.BREAK -> Reason.BREAK
            PendingKind.BASE -> Reason.BASE
            PendingKind.CRYSTAL -> Reason.PLACE
        }, pos = action.pos)
        readyTick = -1
        // Lease the item before vanilla physics/sprint and other pre-movement actions lock swaps.
        if (silentSlots || action.hand == InteractionHand.OFF_HAND || action.kind == PendingKind.BREAK) {
            if (!SilentSwapUtil.prepareSlot(action.slot, 1, this)) { diagnosticState.status(Reason.TOOL_SWAP, pos = action.pos); return }
        } else {
            InventoryUtil.swapToSlot(action.slot)
            if (player.inventory.selectedSlot != action.slot || SilentSwapUtil.serverSlot != action.slot) {
                diagnosticState.status(Reason.TOOL_SWAP, pos = action.pos); return
            }
        }
        val aimed = if (action.kind == PendingKind.BREAK) {
            val crystal = mc.level?.getEntity(action.entityId) as? EndCrystal ?: return
            actions.prepareCrystal(crystal, breakRange, rotations, getMoveFixMode(), wallCheck)
        } else {
            val hit = if (action.kind == PendingKind.BASE) actions.placementHit(action.pos, placeRange, wallCheck, rotations)
                else actions.visibleHit(action.pos, placeRange, wallCheck, action.hit?.location)
            if (hit == null) { diagnosticState.status(Reason.PLACE_FACE, pos = action.pos); clearPrepared(); return }
            // A moving player can expose another support or another part of the same face.
            prepared = action.copy(hit = hit)
            actions.prepareBlock(hit, placeRange, rotations, getMoveFixMode(),
                exactFace = action.kind == PendingKind.BASE, wallCheck = wallCheck)
        }
        if (!aimed) { diagnosticState.status(Reason.ROTATION, pos = action.pos); return }
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
                && level.getBlockState(action.pos.above()).isAir
                && level.getEntities(null, AABB(action.pos)).none { !it.isRemoved && it.blocksBuilding }
        }
        if (target == null) return false
        if (!target.isAlive || target.isRemoved || FriendRepository.isFriend(target)
            || player.distanceToSqr(target) > targetRange * targetRange) return false
        if (action.kind == PendingKind.BREAK) {
            val crystal = level.getEntity(action.entityId) as? EndCrystal ?: return false
            return crystal.isAlive && qualifies(crystal.position(), player, target, AnticheatManager.getHealth(target),
                lead = predictionTicks(ping(player) / 100.0))
        }
        val item = if (action.kind == PendingKind.BASE) Items.OBSIDIAN else Items.END_CRYSTAL
        val stack = if (action.hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(action.slot)
        if (!stack.`is`(item) || !level.worldBorder.isWithinBounds(action.pos)) return false
        if (!qualifies(explosionPos(action.pos), player, target, AnticheatManager.getHealth(target),
                action.pos.takeIf { action.kind == PendingKind.BASE }, placementLead(player, action.kind == PendingKind.BASE))) return false
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
        if (readyTick != tick || player !== localPlayer || level !== world || pending != null) return
        if (!ActionCoordinator.canAct(this)) {
            diagnosticState.status(Reason.PRIORITY, ActionCoordinator.blockReason(this).orEmpty(), action.pos)
            return
        }
        if (player.isUsingItem || player.isSpectator
            || player.containerMenu !== player.inventoryMenu
            || !player.containerMenu.carried.isEmpty || InventoryMoveModule.isInventoryActionSlowed()) {
            diagnosticState.status(Reason.CONTEXT, "Действие приостановлено", action.pos)
            return
        }
        if (SilentSwapUtil.serverSlot != action.slot) { diagnosticState.status(Reason.TOOL_SWAP, pos = action.pos); return }
        if (!validPrepared(player, target, action)) {
            diagnosticState.status(Reason.CONTEXT, "Позиция или прогноз изменились; повторный поиск", action.pos)
            clearPrepared(); return
        }
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(this)) {
            accepted = false
            try {
                if (action.kind == PendingKind.BREAK) {
                    if (breakTimer < breakDelay.toInt()) { diagnosticState.status(Reason.DELAY, pos = action.pos); return@withOwner }
                    val crystal = level.getEntity(action.entityId) as? EndCrystal ?: return@withOwner
                    if (!actions.crystalReady(crystal, breakRange, wallCheck)) { diagnosticState.status(Reason.ROTATION, pos = action.pos); return@withOwner }
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
                    if (!action.terrainBase && placeTimer < placeDelay.toInt()) { diagnosticState.status(Reason.DELAY, pos = action.pos); return@withOwner }
                    // A crystal can spawn between selection and sending. It still wins over placement.
                    if (!action.terrainBase && target != null && findCrystal(player, target, AnticheatManager.getHealth(target), fresh = true) != null) {
                        clearPrepared()
                        return@withOwner
                    }
                    val hit = actions.blockHit(action.hit ?: return@withOwner, placeRange,
                        exactFace = action.kind == PendingKind.BASE, wallCheck = wallCheck) ?: run {
                            diagnosticState.status(Reason.ROTATION, "Серверный взгляд ещё не попал в грань", action.pos)
                            return@withOwner
                        }
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
                    if (action.terrainBase) terrainPlan?.let { it.placementSent(tick); it.recovery.placementSent() }
                    pending = Pending(action.kind, action.pos, tick, action.entityId, action.box)
                    visual.pulse()
                    clearPrepared()
                } else diagnosticState.status(Reason.CONTEXT, "Пакет действия не отправлен", action.pos)
            } finally { sending = null }
        }
    }

    @Commando
    fun onPacketSent(event: PacketSentEvent) {
        if (!mc.isSameThread) return
        if (event.packet === sending) accepted = true
        val packet = event.packet as? ServerboundPlayerActionPacket ?: return
        if (packet.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
            && packet.pos == terrainPlan?.miningPos && ActionCoordinator.resolveOwner() == terrainOwner)
            terrainPlan?.miningStartAccepted()
    }

    private fun allowedTerrainStart(pos: BlockPos): Boolean {
        val plan = terrainPlan ?: return false
        if (plan.miningPos != pos) return false
        if (plan.miningStarted) return true
        val player = mc.player ?: return false
        val target = mc.level?.getEntity(plan.targetId) as? Player ?: return false
        if (!target.isAlive || target.isRemoved || FriendRepository.isFriend(target)
            || player.distanceToSqr(target) > targetRange * targetRange) {
            diagnosticState.status(Reason.NO_TARGET, pos = pos)
            return false
        }
        val miningTicks = remainingMiningTicks(player, plan)
        val cycle = terrainCycle(player, miningTicks, plan.miningBlocks.size)
        val lead = predictionTicks(cycle.toDouble())
        val hp = AnticheatManager.getHealth(target)
        val current = terrainDamage(plan.pos, target)
        val predicted = terrainDamage(plan.pos, target, lead)
        terrainAssessment = terrainAssessment?.copy(damage = current, predictedDamage = predicted,
            miningTicks = miningTicks, cycleTicks = cycle, predictionTicks = lead)
        diagnosticState.candidate(pos, current, predicted, miningTicks, lead)
        val reason = when {
            miningTicks !in 1..200 -> Reason.SLOW_TERRAIN
            current < minDamage && !(lethalOverride && current >= hp) -> Reason.DAMAGE
            !CrystalTerrainScoring.forecastUseful(current, predicted, minDamage.toFloat(), hp, lethalOverride) -> Reason.TARGET_MOVING
            maxOf(terrainDamage(plan.pos, player), terrainDamage(plan.pos, player, lead)) > maxSelfDamage -> Reason.SELF_DAMAGE
            forecast(target, lead).box.intersects(crystalBox(plan.pos)) -> Reason.CRYSTAL_BLOCKED
            else -> terrainGeometryFailure(player, target, plan, lead)
        }
        if (reason != null) diagnosticState.status(reason, pos = pos)
        return reason == null
    }

    /** Recheck from the latest eyes immediately before START, including the future WallCheck scene. */
    private fun terrainMiningFaces(player: LocalPlayer, blocks: List<BlockPos>): Boolean {
        val level = mc.level ?: return false
        val cleared = HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>()
        for (pos in blocks) {
            if (level.getBlockState(pos).isAir) continue
            if (PacketMineModule.targetsPosition(pos) || !miner.canMine(player, pos, placeRange)
                || terrainActions.visibleHit(pos, placeRange, wallCheck, rotations = rotations, overrides = cleared) == null) return false
            cleared[pos] = Blocks.AIR.defaultBlockState()
        }
        return true
    }

    private fun terrainGeometryFailure(player: LocalPlayer, target: Player, plan: CrystalTerrainPlan, lead: Double): Reason? {
        val level = mc.level ?: return Reason.NO_WORLD
        val pos = plan.pos
        if (!terrainMiningFaces(player, plan.miningBlocks)) return Reason.MINE_FACE
        if (level.getEntities(null, crystalBox(pos)).any { it.isAlive }
            || level.getEntities(null, AABB(pos)).any { !it.isRemoved && it.blocksBuilding }) return Reason.CRYSTAL_BLOCKED
        val cleared = plan.clearedStates
        if (actions.placementHit(pos, placeRange, wallCheck, rotations, cleared) == null) return Reason.PLACE_FACE
        val built = plan.builtStates
        val futureCrystal = AABB(pos.x - 0.5, pos.y + 1.0, pos.z - 0.5, pos.x + 1.5, pos.y + 3.0, pos.z + 1.5)
        if (forecast(target, lead).box.intersects(crystalBox(pos))
            || actions.visibleHit(pos, placeRange, wallCheck, rotations = rotations, overrides = built) == null
            || actions.visibleBoxPoint(futureCrystal, breakRange, wallCheck, rotations, built) == null) return Reason.CRYSTAL_BLOCKED
        return null
    }

    @Commando(priority = -128)
    fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.cancel) return
        val plan = terrainPlan ?: return
        val player = localPlayer ?: return
        val level = world ?: return
        fun block(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
            if (pos != plan.pos && pos !in plan.miningBlocks) return
            mc.execute {
                if (!event.cancel && terrainPlan === plan && mc.player === player && mc.level === level)
                    plan.observeServerState(pos, state)
            }
        }
        when (val packet = event.packet) {
            is ClientboundBlockUpdatePacket -> block(packet.pos, packet.blockState)
            is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, state -> block(pos.immutable(), state) }
        }
    }

    private fun findTerrainSite(player: LocalPlayer, target: Player, targetHp: Float, quickOnly: Boolean = false): TerrainCandidate? {
        if (terrainSearchTick == tick) return terrainSearchCandidates.filter { !quickOnly || it.miningTicks <= 20 }.maxByOrNull { it.score }
        terrainSearchTick = tick
        terrainSearchCandidates = emptyList()
        val level = mc.level ?: return null
        val viable = ArrayList<TerrainCandidate>()
        val search = CrystalAuraDiagnostics.Search()
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
            if (pos.y >= target.blockPosition().y) continue
            val state = level.getBlockState(pos)
            val above = pos.above()
            if (!canBase || above.y >= level.maxY) continue
            val blocks = buildList {
                if (!state.canBeReplaced()) add(pos)
                if (!level.getBlockState(above).isAir) add(above)
            }
            if (blocks.isEmpty() || blocks.any { block ->
                    val blockState = level.getBlockState(block)
                    val hardness = blockState.getDestroySpeed(level, block)
                    PacketMineModule.targetsPosition(block) || !hardness.isFinite() || hardness !in 0f..3f
                        || blockState.hasBlockEntity() || !blockState.fluidState.isEmpty
                }) continue
            search.terrain++
            if (blocks.any { !miner.hasTool(player, it) }) continue
            search.tools++
            if (blocks.any { !miner.canMine(player, it, placeRange) }) continue
            search.accessible++
            val miningTicks = blocks.sumOf { miner.estimatedMiningTicks(player, it).coerceAtMost(201) }
            if (miningTicks !in 1..200) continue
            search.speed++
            val futureCrystal = AABB(pos.x - 0.5, pos.y + 1.0, pos.z - 0.5, pos.x + 1.5, pos.y + 3.0, pos.z + 1.5)
            if (player.eyePosition.distanceToSqr(RotationUtil.getNearestPoint(player.eyePosition, futureCrystal)) > attackReach * attackReach) continue
            if (occupancy.crystalBlocked(pos) || occupancy.baseBlocked(pos)) continue
            search.space++
            val damage = terrainDamage(pos, target)
            if (damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)) continue
            search.damage++
            val cycle = terrainCycle(player, miningTicks, blocks.size)
            val lead = predictionTicks(cycle.toDouble())
            val predictedDamage = terrainDamage(pos, target, lead)
            if (!CrystalTerrainScoring.forecastUseful(damage, predictedDamage, minDamage.toFloat(), targetHp, lethalOverride)) continue
            search.predictedDamage++
            if (maxOf(terrainDamage(pos, player), terrainDamage(pos, player, lead)) > maxSelfDamage) continue
            search.safe++
            // Try both orders: the first mined block may expose the face of the second.
            val order = listOf(blocks, blocks.asReversed()).firstOrNull { terrainMiningFaces(player, it) } ?: continue
            search.mineFaces++
            val plan = CrystalTerrainPlan(pos, target.id, order)
            val cleared = plan.clearedStates
            val baseHit = actions.placementHit(pos, placeRange, wallCheck, rotations, cleared) ?: continue
            search.placeFaces++
            val built = plan.builtStates
            if (forecast(target, lead).box.intersects(crystalBox(pos))
                || actions.visibleHit(pos, placeRange, wallCheck, rotations = rotations, overrides = built) == null
                || actions.visibleBoxPoint(futureCrystal, breakRange, wallCheck, rotations, built) == null) continue
            search.crystalFaces++
            val shape = (cleared[baseHit.blockPos] ?: level.getBlockState(baseHit.blockPos)).getShape(level, baseHit.blockPos)
            val margin = CrystalTerrainScoring.faceMargin(baseHit, shape.toAabbs().map { it.move(baseHit.blockPos) })
            val reachMargin = minOf(placeRange, player.blockInteractionRange()) - player.eyePosition.distanceTo(baseHit.location)
            val score = CrystalTerrainScoring.score(predictedDamage, cycle, margin, reachMargin)
            viable.add(TerrainCandidate(plan, damage, predictedDamage, miningTicks, cycle, lead, score))
        }
        terrainSearchCandidates = viable
        terrainSearchReason = search.reason()
        return viable.filter { !quickOnly || it.miningTicks <= 20 }.maxByOrNull { it.score }
    }

    private fun terrainDamage(pos: BlockPos, target: Player, lead: Double = 0.0): Float {
        return damageAt(explosionPos(pos), target, pos, lead, cleared = setOf(pos.above()))
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
        terrainSearchCandidates = emptyList()
        terrainAssessment = null
        terrainSearchReason = Reason.NO_TERRAIN
        siteSearchReason = Reason.DAMAGE
        terrainRetryAt = 0
        terrainAirSince = null
        terrainFailuresSeen = miner.confirmationFailures
        motionPrediction.clear()
        damageCache.beginTick(null, ignoreTerrain)
        occupancy.clear()
        siteSearchMemo.clear()
        diagnosticState.reset()
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
