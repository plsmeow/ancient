package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.player.ElytraHelperModule
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ElytraFlyModule : Module(
    name = "ElytraFly",
    category = MOVEMENT,
    description = "Elytra flight control with Default and Firework modes"
) {
    val modeSetting = mode("Mode", "Default", listOf("Default", "Firework"))
    val mode by modeSetting

    // Default mode settings
    val horizontalSpeed = number("Horizontal Speed", 1.0, 0.1, 5.0, 0.1, 1, isVisible = { mode == "Default" })
    val verticalSpeed = number("Vertical Speed", 1.0, 0.1, 5.0, 0.1, 1, isVisible = { mode == "Default" })

    // Firework mode settings
    val fireworkDelay = number("Firework Delay", 750.0, 100.0, 2000.0, 50.0, 0, isVisible = { mode == "Firework" })

    private var lastFireworkTime = 0L

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return

        val chestItem = player.getItemBySlot(EquipmentSlot.CHEST)
        if (!chestItem.`is`(Items.ELYTRA)) return

        if (mode == "Default") {
            processDefaultMode()
        } else {
            processFireworkMode()
        }
    }

    private fun processDefaultMode() {
        val player = mc.player ?: return
        if (!player.isFallFlying) return

        val rotation = if (RotationManager.isActive()) RotationManager.getServerRotation() else null
        val yaw = rotation?.yaw ?: player.yRot
        val pitch = rotation?.pitch ?: player.xRot
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val forwardX = -sin(yawRad) * cos(pitchRad)
        val forwardY = -sin(pitchRad)
        val forwardZ = cos(yawRad) * cos(pitchRad)
        val rightX = -sin(yawRad + Math.PI / 2.0)
        val rightZ = cos(yawRad + Math.PI / 2.0)

        val hSpeed = horizontalSpeed.value * 10.0
        var velX = 0.0
        var velY = 0.0
        var velZ = 0.0
        var movingForward = false
        var movingStrafe = false

        if (mc.options.keyUp.isDown) {
            velX += forwardX * hSpeed
            velY += forwardY * hSpeed
            velZ += forwardZ * hSpeed
            movingForward = true
        } else if (mc.options.keyDown.isDown) {
            velX -= forwardX * hSpeed
            velY -= forwardY * hSpeed
            velZ -= forwardZ * hSpeed
            movingForward = true
        }

        if (mc.options.keyRight.isDown) {
            velX += rightX * hSpeed
            velZ += rightZ * hSpeed
            movingStrafe = true
        } else if (mc.options.keyLeft.isDown) {
            velX -= rightX * hSpeed
            velZ -= rightZ * hSpeed
            movingStrafe = true
        }

        if (movingForward && movingStrafe) {
            val diagonal = 1.0 / sqrt(2.0)
            velX *= diagonal
            velY *= diagonal
            velZ *= diagonal
        }

        if (mc.options.keyJump.isDown) velY += 0.5 * verticalSpeed.value
        if (mc.options.keyShift.isDown) velY -= 0.5 * verticalSpeed.value

        player.deltaMovement = Vec3(velX, velY, velZ)
    }


    private fun processFireworkMode() {
        val player = mc.player ?: return
        val now = System.currentTimeMillis()

        if (!player.isFallFlying) {
            if (player.onGround()) {
                player.jumpFromGround()
            } else {
                player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING))
                player.startFallFlying()
                deployFirework()
                lastFireworkTime = now
            }
            return
        }

        if (now - lastFireworkTime >= fireworkDelay.value.toLong()) {
            deployFirework()
            lastFireworkTime = now
        }

        val forward = mc.options.keyUp.isDown
        val back = mc.options.keyDown.isDown
        val left = mc.options.keyLeft.isDown
        val right = mc.options.keyRight.isDown
        val isMoving = forward || back || left || right

        if (HitAuraModule.currentTarget == null && isMoving) {
            val cameraYaw = mc.gameRenderer.mainCamera().yRot()
            var yawOffset = 0f
            if (forward) {
                if (left) yawOffset = -45f
                else if (right) yawOffset = 45f
            } else if (back) {
                yawOffset = if (left) -135f else if (right) 135f else 180f
            } else {
                if (left) yawOffset = -90f
                else if (right) yawOffset = 90f
            }

            player.yRot = cameraYaw + yawOffset
            player.xRot = when {
                mc.options.keyJump.isDown -> -35f
                mc.options.keyShift.isDown -> 35f
                else -> 0f
            }
        }
    }

    private fun deployFirework() {
        val player = mc.player ?: return
        ElytraHelperModule.launchFirework(player)
    }
}
