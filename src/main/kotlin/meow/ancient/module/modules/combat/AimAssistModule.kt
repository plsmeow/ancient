package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule.TargetMode
import meow.ancient.module.modules.combat.HitAuraModule.TargetPriority
import meow.ancient.module.modules.combat.hitaura.TargetSelector
import meow.ancient.module.modules.combat.hitaura.rotation.impl.NeuroRotation
import meow.ancient.module.setting.Curve
import meow.ancient.module.setting.TargetSetting
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationUtil
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import sweetie.evaware.flora.api.Commando
import kotlin.math.sqrt
import kotlin.random.Random

object AimAssistModule : Module(
    name = "Aim Assist",
    category = COMBAT,
    description = "Smooth camera assistance toward nearest target"
) {
    private val modeSetting = mode("Mode", "Simple", listOf("Simple", "Humanize", "Neuro"))
    private val mode by modeSetting

    private val rangeSetting = number("Range", 4.5, 3.0, 6.0, 0.1, 1)
    private val range by rangeSetting

    private val speedSetting = number("Speed", 8.0, 1.0, 20.0, 0.5, 1, isVisible = { mode == "Simple" })
    private val speed by speedSetting

    private val fovSetting = number("FOV", 90.0, 30.0, 180.0, 5.0, 0, description = "Only assist when target within FOV")
    private val fov by fovSetting

    private val onlyMouseSetting = boolean("Only On Mouse", true, description = "Activate only when mouse buttons are held")
    private val onlyMouse by onlyMouseSetting

    private val targetsSetting = targets("Targets")

    private val windStrengthSetting = number("Wind Strength", 1.5, 0.0, 5.0, 0.1, 1, isVisible = { mode == "Humanize" })
    private val windStrength by windStrengthSetting

    private val gravityStrengthSetting = number("Gravity", 9.0, 1.0, 20.0, 0.5, 1, isVisible = { mode == "Humanize" })
    private val gravityStrength by gravityStrengthSetting

    private val targetDistanceSetting = number("Target Distance", 10.0, 5.0, 30.0, 1.0, 1, isVisible = { mode == "Humanize" })
    private val targetDistance by targetDistanceSetting

    private val jitterAmplitudeSetting = number("Jitter", 0.3, 0.0, 2.0, 0.05, 2, isVisible = { mode == "Humanize" })
    private val jitterAmplitude by jitterAmplitudeSetting

    private val speedCurveSetting = curve(
        "Speed Curve", Curve.HUMAN,
        xLabel = "Closeness", yLabel = "Speed",
        isVisible = { mode == "Humanize" },
        description = "Step speed multiplier vs how close the crosshair already is"
    )

    private val jitterCurveSetting = curve(
        "Jitter Curve", Curve.EASE_OUT,
        xLabel = "Closeness", yLabel = "Jitter",
        isVisible = { mode == "Humanize" && jitterAmplitude > 0.0 },
        description = "Jitter amplitude scaling vs crosshair closeness"
    )

    private val neuroSpeedSetting = number("Neuro Speed", 0.6, 0.1, 2.0, 0.05, 2, isVisible = { mode == "Neuro" })
    private val neuroSpeed by neuroSpeedSetting

    private val targetSelector = TargetSelector()
    private val neuroRotation = NeuroRotation()

    private var windX = 0.0
    private var windY = 0.0
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var lastMouseActive = false
    private var tickCounter = 0

    @Commando
    private fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return

        if (onlyMouse) {
            val mouseActive = mc.options.keyAttack.isDown || mc.options.keyUse.isDown
            if (!mouseActive) {
                lastMouseActive = false
                return
            }
            if (!lastMouseActive) {
                resetHumanizeState()
            }
            lastMouseActive = true
        }

        val target = targetSelector.updateTarget(
            range, fov.toFloat(), TargetPriority.DISTANCE, TargetMode.SWITCH,
            targetsSetting, false, true
        ) ?: return

        val aimPoint = target.position().add(0.0, target.eyeHeight * 0.9, 0.0)
        val ideal = RotationUtil.getRotationToVec(aimPoint, player.eyePosition)
        val current = Rotation(player.yRot, player.xRot)
        val deltaYaw = Mth.wrapDegrees(ideal.yaw - current.yaw)
        val deltaPitch = ideal.pitch - current.pitch
        val totalAngle = sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch)

        if (totalAngle < 0.5f) return

        val result = when (mode) {
            "Simple" -> calculateSimple(current, deltaYaw, deltaPitch)
            "Humanize" -> calculateHumanize(current, deltaYaw, deltaPitch, target)
            "Neuro" -> calculateNeuro(current, ideal, deltaYaw, deltaPitch, totalAngle, target)
            else -> current
        }

        player.yRot = result.yaw
        player.xRot = result.pitch
        tickCounter++
    }

    private fun calculateSimple(current: Rotation, deltaYaw: Float, deltaPitch: Float): Rotation {
        val factor = (speed / 10.0).coerceIn(0.0, 1.0).toFloat()
        return Rotation(
            current.yaw + deltaYaw * factor,
            (current.pitch + deltaPitch * factor).coerceIn(-90f, 90f)
        )
    }

    private fun calculateHumanize(
        current: Rotation,
        deltaYaw: Float,
        deltaPitch: Float,
        target: LivingEntity
    ): Rotation {
        val dist = sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch)
        if (dist < 0.1f) return current

        windX += (Random.nextDouble(-1.0, 1.0) * windStrength * 0.1 - windX) * 0.1
        windY += (Random.nextDouble(-1.0, 1.0) * windStrength * 0.1 - windY) * 0.1

        val gravityX = deltaYaw / dist * gravityStrength
        val gravityY = deltaPitch / dist * gravityStrength

        velocityX += windX + gravityX
        velocityY += windY + gravityY

        val veloMag = sqrt(velocityX * velocityX + velocityY * velocityY)
        if (veloMag > targetDistance) {
            val scale = targetDistance / veloMag
            velocityX *= scale
            velocityY *= scale
        }

        val closeness = (1.0 - dist / 180.0).coerceIn(0.0, 1.0).toFloat()
        val curveMultiplier = speedCurveSetting.evaluate(closeness).toDouble()
        val stepX = velocityX * curveMultiplier * 0.12
        val stepY = velocityY * curveMultiplier * 0.12

        val jitterScale = jitterCurveSetting.evaluate(closeness).toDouble()
        val jitterX = (Random.nextDouble(-1.0, 1.0) * jitterAmplitude * jitterScale).toFloat()
        val jitterY = (Random.nextDouble(-1.0, 1.0) * jitterAmplitude * jitterScale).toFloat()

        velocityX *= 0.92
        velocityY *= 0.92

        return Rotation(
            current.yaw + stepX.toFloat() + jitterX,
            (current.pitch + stepY.toFloat() + jitterY).coerceIn(-90f, 90f)
        )
    }

    private fun calculateNeuro(
        current: Rotation,
        ideal: Rotation,
        deltaYaw: Float,
        deltaPitch: Float,
        totalAngle: Float,
        target: LivingEntity
    ): Rotation {
        return neuroRotation.calculate(
            current, ideal, deltaYaw, deltaPitch, totalAngle,
            target, neuroSpeed.toFloat(), tickCounter
        )
    }

    private fun resetHumanizeState() {
        windX = 0.0
        windY = 0.0
        velocityX = 0.0
        velocityY = 0.0
    }

    override fun onDisable() {
        super.onDisable()
        resetHumanizeState()
        tickCounter = 0
        targetSelector.reset()
    }
}
