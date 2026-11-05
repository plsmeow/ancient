package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.combat.ExplosionDamageUtil
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.client.util.inventory.InventoryActionSource
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import java.util.concurrent.ThreadLocalRandom
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
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
    val delay by number("Delay", 1.0, 0.0, 10.0, 1.0, 0)

    val moveFixSetting = mode("MoveFix", "Silent", listOf("Silent", "Strict", "Free", "Off"))
    val moveFix by moveFixSetting

    val rotations by boolean("Rotations", true)
    val silentSlots by boolean("Silent Slots", true)
    val ignoreTerrain by boolean("Ignore Terrain", false, "Treats all blocks as indestructible when calculating explosion damage")
    val lethalOverride by boolean("Lethal Override", true)

    private enum class SequenceStage {
        PLACE_SENT,
        CHARGE_SENT,
        DETONATE_SENT
    }

    private data class AnchorSequence(val pos: BlockPos, var stage: SequenceStage)

    private var tickTimer = 0
    private var sequence: AnchorSequence? = null
    private var sequencePlayer: LocalPlayer? = null
    private var sequenceLevel: Level? = null

    private fun reset() {
        tickTimer = 0
        sequence = null
        sequencePlayer = null
        sequenceLevel = null
    }

    private fun reconcileSequence(level: Level) {
        val current = sequence ?: return
        val state = level.getBlockState(current.pos)
        if (state.`is`(Blocks.RESPAWN_ANCHOR)) return
        if (current.stage != SequenceStage.PLACE_SENT || !state.canBeReplaced()) {
            sequence = null
        }
    }

    private fun getMoveFixMode(): MoveFixMode = MoveFixMode.fromName(moveFix)

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
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
        }
        if (level.dimension() == Level.NETHER) return

        reconcileSequence(level)

        val target = findTarget(player)
        if (target == null) {
            tickTimer = 0
            return
        }

        tickTimer++
        if (tickTimer <= delay.toInt()) return

        val targetHp = AnticheatManager.getHealth(target)
        val current = sequence
        val executed = if (current != null) {
            advanceSequence(player, target, targetHp, current)
        } else {
            explodeChargedAnchor(player, target, targetHp)
                || chargeExistingAnchor(player, target, targetHp)
                || placeNewAnchor(player, target, targetHp)
        }

        if (executed) tickTimer = 0
    }

    private fun findTarget(player: Player): Player? {
        val level = mc.level ?: return null
        var best: Player? = null
        var bestDist = targetRange * targetRange
        var bestIsTarget = false
        val maxReachSq = targetRange * targetRange
        for (entity in level.entitiesForRendering()) {
            if (entity !is Player || entity === player || !entity.isAlive || entity.isCreative || entity.isSpectator) continue
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

    private fun advanceSequence(player: LocalPlayer, target: Player, targetHp: Float, current: AnchorSequence): Boolean {
        val level = mc.level ?: return false
        if (current.stage == SequenceStage.DETONATE_SENT) return false
        val state = level.getBlockState(current.pos)
        if (!state.`is`(Blocks.RESPAWN_ANCHOR)) return false
        if (player.eyePosition.distanceToSqr(Vec3.atCenterOf(current.pos)) > range * range) return false
        if (ExplosionDamageUtil.anchorDamage(current.pos, player, ignoreTerrain) > maxSelfDamage.toFloat()) return false
        val targetDamage = ExplosionDamageUtil.anchorDamage(current.pos, target, ignoreTerrain)
        val isLethal = lethalOverride && targetDamage >= targetHp
        if (!isLethal && targetDamage < minDamage.toFloat()) return false

        val hitResult = getAnchorHitResult(current.pos, player)
        if (state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
            if (!detonateAnchor(player, hitResult)) return false
            current.stage = SequenceStage.DETONATE_SENT
            return true
        }
        if (current.stage != SequenceStage.PLACE_SENT || !chargeAnchor(player, hitResult)) return false
        current.stage = SequenceStage.CHARGE_SENT
        return true
    }

    private fun interact(player: LocalPlayer, hand: InteractionHand, hitResult: BlockHitResult, slot: Int? = null): Boolean {
        val gameMode = mc.gameMode ?: return false
        if (!mc.isSameThread || mc.player !== player || mc.level !== sequenceLevel
            || !player.connection.connection.isConnected || !player.containerMenu.carried.isEmpty
            || player.isSpectator || slot != null && slot !in 0..8) return false

        if (hand == InteractionHand.MAIN_HAND && silentSlots) {
            val selected = slot ?: player.inventory.selectedSlot
            var executed = false
            InventoryUtil.withSilentSlot(selected) {
                if (mc.player === player && mc.level === sequenceLevel && mc.gameMode === gameMode) {
                    rotate(player, hitResult.location)
                    executed = InventoryUtil.useItemOnPacket(hand, hitResult)
                }
            }
            return executed
        }

        if (slot != null) {
            InventoryUtil.swapToSlot(slot)
            if (player.inventory.selectedSlot != slot) return false
        }
        rotate(player, hitResult.location)
        val result = gameMode.useItemOn(player, hand, hitResult)
        if (!result.consumesAction()) return false
        player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
        return true
    }

    private fun chargeAnchor(player: LocalPlayer, hitResult: BlockHitResult): Boolean {
        if (player.isSecondaryUseActive) return false
        if (player.offhandItem.`is`(Items.GLOWSTONE)) {
            return interact(player, InteractionHand.OFF_HAND, hitResult)
        }
        val slot = InventoryUtil.findHotbarSlot(Items.GLOWSTONE)
        if (slot !in 0..8) return false
        return interact(player, InteractionHand.MAIN_HAND, hitResult, slot)
    }

    private fun detonateAnchor(player: LocalPlayer, hitResult: BlockHitResult): Boolean {
        val selected = player.inventory.selectedSlot
        val slot = selected.takeIf {
            it in 0..8 && !player.inventory.getItem(it).isEmpty && !player.inventory.getItem(it).`is`(Items.GLOWSTONE)
        }
            ?: (0..8).firstOrNull { !player.inventory.getItem(it).isEmpty && !player.inventory.getItem(it).`is`(Items.GLOWSTONE) }
            ?: (0..8).firstOrNull { !player.inventory.getItem(it).`is`(Items.GLOWSTONE) }
            ?: return false
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

    private fun explodeChargedAnchor(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
        val level = mc.level ?: return false
        val pPos = player.blockPosition()
        val r = range.toInt() + 1
        val maxReachSq = range * range

        var bestPos: BlockPos? = null
        var bestDamage = 0.0f

        val minX = pPos.x - r
        val maxX = pPos.x + r
        val minY = (pPos.y - r).coerceAtLeast(level.minY)
        val maxY = (pPos.y + r).coerceAtMost(level.maxY)
        val minZ = pPos.z - r
        val maxZ = pPos.z + r

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (!state.`is`(Blocks.RESPAWN_ANCHOR)) continue
                    if (state.getValue(RespawnAnchorBlock.CHARGE) <= 0) continue

                    val center = Vec3.atCenterOf(pos)
                    if (player.eyePosition.distanceToSqr(center) > maxReachSq) continue

                    val selfDamage = ExplosionDamageUtil.anchorDamage(pos, player, ignoreTerrain)
                    if (selfDamage > maxSelfDamage.toFloat()) continue

                    val targetDamage = ExplosionDamageUtil.anchorDamage(pos, target, ignoreTerrain)
                    val isLethal = lethalOverride && targetDamage >= targetHp
                    if (!isLethal && targetDamage < minDamage.toFloat()) continue

                    if (targetDamage > bestDamage) {
                        bestDamage = targetDamage
                        bestPos = pos
                    }
                }
            }
        }

        val anchorPos = bestPos ?: return false
        if (!detonateAnchor(player, getAnchorHitResult(anchorPos, player))) return false
        sequence = AnchorSequence(anchorPos, SequenceStage.DETONATE_SENT)
        return true
    }

    private fun chargeExistingAnchor(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
        val level = mc.level ?: return false
        val pPos = player.blockPosition()
        val r = range.toInt() + 1
        val maxReachSq = range * range

        var bestPos: BlockPos? = null
        var bestDamage = 0.0f

        val minX = pPos.x - r
        val maxX = pPos.x + r
        val minY = (pPos.y - r).coerceAtLeast(level.minY)
        val maxY = (pPos.y + r).coerceAtMost(level.maxY)
        val minZ = pPos.z - r
        val maxZ = pPos.z + r

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (!state.`is`(Blocks.RESPAWN_ANCHOR)) continue
                    if (state.getValue(RespawnAnchorBlock.CHARGE) > 0) continue

                    val center = Vec3.atCenterOf(pos)
                    if (player.eyePosition.distanceToSqr(center) > maxReachSq) continue

                    val selfDamage = ExplosionDamageUtil.anchorDamage(pos, player, ignoreTerrain)
                    if (selfDamage > maxSelfDamage.toFloat()) continue

                    val targetDamage = ExplosionDamageUtil.anchorDamage(pos, target, ignoreTerrain)
                    val isLethal = lethalOverride && targetDamage >= targetHp
                    if (!isLethal && targetDamage < minDamage.toFloat()) continue

                    if (targetDamage > bestDamage) {
                        bestDamage = targetDamage
                        bestPos = pos
                    }
                }
            }
        }

        val anchorPos = bestPos ?: return false
        if (!chargeAnchor(player, getAnchorHitResult(anchorPos, player))) return false
        sequence = AnchorSequence(anchorPos, SequenceStage.CHARGE_SENT)
        return true
    }

    private fun placeNewAnchor(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
        val level = mc.level ?: return false
        val pPos = player.blockPosition()
        val r = range.toInt() + 1
        val maxReachSq = range * range

        var bestPos: BlockPos? = null
        var bestDamage = 0.0f
        var bestHit: BlockHitResult? = null

        val minX = pPos.x - r
        val maxX = pPos.x + r
        val minY = (pPos.y - r).coerceAtLeast(level.minY)
        val maxY = (pPos.y + r).coerceAtMost(level.maxY)
        val minZ = pPos.z - r
        val maxZ = pPos.z + r

        val anchorAabb = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    if (!level.getBlockState(pos).canBeReplaced()) continue

                    val center = Vec3.atCenterOf(pos)
                    if (player.eyePosition.distanceToSqr(center) > maxReachSq) continue

                    val selfDamage = ExplosionDamageUtil.anchorDamage(pos, player, ignoreTerrain)
                    if (selfDamage > maxSelfDamage.toFloat()) continue

                    val targetDamage = ExplosionDamageUtil.anchorDamage(pos, target, ignoreTerrain)
                    val isLethal = lethalOverride && targetDamage >= targetHp
                    if (!isLethal && targetDamage < minDamage.toFloat()) continue

                    val box = anchorAabb.move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                    if (level.getEntities(null, box).any { it.isAlive }) continue

                    val hit = getPlaceHitResult(pos, player) ?: continue

                    if (targetDamage > bestDamage) {
                        bestDamage = targetDamage
                        bestPos = pos
                        bestHit = hit
                    }
                }
            }
        }

        val anchorPos = bestPos ?: return false
        val hitResult = bestHit ?: return false
        val executed = if (player.offhandItem.`is`(Items.RESPAWN_ANCHOR)) {
            interact(player, InteractionHand.OFF_HAND, hitResult)
        } else {
            val slot = InventoryUtil.findHotbarSlot(Items.RESPAWN_ANCHOR)
            if (slot !in 0..8) return false
            interact(player, InteractionHand.MAIN_HAND, hitResult, slot)
        }
        if (!executed) return false
        sequence = AnchorSequence(anchorPos, SequenceStage.PLACE_SENT)
        return true
    }

    private fun getPlaceHitResult(pos: BlockPos, player: LocalPlayer): BlockHitResult? {
        val level = mc.level ?: return null
        val eyes = player.eyePosition
        var bestHit: BlockHitResult? = null
        var bestDist = Double.MAX_VALUE

        for (side in Direction.values()) {
            val neighbour = pos.relative(side.opposite)
            val neighbourState = level.getBlockState(neighbour)
            if (neighbourState.canBeReplaced() || neighbourState.isAir || neighbourState.hasBlockEntity()) continue

            val hitVec = Vec3(
                neighbour.x + 0.5 + side.stepX * 0.5,
                neighbour.y + 0.5 + side.stepY * 0.5,
                neighbour.z + 0.5 + side.stepZ * 0.5
            )

            val dot = (eyes.x - hitVec.x) * side.stepX + (eyes.y - hitVec.y) * side.stepY + (eyes.z - hitVec.z) * side.stepZ
            if (dot <= 0.0) continue

            val dist = eyes.distanceToSqr(hitVec)
            if (dist > range * range || dist >= bestDist) continue

            val clip = level.clip(
                ClipContext(
                    eyes,
                    hitVec,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )

            if (clip.type == HitResult.Type.BLOCK && clip.blockPos == neighbour) {
                bestHit = BlockHitResult(hitVec, side, neighbour, false)
                bestDist = dist
            }
        }
        if (bestHit != null) return bestHit

        val down = pos.below()
        val downState = level.getBlockState(down)
        if (!downState.canBeReplaced() && !downState.isAir && !downState.hasBlockEntity() && eyes.y > pos.y) {
            val hitVec = Vec3(down.x + 0.5, down.y + 1.0, down.z + 0.5)
            if (eyes.distanceToSqr(hitVec) <= range * range) {
                return BlockHitResult(hitVec, Direction.UP, down, false)
            }
        }
        return null
    }

    private fun getAnchorHitResult(pos: BlockPos, player: LocalPlayer): BlockHitResult {
        val level = mc.level ?: return BlockHitResult(Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5), Direction.UP, pos, false)
        val eyes = player.eyePosition
        var bestHit: BlockHitResult? = null
        var bestDist = Double.MAX_VALUE

        for (side in Direction.values()) {
            val hitVec = Vec3(
                pos.x + 0.5 + side.stepX * 0.5,
                pos.y + 0.5 + side.stepY * 0.5,
                pos.z + 0.5 + side.stepZ * 0.5
            )

            val dot = (eyes.x - hitVec.x) * side.stepX + (eyes.y - hitVec.y) * side.stepY + (eyes.z - hitVec.z) * side.stepZ
            if (dot <= 0.0) continue

            val dist = eyes.distanceToSqr(hitVec)
            if (dist > range * range || dist >= bestDist) continue

            val clip = level.clip(
                ClipContext(
                    eyes,
                    hitVec,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )

            if (clip.type == HitResult.Type.BLOCK && clip.blockPos == pos) {
                bestHit = BlockHitResult(hitVec, side, pos, false)
                bestDist = dist
            }
        }
        if (bestHit != null) return bestHit

        val side = if (pos.y + 0.5 > eyes.y) Direction.DOWN else Direction.UP
        val hitY = if (side == Direction.UP) pos.y + 1.0 else pos.y.toDouble()
        return BlockHitResult(Vec3(pos.x + 0.5, hitY, pos.z + 0.5), side, pos, false)
    }

    private fun rotate(player: LocalPlayer, location: Vec3) {
        if (rotations) {
            val rot = RotationUtil.getRotationToVec(location, player.eyePosition)
            val rng = ThreadLocalRandom.current()
            rot.yaw += rng.nextFloat(-0.04f, 0.04f)
            rot.pitch += rng.nextFloat(-0.04f, 0.04f)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }
    }

    override fun onEnable() = reset()

    override fun onDisable() = reset()

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()
}
