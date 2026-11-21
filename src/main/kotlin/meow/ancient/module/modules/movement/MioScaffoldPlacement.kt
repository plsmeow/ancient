package meow.ancient.module.modules.movement

import meow.ancient.client.util.rotation.Rotation
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.sin

object MioScaffoldPlacement {
    data class Target(val pos: BlockPos, val direction: Direction?)

    private val supportDirections = arrayOf(
        Direction.DOWN, Direction.NORTH, Direction.EAST,
        Direction.SOUTH, Direction.WEST, Direction.UP
    )
    private val searchDirections = Direction.values()
    private const val SYNC_YAW_STEP = 0.0086f

    fun targets(
        player: LocalPlayer,
        level: ClientLevel,
        slot: Int,
        multi: Boolean,
        offset: Double,
        frequency: Int,
        size: Int,
        airPlace: Boolean,
        tellyY: Double?,
        attack: Boolean,
        movementSync: Boolean,
        out: MutableList<Target>,
        strictDirection: String = "NCP"
    ) {
        out.clear()
        val vehicle = player.vehicle
        val entity = vehicle ?: player
        val box = entity.boundingBox
        val originX = (box.minX + box.maxX) * 0.5
        val originZ = (box.minZ + box.maxZ) * 0.5
        val originY = if (vehicle == null) tellyY ?: box.minY else box.minY
        val placementY = placementY(level, entity, box, originY.toFloat())
        val stack = player.inventory.getItem(if (slot == -1) player.inventory.selectedSlot else slot)
        val nonCollidable = slot != -1 && (stack.item as? BlockItem)?.block?.hasCollision == false
        if (multi) {
            val radius = size - 1
            if (frequency <= 0) return
            for (x in -radius..radius) {
                for (z in -radius..radius) {
                    var pos = BlockPos.containing(originX + x, placementY, originZ + z)
                    if (nonCollidable && !level.getBlockState(pos).canBeReplaced()) pos = pos.above()
                    if (!canPlace(player, level, pos, slot, attack)) continue
                    val direction = if (airPlace) null else supportingDirection(player, level, pos, strictDirection)
                    if (direction == null && !airPlace) continue
                    val target = Target(pos, direction)
                    if (!inRange(player, level, target)) continue
                    val distance = horizontalDistance(pos, originX, originZ)
                    var insertion = out.size
                    while (insertion > 0 && horizontalDistance(out[insertion - 1].pos, originX, originZ) > distance) {
                        insertion--
                    }
                    if (insertion < frequency) {
                        out.add(insertion, target)
                        if (out.size > frequency) out.removeAt(out.lastIndex)
                    }
                }
            }
            return
        }
        val moving = isMoving(player)
        val groundCollision = entity.verticalCollisionBelow
        val movement = movementVector(player)
        var selected: BlockPos? = null
        var step = 0
        while (step.toFloat() < offset.toFloat() + 1.0f) {
            if ((!moving || !player.onGround()) && step > 0) break
            var pos = BlockPos.containing(originX + movement.x * step, placementY, originZ + movement.z * step)
            if ((nonCollidable || (!groundCollision && tellyY == null)) && !level.getBlockState(pos).canBeReplaced()) {
                pos = pos.above()
            }
            if (canPlace(player, level, pos, slot, attack)) {
                selected = pos
                break
            }
            step++
        }
        val pos = selected ?: return
        val direction = supportingDirection(player, level, pos, strictDirection)
        if (direction != null) {
            val target = Target(pos, direction)
            if (inRange(player, level, target)) out.add(target)
            return
        }
        if (airPlace) {
            val target = Target(BlockPos.containing(originX, originY, originZ).below(), null)
            if (inRange(player, level, target)) out.add(target)
            return
        }
        for (distance in 1..2) {
            for (searchDirection in searchDirections) {
                if (searchDirection == Direction.UP) continue
                val support = supportingDirection(player, level, pos.relative(searchDirection, distance), strictDirection) ?: continue
                val target = Target(pos.relative(searchDirection), support)
                if (inRange(player, level, target)) out.add(target)
                return
            }
        }
    }

