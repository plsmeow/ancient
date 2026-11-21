package meow.ancient.module.modules.player

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.util.inventory.BlockPredictionUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.setting.KeybindSetting
import meow.ancient.module.modules.render.FreeCameraModule
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.network.protocol.Packet
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import sweetie.evaware.flora.api.Commando

object PacketMineModule :
    Module(
        name = "PacketMine",
        category = PLAYER,
        description =
            "Click once to packet-mine; Rebreak retains the position for STOP-only repeats",
    ) {
    private val modeSetting = mode("Mode", "Normal", listOf("Normal", "Rebreak"))
    private val mode by modeSetting
    private val forceSetting =
        boolean(
            "Force",
            false,
            "After the first mining cycle, only sends rebreak STOP packets without waiting for air",
            isVisible = { mode == "Rebreak" },
        )
    private val force by forceSetting
    private val doubleBreakSetting =
        boolean(
            "DoubleBreak",
            false,
            "Mines two blocks; Rebreak retains the newest position after the secondary block finishes",
        )
    private val doubleBreak by doubleBreakSetting
    private val fast by
        boolean(
            "Fast",
            false,
            "Repeats rebreak packets every tick, including while the position is air",
            isVisible = { mode == "Rebreak" },
        )
    private val autoTool by
        boolean("Auto Tool", true, "Packet-selects the fastest hotbar tool only for mining actions")
    private val breakProgress by
        number(
            "Break Progress",
            1.0,
            0.7,
            1.0,
            0.01,
            2,
            description = "Progress required before sending STOP; lower values finish earlier",
        )
    private val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1, unit = "b")
    private val rotate by boolean("Rotations", true)
    private val swing by boolean("Swing", true)
    private val render by boolean("Render", true)
    private val renderSlideSpeed by number("Render Slide Speed", 14.0, 5.0, 30.0, 1.0, 0, isVisible = { render })

    private class Target(
        val player: LocalPlayer,
        val level: ClientLevel,
        val pos: BlockPos,
        var direction: Direction,
        var state: BlockState,
        var owner: Any? = null,
    ) {
        val progress = PacketMineProgress()
        var tool = ItemStack.EMPTY
        var miningState = state
        var toolSlot = player.inventory.selectedSlot
        var finishPending = false
        var secondary = false
        var startNanos = 0L
        var lastDelta = 0f
        var serverEfficiency = 0.0
        var serverBreakSpeed = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
        @Volatile var attempt = 0L

        fun isCurrent(): Boolean =
            mc.player === player && mc.level === level && player.connection.connection.isConnected
    }

    private class Prepared(
        val target: Target,
        val phase: PacketMineProgress.Phase,
        val slot: Int,
        val tick: Long,
    )

    @Volatile private var targets = emptyList<Target>()
    private val channels = PacketMineChannels<Target>()
    private var prepared: Prepared? = null
    private val actions = AuraActionQueue(this)
    private val primaryVisual = AuraTargetRenderer({ isEnabled() && render }, { renderSlideSpeed })
    private val secondaryVisual = AuraTargetRenderer({ isEnabled() && render }, { renderSlideSpeed })
    private var sending: Packet<*>? = null
    private var accepted = false
    private var lastAction: Target? = null
    private var tick = 0L
    private var pendingAborts = emptyList<Target>()

    init {
        modeSetting.addListener { clearTargets(true) }
        forceSetting.addListener { clearTargets(true) }
        doubleBreakSetting.addListener {
            if (!doubleBreak && targets.size > 1) {
                targets.dropLast(1).forEach { clearTarget(it, true) }
            }
        }
    }

    override fun onEnable() {
        clearTargets(false)
        actions.beginTick()
        tick = 0L
        mc.gameMode?.stopDestroyBlock()
    }

    override fun onDisable() = clearTargets(true)

    @Commando fun onDisconnect(event: ClientDisconnectEvent) {
        clearTargets(false)
        pendingAborts = emptyList()
    }

    @Commando
    fun onPacketSent(event: PacketSentEvent) {
        if (mc.isSameThread && event.packet === sending) accepted = true
    }

    fun interceptsMining(): Boolean {
        val player = mc.player ?: return false
        return isEnabled() &&
            mc.level != null &&
            mc.gameMode != null &&
            player.isAlive &&
            !player.isCreative &&
            !player.isSpectator &&
            !player.isPassenger &&
            player.abilities.mayBuild &&
            player.connection.connection.isConnected
    }

    fun hasTarget(): Boolean = isEnabled() && targets.isNotEmpty()
    fun targetsPosition(pos: BlockPos): Boolean = isEnabled() && targets.any { it.pos == pos && it.isCurrent() }

    fun hasPendingAction(): Boolean = isEnabled() && targets.any {
        it.progress.phase == PacketMineProgress.Phase.START || it.progress.phase == PacketMineProgress.Phase.MINING ||
            it.progress.phase == PacketMineProgress.Phase.CONFIRM
            || it.progress.phase == PacketMineProgress.Phase.REBREAK && !it.state.isAir
    }

    data class DebugTarget(val x: Int, val y: Int, val z: Int, val phase: String,
        val progress: Float, val primed: Boolean, val confirmationTicks: Long, val toolSlot: Int)

    fun debugTargets(): List<DebugTarget> = targets.filter { it.isCurrent() }.map {
        DebugTarget(it.pos.x, it.pos.y, it.pos.z, it.progress.phase.name, it.progress.damage,
            it.progress.rebreakPrimed, it.progress.confirmationAge(tick), it.toolSlot)
    }

    fun selectBlock(pos: BlockPos, direction: Direction): Boolean {
        return selectBlockInternal(pos, direction, null)
    }

    data class AutomaticTarget(val pos: BlockPos, val primary: Boolean, val retained: Boolean)
    fun automaticTargets(owner: Any): List<AutomaticTarget> = targets.filter { it.owner === owner && it.isCurrent() }.map {
        AutomaticTarget(it.pos, it === targets.lastOrNull(), it.progress.rebreakPrimed)
    }
    fun automaticCapacity(): Int = if (doubleBreak) 2 else 1
    fun hasManualTarget(): Boolean = targets.any { it.owner == null && it.isCurrent() }
    fun releaseAutomaticTargets(owner: Any) { targets.filter { it.owner === owner }.forEach { clearTarget(it, true) } }

    fun selectAutomaticBlock(owner: Any, pos: BlockPos, direction: Direction): Boolean {
        if (targets.any { it.owner !== owner } || targets.size >= automaticCapacity()) return false
        // A new START would overwrite vanilla's one confirmed instant-rebreak position.
        if (targets.any { it.progress.rebreakPrimed }) return false
        return selectBlockInternal(pos, direction, owner)
    }

    private fun selectBlockInternal(pos: BlockPos, direction: Direction, owner: Any?): Boolean {
        if (!interceptsMining()) return false
        val player = mc.player ?: return false
        if (!AnticheatManager.canUseContext(player)) return false
        val level = mc.level ?: return false
        val state = BlockPredictionUtil.serverState(level, pos)
        if (!validBlock(player, level, pos, state) || !withinRange(player, pos)) return false
        targets.firstOrNull { it.pos == pos && it.isCurrent() }?.let {
            if (owner == null) it.owner = null
            return true
        }
        val capacity = if (doubleBreak) 2 else 1
        while (targets.size >= capacity) clearTarget(targets.first(), true)
        if (doubleBreak)
            targets.lastOrNull()?.let { previous ->
                previous.secondary = true
                // Selecting a new primary position makes the previous one a one-shot secondary.
                // Vanilla stores only one current destroy position for instant STOP-only rebreaks.
                if (previous.progress.rebreakPrimed) {
                    if (previous.state.isAir || force) clearTarget(previous, false)
                    else restart(previous)
                }
            }
        targets = targets + Target(player, level, pos.immutable(), direction, state, owner)
        meow.ancient.module.modules.combat.CrystalAuraModule.yieldToManualMining(pos)
        return true
    }

    /** Enabling while attack is held must still acquire the first block. */
    fun continueMining(pos: BlockPos, direction: Direction): Boolean {
        if (!interceptsMining()) return false
        return targets.isNotEmpty() || selectBlock(pos, direction)
    }

    @Commando
    fun onKey(event: KeyPressEvent) {
        if (
            event.cancel ||
                event.action != InputConstants.PRESS ||
                !interceptsMining() ||
                mc.gui.screen() != null
        )
            return
        if (event.key != KeybindSetting.fromMouseButton(InputConstants.MOUSE_BUTTON_LEFT)) return
        val hit = cameraInsideHit() ?: mc.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return
        val current = targets.firstOrNull { it.pos == hit.blockPos }
        if (current != null) {
            if (current.owner != null) current.owner = null else clearTarget(current, true)
            event.cancel = true
        }
        else if (selectBlock(hit.blockPos, hit.direction)) event.cancel = true
    }

    @Commando(priority = 110)
    fun onUpdate(event: PlayerUpdateEvent) {
        tick++
        prepared = null
        actions.beginTick()
        primaryVisual.beginTick()
        secondaryVisual.beginTick()
        channels.delayed?.let { if (!it.isCurrent()) channels.confirmed(it) }
        channels.active?.let { if (!it.isCurrent()) channels.confirmed(it) }
        val sharedSlot = if (autoTool && (doubleBreak || channels.delayed != null)) sharedTool() else null
        val mining = targets.filter { it.progress.phase == PacketMineProgress.Phase.MINING }
        val leased = mining.firstOrNull()?.let { current ->
            if (paused(current.player) || !ActionCoordinator.canAct(this)) false
            else SilentSwapUtil.prepareSlot(sharedSlot ?: current.toolSlot, 2, this)
        } ?: false
        for (current in targets) {
            if (
                !current.isCurrent() ||
                    !interceptsMining() ||
                    !withinRange(current.player, current.pos) ||
                    !current.level.hasChunk(current.pos.x shr 4, current.pos.z shr 4)
            ) {
                clearTarget(current, true)
                continue
            }
            if (current.progress.timedOut(tick, 60L)) retry(current)
            val state = current.state
            if (state.isAir) {
                if (
                    current.progress.phase != PacketMineProgress.Phase.REBREAK &&
                        current.progress.phase != PacketMineProgress.Phase.CONFIRM
                )
                    clearTarget(current, false)
                continue
            }
            if (!validBlock(current.player, current.level, current.pos, state)) {
                clearTarget(current, true)
                continue
            }
            if (paused(current.player)) continue
            if (current.progress.phase != PacketMineProgress.Phase.CONFIRM) {
                current.toolSlot =
                    if (autoTool) sharedSlot ?: selectTool(current, state)
                    else current.player.inventory.selectedSlot
            }
            val tool = current.player.inventory.getItem(current.toolSlot)
            if (
                current.progress.phase == PacketMineProgress.Phase.MINING &&
                    state.block !== current.miningState.block
            ) {
                restart(current)
            }
            if (current.progress.phase == PacketMineProgress.Phase.MINING) {
                // Both server channels use the same held tool. Do not alternate slot leases per block.
                if (!leased || SilentSwapUtil.serverSlot != current.toolSlot) continue
                if (!ItemStack.isSameItemSameComponents(current.tool, tool)) {
                    current.tool = tool.copy()
                    current.serverEfficiency = minOf(current.player.getAttributeValue(Attributes.MINING_EFFICIENCY),
                        miningAttribute(current.player, tool, Attributes.MINING_EFFICIENCY))
                }
                // Begin immediately with conservative attributes; server updates refine the speed.
                current.lastDelta =
                    PacketMineToolSpeed.progress(
                        current.player,
                        tool,
                        state,
                        state.getDestroySpeed(current.level, current.pos),
                        current.serverEfficiency,
                        current.serverBreakSpeed,
                    )
                current.finishPending =
                    current.progress.advance(current.lastDelta, tick, breakProgress.toFloat())
            }
            if (channels.delayed === current && current.progress.phase == PacketMineProgress.Phase.MINING
                && current.finishPending && enoughTime(current)) {
                // STOP for this position is ignored while the second position is active.
                // Vanilla completes the delayed block itself; only the air update confirms it.
                current.progress.stop(tick)
                current.finishPending = false
            }
        }

        targets.lastOrNull()?.let { showMiningTarget(primaryVisual, it) }
        targets.dropLast(1).firstOrNull()?.let { showMiningTarget(secondaryVisual, it) }

        // One tool and one aimed action per movement interval. Both block timers continue
        // independently.
        val candidates =
            targets.filter {
                needsAction(it) && (it.state.isAir || visibleHit(it) != null)
            }
        val current =
            candidates.firstOrNull { needsHandoff(it) }
                ?: candidates.firstOrNull { it.progress.phase == PacketMineProgress.Phase.START }
                ?: candidates.firstOrNull { it !== lastAction }
                ?: candidates.firstOrNull()
                ?: return
        val state = current.state
        if (rotate && !FreeCameraModule.isEnabled() && cameraInsideHit(current.pos) == null) {
            if (!state.isAir) {
                val hit = visibleHit(current) ?: return
                current.direction = hit.direction
                actions.prepareBlock(hit, range, true, MoveFixMode.SILENT)
            }
        }
        // The server recalculates elapsed break damage with the held tool when STOP arrives.
        // Prepare before movement to respect SilentSwap's packet order; the camera slot is
        // untouched.
        val slot = if (autoTool) current.toolSlot else current.player.inventory.selectedSlot
        if (!SilentSwapUtil.prepareSlot(slot, 2, this)) return
        prepared = Prepared(current, current.progress.phase, slot, tick)
    }

    private fun showMiningTarget(visual: AuraTargetRenderer, current: Target) {
        if (!current.isCurrent()) return
        val progress = if (current.progress.phase == PacketMineProgress.Phase.CONFIRM || current.progress.rebreakPrimed)
            1f else (current.progress.damage / breakProgress.toFloat()).coerceIn(0f, 1f)
        visual.show(AABB(current.pos), AuraTargetRenderer.Kind.MINE, progress)
    }

    private fun needsAction(current: Target): Boolean {
        if (!current.isCurrent() || paused(current.player)) return false
        val state = current.state
        if (mode == "Rebreak" && fast && current.progress.rebreakPrimed) return true
        if (state.isAir) return false
        if (needsHandoff(current)) return true
        if (channels.delayed === current) return false
        return when (current.progress.phase) {
            PacketMineProgress.Phase.START -> channels.canStart()
            PacketMineProgress.Phase.MINING -> current.finishPending && enoughTime(current)
            PacketMineProgress.Phase.REBREAK -> true
            PacketMineProgress.Phase.CONFIRM -> false
        }
    }

    private fun needsHandoff(current: Target): Boolean = doubleBreak && channels.canHandoff(current)
        && current.progress.phase == PacketMineProgress.Phase.MINING
        && targets.any { it !== current && it.progress.phase == PacketMineProgress.Phase.START }

    @Commando(priority = -128)
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val action = prepared ?: return
        prepared = null
        if (!ActionCoordinator.canAct(this)) return
        val current = action.target
        if (
            action.tick != tick ||
                current !in targets ||
                current.progress.phase != action.phase ||
                !current.isCurrent() ||
                paused(current.player) ||
                !withinRange(current.player, current.pos) ||
                SilentSwapUtil.serverSlot != action.slot
        )
            return
        val state = current.state
        if (mode == "Rebreak" && fast && current.progress.rebreakPrimed) {
            if (!state.isAir && !validBlock(current.player, current.level, current.pos, state))
                return
            // A primed position does not need a hitbox to keep sending Civ-style STOP packets.
            if (current.progress.phase == PacketMineProgress.Phase.REBREAK) {
                finish(current)
            } else if (current.progress.phase == PacketMineProgress.Phase.CONFIRM) {
                sendAction(current, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)
                swing(current.player)
            }
            lastAction = current
            return
        }
        if (state.isAir || !validBlock(current.player, current.level, current.pos, state)) return
        if (current.progress.phase == PacketMineProgress.Phase.CONFIRM) return
        val hit = if (rotate && !FreeCameraModule.isEnabled() && cameraInsideHit(current.pos) == null) outgoingHit(current)
            else visibleHit(current)
        if (hit == null) return
        val direction = hit.direction
        if (needsHandoff(current)) {
            current.direction = direction
            if (sendAction(current, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)) {
                channels.handoff(current)
                lastAction = current
                swing(current.player)
            }
            return
        }
        when (current.progress.phase) {
            PacketMineProgress.Phase.START -> {
                if (!channels.canStart()) return
                current.direction = direction
                current.tool = current.player.inventory.getItem(action.slot).copy()
                current.miningState = state
                current.serverEfficiency =
                    minOf(
                        current.player.getAttributeValue(Attributes.MINING_EFFICIENCY),
                        miningAttribute(current.player, current.tool, Attributes.MINING_EFFICIENCY),
                    )
                current.serverBreakSpeed =
                    current.player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
                current.startNanos = System.nanoTime()
                current.attempt++
                if (!sendAction(current, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)) return
                // START A -> early STOP A -> START B. A is delayed, B remains the active position.
                channels.started(current)
                current.progress.start()
                swing(current.player)
            }
            PacketMineProgress.Phase.MINING -> {
                if (current.finishPending && enoughTime(current)) finish(current)
            }
            PacketMineProgress.Phase.REBREAK -> {
                current.direction = direction
                finish(current)
            }
            PacketMineProgress.Phase.CONFIRM -> Unit
        }
        lastAction = current
    }

    private fun enoughTime(current: Target): Boolean {
        return current.progress.enoughTime(
            System.nanoTime() - current.startNanos,
            current.lastDelta,
            breakProgress.toFloat(),
        )
    }

    private fun finish(current: Target) {
        // Same sequence as LiquidBounce Normal/Civ: subsequent rebreaks send STOP without a new
        // START.
        // Reference: CCBlueX/LiquidBounce nextgen, 073f912576589bc563cdb56fce03dc336a788c0c,
        // src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/packetmine/mode/CivMineMode.kt
        if (!sendAction(current, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)) return
        current.progress.stop(tick, mode == "Rebreak" && force && !current.secondary)
        if (current.progress.phase == PacketMineProgress.Phase.REBREAK) channels.confirmed(current)
        current.finishPending = false
        swing(current.player)
    }

    private fun sendAction(current: Target, action: ServerboundPlayerActionPacket.Action): Boolean {
        val gameMode = mc.gameMode ?: return false
        accepted = false
        try {
            gameMode.startPrediction(current.level) { sequence ->
                ServerboundPlayerActionPacket(action, current.pos, current.direction, sequence).also { sending = it }
            }
            return accepted
        } finally { sending = null }
    }

    @Commando
    fun onPacket(event: PacketReceiveEvent) {
        if (event.cancel) return
        val snapshot = targets
        val delayed = channels.delayed
        when (val packet = event.packet) {
            is ClientboundUpdateAttributesPacket ->
                mc.execute {
                    val player = mc.player ?: return@execute
                    if (packet.entityId != player.id) return@execute
                    for (attribute in packet.values) {
                        val value = PacketMineToolSpeed.snapshotValue(attribute)
                        for (current in snapshot) {
                            if (
                                current !in targets ||
                                    !current.isCurrent() ||
                                    SilentSwapUtil.serverSlot != current.toolSlot
                            )
                                continue
                            when (attribute.attribute()) {
                                Attributes.MINING_EFFICIENCY ->
                                    current.serverEfficiency =
                                        maxOf(current.serverEfficiency, value)
                                Attributes.BLOCK_BREAK_SPEED -> current.serverBreakSpeed = value
                                else -> Unit
                            }
                        }
                    }
                }
            is ClientboundBlockUpdatePacket -> {
                snapshot.forEach { current ->
                    if (current.pos == packet.pos) queueBlockUpdate(current, packet.blockState)
                }
                if (delayed != null && delayed !in snapshot && delayed.pos == packet.pos)
                    queueBlockUpdate(delayed, packet.blockState)
            }
            is ClientboundSectionBlocksUpdatePacket ->
                packet.runUpdates { pos, state ->
                    snapshot.forEach { current ->
                        if (current.pos == pos) queueBlockUpdate(current, state)
                    }
                    if (delayed != null && delayed !in snapshot && delayed.pos == pos)
                        queueBlockUpdate(delayed, state)
                }
        }
    }

    private fun queueBlockUpdate(current: Target, state: BlockState) {
        val attempt = current.attempt
        mc.execute {
            if (!current.isCurrent() || current.attempt != attempt) return@execute
            if (current !in targets) {
                if (state.isAir && channels.delayed === current) channels.confirmed(current)
                return@execute
            }
            updateBlock(current, current.pos, state)
        }
    }

    private fun updateBlock(current: Target, pos: BlockPos, state: BlockState) {
        if (current !in targets || pos != current.pos) return
        current.state = state
        if (state.isAir) {
            channels.confirmed(current)
            val retain = mode == "Rebreak" && !current.secondary
            // An instant or delayed server break can be confirmed before our final STOP.
            if (!current.progress.confirmAir(retain, allowMining = true))
                clearTarget(current, false)
        } else if (!validBlock(current.player, current.level, pos, state)) {
            clearTarget(current, true)
        } else if (current.progress.phase == PacketMineProgress.Phase.CONFIRM && current.progress.rebreakPrimed) {
            current.progress.retry()
        }
    }

    private fun retry(current: Target) {
        if (mode == "Rebreak" && force && current.progress.completedCycle) {
            if (current.secondary) clearTarget(current, false) else current.progress.retry()
            return
        }
        if (!current.progress.rebreakPrimed) {
            restart(current)
            return
        }
        current.progress.retry()
    }

    private fun restart(current: Target) {
        if (mode == "Rebreak" && force && current.progress.completedCycle) {
            if (current.secondary) clearTarget(current, false) else current.progress.retry()
            return
        }
        if (channels.delayed === current) {
            // ABORT names the active position, never the delayed slot. Preserve its server ownership.
            resetMining(current)
            current.progress.start()
            current.startNanos = System.nanoTime()
            current.miningState = current.state
            return
        }
        abort(current)
        resetMining(current)
    }

    private fun resetMining(current: Target) {
        current.attempt++
        current.progress.restart()
        current.tool = ItemStack.EMPTY
        current.finishPending = false
        current.lastDelta = 0f
        current.serverEfficiency = 0.0
    }

    private fun clearTargets(abort: Boolean) {
        targets.forEach { clearTarget(it, abort) }
        prepared = null
        lastAction = null
        channels.reset()
        primaryVisual.reset()
        secondaryVisual.reset()
        SilentSwapUtil.releasePreparedSlot(this)
    }

    private fun clearTarget(current: Target, abort: Boolean) {
        targets = targets.filter { it !== current }
        if (prepared?.target === current) prepared = null
        if (lastAction === current) lastAction = null
        if (abort) abort(current)
        if (
            targets.none { it.progress.phase == PacketMineProgress.Phase.CONFIRM ||
                it.progress.phase == PacketMineProgress.Phase.MINING }
        )
            SilentSwapUtil.releasePreparedSlot(this)
    }

    private fun selectTool(current: Target, state: BlockState): Int {
        val player = current.player
        val selected = player.inventory.selectedSlot
        var bestSlot = selected
        var bestSpeed = if (player.mainHandItem.canDestroyBlock(state, current.level, current.pos, player))
            toolSpeed(player, player.mainHandItem, state) else Double.NEGATIVE_INFINITY
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (!stack.canDestroyBlock(state, current.level, current.pos, player))
                continue
            val speed = toolSpeed(player, stack, state)
            if (speed > bestSpeed + 1.0E-6) {
                bestSlot = slot
                bestSpeed = speed
            }
        }
        return bestSlot
    }

    private fun sharedTool(): Int? {
        val player = mc.player ?: return null
        val work = (targets + listOfNotNull(channels.delayed)).distinct().filter { current ->
            current.isCurrent() && !current.progress.rebreakPrimed
                && !current.state.isAir
        }
        if (work.isEmpty()) return null
        val selected = SilentSwapUtil.serverSlot.takeIf { it in 0..8 } ?: player.inventory.selectedSlot
        fun score(slot: Int): Double = work.minOf { current ->
            val state = current.state
            val stack = player.inventory.getItem(slot)
            if (!stack.canDestroyBlock(state, current.level, current.pos, player)) Double.NEGATIVE_INFINITY
            else toolSpeed(player, stack, state) / state.getDestroySpeed(current.level, current.pos).coerceAtLeast(0.001f)
        }
        var best = selected
        var speed = score(selected)
        for (slot in 0..8) {
            val candidate = score(slot)
            if (candidate > speed + 1.0E-6) { best = slot; speed = candidate }
        }
        return best
    }

    private fun toolSpeed(player: LocalPlayer, stack: ItemStack, state: BlockState): Double {
        return PacketMineToolSpeed.score(
            stack,
            state,
            miningAttribute(player, stack, Attributes.MINING_EFFICIENCY),
            miningAttribute(player, stack, Attributes.BLOCK_BREAK_SPEED),
        )
    }

    private fun miningAttribute(
        player: LocalPlayer,
        stack: ItemStack,
        attribute: Holder<Attribute>,
    ): Double {
        val actual = player.getAttribute(attribute) ?: return attribute.value().defaultValue
        // Compare equipment modifiers on a copy; effects and armor stay in the calculation.
        return PacketMineToolSpeed.attributeValue(actual, attribute, player.mainHandItem, stack)
    }

    private fun abort(current: Target) {
        if (
            current.isCurrent() &&
                !current.progress.rebreakPrimed &&
                current.progress.phase != PacketMineProgress.Phase.START && channels.active === current
        ) {
            pendingAborts = (pendingAborts + current).distinctBy { it.pos }
            channels.abortActive(current)
        }
    }

    // Called even after disable, so cleanup never sends digging after movement.
    fun flushAborts() {
        val snapshot = pendingAborts
        pendingAborts = emptyList()
        snapshot
            .filter { it.isCurrent() }
            .forEach {
                it.player.connection.send(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                        it.pos,
                        it.direction,
                    )
                )
            }
    }

    private fun swing(player: LocalPlayer) {
        if (swing) {
            player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
            player.connection.send(ServerboundPunchPacket.INSTANCE)
        }
    }

    private fun paused(player: LocalPlayer): Boolean =
        !AnticheatManager.canUseContext(player) || InventoryMoveModule.isInventoryActionSlowed()

    private fun validBlock(
        player: LocalPlayer,
        level: ClientLevel,
        pos: BlockPos,
        state: BlockState,
    ): Boolean =
        !state.isAir &&
            state.getDestroySpeed(level, pos) >= 0f &&
            level.worldBorder.isWithinBounds(pos) &&
            (if (autoTool) (0..8).any { player.inventory.getItem(it).canDestroyBlock(state, level, pos, player) }
                else player.mainHandItem.canDestroyBlock(state, level, pos, player)) &&
            mc.gameMode?.let { !player.blockActionRestricted(level, pos, it.playerMode) } == true

    private fun withinRange(player: LocalPlayer, pos: BlockPos): Boolean {
        val eyes = if (FreeCameraModule.isEnabled()) mc.gameRenderer.mainCamera().position() else player.eyePosition
        val reach = minOf(range, player.blockInteractionRange())
        return AABB(pos).distanceToSqr(eyes) <= reach * reach
            && (FreeCameraModule.isEnabled() || player.isWithinBlockInteractionRange(pos, 0.0))
    }

    private fun outgoingHit(current: Target): BlockHitResult? {
        return actions.blockHit(visibleHit(current) ?: return null, range)
    }

    private fun visibleHit(current: Target): BlockHitResult? {
        cameraInsideHit(current.pos)?.let { return it }
        val eyes = if (FreeCameraModule.isEnabled()) mc.gameRenderer.mainCamera().position() else current.player.eyePosition
        return AuraActionQueue.visibleHit(current.player, current.pos, range, eyes = eyes)
    }

    private fun cameraInsideHit(pos: BlockPos = mc.gameRenderer.mainCamera().blockPosition()): BlockHitResult? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val camera = mc.gameRenderer.mainCamera()
        val eyes = camera.position()
        val shape = level.getBlockState(pos).getShape(level, pos)
        val localEyes = eyes.subtract(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        if (shape.toAabbs().none { it.contains(localEyes) }) return null
        val forward = camera.forwardVector()
        val reach = minOf(range, player.blockInteractionRange())
        if (reach <= 0.0 || !withinRange(player, pos)) return null
        return BlockHitResult(eyes, Direction.getApproximateNearest(forward.x().toDouble(), forward.y().toDouble(), forward.z().toDouble()).opposite,
            pos, true)
    }
}
