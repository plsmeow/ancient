package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.MovementInputEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.CameraType
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object FreeCameraModule : Module(
    name = "FreeCamera",
    category = RENDER,
    description = "Detached free camera view"
) {
    val speed = number("Speed", 1.0, 0.1, 5.0, 0.1, 1)
    val reloadChunks = boolean("Reload Chunks", true)

    var pos: Vec3 = Vec3.ZERO
    var prevPos: Vec3 = Vec3.ZERO
    var yaw: Float = 0f
    var pitch: Float = 0f
    var prevYaw: Float = 0f
    var prevPitch: Float = 0f

    private var previousCameraType: CameraType = CameraType.FIRST_PERSON
    private var pickingEntity: RemotePlayer? = null

    override fun onEnable() {
        if (!inGame) return
        val player = mc.player ?: return
        val cam = mc.gameRenderer.mainCamera()

        pos = cam.position()
        prevPos = pos

        yaw = player.yRot
        pitch = player.xRot
        prevYaw = yaw
        prevPitch = pitch

        previousCameraType = mc.options.getCameraType()
        if (!previousCameraType.isFirstPerson) {
            mc.options.setCameraType(CameraType.FIRST_PERSON)
        }

        unpress()
        if (reloadChunks.value) {
            mc.levelExtractor?.allChanged()
        }
    }

    override fun onDisable() {
        pickingEntity = null
        unpress()
        if (mc.options.getCameraType() != previousCameraType) {
            mc.options.setCameraType(previousCameraType)
        }
        if (reloadChunks.value) {
            mc.levelExtractor?.allChanged()
        }
    }

    private fun unpress() {
        val o = mc.options
        o.keyUp.isDown = false
        o.keyDown.isDown = false
        o.keyLeft.isDown = false
        o.keyRight.isDown = false
        o.keyJump.isDown = false
        o.keyShift.isDown = false
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        unpress()

        if (!AnticheatManager.canUseContext()) {
            prevPos = pos
            prevYaw = yaw
            prevPitch = pitch
            return
        }

        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON)
        }

        val forward = MoveUtil.isKeyPressed(mc.options.keyUp)
        val backward = MoveUtil.isKeyPressed(mc.options.keyDown)
        val left = MoveUtil.isKeyPressed(mc.options.keyLeft)
        val right = MoveUtil.isKeyPressed(mc.options.keyRight)
        val up = MoveUtil.isKeyPressed(mc.options.keyJump)
        val down = MoveUtil.isKeyPressed(mc.options.keyShift)

        val yawRad = Math.toRadians(yaw.toDouble())
        val fwdX = -sin(yawRad)
        val fwdZ = cos(yawRad)
        val leftX = cos(yawRad)
        val leftZ = sin(yawRad)

        var velX = 0.0
        var velY = 0.0
        var velZ = 0.0

        val speedVal = speed.value
        val s = if (MoveUtil.isKeyPressed(mc.options.keySprint)) 1.0 else 0.5

        var movingXZ = false
        var movingSide = false

        if (forward) {
            velX += fwdX * s * speedVal
            velZ += fwdZ * s * speedVal
            movingXZ = true
        }
        if (backward) {
            velX -= fwdX * s * speedVal
            velZ -= fwdZ * s * speedVal
            movingXZ = true
        }
        if (left) {
            velX += leftX * s * speedVal
            velZ += leftZ * s * speedVal
            movingSide = true
        }
        if (right) {
            velX -= leftX * s * speedVal
            velZ -= leftZ * s * speedVal
            movingSide = true
        }

        if (movingXZ && movingSide) {
            val diagonal = 1.0 / sqrt(2.0)
            velX *= diagonal
            velZ *= diagonal
        }

        if (up) velY += s * speedVal
        if (down) velY -= s * speedVal

        prevPos = pos
        pos = pos.add(velX, velY, velZ)
        prevYaw = yaw
        prevPitch = pitch
    }

    @Commando
    fun onInput(event: MovementInputEvent) {
        if (!inGame) return
        event.input.keyPresses = Input.EMPTY
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        setEnabled(false)
    }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        if (!inGame) return
        val packet = event.packet
        if (packet is ClientboundRespawnPacket || packet is ClientboundPlayerCombatKillPacket) {
            setEnabled(false)
        }
    }

    fun pick(tickDelta: Float): HitResult? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val eyes = Vec3(getX(tickDelta), getY(tickDelta), getZ(tickDelta))
        val lookYaw = getYaw(tickDelta)
        val lookPitch = getPitch(tickDelta)
        val direction = net.minecraft.world.entity.Entity.calculateViewVector(lookPitch, lookYaw)
        val blockRange = player.blockInteractionRange()
        val attackRange = player.activeItem.get(DataComponents.ATTACK_RANGE)
        if (attackRange != null) {
            val source = interactionEntity(eyes, lookYaw, lookPitch) ?: return null
            val hit = attackRange.getClosesetHit(source, 1f) { it !== player && EntitySelector.CAN_BE_PICKED.test(it) }
            if (hit.type != HitResult.Type.MISS) {
                if (hit !is BlockHitResult || eyes.distanceToSqr(hit.location) < blockRange * blockRange) return hit
            }
        }
        val entityRange = player.entityInteractionRange()
        val reach = maxOf(blockRange, entityRange)
        val end = eyes.add(direction.scale(reach))
        val block = level.clip(ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        val blockDistance = eyes.distanceToSqr(block.location)
        val entityEnd = if (block.type == HitResult.Type.BLOCK) block.location else end
        val bounds = AABB(eyes, entityEnd).inflate(1.0)
        val entity = ProjectileUtil.getEntityHitResult(player, eyes, entityEnd, bounds, EntitySelector.CAN_BE_PICKED,
            if (block.type == HitResult.Type.BLOCK) blockDistance else reach * reach)
        if (entity != null && eyes.distanceToSqr(entity.location) < blockDistance) {
            return if (eyes.distanceToSqr(entity.location) < entityRange * entityRange) entity
                else BlockHitResult.miss(entity.location, Direction.getApproximateNearest(direction), BlockPos.containing(entity.location))
        }
        return if (block.type == HitResult.Type.MISS || blockDistance < blockRange * blockRange) block
            else BlockHitResult.miss(block.location, Direction.getApproximateNearest(direction), block.blockPos)
    }

    @JvmOverloads
    fun interactionEntity(eyes: Vec3 = mc.gameRenderer.mainCamera().position(), lookYaw: Float = yaw, lookPitch: Float = pitch): RemotePlayer? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val source = pickingEntity?.takeIf { it.level() === level } ?: RemotePlayer(level, player.gameProfile).also { pickingEntity = it }
        source.setPos(eyes.x, eyes.y - source.eyeHeight, eyes.z)
        source.yRot = lookYaw
        source.xRot = lookPitch
        source.yHeadRot = lookYaw
        source.abilities.instabuild = player.abilities.instabuild
        source.setOldPosAndRot()
        return source
    }

    fun changeLookDirection(deltaX: Double, deltaY: Double) {
        prevYaw = yaw
        prevPitch = pitch
        yaw += deltaX.toFloat()
        pitch = Mth.clamp(pitch + deltaY.toFloat(), -90.0f, 90.0f)
    }

    fun getX(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), prevPos.x, pos.x)
    fun getY(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), prevPos.y, pos.y)
    fun getZ(tickDelta: Float): Double = Mth.lerp(tickDelta.toDouble(), prevPos.z, pos.z)
    fun getYaw(tickDelta: Float): Float = Mth.lerp(tickDelta, prevYaw, yaw)
    fun getPitch(tickDelta: Float): Float = Mth.lerp(tickDelta, prevPitch, pitch)
}
