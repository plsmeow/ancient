package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.extensions.movementForward
import meow.ancient.client.extensions.movementSideways
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object FlightModule : Module(
    name = "Flight",
    category = MOVEMENT,
    description = "Flight module with multi-server bypasses"
) {
    val modeSetting = mode("Mode", "Vanilla", listOf("Vanilla", "Vulcan", "Vulcan XZ", "FunSky", "Funsky Elytra"))
    val mode by modeSetting

    val speed = number("Speed", 1.0, 0.1, 40.0, 0.1, 1, isVisible = { mode == "Vanilla" })
    val vulcanXzSpeed = number("Speed", 1.0, 0.1, 40.0, 0.1, 1, isVisible = { mode == "Vulcan XZ" })
    val airJumpRiseSpeed = number("Rise Speed", 0.4, 0.05, 1.5, 0.05, 2, isVisible = { mode == "FunSky" })
    val airJumpSpeed = number("Speed", 1.0, 0.1, 40.0, 0.1, 1, isVisible = { mode == "FunSky" })
    val airJumpDescendSpeed = number("Descend Speed", 0.04, 0.01, 1.0, 0.01, 2, isVisible = { mode == "FunSky" })
    val funskySpeedX = number("Speed X", 2.0, 0.5, 10.0, 0.5, 1, isVisible = { mode == "Funsky Elytra" })
    val funskyDelay = number("Delay", 2.0, 1.0, 10.0, 1.0, 0, isVisible = { mode == "Funsky Elytra" })
    val funskyRepeatTicks = number("Repeat Ticks", 100.0, 1.0, 600.0, 1.0, 0, isVisible = { mode == "Funsky Elytra" })
    val antiKick = boolean("Anti-Kick", true)

    private var vulcanStep = 0
    private var vulcanResetCount = 0
    private var vulcanSwitch = false
    private var vulcanDownwards = false
    private var vulcanStartHeight = 0.0
    private var lastMotionX = 0.0
    private var lastMotionZ = 0.0

    private var vulcanXzLockedY = 0.0
    private var funskyRefreshCounter = 0
    private var funskyAfterCommand = false
    private var funskyStarted = false
    private var funskyFlyingActive = false

    override fun onEnable() {
        val player = mc.player ?: return
        vulcanStartHeight = player.y
        vulcanXzLockedY = player.y
        vulcanStep = 0
        vulcanResetCount = 0
        vulcanSwitch = false
        vulcanDownwards = false
        funskyRefreshCounter = 0
        funskyAfterCommand = false
        funskyStarted = false
        funskyFlyingActive = false
    }

    override fun onDisable() {
        val player = mc.player ?: return
        if (funskyFlyingActive) {
            player.abilities.flying = false
            player.connection.send(ServerboundPlayerAbilitiesPacket(player.abilities))
        }
        player.abilities.flyingSpeed = 0.05f
        funskyStarted = false
        funskyFlyingActive = false
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return

        when (mode) {
            "Vanilla" -> handleVanilla()
            "Vulcan" -> handleVulcan()
            "Vulcan XZ" -> handleVulcanXz()
            "FunSky" -> handleAirJump()
            "Funsky Elytra" -> handleFunskyElytra()
        }
    }

    @Commando
    fun onPacketSend(event: PacketSendEvent) {
        if (!inGame) return
        val player = mc.player ?: return
        if (mode != "FunSky") return

        val packet = event.packet
        if (packet is ServerboundMovePlayerPacket && !packet.isOnGround) {
            val x = packet.getX(player.x)
            val y = packet.getY(player.y)
            val z = packet.getZ(player.z)
            val yaw = packet.getYRot(player.yRot)
            val pitch = packet.getXRot(player.xRot)
            val collision = player.horizontalCollision

            val modifiedPacket = when {
                packet.hasPosition() && packet.hasRotation() ->
                    ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, true, collision)
                packet.hasPosition() ->
                    ServerboundMovePlayerPacket.Pos(x, y, z, true, collision)
                packet.hasRotation() ->
                    ServerboundMovePlayerPacket.Rot(yaw, pitch, true, collision)
                else ->
                    ServerboundMovePlayerPacket.StatusOnly(true, collision)
            }
            player.connection.send(modifiedPacket)
        }
    }

    private fun handleVanilla() {
        val player = mc.player ?: return
        val velY = when {
            mc.options.keyJump.isDown -> speed.value * 0.5
            mc.options.keyShift.isDown -> -speed.value * 0.5
            antiKick.value && player.tickCount % 40 == 0 -> -0.0313
            else -> 0.0
        }

        val forward = player.input.movementForward
        val strafe = player.input.movementSideways

        if (forward != 0f || strafe != 0f) {
            val angle = atan2(-strafe.toDouble(), forward.toDouble())
            val finalYaw = Math.toRadians(player.yRot.toDouble()) + angle
            val s = speed.value
            player.deltaMovement = Vec3(-sin(finalYaw) * s, velY, cos(finalYaw) * s)
        } else {
            player.deltaMovement = Vec3(0.0, velY, 0.0)
        }
    }

    private fun handleVulcan() {
        val player = mc.player ?: return
        val downwards = mc.options.keyShift.isDown
        val upwards = mc.options.keyJump.isDown

        if (player.onGround()) {
            vulcanStartHeight = player.y
            vulcanStep = 0
            vulcanDownwards = false
            vulcanResetCount = 0
            vulcanSwitch = false
            player.resetFallDistance()
            return
        }

        val currentY = player.y
        var motionX = player.deltaMovement.x
        var motionY = 0.0
        var motionZ = player.deltaMovement.z
        val deltaY = currentY - vulcanStartHeight

        vulcanStep++
        when (vulcanStep) {
            1 -> {
                if (deltaY < 0.073) {
                    if (deltaY > 0.0 || vulcanResetCount > 1) {
                        motionY = -deltaY
                    }
                    vulcanSwitch = true
                } else return
            }
            2 -> {
                player.connection.send(ServerboundMovePlayerPacket.StatusOnly(false, player.horizontalCollision))
            }
            3 -> {
                lastMotionX = motionX
                lastMotionZ = motionZ
                motionY = -deltaY + (if (vulcanSwitch) 0.015625 else if (upwards) 0.5 else 0.0625)
                if (upwards) vulcanStartHeight = currentY + motionY
            }
            4 -> {
                vulcanSwitch = !vulcanSwitch
                motionX = lastMotionX * 0.88
                motionY = if (downwards) (if (vulcanSwitch) -0.097000002 else -0.147000003) else -0.097000002
                motionZ = lastMotionZ * 0.88
                vulcanDownwards = downwards
                vulcanStep = if (downwards) vulcanStep - 1 else 1
            }
        }

        player.deltaMovement = Vec3(motionX, motionY, motionZ)
        player.resetFallDistance()
    }

    private fun handleVulcanXz() {
        val player = mc.player ?: return
        player.setPos(player.x, vulcanXzLockedY, player.z)

        val forward = player.input.movementForward
        val strafe = player.input.movementSideways

        if (forward != 0f || strafe != 0f) {
            val angle = atan2(-strafe.toDouble(), forward.toDouble())
            val finalYaw = Math.toRadians(player.yRot.toDouble()) + angle
            val s = vulcanXzSpeed.value
            player.deltaMovement = Vec3(-sin(finalYaw) * s, 0.0, cos(finalYaw) * s)
        } else {
            player.deltaMovement = Vec3(0.0, 0.0, 0.0)
        }
    }

    private fun handleAirJump() {
        val player = mc.player ?: return
        if (mc.options.keyJump.isDown) {
            val targetY = player.y + airJumpRiseSpeed.value
            player.connection.send(ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, false, player.horizontalCollision))
            player.connection.send(ServerboundMovePlayerPacket.Pos(player.x, targetY, player.z, false, player.horizontalCollision))
            player.setPos(player.x, targetY, player.z)
            player.deltaMovement = Vec3(player.deltaMovement.x, 0.0, player.deltaMovement.z)
        } else {
            val velY = when {
                mc.options.keyShift.isDown -> -airJumpSpeed.value
                !player.onGround() -> -airJumpDescendSpeed.value
                else -> 0.0
            }
            player.deltaMovement = Vec3(player.deltaMovement.x, velY, player.deltaMovement.z)
        }

        if (MoveUtil.isMoving()) {
            val dir = MoveUtil.forward(airJumpSpeed.value)
            player.deltaMovement = Vec3(dir[0], player.deltaMovement.y, dir[1])
        }
        player.resetFallDistance()
    }

    private fun handleFunskyElytra() {
        val player = mc.player ?: return
        val chestItem = player.getItemBySlot(EquipmentSlot.CHEST)
        if (!chestItem.`is`(Items.ELYTRA)) {
            setEnabled(false)
            return
        }

        if (!funskyStarted) {
            funskyStarted = true
            funskyAfterCommand = true
            funskyRefreshCounter = 0
            player.connection.sendCommand("fly")
            return
        }

        if (funskyAfterCommand) {
            funskyRefreshCounter++
            if (funskyRefreshCounter >= funskyDelay.value.toInt()) {
                player.abilities.flying = true
                player.connection.send(ServerboundPlayerAbilitiesPacket(player.abilities))
                funskyFlyingActive = true
                funskyAfterCommand = false
                funskyRefreshCounter = 0
            }
            return
        }

        funskyRefreshCounter++
        if (funskyRefreshCounter >= funskyRepeatTicks.value.toInt()) {
            player.connection.sendCommand("fly")
            funskyAfterCommand = true
            funskyRefreshCounter = 0
        }

        if (player.abilities.flying) {
            player.abilities.flyingSpeed = (funskySpeedX.value * 0.05f).toFloat()
        }
    }
}
