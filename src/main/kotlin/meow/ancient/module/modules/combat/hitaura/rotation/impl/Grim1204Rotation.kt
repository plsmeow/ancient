package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ThreadLocalRandom

class Grim1204Rotation : RotationStrategy("Grim 1.20.4") {
    var rotationYaw = 0.0f
        private set
    var rotationPitch = 0.0f
        private set
    private var pitchAcceleration = 1.0f

    override fun shouldUpdateRotation(shouldSnapNow: Boolean): Boolean = false

    override fun calculate(
        current: Rotation,
        ideal: Rotation,
        deltaYaw: Float,
        deltaPitch: Float,
        totalAngle: Float,
        target: LivingEntity,
        speed: Float,
        tickCounter: Int
    ): Rotation {
        val player = mc.player ?: return current
        if (rotationYaw == 0.0f && rotationPitch == 0.0f) {
            rotationYaw = ideal.yaw
            rotationPitch = ideal.pitch
        }

        val dYaw = Mth.wrapDegrees(ideal.yaw - rotationYaw)
        val dPitch = ideal.pitch - rotationPitch

        val currentRot = Rotation(rotationYaw, rotationPitch)
        val lookingAtTarget = RayTraceUtil.isLookingAtEntity(target, currentRot, 999.0, true)
        pitchAcceleration = if (lookingAtTarget) {
            AIMED_PITCH_STEP
        } else if (pitchAcceleration < MAX_PITCH_STEP) {
            pitchAcceleration * PITCH_ACCELERATE
        } else {
            MAX_PITCH_STEP
        }

        val random = ThreadLocalRandom.current()
        val yawStep = random.nextFloat(MIN_YAW_STEP, MAX_YAW_STEP)
        val pitchStep = pitchAcceleration + random.nextFloat(-1.0f, 1.0f)

        val stepYaw = dYaw.coerceIn(-yawStep, yawStep)
        val stepPitch = dPitch.coerceIn(-pitchStep, pitchStep)

        val newYaw = rotationYaw + stepYaw
        val newPitch = (rotationPitch + stepPitch).coerceIn(-90.0f, 90.0f)

        val sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2
        val gcdFix = (sensitivity * sensitivity * sensitivity * 8.0 * 0.15).toFloat()

        if (gcdFix > 0.001f) {
            rotationYaw = newYaw - (newYaw - rotationYaw) % gcdFix
            rotationPitch = newPitch - (newPitch - rotationPitch) % gcdFix
        } else {
            rotationYaw = newYaw
            rotationPitch = newPitch
        }

        return current
    }

    fun sendRotationPacket(player: LocalPlayer) {
        player.connection.send(
            ServerboundMovePlayerPacket.PosRot(
                player.position(),
                rotationYaw,
                rotationPitch,
                player.onGround(),
                player.horizontalCollision
            )
        )
    }

    fun sendResetPacket(player: LocalPlayer) {
        player.connection.send(
            ServerboundMovePlayerPacket.PosRot(
                player.position(),
                player.yRot,
                player.xRot,
                player.onGround(),
                player.horizontalCollision
            )
        )
    }

    override fun reset() {
        mc.player?.let {
            rotationYaw = it.yRot
            rotationPitch = it.xRot
        }
        pitchAcceleration = 1.0f
    }

    companion object {
        private const val MIN_YAW_STEP = 65.0f
        private const val MAX_YAW_STEP = 75.0f
        private const val AIMED_PITCH_STEP = 1.0f
        private const val MAX_PITCH_STEP = 8.0f
        private const val PITCH_ACCELERATE = 1.65f
    }
}
