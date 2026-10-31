package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.MovementInputEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.CameraType
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.util.Mth
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

        if (mc.gui.screen() != null) {
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