    fun blockScore(
        player: LocalPlayer,
        level: ClientLevel,
        stack: ItemStack,
        selection: String,
        blocks: Set<String>
    ): Int {
        val block = (stack.item as? BlockItem)?.block ?: return 0
        if (block == Blocks.POWDER_SNOW && !player.isShiftKeyDown) return 0
        if (block is FallingBlock) {
            val below = level.clip(
                ClipContext(
                    player.position(), Vec3(player.x, level.minY.toDouble(), player.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
                )
            )
            if (Math.round(player.y - below.location.y) > 1L) return 0
        }
        val identifier = BuiltInRegistries.BLOCK.getKey(block).toString()
        if (selection == "WhiteList" && identifier !in blocks || selection == "BlackList" && identifier in blocks) return 0
        if (block.hasCollision) return 1
        val shape = block.defaultBlockState().getCollisionShape(level, BlockPos.ZERO)
        if (shape.isEmpty) return 2
        val bounds = shape.bounds()
        return if (bounds.xsize == 1.0 && bounds.ysize == 1.0 && bounds.zsize == 1.0) 3 else 2
    }

    fun canPlace(player: LocalPlayer, level: ClientLevel, pos: BlockPos, slot: Int, attack: Boolean, playersOnly: Boolean = true): Boolean {
        if (!validPosition(level, pos) || !level.getBlockState(pos).canBeReplaced()) return false
        val range = player.blockInteractionRange()
        if (AABB(pos).distanceToSqr(player.eyePosition) > range * range) return false
        val stack = player.inventory.getItem(if (slot == -1) player.inventory.selectedSlot else slot)
        val block = (stack.item as? BlockItem)?.block
        val state = block?.defaultBlockState()
        if (block != null && !block.hasCollision) return state!!.canSurvive(level, pos)
        val shape = state?.getCollisionShape(level, BlockPos.ZERO)
        val box = if (shape == null || shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
        for (entity in level.getEntitiesOfClass(Entity::class.java, box)) {
            if (entity is ThrownExperienceBottle || entity is ItemEntity || entity is ItemFrame ||
                entity is FireworkRocketEntity || entity is ExperienceOrb || entity is Arrow ||
                attack && entity is EndCrystal) continue
            val bounds = entity.boundingBox
            if (entity != player) {
                if (!bounds.intersects(box)) continue
                val overlap = bounds.intersect(box)
                if (minOf(overlap.xsize, overlap.ysize, overlap.zsize).toFloat().toDouble() <= 1.0e-7) continue
            }
            if (entity is Player) {
                if (bounds.intersects(box)) return false
            } else if (!playersOnly) {
                return false
            }
        }
        return true
    }

    fun hit(player: LocalPlayer, level: ClientLevel, target: Target): BlockHitResult {
        val direction = target.direction
        if (direction == null) {
            val face = (Minecraft.getInstance().hitResult as? BlockHitResult)?.direction ?: Direction.DOWN
            return BlockHitResult(Vec3.atCenterOf(target.pos), face, target.pos, false)
        }
        val support = target.pos.relative(direction)
        val shape = level.getBlockState(support).getCollisionShape(level, BlockPos.ZERO)
        val y = if (direction == Direction.DOWN) {
            target.pos.y.toDouble()
        } else {
            support.y + if (shape.isEmpty) 0.5 else max(shape.bounds().maxY, (player.y - floor(player.y)).toFloat().toDouble()) - 0.05
        }
        val point = Vec3(
            player.x.coerceIn(support.x.toDouble(), support.x + 1.0),
            y,
            player.z.coerceIn(support.z.toDouble(), support.z + 1.0)
        )
        return BlockHitResult(point, direction.opposite, support, false)
    }

    fun rotation(player: LocalPlayer, level: ClientLevel, target: Target, movementSync: Boolean): Rotation {
        val direction = target.direction
        val support = if (direction == null) target.pos else target.pos.relative(direction)
        val shape = if (direction == null) null else level.getBlockState(support).getCollisionShape(level, BlockPos.ZERO)
        val point = if (direction == null) {
            Vec3.atCenterOf(target.pos)
        } else {
            Vec3(
                player.x.coerceIn(support.x.toDouble(), support.x + 1.0),
                support.y + if (shape!!.isEmpty) 0.5 else shape.bounds().maxY - 0.01,
                player.z.coerceIn(support.z.toDouble(), support.z + 1.0)
            )
        }
        val eye = player.eyePosition
        val x = point.x - eye.x
        val y = point.y - eye.y
        val z = point.z - eye.z
        var yaw = Mth.wrapDegrees(Math.toDegrees(atan2(z, x)) - 90.0).toFloat()
        val pitch = Mth.wrapDegrees(Math.toDegrees(atan2(-y, sqrt(x * x + z * z)))).toFloat()
        val moving = isMoving(player)
        if (moving && !movementSync) {
            val movement = movementVector(player)
            val backwardsYaw = (Math.toDegrees(atan2(movement.z, movement.x)) - 270.0).toFloat()
            if (abs(Mth.wrapDegrees(backwardsYaw - yaw)) < 60.0f) yaw = backwardsYaw
        }
        if (movementSync && !moving) yaw += ThreadLocalRandom.current().nextInt(8) * SYNC_YAW_STEP
        return Rotation(yaw, pitch)
    }

    private fun placementY(level: ClientLevel, entity: Entity, box: AABB, y: Float): Double {
        var highest = sampleY(level, entity, (box.minX + box.maxX) * 0.5, y.toDouble(), (box.minZ + box.maxZ) * 0.5)
        if (entity.onGround()) {
            highest = max(highest, sampleY(level, entity, box.minX, y.toDouble(), box.minZ))
            highest = max(highest, sampleY(level, entity, box.maxX, y.toDouble(), box.minZ))
            highest = max(highest, sampleY(level, entity, box.minX, y.toDouble(), box.maxZ))
            highest = max(highest, sampleY(level, entity, box.maxX, y.toDouble(), box.maxZ))
        }
        return highest
    }

    private fun sampleY(level: ClientLevel, entity: Entity, x: Double, y: Double, z: Double): Double {
        val start = Vec3(x, y, z)
        val hit = level.clip(ClipContext(start, start.add(0.0, -1.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))
        return if (hit.type == HitResult.Type.MISS) y - 1.0 else hit.location.y - 0.01f
    }

    private fun supportingDirection(player: LocalPlayer, level: ClientLevel, pos: BlockPos, strictDirection: String): Direction? {
        for (direction in supportDirections) {
            val support = pos.relative(direction)
            if (!validPosition(level, support) || level.getBlockState(support).canBeReplaced()) continue
            if (strictDirection != "Off" && !visibleFace(player, level, support, direction.opposite, strictDirection)) continue
            return direction
        }
        return null
    }

    private fun visibleFace(player: LocalPlayer, level: ClientLevel, pos: BlockPos, face: Direction, strictDirection: String): Boolean {
        if (face == Direction.UP && pos.y + 1 > level.maxY) return false
        if (strictDirection == "NCP") {
            val neighbor = level.getBlockState(pos.relative(face))
            if (!neighbor.canBeReplaced() && neighbor.isSolidRender) return false
        }
        val eye = player.eyePosition
        return when (face) {
            Direction.WEST -> eye.x - (pos.x + 0.5) < -0.5
            Direction.EAST -> eye.x - (pos.x + 0.5) > 0.5
            Direction.NORTH -> eye.z - (pos.z + 0.5) < -0.5
            Direction.SOUTH -> eye.z - (pos.z + 0.5) > 0.5
            Direction.UP -> if (strictDirection == "Grim") player.y + 1.62f * player.getAttributeValue(Attributes.SCALE) > pos.y + 1 else eye.y >= pos.y
            Direction.DOWN -> if (strictDirection == "Grim") player.y + 0.4f * player.getAttributeValue(Attributes.SCALE) < pos.y else eye.y <= pos.y
        }
    }

    private fun validPosition(level: ClientLevel, pos: BlockPos): Boolean =
        !level.isOutsideBuildHeight(pos) && level.worldBorder.isWithinBounds(pos)

    private fun inRange(player: LocalPlayer, level: ClientLevel, target: Target): Boolean {
        if (!validPosition(level, target.pos)) return false
        val range = player.blockInteractionRange()
        return player.eyePosition.distanceToSqr(hit(player, level, target).location) <= range * range
    }

    private fun horizontalDistance(pos: BlockPos, x: Double, z: Double): Float {
        val dx = x - (pos.x + 0.5)
        val dz = z - (pos.z + 0.5)
        return (dx * dx + dz * dz).toFloat()
    }

    private fun isMoving(player: LocalPlayer): Boolean {
        val input = player.input.moveVector
        return input.x != 0.0f || input.y != 0.0f
    }

    private fun movementVector(player: LocalPlayer): Vec3 {
        val input = player.input.moveVector
        var forward = input.y
        var sideways = input.x
        var yaw = player.yRot
        if (forward != 0.0f) {
            if (sideways > 0.0f) yaw += if (forward > 0.0f) -45.0f else 45.0f
            else if (sideways < 0.0f) yaw += if (forward > 0.0f) 45.0f else -45.0f
            sideways = 0.0f
            forward = if (forward > 0.0f) 1.0f else -1.0f
        }
        val radians = Math.toRadians(yaw.toDouble())
        val sine = sin(radians)
        val cosine = cos(radians)
        return Vec3(forward * -sine + sideways * cosine, 0.0, forward * cosine + sideways * sine)
    }
}
