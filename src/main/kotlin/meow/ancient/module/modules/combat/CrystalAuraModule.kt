package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.combat.ExplosionDamageUtil
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SwapUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import java.util.concurrent.ThreadLocalRandom

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
    val placeDelay by number("Place Delay", 1.0, 0.0, 10.0, 1.0, 0)
    val breakDelay by number("Break Delay", 1.0, 0.0, 10.0, 1.0, 0)

    val moveFixSetting = mode("MoveFix", "Silent", listOf("Silent", "Strict", "Free", "Off"))
    val moveFix by moveFixSetting

    val rotations by boolean("Rotations", true)
    val silentSlots by boolean("Silent Slots", true)
    val basePlace by boolean("Base Place", true, "Places obsidian as base for crystals if none available")
    val lethalOverride by boolean("Lethal Override", true)

    private var placeTimer = 0
    private var breakTimer = 0
    private var originalSlot = -1
    private fun getMoveFixMode(): MoveFixMode = MoveFixMode.fromName(moveFix)

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return
        val level = mc.level ?: return

        val target = findTarget(player)
        if (target == null) {
            restoreSlot(player)
            return
        }

        placeTimer++
        breakTimer++

        val targetHp = AnticheatManager.getHealth(target)

        if (breakTimer >= breakDelay.toInt()) {
            val broken = breakCrystals(player, target, targetHp)
            if (broken) {
                breakTimer = 0
                return
            }
        }

        if (placeTimer >= placeDelay.toInt()) {
            val placed = placeCrystal(player, target, targetHp)
            if (placed) {
                placeTimer = 0
                return
            }
            if (placeBase(player, target, targetHp)) {
                placeTimer = 0
                return
            }
        }
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
    private fun breakCrystals(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
        val level = mc.level ?: return false
        var bestCrystal: EndCrystal? = null
        var bestDamage = 0.0f
        val maxReachSq = breakRange * breakRange

        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal || !entity.isAlive) continue
            val distSq = player.distanceToSqr(entity)
            if (distSq > maxReachSq) continue

            val crystalPos = entity.position()
            val selfDamage = ExplosionDamageUtil.crystalDamage(crystalPos, player)
            if (selfDamage > maxSelfDamage.toFloat()) continue

            val targetDamage = ExplosionDamageUtil.crystalDamage(crystalPos, target)
            val isLethal = lethalOverride && targetDamage >= targetHp
            if (!isLethal && targetDamage < minDamage.toFloat()) continue

            if (targetDamage > bestDamage) {
                bestDamage = targetDamage
                bestCrystal = entity
            }
        }

        val crystal = bestCrystal ?: return false

        val rot = RotationUtil.getRotationToVec(crystal.position().add(0.0, 0.5, 0.0), player.eyePosition)
        if (rotations) {
            val rng = ThreadLocalRandom.current()
            rot.yaw += rng.nextFloat(-0.04f, 0.04f)
            rot.pitch += rng.nextFloat(-0.04f, 0.04f)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }

        val anim = player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation
        player.swing(InteractionHand.MAIN_HAND, anim, false)
        mc.gameMode?.attack(player, crystal)
        CrystalOptimizerModule.onCrystalAttacked(crystal)
        return true
    }

    private fun placeCrystal(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
        val level = mc.level ?: return false
        val pPos = player.blockPosition()
        val r = placeRange.toInt() + 1
        val maxReachSq = placeRange * placeRange

        var bestPos: BlockPos? = null
        var bestDamage = 0.0f

        val minX = pPos.x - r
        val maxX = pPos.x + r
        val minY = (pPos.y - r).coerceAtLeast(level.minY)
        val maxY = (pPos.y + r).coerceAtMost(level.maxY)
        val minZ = pPos.z - r
        val maxZ = pPos.z + r

        val crystalBox = AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0)

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    val baseState = level.getBlockState(pos)
                    if (!baseState.`is`(Blocks.OBSIDIAN) && !baseState.`is`(Blocks.BEDROCK)) continue

                    val upPos = pos.above()
                    if (!level.getBlockState(upPos).isAir) continue

                    val center = Vec3.atCenterOf(pos)
                    if (player.eyePosition.distanceToSqr(center) > maxReachSq) continue

                    val box = crystalBox.move(pos.x.toDouble(), pos.y.toDouble() + 1.0, pos.z.toDouble())
                    if (level.getEntities(null, box).any { it.isAlive }) continue

                    val explosionPos = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
                    val selfDamage = ExplosionDamageUtil.crystalDamage(explosionPos, player)
                    if (selfDamage > maxSelfDamage.toFloat()) continue

                    val targetDamage = ExplosionDamageUtil.crystalDamage(explosionPos, target)
                    val isLethal = lethalOverride && targetDamage >= targetHp
                    if (!isLethal && targetDamage < minDamage.toFloat()) continue

                    if (targetDamage > bestDamage) {
                        bestDamage = targetDamage
                        bestPos = pos
                    }
                }
            }
        }

        val placePos = bestPos ?: return false

        val hotbarCrystal = InventoryUtil.findHotbarSlot(Items.END_CRYSTAL)
        val crystalSlot = if (hotbarCrystal != -1) hotbarCrystal else InventoryUtil.findInventorySlot(Items.END_CRYSTAL)
        if (crystalSlot == -1 && !player.offhandItem.`is`(Items.END_CRYSTAL)) return false

        val hitResult = getPlaceInfo(placePos, player)

        if (rotations) {
            val rot = RotationUtil.getRotationToVec(hitResult.location, player.eyePosition)
            val rng = ThreadLocalRandom.current()
            rot.yaw += rng.nextFloat(-0.04f, 0.04f)
            rot.pitch += rng.nextFloat(-0.04f, 0.04f)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }

        val hand = if (player.offhandItem.`is`(Items.END_CRYSTAL)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

        if (hand == InteractionHand.OFF_HAND) {
            mc.gameMode?.useItemOn(player, hand, hitResult)
            player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
            return true
        }

        if (crystalSlot in 0..8) {
            switchToSlot(player, crystalSlot)
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
            return true
        }

        SwapUtil.clickWithBypass {
            InventoryUtil.swapSlots(crystalSlot, player.inventory.selectedSlot, false)
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
            InventoryUtil.swapSlots(player.inventory.selectedSlot, crystalSlot, true)
        }
        return true
    }

    private fun placeBase(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
        if (!basePlace) return false
        val level = mc.level ?: return false
        val obsidianSlot = InventoryUtil.findHotbarSlot(Items.OBSIDIAN)
        if (obsidianSlot == -1) return false

        val hasCrystal = player.offhandItem.`is`(Items.END_CRYSTAL) ||
            InventoryUtil.findHotbarSlot(Items.END_CRYSTAL) != -1 ||
            InventoryUtil.findInventorySlot(Items.END_CRYSTAL) != -1
        if (!hasCrystal) return false

        val pPos = player.blockPosition()
        val r = placeRange.toInt() + 1
        val maxReachSq = placeRange * placeRange

        val targetY = target.blockPosition().y
        val minX = pPos.x - r
        val maxX = pPos.x + r
        val minY = (pPos.y - r).coerceAtLeast(level.minY)
        val maxY = minOf(pPos.y + r, targetY - 1).coerceAtMost(level.maxY)
        if (minY > maxY) return false
        val minZ = pPos.z - r
        val maxZ = pPos.z + r

        val crystalBox = AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0)
        val requiredMinDamage = minDamage.toFloat()

        var bestPos: BlockPos? = null
        var bestDamage = 0.0f
        var bestHit: BlockHitResult? = null

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    if (pos.y >= targetY) continue
                    if (!level.getBlockState(pos).canBeReplaced()) continue

                    val upPos = pos.above()
                    if (!level.getBlockState(upPos).isAir) continue

                    val center = Vec3.atCenterOf(pos)
                    if (player.eyePosition.distanceToSqr(center) > maxReachSq) continue

                    val box = crystalBox.move(pos.x.toDouble(), pos.y.toDouble() + 1.0, pos.z.toDouble())
                    if (level.getEntities(null, box).any { it.isAlive }) continue

                    val hit = findBasePlacementHit(pos, player) ?: continue

                    val explosionPos = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
                    val selfDamage = ExplosionDamageUtil.crystalDamage(explosionPos, player, pos)
                    if (selfDamage > maxSelfDamage.toFloat()) continue

                    val targetDamage = ExplosionDamageUtil.crystalDamage(explosionPos, target, pos)
                    if (targetDamage < requiredMinDamage) continue

                    if (targetDamage > bestDamage) {
                        bestDamage = targetDamage
                        bestPos = pos
                        bestHit = hit
                    }
                }
            }
        }
        val hitResult = bestHit ?: return false

        if (rotations) {
            val rot = RotationUtil.getRotationToVec(hitResult.location, player.eyePosition)
            val rng = ThreadLocalRandom.current()
            rot.yaw += rng.nextFloat(-0.04f, 0.04f)
            rot.pitch += rng.nextFloat(-0.04f, 0.04f)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }

        switchToSlot(player, obsidianSlot)
        mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
        player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
        return true
    }

    private fun findBasePlacementHit(pos: BlockPos, player: LocalPlayer): BlockHitResult? {
        val level = mc.level ?: return null
        val eyes = player.eyePosition

        for (side in Direction.values()) {
            val neighbour = pos.relative(side.opposite)
            val state = level.getBlockState(neighbour)
            if (state.canBeReplaced() || state.isAir || state.hasBlockEntity()) continue

            val hitVec = Vec3(
                neighbour.x + 0.5 + side.stepX * 0.5,
                neighbour.y + 0.5 + side.stepY * 0.5,
                neighbour.z + 0.5 + side.stepZ * 0.5
            )

            val dot = (eyes.x - hitVec.x) * side.stepX + (eyes.y - hitVec.y) * side.stepY + (eyes.z - hitVec.z) * side.stepZ
            if (dot <= 0.0) continue

            if (eyes.distanceToSqr(hitVec) > placeRange * placeRange) continue

            val clip = level.clip(ClipContext(eyes, hitVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            if (clip.type == HitResult.Type.BLOCK && clip.blockPos == neighbour) {
                return BlockHitResult(hitVec, side, neighbour, false)
            }
        }

        val down = pos.below()
        val downState = level.getBlockState(down)
        if (!downState.canBeReplaced() && !downState.isAir && !downState.hasBlockEntity() && eyes.y > pos.y) {
            val hitVec = Vec3(down.x + 0.5, down.y + 1.0, down.z + 0.5)
            if (eyes.distanceToSqr(hitVec) <= placeRange * placeRange) {
                return BlockHitResult(hitVec, Direction.UP, down, false)
            }
        }
        return null
    }

    private fun getPlaceInfo(blockPos: BlockPos, player: LocalPlayer): BlockHitResult {
        val eyes = player.eyePosition
        val level = mc.level ?: return BlockHitResult(Vec3(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5), Direction.UP, blockPos, false)

        for (side in Direction.values()) {
            val hitVec = Vec3(
                blockPos.x + 0.5 + side.stepX * 0.5,
                blockPos.y + 0.5 + side.stepY * 0.5,
                blockPos.z + 0.5 + side.stepZ * 0.5
            )

            val dot = (eyes.x - hitVec.x) * side.stepX + (eyes.y - hitVec.y) * side.stepY + (eyes.z - hitVec.z) * side.stepZ
            if (dot <= 0.0) continue

            val clip = level.clip(ClipContext(eyes, hitVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            if (clip.type == HitResult.Type.BLOCK && clip.blockPos == blockPos) {
                return BlockHitResult(hitVec, side, blockPos, false)
            }
        }

        val side = if (blockPos.y + 0.5 > eyes.y) Direction.DOWN else Direction.UP
        val hitY = if (side == Direction.UP) blockPos.y + 1.0 else blockPos.y.toDouble()
        return BlockHitResult(Vec3(blockPos.x + 0.5, hitY, blockPos.z + 0.5), side, blockPos, false)
    }
    private fun switchToSlot(player: LocalPlayer, targetSlot: Int) {
        if (originalSlot == -1) {
            originalSlot = player.inventory.selectedSlot
        }
        if (player.inventory.selectedSlot != targetSlot) {
            InventoryUtil.swapToSlot(targetSlot)
        }
    }

    private fun restoreSlot(player: LocalPlayer) {
        val slot = originalSlot
        if (slot in 0..8) {
            originalSlot = -1
            if (player.inventory.selectedSlot != slot) {
                InventoryUtil.swapToSlot(slot)
            }
        }
    }
    override fun onDisable() {
        placeTimer = 0
        breakTimer = 0
        mc.player?.let(::restoreSlot)
    }
}
