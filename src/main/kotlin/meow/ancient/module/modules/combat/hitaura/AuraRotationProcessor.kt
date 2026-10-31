package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.box
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.*
import meow.ancient.module.modules.combat.HitAuraModule.AimPointMode
import meow.ancient.module.modules.combat.HitAuraModule.RotationMode
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import meow.ancient.module.modules.combat.hitaura.rotation.impl.*
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.hypot

class AuraRotationProcessor {
    private var tickCounter: Int = 0

    private val strategies: Map<RotationMode, RotationStrategy> = mapOf(
        RotationMode.VANILLA to VanillaRotation(),
        RotationMode.SNAP to SnapRotation(),
        RotationMode.SPOOKY_TIME to SpookyTimeRotation(),
        RotationMode.TEST to TestRotation(),
        RotationMode.TEST2 to Test2Rotation(),
        RotationMode.SLOTH_070926 to Sloth070926Rotation(),
        RotationMode.WELLMINE to WellmineRotation(),
        RotationMode.NO_ROT to NoRotRotation(),
        RotationMode.LONY_GRIEF to LonyGriefRotation(),
        RotationMode.VULCAN to VulcanRotation(),
        RotationMode.FUNTIME to FuntimeRotation(),
        RotationMode.UNIVERSAL to UniversalRotation(),
        RotationMode.GRIM_1204 to Grim1204Rotation(),
        RotationMode.GRIM_FUN to GrimFunRotation(),
        RotationMode.MLSAC to MlsacRotation()
    )

    fun getStrategy(mode: RotationMode): RotationStrategy {
        return strategies[mode] ?: strategies.getValue(RotationMode.VANILLA)
    }

    fun reset() {
        tickCounter = 0
        strategies.values.forEach(RotationStrategy::reset)
    }

    fun processRotation(
        target: LivingEntity,
        rotationMode: RotationMode,
        aimPointMode: AimPointMode,
        rotationSpeed: Float,
        gcdEnabled: Boolean,
        moveFixMode: MoveFixMode,
        shouldSnapNow: Boolean,
        overrideAimPoint: Vec3? = null
    ): Rotation {
        tickCounter++

        val strategy = getStrategy(rotationMode)
        val currentRot = RotationManager.getServerRotation()

        if (!strategy.shouldUpdateRotation(shouldSnapNow)) {
            return currentRot
        }

        val aimPoint = overrideAimPoint ?: calculateAimPoint(target, aimPointMode)
        val idealRotation = RotationUtil.getRotationToVec(aimPoint).normalizeTo(currentRot)

        val dYaw = Mth.wrapDegrees(idealRotation.yaw - currentRot.yaw)
        val dPitch = idealRotation.pitch - currentRot.pitch
        val totalAngle = hypot(dYaw, dPitch)

        if (totalAngle <= 0.01f) {
            RotationManager.setRotation(
                rotation = currentRot,
                moveFix = moveFixMode,
                gcd = gcdEnabled,
                keepTicks = strategy.keepTicks
            )
            return RotationManager.getServerRotation()
        }

        val steppedRotation = strategy.calculate(
            current = currentRot,
            ideal = idealRotation,
            deltaYaw = dYaw,
            deltaPitch = dPitch,
            totalAngle = totalAngle,
            target = target,
            speed = rotationSpeed,
            tickCounter = tickCounter
        )

        RotationManager.setRotation(
            rotation = steppedRotation,
            moveFix = moveFixMode,
            gcd = gcdEnabled,
            keepTicks = strategy.keepTicks,
            returnSpeed = rotationSpeed.coerceAtLeast(40.0f)
        )

        return RotationManager.getServerRotation()
    }

    fun isAimedAtTarget(target: LivingEntity, maxAngleError: Float = 28.0f, overrideAimPoint: Vec3? = null): Boolean {
        val currentRot = RotationManager.getServerRotation()
        val eyePos = mc.player?.eyePosition ?: return false
        val aimTarget = overrideAimPoint ?: RotationUtil.getNearestPoint(eyePos, target.box)
        val neededRot = RotationUtil.getRotationToVec(aimTarget)
        val yawError = abs(Mth.wrapDegrees(neededRot.yaw - currentRot.yaw))
        val pitchError = abs(neededRot.pitch - currentRot.pitch)
        return yawError <= maxAngleError && pitchError <= (maxAngleError * 1.35f)
    }

    private fun calculateAimPoint(target: LivingEntity, aimPointMode: AimPointMode): Vec3 {
        val box = target.box
        val eyePos = mc.player?.eyePosition ?: Vec3.ZERO
        return when (aimPointMode) {
            AimPointMode.NEAREST -> RotationUtil.getNearestPoint(eyePos, box, 0.10)
            AimPointMode.MULTIPOINT -> RotationUtil.getMultipoint(eyePos, box, 0.78)
            AimPointMode.HEAD -> Vec3(
                box.center.x,
                (box.minY + (box.maxY - box.minY) * 0.88).coerceAtMost(box.maxY - 0.05),
                box.center.z
            )
            AimPointMode.CHEST -> Vec3(
                box.center.x,
                box.minY + (box.maxY - box.minY) * 0.55,
                box.center.z
            )
        }
    }
}
