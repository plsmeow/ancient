package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.combat.ExplosionDamageUtil
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SwapUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import net.minecraft.client.player.LocalPlayer
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
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
    val placeDelay by number("Place Delay", 1.0, 0.0, 10.0, 1.0, 0)
    val breakDelay by number("Break Delay", 0.0, 0.0, 10.0, 1.0, 0)

    val moveFixSetting = mode("MoveFix", "Silent", listOf("Silent", "Strict", "Free", "Off"))
    val moveFix by moveFixSetting

    val rotations by boolean("Rotations", true)
    val silentSlots by boolean("Silent Slots", true)
    val lethalOverride by boolean("Lethal Override", true)

    private var placeTimer = 0
    private var breakTimer = 0

    private fun getMoveFixMode(): MoveFixMode = MoveFixMode.fromName(moveFix)

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return
        val level = mc.level ?: return

        placeTimer++
        breakTimer++

        val target = findTarget(player) ?: return
        val targetHp = AnticheatManager.getHealth(target)

        if (breakTimer >= breakDelay.toInt()) {
            val broken = breakCrystals(player, target, targetHp)
            if (broken) {
                breakTimer = 0
            }
        }

        if (placeTimer >= placeDelay.toInt()) {
            val placed = placeCrystal(player, target, targetHp)
            if (placed) {
                placeTimer = 0
            }
        }
    }

    private fun findTarget(player: Player): Player? {
        val level = mc.level ?: return null
        var best: Player? = null
        var bestDist = targetRange * targetRange
        for (entity in level.entitiesForRendering()) {
            if (entity !is Player || entity === player || !entity.isAlive || entity.isCreative || entity.isSpectator) continue
            if (FriendRepository.isFriend(entity)) continue
            val dist = player.distanceToSqr(entity)
            if (dist <= bestDist) {
                bestDist = dist
                best = entity
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

        if (rotations) {
            val rot = RotationUtil.getRotationToVec(crystal.position().add(0.0, 0.5, 0.0), player.eyePosition)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }

        mc.gameMode?.attack(player, crystal)
        val anim = player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation
        player.swing(InteractionHand.MAIN_HAND, anim, false)
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

                    val center = Vec3.atBottomCenterOf(upPos)
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

        val crystalSlot = InventoryUtil.findItem(Items.END_CRYSTAL)
        if (crystalSlot == -1 && !player.offhandItem.`is`(Items.END_CRYSTAL)) return false

        val hitResult = BlockHitResult(
            Vec3(placePos.x + 0.5, placePos.y + 1.0, placePos.z + 0.5),
            Direction.UP,
            placePos,
            false
        )

        if (rotations) {
            val rot = RotationUtil.getRotationToVec(hitResult.location, player.eyePosition)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }

        val hand = if (player.offhandItem.`is`(Items.END_CRYSTAL)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

        if (hand == InteractionHand.OFF_HAND) {
            mc.gameMode?.useItemOn(player, hand, hitResult)
            player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
            return true
        }

        if (crystalSlot in 0..8) {
            val prevSlot = player.inventory.selectedSlot
            player.inventory.selectedSlot = crystalSlot
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
            if (silentSlots) {
                player.inventory.selectedSlot = prevSlot
            }
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
    override fun onDisable() {
        placeTimer = 0
        breakTimer = 0
    }
}
