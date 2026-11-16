/*
 * Copyright (c) 2015 - 2026 CCBlueX; earlier adaptation (c) Meteor Development.
 * LiquidBounce Normal adaptation: 2026-11-15.
 * SPDX-License-Identifier: GPL-3.0-only. See docs/scaffold-port.md.
 */
package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.PlacementReservations
import meow.ancient.client.util.inventory.InventoryAutomation
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.render.AuraTargetRenderer
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.MovementInputEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerMovementSentEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object ScaffoldModule : Module("Scaffold", MOVEMENT, "LiquidBounce Normal block bridging") {
    private val range by number("Range", 4.5, 1.0, 6.0, 0.1, 1)
    private val delay by number("Delay", 0.0, 0.0, 10.0, 1.0, 0, unit = "ticks")
    private val rotations by boolean("Rotations", true)
    private val keepY by boolean("Keep Y", false)
    private val prediction by boolean("Prediction", true)
    private val autoSwitch by boolean("Auto Switch", true)
    private val safeWalk by boolean("Safe Walk", true, "Waits at edges for confirmed placement")

    private data class Placement(val site: ScaffoldPlacement.Site, val hand: InteractionHand, val slot: Int,
                                 val line: ScaffoldMovementPlanner.Line?, val fallOffPosition: Vec3?)
    private var prepared: Placement? = null
    private val attempts = ScaffoldAttempts<BlockPos>()
    private var playerContext: LocalPlayer? = null
    private var world: ClientLevel? = null
    private var placementY = 0
    private val movementPlanner = ScaffoldMovementPlanner()
    private val movementPrediction = ScaffoldMovementPrediction()
    private var bridgeY = 0
    private var previousPosition = Vec3.ZERO
    private var sentPosition = Vec3.ZERO
    private var sentRotation = Rotation(0f, 0f)
    private var hasMovement = false
    private var waitingForSupport = false
    private val visual = AuraTargetRenderer({ isEnabled() }, { 14.0 })

    fun hasPendingAction(): Boolean = isEnabled() && prepared != null && attempts.target == null
    fun shouldBlockAutoJump(): Boolean = isEnabled() && safeWalk && waitingForSupport
        && !mc.options.keyJump.isDown

    private fun releaseRotation() {
        if (RotationManager.rotationOwner == name) RotationManager.resetImmediate()
    }

    private fun clearPrepared() {
        prepared = null
        SilentSwapUtil.releasePreparedSlot(this)
        releaseRotation()
    }

    private fun reset() {
        clearPrepared(); attempts.reset(); playerContext = null; world = null
        hasMovement = false; waitingForSupport = false
        movementPlanner.reset(); movementPrediction.reset()
        visual.reset(); PlacementReservations.release(this)
        InventoryMoveModule.cancelInventoryActions(this)
    }

    override fun onEnable() = reset()
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

    private fun available(player: LocalPlayer): Boolean = InventoryAutomation.available()
        && !InventoryAutomation.busy() && !InventoryMoveModule.isInventoryActionSlowed()
        && !player.isPassenger && !player.isFallFlying && !player.abilities.flying

    @Commando(priority = -128)
    fun onPacket(event: PacketSentEvent) {
        if (!mc.isSameThread) return
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return
        if (mc.player !== playerContext || mc.level !== world) return
        sentPosition = Vec3(packet.getX(sentPosition.x), packet.getY(sentPosition.y), packet.getZ(sentPosition.z))
        sentRotation = Rotation(packet.getYRot(sentRotation.yaw), packet.getXRot(sentRotation.pitch))
        hasMovement = true
    }

    private fun heading(): Vec3 {
        val forward = (if (mc.options.keyUp.isDown) 1 else 0) - (if (mc.options.keyDown.isDown) 1 else 0)
        val sideways = (if (mc.options.keyLeft.isDown) 1 else 0) - (if (mc.options.keyRight.isDown) 1 else 0)
        val yaw = Math.toRadians(RotationManager.clientYaw.toDouble())
        val direction = Vec3(sideways * cos(yaw) - forward * sin(yaw), 0.0,
            forward * cos(yaw) + sideways * sin(yaw))
        return if (direction.lengthSqr() > 0.0001) direction.normalize() else Vec3.ZERO
    }

    private fun blockHand(pos: BlockPos): Pair<InteractionHand, Int>? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        if (ScaffoldPlacement.validItem(level, pos, player.mainHandItem))
            return InteractionHand.MAIN_HAND to player.inventory.selectedSlot
        if (ScaffoldPlacement.validItem(level, pos, player.offhandItem))
            return InteractionHand.OFF_HAND to player.inventory.selectedSlot
        if (!autoSwitch) return null
        return (sequenceOf(SilentSwapUtil.serverSlot) + (0..8).asSequence()).distinct()
            .filter { it in 0..8 && ScaffoldPlacement.validItem(level, pos, player.inventory.getItem(it)) }
            .sortedWith(compareBy<Int> { ScaffoldPlacement.unfavorable(level, pos, player.inventory.getItem(it)) }
                .thenBy { !(player.inventory.getItem(it).item as net.minecraft.world.item.BlockItem).block
                    .defaultBlockState().isCollisionShapeFullBlock(level, pos) }
                .thenByDescending { player.inventory.getItem(it).count })
            .firstOrNull()?.let { InteractionHand.MAIN_HAND to it }
    }

    private fun syncContext(player: LocalPlayer, level: ClientLevel) {
        if (playerContext !== player || world !== level || previousPosition.distanceToSqr(player.position()) > 64.0) {
            reset(); playerContext = player; world = level
            placementY = ScaffoldTargetGeometry.targetY(player.boundingBox.minY, null, false)
            sentPosition = player.position(); sentRotation = Rotation(player.yRot, player.xRot)
        }
        previousPosition = player.position()
        if (player.onGround() && ScaffoldPlacement.hasSupport(player, level, player.boundingBox))
            placementY = ScaffoldTargetGeometry.targetY(player.boundingBox.minY, null, false)
        // LiquidBounce SameY Off follows current feet even in the air. Normal stationary jumping
        // also follows feet, allowing vanilla jump placement without a synthetic tower velocity.
        val stationaryJump = mc.options.keyJump.isDown
            && (heading().horizontalDistanceSqr() < 1.0e-8 || player.horizontalCollision)
        bridgeY = ScaffoldTargetGeometry.targetY(player.boundingBox.minY, placementY.takeIf { keepY }, stationaryJump)
        attempts.retainFailures { hypot(it.x + 0.5 - player.x, it.z + 0.5 - player.z) < 2.0 }
    }

    private fun updateConfirmation(player: LocalPlayer, level: ClientLevel) {
        val pos = attempts.target ?: return
        val result = attempts.update(player.tickCount, ScaffoldPlacement.confirmedBlock(level, pos),
            level.blockStatePredictionHandler.serverVerifiedStates.containsKey(pos.asLong()))
        if (result == ScaffoldAttempts.Result.CONFIRMED || result == ScaffoldAttempts.Result.REJECTED)
            PlacementReservations.release(this)
    }

    private fun plan(player: LocalPlayer, level: ClientLevel, planningTick: Int): Placement? {
        val direction = heading()
        val line = movementPlanner.movementLine(player, level, direction)
        val predicted = if (prediction) movementPrediction.predictedPosition(player, level, movementPlanner, line)
            ?: player.position() else player.position()
        val requested = BlockPos.containing(predicted.x, bridgeY.toDouble(), predicted.z)
        // The key guard from LiquidBounce's findBestBlockPlacementTarget: once the requested
        // support exists, investigating offsets would only place stray blocks beside it.
        if (ScaffoldPlacement.confirmedBlock(level, requested)) return null
        val rotation = RotationManager.getServerRotation()
        val reach = minOf(range, player.blockInteractionRange())
        for (pos in ScaffoldTargetGeometry.candidates(requested, predicted, line, keepY && bridgeY == placementY)) {
            if (!attempts.canAttempt(pos, planningTick, delay.toInt()) || PacketMineModule.targetsPosition(pos)
                || PlacementReservations.reserved(pos, this) || !level.getBlockState(pos).canBeReplaced()) continue
            val (hand, slot) = blockHand(pos) ?: continue
            val stack = if (hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(slot)
            val site = ScaffoldPlacement.findSite(player, level, pos, hand, stack, reach, predicted, rotation, line) ?: continue
            val edge = if (prediction && line != null) movementPrediction.fallOffPosition(player, level, line) else null
            return Placement(site, hand, slot, line, edge)
        }
        return null
    }

    private fun updatePlan(beforePhysics: Boolean = false) {
        val player = mc.player ?: return reset()
        val level = mc.level ?: return reset()
        syncContext(player, level)
        updateConfirmation(player, level)
        if (attempts.target != null) {
            clearPrepared()
            visual.show(AABB(attempts.target!!), AuraTargetRenderer.Kind.PLACE)
            return
        }
        if (!available(player) || player.y < bridgeY + 0.7 || player.y > bridgeY + 3.0) {
            clearPrepared(); return
        }
        // Entity increments tickCount between PlayerUpdate (tick HEAD) and input/physics.
        // Prepare for that upcoming tick; place() still checks the actual current tick.
        val placement = plan(player, level, player.tickCount + if (beforePhysics) 1 else 0)
            ?: run { clearPrepared(); return }
        prepared = placement
        if (!ActionCoordinator.canAct(this) || !SilentSwapUtil.prepareSlot(placement.slot, 2, this)) return
        if (rotations) ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(this)) {
            RotationManager.setRotation(placement.site.rotation,
                MoveFixMode.SILENT, keepTicks = 0, returnSpeed = 180f)
        } else releaseRotation()
        visual.show(AABB(placement.site.pos), AuraTargetRenderer.Kind.PLACE)
    }

    @Commando(priority = 120)
    fun onUpdate(event: PlayerUpdateEvent) {
        visual.beginTick(); waitingForSupport = false
        updatePlan(beforePhysics = true)
    }

    /** Recompute from the position after physics, before sendChanges applies the outgoing look. */
    fun onAfterMovement() {
        if (isEnabled()) updatePlan()
    }

    @Commando(priority = -128)
    fun onMovementSent(event: PlayerMovementSentEvent) = place()

    private fun place() {
        val placement = prepared ?: return
        val player = mc.player ?: return
        val level = mc.level ?: return
        val pos = placement.site.pos
        if (!hasMovement || player !== playerContext || level !== world || !available(player)
            || !attempts.canAttempt(pos, player.tickCount, delay.toInt()) || !ActionCoordinator.canAct(this)
            || SilentSwapUtil.serverSlot != placement.slot || PacketMineModule.targetsPosition(pos)
            || PlacementReservations.reserved(pos, this)) return
        val hit = ScaffoldPlacement.aimedHit(player, level, sentPosition.add(0.0, player.eyeHeight.toDouble(), 0.0),
            sentRotation, minOf(range, player.blockInteractionRange()), placement.site) ?: return
        val stack = if (placement.hand == InteractionHand.OFF_HAND) player.offhandItem else player.inventory.getItem(placement.slot)
        if (!ScaffoldPlacement.canPlace(player, level, pos, placement.hand, stack, hit)) return
        val ping = player.connection.getPlayerInfo(player.uuid)?.latency ?: 150
        val timeout = (ping / 50 + 10).coerceIn(10, 80)
        // Reserve the attempt before calling native code: input and send callbacks cannot double-use it.
        attempts.begin(pos, player.tickCount, timeout)
        prepared = null
        PlacementReservations.claim(pos, this, timeout)
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(this)) {
            val yaw = player.yRot; val pitch = player.xRot
            player.yRot = sentRotation.yaw; player.xRot = sentRotation.pitch
            try {
                if (InventoryUtil.useItemOn(placement.hand, hit)) {
                    if (!level.getBlockState(pos).canBeReplaced()) {
                        movementPlanner.trackPlacedBlock(pos)
                        if (prediction) movementPrediction.onPlace(placement.line, placement.fallOffPosition, player.position())
                        visual.pulse()
                    }
                } else { attempts.reject(player.tickCount); PlacementReservations.release(this) }
            } finally {
                player.yRot = yaw; player.xRot = pitch
                SilentSwapUtil.releasePreparedSlot(this)
            }
        }
    }

    @Commando(priority = -100)
    fun onInput(event: MovementInputEvent) {
        place()
        waitingForSupport = false
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!safeWalk || !player.isAlive || player.isSpectator || !player.onGround() || player.isPassenger
            || player.isFallFlying || player.abilities.flying || mc.gui.screen() != null) return
        val input = event.input.keyPresses
        val direction = heading().let {
            if (it.lengthSqr() > 0.0001) it else Vec3(player.deltaMovement.x, 0.0, player.deltaMovement.z).let { motion ->
                if (motion.lengthSqr() > 0.0001) motion.normalize() else Vec3.ZERO
            }
        }
        if (direction.lengthSqr() < 0.0001) return
        val step = maxOf(if (input.jump()) 0.65 else 0.35,
            hypot(player.deltaMovement.x, player.deltaMovement.z) + 0.12).coerceAtMost(1.0)
        if (ScaffoldPlacement.hasSupport(player, level, player.boundingBox.move(direction.scale(step)))) return
        waitingForSupport = true
        event.input.keyPresses = Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), true, false)
        player.isSprinting = false
    }

    /** Vanilla-style crouching edge restriction, checked against server blocks instead of prediction. */
    fun limitMovement(movement: Vec3): Vec3 {
        if (!isEnabled() || !safeWalk) return movement
        val player = mc.player ?: return movement
        val level = mc.level ?: return movement
        if (movement.y > 0.0 && player.input.keyPresses.jump()) return movement
        if (!player.onGround() || player.isSpectator || player.isPassenger || player.isFallFlying
            || player.abilities.flying || mc.gui.screen() != null
            || (!waitingForSupport && !player.input.keyPresses.shift())
            || !ScaffoldPlacement.hasSupport(player, level, player.boundingBox)) return movement
        return ScaffoldSafeWalk.movement(movement) { x, z ->
            ScaffoldPlacement.hasSupport(player, level, player.boundingBox.move(x, 0.0, z))
        }
    }
}
