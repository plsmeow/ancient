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
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import net.minecraft.client.player.LocalPlayer
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.BlockHitResult
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
    val lethalOverride by boolean("Lethal Override", true)

    private var tickTimer = 0

    private fun getMoveFixMode(): MoveFixMode = MoveFixMode.fromName(moveFix)

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (level.dimension() == Level.NETHER) return

        tickTimer++
        if (tickTimer < delay.toInt()) return

        val target = findTarget(player) ?: return
        val targetHp = AnticheatManager.getHealth(target)

        val executed = detonateOrChargeAnchor(player, target, targetHp) || placeNewAnchor(player, target, targetHp)
        if (executed) {
            tickTimer = 0
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

    private fun detonateOrChargeAnchor(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
        val level = mc.level ?: return false
        val pPos = player.blockPosition()
        val r = range.toInt() + 1
        val maxReachSq = range * range

        var bestPos: BlockPos? = null
        var bestDamage = 0.0f
        var bestCharged = false

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

                    val center = Vec3.atCenterOf(pos)
                    if (player.eyePosition.distanceToSqr(center) > maxReachSq) continue

                    val selfDamage = ExplosionDamageUtil.anchorDamage(pos, player)
                    if (selfDamage > maxSelfDamage.toFloat()) continue

                    val targetDamage = ExplosionDamageUtil.anchorDamage(pos, target)
                    val isLethal = lethalOverride && targetDamage >= targetHp
                    if (!isLethal && targetDamage < minDamage.toFloat()) continue

                    if (targetDamage > bestDamage) {
                        bestDamage = targetDamage
                        bestPos = pos
                        bestCharged = state.getValue(RespawnAnchorBlock.CHARGE) > 0
                    }
                }
            }
        }

        val anchorPos = bestPos ?: return false

        val hitResult = BlockHitResult(
            Vec3(anchorPos.x + 0.5, anchorPos.y + 0.5, anchorPos.z + 0.5),
            Direction.UP,
            anchorPos,
            false
        )

        if (rotations) {
            val rot = RotationUtil.getRotationToVec(hitResult.location, player.eyePosition)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }

        if (bestCharged) {
            val useOffhand = !player.offhandItem.`is`(Items.GLOWSTONE)
            val hand = if (useOffhand) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

            if (hand == InteractionHand.MAIN_HAND && player.mainHandItem.`is`(Items.GLOWSTONE)) {
                val nonGlowSlot = (0..8).firstOrNull { !player.inventory.getItem(it).`is`(Items.GLOWSTONE) }
                if (nonGlowSlot != null) {
                    val prev = player.inventory.selectedSlot
                    player.inventory.selectedSlot = nonGlowSlot
                    mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
                    player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
                    if (silentSlots) player.inventory.selectedSlot = prev
                    return true
                }
            }

            mc.gameMode?.useItemOn(player, hand, hitResult)
            player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
            return true
        }

        val glowSlot = InventoryUtil.findItem(Items.GLOWSTONE)
        if (glowSlot == -1 && !player.offhandItem.`is`(Items.GLOWSTONE)) return false

        if (player.offhandItem.`is`(Items.GLOWSTONE)) {
            mc.gameMode?.useItemOn(player, InteractionHand.OFF_HAND, hitResult)
            player.swing(InteractionHand.OFF_HAND, player.getItemInHand(InteractionHand.OFF_HAND).attackAnimation, false)
            return true
        }

        if (glowSlot in 0..8) {
            val prevSlot = player.inventory.selectedSlot
            player.inventory.selectedSlot = glowSlot
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
            if (silentSlots) player.inventory.selectedSlot = prevSlot
            return true
        }

        SwapUtil.clickWithBypass {
            InventoryUtil.swapSlots(glowSlot, player.inventory.selectedSlot, false)
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
            InventoryUtil.swapSlots(player.inventory.selectedSlot, glowSlot, true)
        }
        return true
    }

    private fun placeNewAnchor(player: LocalPlayer, target: Player, targetHp: Float): Boolean {
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
                    if (!level.getBlockState(pos).canBeReplaced()) continue

                    val downPos = pos.below()
                    val downState = level.getBlockState(downPos)
                    if (downState.canBeReplaced()) continue

                    val center = Vec3.atCenterOf(pos)
                    if (player.eyePosition.distanceToSqr(center) > maxReachSq) continue

                    val selfDamage = ExplosionDamageUtil.anchorDamage(pos, player)
                    if (selfDamage > maxSelfDamage.toFloat()) continue

                    val targetDamage = ExplosionDamageUtil.anchorDamage(pos, target)
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

        val anchorSlot = InventoryUtil.findItem(Items.RESPAWN_ANCHOR)
        if (anchorSlot == -1 && !player.offhandItem.`is`(Items.RESPAWN_ANCHOR)) return false

        val hitResult = BlockHitResult(
            Vec3(placePos.x + 0.5, placePos.y + 0.0, placePos.z + 0.5),
            Direction.UP,
            placePos.below(),
            false
        )

        if (rotations) {
            val rot = RotationUtil.getRotationToVec(hitResult.location, player.eyePosition)
            RotationManager.setRotation(rot, moveFix = getMoveFixMode())
        }

        val hand = if (player.offhandItem.`is`(Items.RESPAWN_ANCHOR)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

        if (hand == InteractionHand.OFF_HAND) {
            mc.gameMode?.useItemOn(player, hand, hitResult)
            player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
            return true
        }

        if (anchorSlot in 0..8) {
            val prevSlot = player.inventory.selectedSlot
            player.inventory.selectedSlot = anchorSlot
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
            if (silentSlots) player.inventory.selectedSlot = prevSlot
            return true
        }

        SwapUtil.clickWithBypass {
            InventoryUtil.swapSlots(anchorSlot, player.inventory.selectedSlot, false)
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)
            InventoryUtil.swapSlots(player.inventory.selectedSlot, anchorSlot, true)
        }
        return true
    }

    override fun onDisable() {
        tickTimer = 0
    }
}
