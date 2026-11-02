package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.neuro.NeuroModel
import meow.ancient.client.util.neuro.NeuroRecorder
import meow.ancient.client.util.neuro.NeuroRotationTracker
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.hypot

class NeuroRotation : RotationStrategy("Neuro") {
    private val combatTracker = NeuroRotationTracker()
    private val returnTracker = NeuroRotationTracker()
    val recorder = NeuroRecorder()
    private val deltaOut = FloatArray(2)
    private var trackedModel: NeuroModel? = null
    private var lastTick = -1
    private var lastTarget: LivingEntity? = null
    private var nextRotation = Rotation(0f, 0f)
    private var returnTicks = 0
    private var snapTicks = 0
    private var aimOffset = 0f
    private var attacked = false
    private var modelWarned = false

    override fun shouldUpdateRotation(shouldSnapNow: Boolean): Boolean {
        if (!HitAuraModule.neuroSnap) return true
        val player = mc.player ?: return false
        if (player.getAttackStrengthScale(0f) >= 0.95f) return true
        combatTracker.reset()
        lastTarget = null
        lastTick = -1
        snapTicks = 0
        return false
    }

    fun isAttackReady(): Boolean = NeuroModel.getActive() != null &&
        (!HitAuraModule.neuroSnap || snapTicks >= HitAuraModule.neuroSnapTicks.toInt())

    fun processNeuro(target: LivingEntity, moveFixMode: MoveFixMode, gcdEnabled: Boolean, overrideAimPoint: Vec3?) {
        if (!shouldUpdateRotation(true)) return
        if (NeuroModel.getActive() == null) {
            if (!modelWarned) {
                modelWarned = true
                ChatUtil.error("Модель ${NeuroModel.getActiveName()} не загрузилась. Выбери другую: .neuro list / .neuro load <имя>")
            }
            return
        }
        val current = RotationManager.getServerRotation()
        val ideal = RotationUtil.getRotationToVec(overrideAimPoint ?: target.boundingBox.center).normalizeTo(current)
        val result = calculate(current, ideal, 0f, 0f, 0f, target, 0f, 0)
        RotationManager.setRotation(result, moveFixMode, gcdEnabled, keepTicks)
    }

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
        val model = NeuroModel.getActive() ?: return current
        if (trackedModel !== model || lastTarget !== target || !combatTracker.isInitialized()) {
            combatTracker.init(model, current.yaw, current.pitch, ideal.yaw, ideal.pitch)
            trackedModel = model
            lastTarget = target
            lastTick = -1
            nextRotation = current
            snapTicks = 0
            aimOffset = sampleAimOffset(model)
        }
        if (lastTick == player.tickCount) return nextRotation
        lastTick = player.tickCount
        returnTicks = 0
        returnTracker.reset()
        modelWarned = false
        val box = target.boundingBox
        val horizontal = hypot(box.center.x - player.eyePosition.x, box.center.z - player.eyePosition.z).coerceAtLeast(0.05)
        val fovX = Math.toDegrees(Math.atan2(box.xsize * 0.5, horizontal)).toFloat().coerceAtLeast(0.5f)
        val fovY = Math.toDegrees(Math.atan2(box.ysize * 0.5, horizontal)).toFloat().coerceAtLeast(0.5f)
        combatTracker.step(
            model, current.yaw, current.pitch, ideal.yaw, ideal.pitch,
            fovX, fovY, NeuroRecorder.distanceToBox(player.eyePosition, box), aimOffset,
            12, 0.7f, 4, if (player.isUnderWater) 0.8f else 1.1f, deltaOut
        )
        nextRotation = Rotation(current.yaw + deltaOut[0], (current.pitch + deltaOut[1]).coerceIn(-90f, 90f))
        if (attacked) {
            attacked = false
            combatTracker.onAttack()
            aimOffset = sampleAimOffset(model)
        } else combatTracker.tick()
        snapTicks++
        return nextRotation
    }

    fun stepReturn(current: Rotation, target: Rotation): Rotation? {
        val model = NeuroModel.getActive() ?: return null
        if (lastTarget == null && !returnTracker.isInitialized()) return null
        lastTarget = null
        combatTracker.reset()
        snapTicks = 0
        val minimum = maxOf(3f, GCDUtil.getGCD())
        if (abs(Mth.wrapDegrees(target.yaw - current.yaw)) <= minimum && abs(target.pitch - current.pitch) <= minimum) return null
        if (++returnTicks > 30) return null
        returnTracker.step(model, current.yaw, current.pitch, target.yaw, target.pitch,
            5f, 15f, 3.0, 0f, 12, 0.7f, 4, 1.1f, deltaOut)
        returnTracker.tick()
        return Rotation(current.yaw + deltaOut[0], (current.pitch + deltaOut[1]).coerceIn(-90f, 90f))
    }

    fun notifyAttack() {
        attacked = true
        snapTicks = 0
    }

    private fun sampleAimOffset(model: NeuroModel): Float =
        (0.45f * model.sampleAimError(ThreadLocalRandom.current().nextFloat())).coerceAtMost(0.35f)

    override fun reset() {
        combatTracker.reset()
        returnTracker.reset()
        trackedModel = null
        lastTarget = null
        lastTick = -1
        returnTicks = 0
        snapTicks = 0
        attacked = false
        modelWarned = false
        aimOffset = 0f
    }
}
