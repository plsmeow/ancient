package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.MovementInputEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.player.LocalPlayer
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.sin

object DragonFlyModule : Module(
    name = "DragonFly",
    category = MOVEMENT,
    description = "Speeds up creative and server-authorized flight with instant motion"
) {
    val speedX by number("Speed X", 2.0, 0.5, 10.0, 0.5)
    val speedY by number("Speed Y", 2.0, 0.5, 40.0, 0.5)
    val instantMotion by boolean("Instant Motion", true)

    private var flightPlayer: LocalPlayer? = null
    private var originalFlyingSpeed = 0.0f
    private var appliedFlyingSpeed = 0.0f
    private var changedFlyingSpeed = false

    override fun onDisable() {
        restoreFlyingSpeed()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        restoreFlyingSpeed()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player
        if (player == null || mc.level == null || !player.abilities.flying) {
            restoreFlyingSpeed()
            return
        }
        if (flightPlayer !== player) {
            restoreFlyingSpeed()
            flightPlayer = player
            originalFlyingSpeed = player.abilities.flyingSpeed
        }

        val abilities = player.abilities
        val currentSpeed = abilities.flyingSpeed
        if (!changedFlyingSpeed || currentSpeed != appliedFlyingSpeed) {
            originalFlyingSpeed = currentSpeed
        }
        val desiredSpeed = (speedX * 0.05).toFloat()
        if (currentSpeed != desiredSpeed) abilities.flyingSpeed = desiredSpeed
        appliedFlyingSpeed = desiredSpeed
        changedFlyingSpeed = desiredSpeed != originalFlyingSpeed
    }

    @Commando
    fun onInput(event: MovementInputEvent) {
        val player = mc.player ?: return
        if (mc.level == null || event.input !== player.input || !player.abilities.flying
            || !instantMotion || AirStuckModule.isEnabled()) return
        if (InventoryMoveModule.isInventoryActionSlowed()) {
            InventoryMoveModule.applySlownessInput(event.input)
            return
        }

        val options = mc.options
        val forward = (if (options.keyUp.isDown) 1.0 else 0.0) - (if (options.keyDown.isDown) 1.0 else 0.0)
        val sideways = (if (options.keyLeft.isDown) 1.0 else 0.0) - (if (options.keyRight.isDown) 1.0 else 0.0)
        val velocity = player.deltaMovement
        val vertical = when {
            options.keyJump.isDown -> speedY * 0.2
            options.keyShift.isDown -> -speedY * 0.2
            else -> velocity.y
        }
        if (forward == 0.0 && sideways == 0.0) {
            player.setDeltaMovement(0.0, vertical, 0.0)
            return
        }

        val yaw = Math.toRadians(player.yRot.toDouble())
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)
        val horizontalSpeed = if (forward != 0.0 && sideways != 0.0) speedX * 0.7071067811865476 else speedX
        player.setDeltaMovement(
            (-sinYaw * forward + cosYaw * sideways) * horizontalSpeed,
            vertical,
            (cosYaw * forward + sinYaw * sideways) * horizontalSpeed
        )
    }

    private fun restoreFlyingSpeed() {
        val player = flightPlayer
        if (player != null && changedFlyingSpeed && player.abilities.flyingSpeed == appliedFlyingSpeed) {
            player.abilities.flyingSpeed = originalFlyingSpeed
        }
        flightPlayer = null
        originalFlyingSpeed = 0.0f
        appliedFlyingSpeed = 0.0f
        changedFlyingSpeed = false
    }
}
