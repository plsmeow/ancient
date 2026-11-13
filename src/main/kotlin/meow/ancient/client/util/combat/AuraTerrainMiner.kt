package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.event.EventManager
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.module.modules.player.PacketMineProgress
import meow.ancient.module.modules.player.PacketMineModule
import meow.ancient.module.modules.player.PacketMineToolSpeed
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import sweetie.evaware.flora.api.Commando

/** A single temporary terrain target. It does not alter PacketMine's manual targets/settings. */
class AuraTerrainMiner(private val owner: Any, private val actions: AuraActionQueue,
                       private val startAllowed: (BlockPos) -> Boolean = { true }) {
    private class Target(val player: LocalPlayer, val level: ClientLevel, val pos: BlockPos) {
        val progress = PacketMineProgress()
        var state = level.getBlockState(pos)
        var slot = -1
        var tool = ItemStack.EMPTY
        var packet = false
        var started = false
        var direction = Direction.UP
        var startNanos = 0L
        var delta = 0f
        var ready = false
        var confirmed = false
        var efficiency = 0.0
        var breakSpeed = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
        var stopTick = 0
        fun current() = mc.player === player && mc.level === level && player.connection.connection.isConnected
    }

    private var target: Target? = null
    private var abort: Target? = null
    private var sending: Packet<*>? = null
    private var accepted = false
    val active get() = target != null
    val position get() = target?.pos
    val progress: Float get() = target?.let {
        when {
            it.confirmed || it.stopTick > 0 || it.progress.phase == PacketMineProgress.Phase.CONFIRM -> 1f
            it.packet -> it.progress.damage.coerceIn(0f, 1f)
            it.started -> ((mc.gameMode?.destroyStage ?: -1) + 1).coerceIn(0, 10) / 10f
            else -> 0f
        }
    } ?: 0f

    init { EventManager.register(this) }

    companion object {
        private var vanillaDepth = 0
        private fun terrainHardness(hardness: Float) = hardness.isFinite() && hardness in 0f..3f
        @JvmStatic fun isVanillaCall() = vanillaDepth > 0
        private fun <T> vanilla(action: () -> T): T {
            vanillaDepth++
            return try { action() } finally { vanillaDepth-- }
        }
    }

    fun keepVanillaMining(): Boolean = target?.let {
        it.current() && !it.packet && it.started && !isVanillaCall() && !mc.options.keyAttack.isDown
            && !mc.options.keyUse.isDown && mc.gui.screen() == null && !it.player.isUsingItem
            && ActionCoordinator.canAct(owner)
    } == true

    fun canMine(player: LocalPlayer, pos: BlockPos, range: Double): Boolean {
        val level = mc.level ?: return false
        val state = level.getBlockState(pos)
        val gameMode = mc.gameMode ?: return false
        val reach = minOf(range, player.blockInteractionRange())
        return !state.isAir && !state.hasBlockEntity() && state.fluidState.isEmpty
            && terrainHardness(state.getDestroySpeed(level, pos)) && level.worldBorder.isWithinBounds(pos)
            && !player.blockActionRestricted(level, pos, gameMode.playerMode)
            && player.isAlive && !player.isSpectator && !player.isCreative && !player.isPassenger
            && player.abilities.mayBuild && player.eyePosition.distanceToSqr(
                meow.ancient.client.util.rotation.RotationUtil.getNearestPoint(player.eyePosition, AABB(pos))) <= reach * reach
            && toolSlot(player, state, pos) >= 0
    }

    private fun toolSlot(player: LocalPlayer, state: BlockState, pos: BlockPos): Int = (0..8)
        .filter {
            val stack = player.inventory.getItem(it)
            !stack.isEmpty && stack.getDestroySpeed(state) > 1f
                && (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state))
                && stack.canDestroyBlock(state, mc.level!!, pos, player)
        }.maxByOrNull { player.inventory.getItem(it).getDestroySpeed(state) } ?: -1

    fun select(pos: BlockPos): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        if (target?.pos == pos) return true
        cancel()
        target = Target(player, level, pos.immutable())
        return true
    }

    fun cancel() {
        val current = target ?: return
        target = null
        if (current.started && !current.confirmed) abort = current
        SilentSwapUtil.releasePreparedSlot(owner)
    }

    @Commando(priority = 127)
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val current = abort ?: return
        abort = null
        if (!current.current()) return
        // ABORT clears the server's mining state even when it names another position.
        // The manual miner's START replaces our old target; never abort its new operation.
        if (PacketMineModule.hasPendingAction() || mc.options.keyAttack.isDown) return
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(owner)) {
            if (current.packet) current.player.connection.send(ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, current.pos, current.direction))
            else if (!mc.options.keyAttack.isDown) vanilla { mc.gameMode?.stopDestroyBlock() }
        }
    }

    fun update(range: Double, packet: Boolean, rotations: Boolean, moveFix: MoveFixMode) {
        val current = target ?: return
        if (!current.current() || current.confirmed && current.level.getBlockState(current.pos).isAir
            || mc.options.keyAttack.isDown) {
            cancel()
            return
        }
        val timeout = ((current.player.connection.getPlayerInfo(current.player.uuid)?.latency ?: 150).coerceAtLeast(0) / 50 + 5).coerceIn(5, 80)
        if (!current.packet && current.stopTick > 0) {
            if (current.player.tickCount - current.stopTick >= timeout) cancel()
            return
        }
        if (!canMine(current.player, current.pos, range)) { cancel(); return }
        if (current.started && current.packet != packet) { cancel(); return }
        current.packet = packet
        val player = current.player
        val state = current.level.getBlockState(current.pos)
        val slot = toolSlot(player, state, current.pos)
        val tool = player.inventory.getItem(slot)
        if (current.started && (state.block !== current.state.block || slot != current.slot
            || !ItemStack.isSameItemSameComponents(current.tool, tool))) { cancel(); return }
        val hit = AuraActionQueue.visibleHit(player, current.pos, range) ?: run { cancel(); return }
        if (current.progress.timedOut(player.tickCount.toLong(), timeout.toLong())) { cancel(); return }
        if (packet) {
            if (!SilentSwapUtil.prepareSlot(slot, 2, owner)) return
            if (!current.started) {
                current.efficiency = minOf(player.getAttributeValue(Attributes.MINING_EFFICIENCY),
                    PacketMineToolSpeed.attributeValue(player.getAttribute(Attributes.MINING_EFFICIENCY)!!,
                        Attributes.MINING_EFFICIENCY, player.mainHandItem, tool))
            }
            if (current.progress.phase == PacketMineProgress.Phase.CONFIRM) return
            if (current.started) {
                current.delta = PacketMineToolSpeed.progress(player, tool, state, state.getDestroySpeed(current.level, current.pos),
                    current.efficiency, current.breakSpeed)
                current.ready = current.progress.advance(current.delta, player.tickCount.toLong())
                    && current.progress.enoughTime(System.nanoTime() - current.startNanos, current.delta, 1f)
            }
        } else {
            InventoryUtil.swapToSlot(slot)
            if (player.inventory.selectedSlot != slot) return
        }
        current.slot = slot
        current.state = state
        current.tool = tool.copy()
        actions.block(hit, range, rotations, moveFix) { actualHit ->
            if (target !== current || !canMine(player, current.pos, range)
                || !ItemStack.isSameItemSameComponents(player.inventory.getItem(slot), current.tool)) return@block
            if (!current.started && !startAllowed(current.pos)) return@block
            current.direction = actualHit.direction
            if (packet) {
                if (SilentSwapUtil.serverSlot != slot) return@block
                if (!current.started) {
                    if (!send(current, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)) return@block
                    current.started = true
                    current.startNanos = System.nanoTime()
                    current.progress.start()
                } else if (current.ready) {
                    if (!send(current, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)) return@block
                    current.progress.stop(player.tickCount.toLong())
                }
            } else {
                vanilla { mc.gameMode?.continueDestroyBlock(current.pos, actualHit.direction) }
                current.started = true
                if (current.level.getBlockState(current.pos).isAir) current.stopTick = player.tickCount
            }
            player.swing(InteractionHand.MAIN_HAND, tool.attackAnimation, false)
            player.connection.send(ServerboundPunchPacket.INSTANCE)
        }
    }

    private fun send(current: Target, action: ServerboundPlayerActionPacket.Action): Boolean {
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
    fun onPacketSent(event: PacketSentEvent) {
        if (mc.isSameThread && event.packet === sending) accepted = true
    }

    @Commando
    fun onPacket(event: PacketReceiveEvent) {
        if (event.cancel) return
        val current = target ?: return
        fun block(pos: BlockPos, state: BlockState) {
            if (pos != current.pos) return
            mc.execute { if (target === current && current.current() && state.isAir) current.confirmed = true }
        }
        when (val packet = event.packet) {
            is ClientboundBlockUpdatePacket -> block(packet.pos, packet.blockState)
            is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, state -> block(pos.immutable(), state) }
            is ClientboundUpdateAttributesPacket -> mc.execute {
                if (target !== current || !current.current() || packet.entityId != current.player.id
                    || SilentSwapUtil.serverSlot != current.slot) return@execute
                for (attribute in packet.values) {
                    val value = PacketMineToolSpeed.snapshotValue(attribute)
                    when (attribute.attribute()) {
                        Attributes.MINING_EFFICIENCY -> current.efficiency = value
                        Attributes.BLOCK_BREAK_SPEED -> current.breakSpeed = value
                        else -> Unit
                    }
                }
            }
        }
    }
}
