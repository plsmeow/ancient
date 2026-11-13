package meow.ancient.module.modules.render

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.CameraType
import net.minecraft.util.Mth
import sweetie.evaware.flora.api.Commando

object FreeLookModule : Module(
    name = "FreeLook",
    category = RENDER,
    description = "Independent camera rotation while the key is held"
) {
    private val lookKey by keybind("Look Key", InputConstants.KEY_V)
    private val thirdPerson by boolean("Third Person", true)

    private var looking = false
    private var holdKey = InputConstants.UNKNOWN.value
    private var previousCameraType: CameraType? = null
    private var yaw = 0.0f
    private var pitch = 0.0f
    private var previousYaw = 0.0f
    private var previousPitch = 0.0f

    override fun onDisable() {
        stopLooking()
    }

    @Commando
    fun onKeyPress(event: KeyPressEvent) {
        if (lookKey == InputConstants.UNKNOWN.value || event.key != lookKey) return

        when (event.action) {
            InputConstants.PRESS -> {
                if (!event.cancel) startLooking()
            }
            InputConstants.RELEASE -> stopLooking()
        }

        if (event.action == InputConstants.PRESS || event.action == InputConstants.RELEASE) {
            event.cancel = true
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (holdKey != InputConstants.UNKNOWN.value && !isLookKeyDown(holdKey)) {
            stopLooking()
            return
        }
        if (!looking) {
            if (currentScreen == null && lookKey != InputConstants.UNKNOWN.value && isLookKeyDown(lookKey)) {
                startLooking()
            }
            return
        }
        if (!inGame || currentScreen != null || FreeCameraModule.isEnabled()) {
            stopLooking()
            return
        }
        updateCameraType()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        stopLooking()
    }

    fun isActive(): Boolean = isEnabled() && looking && inGame

    fun changeLookDirection(deltaX: Double, deltaY: Double) {
        previousYaw = yaw
        previousPitch = pitch
        yaw += (deltaX * 0.15).toFloat()
        pitch = Mth.clamp(pitch + (deltaY * 0.15).toFloat(), -90.0f, 90.0f)
    }

    fun getYaw(tickDelta: Float): Float = Mth.lerp(tickDelta, previousYaw, yaw)

    fun getPitch(tickDelta: Float): Float = Mth.lerp(tickDelta, previousPitch, pitch)

    private fun startLooking() {
        if (looking || !inGame || currentScreen != null || FreeCameraModule.isEnabled()) return
        val player = mc.player ?: return

        yaw = player.yRot
        pitch = player.xRot
        previousYaw = yaw
        previousPitch = pitch
        looking = true
        holdKey = lookKey
        updateCameraType()
    }

    private fun stopLooking() {
        if (!looking) return
        looking = false
        holdKey = InputConstants.UNKNOWN.value
        previousCameraType?.let {
            if (mc.options.cameraType != it) mc.options.cameraType = it
        }
        previousCameraType = null
    }

    private fun isLookKeyDown(key: Int): Boolean = when {
        key <= -100 -> isMouseButtonDown(-key - 100)
        key in -8..-2 -> isMouseButtonDown(-key)
        else -> InputConstants.isKeyDown(key)
    }

    private fun isMouseButtonDown(button: Int): Boolean = when (button) {
        InputConstants.MOUSE_BUTTON_LEFT -> mc.mouseHandler.isLeftPressed
        InputConstants.MOUSE_BUTTON_RIGHT -> mc.mouseHandler.isRightPressed
        InputConstants.MOUSE_BUTTON_MIDDLE -> mc.mouseHandler.isMiddlePressed
        else -> true
    }

    private fun updateCameraType() {
        if (!thirdPerson) return
        if (previousCameraType == null) previousCameraType = mc.options.cameraType
        if (mc.options.cameraType != CameraType.THIRD_PERSON_BACK) {
            mc.options.cameraType = CameraType.THIRD_PERSON_BACK
        }
    }
}
