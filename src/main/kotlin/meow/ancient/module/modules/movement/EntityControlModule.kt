package meow.ancient.module.modules.movement

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.setting.KeybindSetting
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.Vec3
import org.lwjgl.sdl.SDLMouse
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object EntityControlModule : Module(
    name = "EntityControl",
    category = MOVEMENT,
    description = "Fly ridden entities with WASD, jump to ascend and a separate descend key"
) {
    private val speed by number("Speed", 1.0, 0.1, 50.0, 0.1, unit = "b/t")
    private val verticalSpeed by number("Vertical Speed", 0.5, 0.0, 5.0, 0.05, 2, "b/t")
    private val glide by number("Glide", 0.0, 0.0, 0.5, 0.005, 3, "b/t")
    private val descendKey by keybind("Descend Key", InputConstants.KEY_LCONTROL)
    private val noClip by boolean("NoClip", false, description = "Move the ridden entity through blocks")
    private val grimNaN by boolean(
        "Grim NaN", false,
        description = "NaN steering like horsefly; requires ViaFabricPlus targeting 1.21.1 or older"
    ).addListener { resetNaN() }
    private val nanDelay by number(
        "NaN Delay", 0.0, 0.0, 20.0, 1.0, 0, "ticks",
        description = "Ordinary steering ticks between NaN inputs; zero sends NaN every tick",
        isVisible = { grimNaN }
    ).addListener { resetNaN() }

    private var controlledVehicle: Entity? = null
    private var nanTicks = 0
    @Volatile private var nanVehicleId = -1

    override fun onEnable() = reset()

    override fun onDisable() = reset()

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val vehicle = mc.player?.vehicle
        if (vehicle == null || !controls(vehicle)) {
            reset()
            return
        }
        trackVehicle(vehicle)
        // ViaVersion reads this snapshot on its network thread while translating steering.
        nanVehicleId = if (grimNaN && nanTicks == 0) vehicle.id else -1
        nanTicks = if (grimNaN && nanTicks < nanDelay.toInt()) nanTicks + 1 else 0
    }

    fun isGrimNaNActive(vehicleId: Int): Boolean = nanVehicleId == vehicleId && vehicleId != -1

    fun controls(entity: Entity): Boolean {
        if (!isEnabled()) return false
        val player = mc.player ?: return false
        if (mc.level == null || entity.level() !== mc.level || !player.isAlive || entity.isRemoved
            || !entity.isAlive || player.vehicle !== entity || player.rootVehicle !== entity) return false
        val controller = entity.controllingPassenger
        return controller === player || controller == null && entity.firstPassenger === player
    }

    fun travel(entity: Entity): Boolean {
        if (!controls(entity)) return false
        val player = mc.player ?: return false
        trackVehicle(entity)
        entity.interpolation.cancel()

        val screen = currentScreen
        val acceptsInput = mc.isWindowActive && (screen == null || InventoryMoveModule.canMoveInScreen(screen))
            && !InventoryMoveModule.isInventoryActionSlowed()
        // Use camera-relative keys, since silent aura move-fix has already rotated player.input.
        val forward = if (acceptsInput) {
            (if (mc.options.keyUp.isDown) 1.0 else 0.0) - (if (mc.options.keyDown.isDown) 1.0 else 0.0)
        } else 0.0
        val sideways = if (acceptsInput) {
            (if (mc.options.keyLeft.isDown) 1.0 else 0.0) - (if (mc.options.keyRight.isDown) 1.0 else 0.0)
        } else 0.0
        val length = hypot(forward, sideways)
        val yaw = Math.toRadians(player.yRot.toDouble())
        val horizontal = if (length > 1.0e-4) speed / length else 0.0
        val up = acceptsInput && mc.options.keyJump.isDown
        val down = acceptsInput && isDescendPressed()
        val vertical = if (up == down) -glide else if (up) verticalSpeed else -verticalSpeed
        val motion = Vec3(
            (sideways * cos(yaw) - forward * sin(yaw)) * horizontal,
            vertical,
            (forward * cos(yaw) + sideways * sin(yaw)) * horizontal
        )
        entity.yRot = player.yRot
        entity.xRot = 0f
        if (entity is LivingEntity) {
            entity.yHeadRot = player.yRot
            entity.yBodyRot = player.yRot
        }
        entity.deltaMovement = motion
        val originalNoPhysics = entity.noPhysics
        try {
            // Limit collision bypass to this movement, so dismounting and disabling need no cleanup.
            if (noClip) entity.noPhysics = true
            entity.move(MoverType.SELF, motion)
        } finally {
            entity.noPhysics = originalNoPhysics
        }
        entity.resetFallDistance()
        return true
    }

    private fun isDescendPressed(): Boolean = when {
        KeybindSetting.isMouseButton(descendKey) -> {
            val button = KeybindSetting.toMouseButton(descendKey)
            button in 1..8 && (SDLMouse.SDL_GetMouseState(null, null) and (1 shl (button - 1))) != 0
        }
        descendKey > 0 -> InputConstants.isKeyDown(descendKey)
        else -> false
    }

    private fun trackVehicle(vehicle: Entity) {
        if (controlledVehicle === vehicle) return
        reset()
        controlledVehicle = vehicle
    }

    private fun resetNaN() {
        nanVehicleId = -1
        nanTicks = 0
    }

    private fun reset() {
        resetNaN()
        controlledVehicle?.let { if (!it.isRemoved) it.deltaMovement = Vec3.ZERO }
        controlledVehicle = null
    }
}
