/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
// Adapted for Ancient Alpha / Minecraft 26.3 from LiquidBounce commit
// 0eccefdb56d5afaca6443ec79016be2c3529dbce. See docs/scaffold-port.md.
package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.BlockPredictionUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationUtil
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.SupportType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

/** Port of LiquidBounce Normal target finding and verifyClick, using native placement contexts. */
object ScaffoldPlacement {
    data class Site(val pos: BlockPos, val hit: BlockHitResult, val rotation: Rotation, val minPlacementY: Double)

    fun validItem(level: ClientLevel, pos: BlockPos, stack: ItemStack): Boolean {
        val player = mc.player ?: return false
        if (stack.isEmpty) return false
        val block = (stack.item as? BlockItem)?.block ?: return false
        return block !is FallingBlock && block !== Blocks.TNT && block !== Blocks.COBWEB
            && block !== Blocks.NETHER_PORTAL && block.defaultBlockState().entityCanStandOnFace(level, pos, player, Direction.UP)
    }
    fun unfavorable(level: ClientLevel, pos: BlockPos, stack: ItemStack): Boolean {
        val block = (stack.item as? BlockItem)?.block ?: return true
        return block.friction > 0.6f || block.speedFactor < 1f || block.jumpFactor < 1f || block is EntityBlock
            || !block.defaultBlockState().isCollisionShapeFullBlock(level, pos)
            || block === Blocks.CRAFTING_TABLE || block === Blocks.MAGMA_BLOCK || block === Blocks.SMITHING_TABLE
            || block === Blocks.FLETCHING_TABLE || block === Blocks.ENCHANTING_TABLE || block === Blocks.CAULDRON
    }
    fun confirmedBlock(level: ClientLevel, pos: BlockPos): Boolean =
        BlockPredictionUtil.serverState(level, pos).isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER)

    fun hasSupport(player: LocalPlayer, level: ClientLevel, box: AABB): Boolean {
        val feet = AABB(box.minX + 0.001, box.minY - 0.08, box.minZ + 0.001,
            box.maxX - 0.001, box.minY - 0.001, box.maxZ - 0.001)
        for (x in floor(feet.minX).toInt()..floor(feet.maxX).toInt())
            for (z in floor(feet.minZ).toInt()..floor(feet.maxZ).toInt())
                for (y in floor(feet.minY).toInt()..floor(feet.maxY).toInt()) {
                    val pos = BlockPos(x, y, z)
                    if (!level.hasChunk(x shr 4, z shr 4) || !level.worldBorder.isWithinBounds(pos)) continue
                    val state = BlockPredictionUtil.serverState(level, pos)
                    if (state.getCollisionShape(level, pos).toAabbs().any { it.move(pos).intersects(feet) }) return true
                }
        return false
    }

    private fun resolve(player: LocalPlayer, level: ClientLevel, pos: BlockPos, hand: InteractionHand,
                        stack: ItemStack, hit: BlockHitResult): BlockPlaceContext? {
        val gameMode = mc.gameMode ?: return null
        val item = stack.item as? BlockItem ?: return null
        if (!level.worldBorder.isWithinBounds(pos) || level.isOutsideBuildHeight(pos)
            || !level.hasChunk(pos.x shr 4, pos.z shr 4) || !BlockPredictionUtil.serverState(level, pos).canBeReplaced()
            || !validItem(level, pos, stack) || player.blockActionRestricted(level, pos, gameMode.playerMode)) return null
        val context = item.updatePlacementContext(BlockPlaceContext(player, hand, stack, hit)) ?: return null
        return context.takeIf { it.clickedPos == pos && it.canPlace() }
    }
    fun canPlace(player: LocalPlayer, level: ClientLevel, pos: BlockPos, hand: InteractionHand,
                 stack: ItemStack, hit: BlockHitResult): Boolean {
        val context = resolve(player, level, pos, hand, stack, hit) ?: return false
        val state = (stack.item as BlockItem).block.getStateForPlacement(context) ?: return false
        return state.canSurvive(level, pos) && state.getCollisionShape(level, pos).toAabbs().none { shape ->
            level.getEntities(null, shape.move(pos)).any { !it.isRemoved && it.blocksBuilding }
        }
    }
    private fun attachable(level: ClientLevel, pos: BlockPos): Boolean {
        val state = BlockPredictionUtil.serverState(level, pos)
        return state == level.getBlockState(pos) && !state.getShape(level, pos).isEmpty && state.fluidState.isEmpty
            && !state.hasBlockEntity() && state.getMenuProvider(level, pos) == null
            && !state.`is`(BlockTags.DOORS) && !state.`is`(BlockTags.TRAPDOORS) && !state.`is`(BlockTags.BUTTONS)
            && !state.`is`(BlockTags.FENCE_GATES) && !state.`is`(BlockTags.BEDS)
            && !state.`is`(Blocks.LEVER) && !state.`is`(Blocks.NOTE_BLOCK)
    }

    fun findSite(player: LocalPlayer, level: ClientLevel, pos: BlockPos, hand: InteractionHand, stack: ItemStack,
                 reach: Double, predicted: Vec3, rotation: Rotation, line: ScaffoldMovementPlanner.Line?): Site? {
        val eyes = predicted.add(0.0, player.eyeHeight.toDouble(), 0.0)
        var best: Site? = null
        var bestFacing = Double.NEGATIVE_INFINITY
        for (side in Direction.entries) {
            // Both neighbor attachment and replacing the clicked position itself are resolved by vanilla.
            for (clicked in listOf(pos.relative(side.opposite), pos)) {
                if (!attachable(level, clicked)) continue
                var point: Vec3? = null
                var minY = 0.0
                var pointPriority = Double.NEGATIVE_INFINITY
                for (local in level.getBlockState(clicked).getShape(level, clicked).toAabbs()) {
                    val bounds = local.move(clicked)
                    val sampled = ScaffoldTargetGeometry.facePoint(bounds, side, eyes, rotation.toDirectionVec(), predicted, line)
                    val normal = Vec3(side.stepX.toDouble(), side.stepY.toDouble(), side.stepZ.toDouble())
                    if (eyes.subtract(sampled).dot(normal) < 0.0) continue
                    val coordinate = side.axis.choose(sampled.x - clicked.x - 0.5, sampled.y - clicked.y - 0.5,
                        sampled.z - clicked.z - 0.5)
                    val priority = coordinate * coordinate
                    if (priority > pointPriority || priority == pointPriority && sampled.y > (point?.y ?: Double.NEGATIVE_INFINITY)) {
                        point = sampled; pointPriority = priority
                        minY = if (side == Direction.UP) bounds.maxY else bounds.minY
                    }
                }
                val chosen = point ?: continue
                val hit = BlockHitResult(chosen, side, clicked, false)
                if (resolve(player, level, pos, hand, stack, hit) == null) continue
                val delta = eyes.subtract(chosen)
                val distance = delta.length()
                if (distance > reach || distance <= 1.0e-10) continue
                val facing = delta.dot(Vec3(side.stepX.toDouble(), side.stepY.toDouble(), side.stepZ.toDouble())) / distance
                if (facing > bestFacing) {
                    bestFacing = facing
                    best = Site(pos.immutable(), hit, RotationUtil.getRotationToVec(chosen, eyes), minY)
                }
            }
        }
        return best
    }

    fun aimedHit(player: LocalPlayer, level: ClientLevel, eyes: Vec3, rotation: Rotation,
                 reach: Double, site: Site): BlockHitResult? {
        if (!attachable(level, site.hit.blockPos)) return null
        val hit = level.clip(ClipContext(eyes, eyes.add(rotation.toDirectionVec().scale(reach)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        return hit.takeIf { it.type == HitResult.Type.BLOCK && it.blockPos == site.hit.blockPos
            && it.direction == site.hit.direction && it.location.y >= site.minPlacementY - 1.0e-7
            && player.eyePosition.distanceToSqr(it.location) <= reach * reach }
    }
}
