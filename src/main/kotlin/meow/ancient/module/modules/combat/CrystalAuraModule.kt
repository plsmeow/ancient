package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.combat.AuraTerrainMiner
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
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPunchPacket
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
    val silentSlots by boolean("Silent Slots", true)
    val ignoreTerrain by boolean("Ignore Terrain", false, "Treats all blocks as indestructible when calculating explosion damage")
    val basePlace by boolean("Base Place", true, "Places obsidian as base for crystals if none available")
    val lethalOverride by boolean("Lethal Override", true)
    val targetNPCs by boolean("Target NPCs", true, "Includes practice NPCs reported as creative players")

    val autoMineTerrain by boolean("AutoMine Terrain", false, "Clears a crystal site only with a suitable hotbar tool")
    val terrainPacketMine by boolean("Terrain PacketMine", false, "Uses packet mining instead of vanilla block breaking",
        isVisible = { autoMineTerrain })
    val render by boolean("Render", true, "Animated placement, crystal and terrain targets")
    val renderSlideSpeed by number("Render Slide Speed", 14.0, 5.0, 30.0, 1.0, 0, isVisible = { render })

    private enum class PendingKind { CRYSTAL, BASE, BREAK }
    private data class Pending(val kind: PendingKind, val pos: BlockPos, val sentTick: Int, val entityId: Int = -1,
                               val box: AABB = AABB(pos))
    private data class Site(val pos: BlockPos, val hit: BlockHitResult)
    private data class Prepared(val kind: PendingKind, val pos: BlockPos, val targetId: Int,
        val since: Int, val box: AABB, val hit: BlockHitResult? = null, val entityId: Int = -1,
        val hand: InteractionHand = InteractionHand.MAIN_HAND, val slot: Int = -1)
    private val actions = AuraActionQueue(this)
    private val miner = AuraTerrainMiner(this, actions)
    private val visual = AuraTargetRenderer({ isEnabled() && render }, { renderSlideSpeed })
    private var preferredPos: BlockPos? = null
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

    fun shouldKeepTerrainMining() = isEnabled() && autoMineTerrain && miner.keepVanillaMining()

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        actions.beginTick()
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
        if (!ActionCoordinator.canAct(this) || player.isUsingItem || player.isSpectator || mc.gui.screen() != null
            || player.containerMenu !== player.inventoryMenu || !player.containerMenu.carried.isEmpty
            || InventoryMoveModule.isInventoryActionSlowed()) {
            miner.cancel()
            clearPrepared()
            return
        }
        val target = currentTarget ?: run { miner.cancel(); clearPrepared(); return }
        val targetHp = AnticheatManager.getHealth(target)
        // Never spend another crystal while spawn, base placement or destruction is unconfirmed.
        if (awaitingConfirmation(player, level)) return

        val crystal = findCrystal(player, target, targetHp)
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
        val existing = prepared
        if (existing != null) {
            if (existing.targetId == target.id && validPrepared(player, target, existing) && tick - existing.since < 6) {
                prepare(player)
                return
            }
            if (tick - existing.since >= 6) failedSites[existing.pos] = tick + 2
            clearPrepared()
        }
        if (miner.active && autoMineTerrain) {
            miner.position?.let { visual.show(AABB(it), AuraTargetRenderer.Kind.MINE) }
            miner.update(placeRange, terrainPacketMine, rotations, getMoveFixMode())
            return
        }
        miner.cancel()
        if (placeTimer < placeDelay.toInt()) return
        val hasCrystal = player.offhandItem.`is`(Items.END_CRYSTAL) || InventoryUtil.findHotbarSlot(Items.END_CRYSTAL) >= 0
        if (!hasCrystal) return
        val crystalSite = findSite(player, target, targetHp, base = false)
        if (crystalSite != null) {
            schedulePlace(player, crystalSite, Items.END_CRYSTAL, PendingKind.CRYSTAL)
            return
        }
        if (basePlace && (player.offhandItem.`is`(Items.OBSIDIAN) || InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0)) {
            val baseSite = findSite(player, target, targetHp, base = true)
            if (baseSite != null) {
                schedulePlace(player, baseSite, Items.OBSIDIAN, PendingKind.BASE)
                return
            }
        }
        if (autoMineTerrain) {
            val site = findTerrainSite(player, target, targetHp) ?: return
            visual.show(AABB(site), AuraTargetRenderer.Kind.MINE)
            if (miner.select(site)) miner.update(placeRange, terrainPacketMine, rotations, getMoveFixMode())
        }
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
            if (!confirmed && current.kind != PendingKind.BREAK) failedSites[current.pos] = tick + 2
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
            if (AuraActionQueue.visibleCrystalPoint(player, entity, breakRange) == null) continue
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

    private fun findSite(player: LocalPlayer, target: Player, targetHp: Float, base: Boolean): Site? {
        val level = mc.level ?: return null
        var best: Site? = null
        var bestDamage = 0f
        var preferred: Site? = null
        var preferredDamage = 0f
        val attackReach = minOf(breakRange, player.entityInteractionRange())
        for (mutablePos in positions(player)) {
            val pos = mutablePos.immutable()
            if (pos in failedSites) continue
            val state = level.getBlockState(pos)
            if (base) {
                if (pos.y >= target.blockPosition().y || !state.canBeReplaced()) continue
            } else if (!state.`is`(Blocks.OBSIDIAN) && !state.`is`(Blocks.BEDROCK)) continue
            if (!level.getBlockState(pos.above()).isAir) continue
            if (player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > placeRange * placeRange) continue
            val futureCrystal = AABB(pos.x - 0.5, pos.y + 1.0, pos.z - 0.5, pos.x + 1.5, pos.y + 3.0, pos.z + 1.5)
            if (player.eyePosition.distanceToSqr(RotationUtil.getNearestPoint(player.eyePosition, futureCrystal)) > attackReach * attackReach) continue
            if (level.getEntities(null, crystalBox(pos)).any { it.isAlive }) continue
            if (base && level.getEntities(null, AABB(pos)).any { it.isAlive }) continue
            val damage = ExplosionDamageUtil.crystalDamage(explosionPos(pos), target, if (base) pos else null, ignoreTerrain, ignoreCreative = targetNPCs)
            if (damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)) continue
            if (damage <= bestDamage && pos != preferredPos) continue
            if (ExplosionDamageUtil.crystalDamage(explosionPos(pos), player, if (base) pos else null, ignoreTerrain) > maxSelfDamage.toFloat()) continue
            val hit = if (base) AuraActionQueue.placementHit(player, pos, placeRange)
                else AuraActionQueue.visibleHit(player, pos, placeRange)
            if (hit == null) continue
            val site = Site(pos, hit)
            if (pos == preferredPos) { preferred = site; preferredDamage = damage }
            if (damage > bestDamage) { best = site; bestDamage = damage }
        }
        // Keep aiming at a still-useful site rather than switching for tiny damage differences.
        if (preferred != null && preferredDamage + 0.5f >= bestDamage) return preferred
        return best
    }

    private fun schedulePlace(player: LocalPlayer, site: Site, item: Item, kind: PendingKind) {
        val hand = if (player.offhandItem.`is`(item)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val slot = if (hand == InteractionHand.OFF_HAND) player.inventory.selectedSlot else InventoryUtil.findHotbarSlot(item)
        if (slot !in 0..8) return
        val target = currentTarget ?: return
        preferredPos = site.pos
        prepared = Prepared(kind, site.pos, target.id, tick,
            AABB(if (kind == PendingKind.CRYSTAL) site.pos.above() else site.pos),
            hit = site.hit, hand = hand, slot = slot)
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
            actions.prepareCrystal(crystal, breakRange, rotations, getMoveFixMode())
        } else actions.prepareBlock(action.hit ?: return, placeRange, rotations, getMoveFixMode(),
            exactFace = action.kind == PendingKind.BASE)
        if (!aimed) return
        readyTick = tick
        visual.show(action.box, renderKind(action.kind))
    }

    private fun renderKind(kind: PendingKind) = when (kind) {
        PendingKind.BREAK -> AuraTargetRenderer.Kind.BREAK
        PendingKind.BASE -> AuraTargetRenderer.Kind.BASE
        PendingKind.CRYSTAL -> AuraTargetRenderer.Kind.PLACE
    }

    private fun validPrepared(player: LocalPlayer, target: Player, action: Prepared): Boolean {
        val level = mc.level ?: return false
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
            if (!state.canBeReplaced() || level.getEntities(null, AABB(action.pos)).any { it.isAlive }) return false
        } else if (!state.`is`(Blocks.OBSIDIAN) && !state.`is`(Blocks.BEDROCK)) return false
        return level.getBlockState(action.pos.above()).isAir
            && level.getEntities(null, crystalBox(action.pos)).none { it.isAlive }
    }

    @Commando(priority = -128)
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val action = prepared ?: return
        val player = mc.player ?: return
        val level = mc.level ?: return
        val target = level.getEntity(action.targetId) as? Player ?: return
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
                    if (!actions.crystalReady(crystal, breakRange)) return@withOwner
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
                    if (placeTimer < placeDelay.toInt()) return@withOwner
                    // A crystal can spawn between selection and sending. It still wins over placement.
                    if (findCrystal(player, target, AnticheatManager.getHealth(target)) != null) {
                        clearPrepared()
                        return@withOwner
                    }
                    val hit = actions.blockHit(action.hit ?: return@withOwner, placeRange,
                        exactFace = action.kind == PendingKind.BASE) ?: return@withOwner
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
                    pending = Pending(action.kind, action.pos, tick, action.entityId, action.box)
                    visual.pulse()
                    clearPrepared()
                }
            } finally { sending = null }
        }
    }

    @Commando
    fun onPacketSent(event: PacketSentEvent) {
        if (mc.isSameThread && event.packet === sending) accepted = true
    }

    private fun findTerrainSite(player: LocalPlayer, target: Player, targetHp: Float): BlockPos? {
        val level = mc.level ?: return null
        var best: BlockPos? = null
        var bestScore = Double.NEGATIVE_INFINITY
        val canBase = basePlace && InventoryUtil.findHotbarSlot(Items.OBSIDIAN) >= 0
        for (mutablePos in positions(player)) {
            val pos = mutablePos.immutable()
            if (pos.y >= target.blockPosition().y || player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > placeRange * placeRange) continue
            val state = level.getBlockState(pos)
            val existingBase = state.`is`(Blocks.OBSIDIAN) || state.`is`(Blocks.BEDROCK)
            if (!existingBase && !canBase) continue
            val clear = buildList {
                if (!existingBase && !state.canBeReplaced()) add(pos)
                if (!level.getBlockState(pos.above()).isAir) add(pos.above())
                if (!level.getBlockState(pos.above(2)).isAir) add(pos.above(2))
            }
            if (clear.isEmpty() || clear.any { !miner.canMine(player, it, placeRange) }) continue
            if (level.getEntities(null, crystalBox(pos)).any { it.isAlive }
                || !existingBase && level.getEntities(null, AABB(pos)).any { it.isAlive }) continue
            val damage = terrainDamage(pos, target, clear)
            if (damage < minDamage.toFloat() && !(lethalOverride && damage >= targetHp)
                || terrainDamage(pos, player, clear) > maxSelfDamage.toFloat()) continue
            val visible = clear.firstOrNull { AuraActionQueue.visibleHit(player, it, placeRange) != null } ?: continue
            val score = damage - clear.size * 0.5 - player.eyePosition.distanceToSqr(Vec3.atCenterOf(visible)) * 0.01
            if (score > bestScore) { best = visible; bestScore = score }
        }
        return best
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
        world = null
        localPlayer = null
        actions.beginTick()
        visual.reset()
        miner.cancel()
    }

    override fun onEnable() = reset()
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()
}
