package meow.ancient.module.modules.player

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.AuraActionQueue
import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.inventory.SilentSwapUtil
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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
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
            "Click once to packet-mine; Rebreak keeps the position after the first confirmed break",
    ) {
    private val modeSetting = mode("Mode", "Normal", listOf("Normal", "Rebreak"))
    private val mode by modeSetting
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
    private val postBreakDelay by number("Post Break Delay", 0.0, 0.0, 20.0, 1.0, 0, unit = "ticks")
    private val confirmTimeout by
        number("Confirm Timeout", 60.0, 20.0, 200.0, 1.0, 0, unit = "ticks")

    private class Target(
        val player: LocalPlayer,
        val level: ClientLevel,
        val pos: BlockPos,
        var direction: Direction,
        var state: BlockState,
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
        var retryAfterTick = 0L
        val renderSize = AnimFloat(0.08f, speed = 22f)
        var lastRenderNanos = System.nanoTime()

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
    private var prepared: Prepared? = null
    private val actions = AuraActionQueue(this)
    private var sending: Packet<*>? = null
    private var accepted = false
    private var lastAction: Target? = null
    private var tick = 0L
    private var nextStartTick = 0L
    private var renderRegistered = false
    private var pendingAborts = emptyList<Target>()

    init {
        modeSetting.addListener { clearTargets(true) }
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
        nextStartTick = 0L
        mc.gameMode?.stopDestroyBlock()
        if (!renderRegistered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (!isEnabled() || !render) return@register
                for (current in targets) {
                    if (!current.isCurrent()) continue
                    val color =
                        if (current.progress.phase == PacketMineProgress.Phase.REBREAK)
                            M3Theme.current.tertiary
                        else M3Theme.current.primary
                    val box = AABB(current.pos)
                    Gizmos.cuboid(
                            box.inflate(0.002),
                            GizmoStyle.strokeAndFill(
                                color.alpha(210).argb,
                                3f,
                                color.alpha(28).argb,
                            ),
                        )
                        .setAlwaysOnTop()
                    val progress =
                        if (
                            current.progress.phase == PacketMineProgress.Phase.CONFIRM ||
                                current.progress.rebreakPrimed
                        )
                            1f
                        else (current.progress.damage / breakProgress.toFloat()).coerceIn(0f, 1f)
                    val now = System.nanoTime()
                    val dt = ((now - current.lastRenderNanos) / 1.0E9).toFloat()
                    current.lastRenderNanos = now
                    val size =
                        current.renderSize.update(0.08f + progress * 0.92f, dt).coerceIn(0.08f, 1f)
                    Gizmos.cuboid(
                            box.inflate((size.toDouble() - 1.0) * 0.5),
                            GizmoStyle.strokeAndFill(
                                color.alpha(245).argb,
                                3f,
                                color.alpha(85).argb,
                            ),
                        )
                        .setAlwaysOnTop()
                }
            }
            renderRegistered = true
        }
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
            || it.progress.phase == PacketMineProgress.Phase.REBREAK && !it.level.getBlockState(it.pos).isAir
    }

    data class DebugTarget(val x: Int, val y: Int, val z: Int, val phase: String,
        val progress: Float, val primed: Boolean, val confirmationTicks: Long, val toolSlot: Int)

    fun debugTargets(): List<DebugTarget> = targets.filter { it.isCurrent() }.map {
        DebugTarget(it.pos.x, it.pos.y, it.pos.z, it.progress.phase.name, it.progress.damage,
            it.progress.rebreakPrimed, it.progress.confirmationAge(tick), it.toolSlot)
    }

    fun selectBlock(pos: BlockPos, direction: Direction): Boolean {
        if (!interceptsMining() || mc.gui.screen() != null) return false
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val state = level.getBlockState(pos)
        if (!validBlock(player, level, pos, state) || !withinRange(player, pos)) return false
        if (targets.any { it.pos == pos && it.isCurrent() }) return true
        val capacity = if (doubleBreak) 2 else 1
        while (targets.size >= capacity) clearTarget(targets.first(), true)
        if (doubleBreak)
            targets.lastOrNull()?.let { previous ->
                previous.secondary = true
                // Selecting a new primary position makes the previous one a one-shot secondary.
                // Vanilla stores only one current destroy position for instant STOP-only rebreaks.
                if (previous.progress.rebreakPrimed) {
                    if (level.getBlockState(previous.pos).isAir) clearTarget(previous, false)
                    else restart(previous)
                }
            }
        targets = targets + Target(player, level, pos.immutable(), direction, state)
        nextStartTick = tick
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
        val hit = mc.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return
        val current = targets.firstOrNull { it.pos == hit.blockPos }
        if (current != null) { clearTarget(current, true); event.cancel = true }
        else if (selectBlock(hit.blockPos, hit.direction)) event.cancel = true
    }

    @Commando(priority = 110)
    fun onUpdate(event: PlayerUpdateEvent) {
        tick++
        prepared = null
        actions.beginTick()
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
            if (current.progress.timedOut(tick, confirmTimeout.toLong())) retry(current)
            val state = current.level.getBlockState(current.pos)
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
            current.state = state
            if (paused(current.player)) continue
            if (current.progress.phase != PacketMineProgress.Phase.CONFIRM) {
                current.toolSlot =
                    if (autoTool) selectTool(current, state)
                    else current.player.inventory.selectedSlot
            }
            val tool = current.player.inventory.getItem(current.toolSlot)
            if (
                current.progress.phase == PacketMineProgress.Phase.MINING &&
                    (state.block !== current.miningState.block ||
                        !ItemStack.isSameItemSameComponents(current.tool, tool))
            ) {
                restart(current)
            }
            if (
                current.progress.phase == PacketMineProgress.Phase.MINING && !current.finishPending
            ) {
                // Keep the tool leased while damage advances.
                if (!ActionCoordinator.canAct(this)
                    || !SilentSwapUtil.prepareSlot(current.toolSlot, 2, this)) continue
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
        }

        // One tool and one aimed action per movement interval. Both block timers continue
        // independently.
        val candidates =
            targets.filter {
                needsAction(it) && (it.level.getBlockState(it.pos).isAir || visibleHit(it) != null)
            }
        val current =
            candidates.firstOrNull { it.progress.phase == PacketMineProgress.Phase.START }
                ?: candidates.firstOrNull { it !== lastAction }
                ?: candidates.firstOrNull()
                ?: return
        val state = current.level.getBlockState(current.pos)
        if (rotate) {
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

    private fun needsAction(current: Target): Boolean {
        if (!current.isCurrent() || paused(current.player)) return false
        val state = current.level.getBlockState(current.pos)
        if (mode == "Rebreak" && fast && current.progress.rebreakPrimed) return true
        if (state.isAir) return false
        return when (current.progress.phase) {
            PacketMineProgress.Phase.START -> tick >= nextStartTick
            PacketMineProgress.Phase.MINING -> current.finishPending && enoughTime(current)
            PacketMineProgress.Phase.REBREAK -> tick >= current.retryAfterTick
            PacketMineProgress.Phase.CONFIRM -> false
        }
    }

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
        val state = current.level.getBlockState(current.pos)
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
        val hit = if (rotate) outgoingHit(current) else visibleHit(current)
        if (hit == null) return
        val direction = hit.direction
        when (current.progress.phase) {
            PacketMineProgress.Phase.START -> {
                if (tick < nextStartTick) return
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
                if (doubleBreak)
                    sendAction(current, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)
                if (!sendAction(current, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)) return
                // ThunderHack SpeedMine DoubleMine uses STOP / START / STOP. The early STOP puts
                // the first block in delayed destroy while a second block becomes the active
                // destroy.
                // https://github.com/Pan4ur/ThunderHack-Recode/blob/main/src/main/java/thunder/hack/features/modules/player/SpeedMine.java
                if (doubleBreak)
                    sendAction(current, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)
                current.progress.start()
                swing(current.player)
            }
            PacketMineProgress.Phase.MINING -> {
                if (current.finishPending && enoughTime(current)) finish(current)
            }
            PacketMineProgress.Phase.REBREAK -> {
                if (tick < current.retryAfterTick) return
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
        current.progress.stop(tick)
        current.finishPending = false
        current.retryAfterTick = 0L
        nextStartTick = tick + postBreakDelay.toLong()
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
            is ClientboundBlockUpdatePacket ->
                mc.execute {
                    snapshot
                        .filter { it in targets && it.isCurrent() }
                        .forEach { updateBlock(it, packet.pos, packet.blockState) }
                }
            is ClientboundSectionBlocksUpdatePacket ->
                mc.execute {
                    packet.runUpdates { pos, state ->
                        snapshot
                            .filter { it in targets && it.isCurrent() }
                            .forEach { updateBlock(it, pos, state) }
                    }
                }
        }
    }

    private fun updateBlock(current: Target, pos: BlockPos, state: BlockState) {
        if (current !in targets || pos != current.pos) return
        current.state = state
        if (state.isAir) {
            val retain = mode == "Rebreak" && !current.secondary
            // An instant or delayed server break can be confirmed before our final STOP.
            if (!current.progress.confirmAir(retain, allowMining = true))
                clearTarget(current, false)
        } else if (!validBlock(current.player, current.level, pos, state)) {
            clearTarget(current, true)
        } else if (current.progress.phase == PacketMineProgress.Phase.CONFIRM) {
            // Corrections cannot arm a new position, but must retain an already confirmed Rebreak.
            retry(current)
        }
    }

    private fun retry(current: Target) {
        if (!current.progress.rebreakPrimed) {
            restart(current)
            return
        }
        current.progress.retry()
        current.retryAfterTick = tick + postBreakDelay.toLong()
    }

    private fun restart(current: Target) {
        abort(current)
        resetMining(current)
        nextStartTick = maxOf(nextStartTick, tick + postBreakDelay.toLong())
    }

    private fun resetMining(current: Target) {
        current.progress.restart()
        current.renderSize.setImmediate(0.08f)
        current.tool = ItemStack.EMPTY
        current.finishPending = false
        current.lastDelta = 0f
        current.serverEfficiency = 0.0
    }

    private fun clearTargets(abort: Boolean) {
        targets.forEach { clearTarget(it, abort) }
        prepared = null
        lastAction = null
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
                current.progress.phase != PacketMineProgress.Phase.START
        ) {
            pendingAborts = (pendingAborts + current).distinctBy { it.pos }
            // ABORT can cancel the server's active position even when it names the delayed block.
            // Re-establish any remaining initial break without erasing confirmed Rebreak priming.
            targets
                .filter { it !== current && it.progress.phase == PacketMineProgress.Phase.MINING }
                .forEach { resetMining(it) }
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
        mc.gui.screen() != null ||
            player.isUsingItem ||
            player.containerMenu !== player.inventoryMenu ||
            !player.containerMenu.carried.isEmpty ||
            InventoryMoveModule.isInventoryActionSlowed()

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
        val distance =
            player.eyePosition.distanceToSqr(
                RotationUtil.getNearestPoint(player.eyePosition, AABB(pos))
            )
        val reach = minOf(range, player.blockInteractionRange())
        return distance <= reach * reach && player.isWithinBlockInteractionRange(pos, 0.0)
    }

    private fun outgoingHit(current: Target): BlockHitResult? {
        return actions.blockHit(visibleHit(current) ?: return null, range)
    }

    private fun visibleHit(current: Target): BlockHitResult? {
        return AuraActionQueue.visibleHit(current.player, current.pos, range)
    }
}
