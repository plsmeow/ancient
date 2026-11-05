package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.box
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedAutomaticPoint
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedManualSelection
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedPointSource
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedPostAttackMode
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedRotationPolicy
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedRotationSettings
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

class AdvancedRotation(private val settings: AdvancedRotationSettings) : RotationStrategy("Advanced") {
    override val keepTicks: Int
        get() = settings.keepTicks.getInt()
    val returnSpeed: Float
        get() = settings.returnSpeed.getFloat()
    val visibleRotation: Boolean
        get() = settings.visibleRotation.value

    private var playerIdentity: Any? = null
    private var levelIdentity: Any? = null
    private var clockTick = Int.MIN_VALUE
    private var now = 0
    private var eyeTick = Int.MIN_VALUE
    private var cachedEye: Vec3? = null
    private var targetIdentity: LivingEntity? = null
    private var acquiredTick = 0
    private var reactionUntil = 0
    private var clickReady = false
    private var snapUntil = Int.MIN_VALUE
    private var postAttackUntil = Int.MIN_VALUE
    private var afterAttackRecoveryStart = Int.MIN_VALUE
    private var afterAttackRecoveryUntil = Int.MIN_VALUE
    private var holdYaw = 0f
    private var holdPitch = 0f
    private var lastCalculatedTick = Int.MIN_VALUE
    private var settledTicks = 0
    private var yawVelocity = 0f
    private var pitchVelocity = 0f
    private var previousInputYaw = 0f
    private var previousInputPitch = 0f
    private var previousInputInitialized = false
    private var inputYaw = 0f
    private var inputPitch = 0f
    private val output = Rotation(0f, 0f)

    private var movementPending = true
    private var movementStart = 0
    private var movementDuration = 1
    private var movementYaw = 0f
    private var movementPitch = 0f
    private var overshootYaw = 0f
    private var overshootPitch = 0f
    private var overshootEnd = Int.MIN_VALUE
    private var recoveryStart = Int.MIN_VALUE
    private var recoveryUntil = Int.MIN_VALUE
    private var randomState = 1
    private var noisePhase = 1f
    private var noiseTargetYaw = 0f
    private var noiseTargetPitch = 0f
    private var noiseYaw = 0f
    private var noisePitch = 0f

    private var selectedX = 0.5
    private var selectedY = 0.55
    private var selectedZ = 0.5
    private var smoothX = 0.5
    private var smoothY = 0.55
    private var smoothZ = 0.5
    private var pointInitialized = false
    private var pointUntil = Int.MIN_VALUE
    private var pointCycle = 0
    private var bestX = 0.5
    private var bestY = 0.55
    private var bestZ = 0.5
    private var bestScore = Double.POSITIVE_INFINITY
    private var selectionYaw = 0f
    private var selectionPitch = 0f
    private var resolvedPoint: Vec3? = null
    private var lastResolvedTick = Int.MIN_VALUE
    private var overrideActive = false
    private var pointVisible = true
    private var predictionInitialized = false
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var velocityZ = 0.0
    private var accelerationX = 0.0
    private var accelerationY = 0.0
    private var accelerationZ = 0.0

    override fun reset() {
        clearTarget()
        playerIdentity = null
        levelIdentity = null
        clockTick = Int.MIN_VALUE
        now = 0
        clickReady = false
    }

    fun onTargetLost() {
        clearTarget()
        clickReady = false
    }

    private fun clearTarget() {
        targetIdentity = null
        acquiredTick = 0
        reactionUntil = 0
        snapUntil = Int.MIN_VALUE
        postAttackUntil = Int.MIN_VALUE
        afterAttackRecoveryStart = Int.MIN_VALUE
        afterAttackRecoveryUntil = Int.MIN_VALUE
        holdYaw = 0f
        holdPitch = 0f
        lastCalculatedTick = Int.MIN_VALUE
        settledTicks = 0
        yawVelocity = 0f
        pitchVelocity = 0f
        previousInputYaw = 0f
        previousInputPitch = 0f
        previousInputInitialized = false
        inputYaw = 0f
        inputPitch = 0f
        output.yaw = 0f
        output.pitch = 0f
        movementPending = true
        movementStart = 0
        movementDuration = 1
        movementYaw = 0f
        movementPitch = 0f
        overshootYaw = 0f
        overshootPitch = 0f
        overshootEnd = Int.MIN_VALUE
        recoveryStart = Int.MIN_VALUE
        recoveryUntil = Int.MIN_VALUE
        randomState = 1
        noisePhase = 1f
        noiseTargetYaw = 0f
        noiseTargetPitch = 0f
        noiseYaw = 0f
        noisePitch = 0f
        selectedX = 0.5
        selectedY = 0.55
        selectedZ = 0.5
        smoothX = 0.5
        smoothY = 0.55
        smoothZ = 0.5
        pointInitialized = false
        pointUntil = Int.MIN_VALUE
        pointCycle = 0
        bestX = 0.5
        bestY = 0.55
        bestZ = 0.5
        bestScore = Double.POSITIVE_INFINITY
        selectionYaw = 0f
        selectionPitch = 0f
        eyeTick = Int.MIN_VALUE
        cachedEye = null
        resolvedPoint = null
        lastResolvedTick = Int.MIN_VALUE
        overrideActive = false
        pointVisible = true
        predictionInitialized = false
        velocityX = 0.0
        velocityY = 0.0
        velocityZ = 0.0
        accelerationX = 0.0
        accelerationY = 0.0
        accelerationZ = 0.0
    }

    private fun syncClock(): Boolean {
        val player = mc.player
        val level = mc.level
        if (player == null || level == null) {
            if (playerIdentity != null || levelIdentity != null || targetIdentity != null) reset()
            return false
        }
        val tick = player.tickCount
        if (playerIdentity !== player || levelIdentity !== level || tick < clockTick) {
            clearTarget()
            clickReady = false
            playerIdentity = player
            levelIdentity = level
        }
        now = tick
        clockTick = tick
        return true
    }

    private fun eyePosition(): Vec3 {
        val cached = cachedEye
        if (eyeTick == now && cached != null) return cached
        val eye = mc.player!!.eyePosition
        cachedEye = eye
        eyeTick = now
        return eye
    }

    private fun ensureTarget(target: LivingEntity) {
        if (targetIdentity === target) return
        val currentSnapUntil = snapUntil
        clearTarget()
        targetIdentity = target
        acquiredTick = now
        snapUntil = currentSnapUntil
        randomState = target.id * 747796405 xor now * 289133645 xor 0x6D2B79F5
        if (randomState == 0) randomState = 1
        val variation = settings.reactionJitterTicks.getInt()
        reactionUntil = now + settings.reactionTicks.getInt() + if (variation == 0) 0 else (randomUnit() * (variation + 1)).toInt()
        if (clickReady) snapUntil = now + settings.snapHoldTicks.getInt()
    }

    private fun randomUnit(): Float {
        var value = randomState
        value = value xor (value shl 13)
        value = value xor (value ushr 17)
        value = value xor (value shl 5)
        randomState = value
        return (value ushr 8) * (1f / 16777216f)
    }

    private fun snapWindow(): Boolean = clickReady || now <= snapUntil

    override fun shouldUpdateRotation(shouldSnapNow: Boolean): Boolean {
        if (!syncClock()) return false
        clickReady = shouldSnapNow
        val lead = settings.snapLeadTicks.getInt()
        if (shouldSnapNow || settings.policy.value != AdvancedRotationPolicy.CONTINUOUS && lead > 0 && mc.player!!.getAttackStrengthScale(lead.toFloat()) >= 0.95f) {
            snapUntil = now + settings.snapHoldTicks.getInt()
        }
        return settings.policy.value != AdvancedRotationPolicy.SNAP || snapWindow() || now < postAttackUntil
    }

    fun resolveAimPoint(target: LivingEntity, overrideAimPoint: Vec3? = null): Vec3 {
        if (!syncClock()) return overrideAimPoint ?: target.box.center
        ensureTarget(target)
        eyePosition()
        if (overrideAimPoint != null) {
            overrideActive = true
            predictionInitialized = false
            resolvedPoint = overrideAimPoint
            lastResolvedTick = now
            pointVisible = !settings.requireVisible.value || isVisible(overrideAimPoint)
            return overrideAimPoint
        }
        val cached = resolvedPoint
        if (!overrideActive && lastResolvedTick == now && cached != null) return cached
        overrideActive = false
        val box = target.box
        val needsVisibility = settings.preferVisible.value || settings.requireVisible.value
        val hadPoint = pointInitialized
        val heldX = selectedX
        val heldY = selectedY
        val heldZ = selectedZ
        val heldVisible = !pointInitialized || !needsVisibility || isVisible(pointVector(target, selectedX, selectedY, selectedZ))
        if (!pointInitialized || now >= pointUntil || !heldVisible && settings.switchOnOccluded.value) {
            selectPoint(target, heldVisible)
            pointUntil = now + settings.pointHoldTicks.getInt()
        }
        val margin = settings.pointMargin.value
        val x = offsetCoordinate(selectedX, settings.offsetX.value, margin)
        val y = offsetCoordinate(selectedY, settings.offsetY.value, margin)
        val z = offsetCoordinate(selectedZ, settings.offsetZ.value, margin)
        if (!pointInitialized) {
            smoothX = x
            smoothY = y
            smoothZ = z
            pointInitialized = true
        } else {
            val factor = settings.pointSmoothing.value
            smoothX += (x - smoothX) * factor
            smoothY += (y - smoothY) * factor
            smoothZ += (z - smoothZ) * factor
        }
        var worldX = box.minX + smoothX * (box.maxX - box.minX)
        var worldY = box.minY + smoothY * (box.maxY - box.minY)
        var worldZ = box.minZ + smoothZ * (box.maxZ - box.minZ)
        if (settings.predictionEnabled.value) {
            updatePrediction(target)
            val ticks = settings.predictionTicks.value
            val localMotion = mc.player!!.deltaMovement
            val relative = settings.relativePrediction.value
            val accelFactor = if (settings.predictAcceleration.value) 0.5 * ticks * ticks * settings.accelerationPrediction.value else 0.0
            var dx = ((velocityX - if (relative) localMotion.x else 0.0) * ticks + accelerationX * accelFactor) * settings.horizontalPrediction.value
            var dy = ((velocityY - if (relative) localMotion.y else 0.0) * ticks + accelerationY * accelFactor) * settings.verticalPrediction.value
            var dz = ((velocityZ - if (relative) localMotion.z else 0.0) * ticks + accelerationZ * accelFactor) * settings.horizontalPrediction.value
            val lengthSquared = dx * dx + dy * dy + dz * dz
            val limit = settings.maxPredictionDistance.value
            if (lengthSquared > limit * limit) {
                val factor = limit / sqrt(lengthSquared)
                dx *= factor
                dy *= factor
                dz *= factor
            }
            worldX += dx
            worldY += dy
            worldZ += dz
        } else {
            predictionInitialized = false
        }
        val point = Vec3(worldX, worldY, worldZ)
        resolvedPoint = point
        lastResolvedTick = now
        val reuseVisibility = hadPoint && selectedX == heldX && selectedY == heldY && selectedZ == heldZ && settings.pointSmoothing.value == 1.0 && !settings.predictionEnabled.value
        pointVisible = !settings.requireVisible.value || if (reuseVisibility) heldVisible else isVisible(point)
        return point
    }

    private fun offsetCoordinate(value: Double, offset: Double, margin: Double): Double =
        if (offset == 0.0) value.coerceIn(0.0, 1.0) else (value + offset).coerceIn(margin, 1.0 - margin)

    private fun pointVector(target: LivingEntity, x: Double, y: Double, z: Double): Vec3 {
        val box = target.box
        val margin = settings.pointMargin.value
        return Vec3(
            box.minX + offsetCoordinate(x, settings.offsetX.value, margin) * (box.maxX - box.minX),
            box.minY + offsetCoordinate(y, settings.offsetY.value, margin) * (box.maxY - box.minY),
            box.minZ + offsetCoordinate(z, settings.offsetZ.value, margin) * (box.maxZ - box.minZ)
        )
    }

    private fun isVisible(point: Vec3): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        return level.clip(ClipContext(eyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).type == HitResult.Type.MISS
    }

    private fun pointScore(target: LivingEntity, x: Double, y: Double, z: Double, checkVisibility: Boolean = true): Double {
        val box = target.box
        val margin = settings.pointMargin.value
        val nx = offsetCoordinate(x, settings.offsetX.value, margin)
        val ny = offsetCoordinate(y, settings.offsetY.value, margin)
        val nz = offsetCoordinate(z, settings.offsetZ.value, margin)
        val eye = eyePosition()
        val dx = box.minX + nx * (box.maxX - box.minX) - eye.x
        val dy = box.minY + ny * (box.maxY - box.minY) - eye.y
        val dz = box.minZ + nz * (box.maxZ - box.minZ) - eye.z
        var score = if (settings.pointSource.value == AdvancedPointSource.MANUAL && settings.manualSelection.value == AdvancedManualSelection.ANGLE) {
            val yaw = Mth.wrapDegrees((Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat() - selectionYaw)
            val pitch = -Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz))) - selectionPitch
            (yaw * yaw + pitch * pitch) / 100.0
        } else {
            dx * dx + dy * dy + dz * dz
        }
        if (settings.pointSource.value == AdvancedPointSource.AUTOMATIC && settings.automaticPoint.value != AdvancedAutomaticPoint.NEAREST) {
            val height = when (settings.automaticPoint.value) {
                AdvancedAutomaticPoint.HEAD -> 0.88
                AdvancedAutomaticPoint.CHEST -> 0.55
                AdvancedAutomaticPoint.MULTIPOINT -> settings.preferredHeight.value
                else -> 0.5
            }
            val heightError = ny - height
            score += heightError * heightError * 4.0
        }
        if (checkVisibility && (settings.preferVisible.value || settings.requireVisible.value) && !isVisible(Vec3(eye.x + dx, eye.y + dy, eye.z + dz))) score += 1_000_000.0
        return score
    }

    private fun consider(target: LivingEntity, x: Double, y: Double, z: Double) {
        val score = pointScore(target, x, y, z)
        if (score < bestScore) {
            bestScore = score
            bestX = x
            bestY = y
            bestZ = z
        }
    }

    private fun considerAutomaticPoints(target: LivingEntity) {
        val margin = settings.pointMargin.value
        val height = when (settings.automaticPoint.value) {
            AdvancedAutomaticPoint.HEAD -> 0.88
            AdvancedAutomaticPoint.CHEST -> 0.55
            AdvancedAutomaticPoint.MULTIPOINT -> settings.preferredHeight.value
            else -> 0.5
        }.coerceIn(margin, 1.0 - margin)
        var ix = 0
        while (ix < 3) {
            val x = if (ix == 0) margin else if (ix == 1) 0.5 else 1.0 - margin
            var iy = 0
            while (iy < 3) {
                val y = if (iy == 0) margin else if (iy == 1) height else 1.0 - margin
                var iz = 0
                while (iz < 3) {
                    val z = if (iz == 0) margin else if (iz == 1) 0.5 else 1.0 - margin
                    consider(target, x, y, z)
                    iz++
                }
                iy++
            }
            ix++
        }
    }

    private fun selectPoint(target: LivingEntity, heldVisible: Boolean) {
        bestScore = Double.POSITIVE_INFINITY
        if (settings.pointSource.value == AdvancedPointSource.MANUAL && settings.manualSelection.value == AdvancedManualSelection.ANGLE) {
            val current = RotationManager.getServerRotation()
            selectionYaw = current.yaw
            selectionPitch = current.pitch
        }
        val needsVisibility = settings.preferVisible.value || settings.requireVisible.value
        var heldEligible = settings.pointSource.value != AdvancedPointSource.MANUAL
        var scoredSelection = false
        if (settings.pointSource.value == AdvancedPointSource.MANUAL) {
            val points = settings.hitboxPoints.value
            val mode = settings.manualSelection.value
            if (mode == AdvancedManualSelection.CYCLE || mode == AdvancedManualSelection.RANDOM) {
                val start = if (mode == AdvancedManualSelection.CYCLE) pointCycle % points.size else (randomUnit() * points.size).toInt()
                var index = start
                if (needsVisibility) {
                    var checked = 0
                    while (checked < points.size) {
                        val candidate = points[index]
                        if (isVisible(pointVector(target, candidate.x, candidate.y, candidate.z))) break
                        index = (index + 1) % points.size
                        checked++
                    }
                    if (checked == points.size) index = start
                }
                pointCycle = (index + 1) % points.size
                val point = points[index]
                bestX = point.x
                bestY = point.y
                bestZ = point.z
            } else {
                scoredSelection = true
                var index = 0
                while (index < points.size) {
                    val point = points[index++]
                    if (point.x == selectedX && point.y == selectedY && point.z == selectedZ) heldEligible = true
                    consider(target, point.x, point.y, point.z)
                }
            }
        } else {
            val box = target.box
            val margin = settings.pointMargin.value
            when (settings.automaticPoint.value) {
                AdvancedAutomaticPoint.CENTER -> {
                    bestX = 0.5
                    bestY = 0.5
                    bestZ = 0.5
                }
                AdvancedAutomaticPoint.CHEST -> {
                    bestX = 0.5
                    bestY = 0.55.coerceIn(margin, 1.0 - margin)
                    bestZ = 0.5
                }
                AdvancedAutomaticPoint.HEAD -> {
                    bestX = 0.5
                    bestY = 0.88.coerceIn(margin, 1.0 - margin)
                    bestZ = 0.5
                }
                AdvancedAutomaticPoint.NEAREST -> {
                    val eye = eyePosition()
                    bestX = ((eye.x - box.minX) / (box.maxX - box.minX).coerceAtLeast(1e-6)).coerceIn(margin, 1.0 - margin)
                    bestY = ((eye.y - box.minY) / (box.maxY - box.minY).coerceAtLeast(1e-6)).coerceIn(margin, 1.0 - margin)
                    bestZ = ((eye.z - box.minZ) / (box.maxZ - box.minZ).coerceAtLeast(1e-6)).coerceIn(margin, 1.0 - margin)
                }
                AdvancedAutomaticPoint.MULTIPOINT -> {
                    scoredSelection = true
                    considerAutomaticPoints(target)
                }
            }
            if (!scoredSelection && needsVisibility && !isVisible(pointVector(target, bestX, bestY, bestZ))) {
                bestScore = pointScore(target, bestX, bestY, bestZ, false) + 1_000_000.0
                scoredSelection = true
                considerAutomaticPoints(target)
            }
        }
        if (scoredSelection && pointInitialized && heldEligible && (heldVisible || !settings.switchOnOccluded.value)) {
            var heldScore = pointScore(target, selectedX, selectedY, selectedZ, false)
            if (!heldVisible && (settings.preferVisible.value || settings.requireVisible.value)) heldScore += 1_000_000.0
            if (bestScore + settings.pointSwitchThreshold.value >= heldScore) return
        }
        selectedX = bestX
        selectedY = bestY
        selectedZ = bestZ
    }

    private fun updatePrediction(target: LivingEntity) {
        val motion = target.deltaMovement
        if (!predictionInitialized) {
            velocityX = motion.x
            velocityY = motion.y
            velocityZ = motion.z
            accelerationX = 0.0
            accelerationY = 0.0
            accelerationZ = 0.0
            predictionInitialized = true
            return
        }
        val factor = settings.predictionSmoothing.value
        val previousX = velocityX
        val previousY = velocityY
        val previousZ = velocityZ
        velocityX += (motion.x - velocityX) * factor
        velocityY += (motion.y - velocityY) * factor
        velocityZ += (motion.z - velocityZ) * factor
        accelerationX += (velocityX - previousX - accelerationX) * factor
        accelerationY += (velocityY - previousY - accelerationY) * factor
        accelerationZ += (velocityZ - previousZ - accelerationZ) * factor
    }

    private fun beginMovement(current: Rotation, ideal: Rotation, totalAngle: Float) {
        movementPending = false
        movementStart = now
        movementYaw = current.yaw
        movementPitch = current.pitch
        val durationScale = if (settings.scaleTrajectoryByAngle.value) (totalAngle / settings.trajectoryAngleRange.getFloat()).coerceIn(0.1f, 1f) else 1f
        movementDuration = (settings.trajectoryTicks.getFloat() * durationScale).toInt().coerceAtLeast(1)
        overshootYaw = 0f
        overshootPitch = 0f
        overshootEnd = Int.MIN_VALUE
        recoveryStart = Int.MIN_VALUE
        recoveryUntil = Int.MIN_VALUE
        if (settings.overshootEnabled.value && totalAngle >= settings.overshootThreshold.getFloat() && randomUnit() < settings.overshootChance.getFloat()) {
            overshootYaw = sign(Mth.wrapDegrees(ideal.yaw - current.yaw)) * settings.yawOvershoot.getFloat()
            overshootPitch = sign(ideal.pitch - current.pitch) * settings.pitchOvershoot.getFloat()
            if (overshootYaw != 0f || overshootPitch != 0f) {
                overshootEnd = now + movementDuration + settings.overshootTicks.getInt()
                recoveryStart = overshootEnd
                recoveryUntil = recoveryStart + settings.recoveryTicks.getInt()
            }
        }
    }

    private fun updateNoise() {
        if (!settings.noiseEnabled.value) {
            noisePhase = 1f
            noiseTargetYaw = 0f
            noiseTargetPitch = 0f
            noiseYaw = 0f
            noisePitch = 0f
            return
        }
        noisePhase += settings.noiseFrequency.getFloat() * 0.05f
        if (noisePhase >= 1f) {
            noisePhase -= 1f
            val shared = randomUnit() * 2f - 1f
            val correlation = settings.noiseCorrelation.getFloat()
            noiseTargetYaw = shared
            noiseTargetPitch = shared * correlation + (randomUnit() * 2f - 1f) * (1f - abs(correlation))
        }
        val smoothing = settings.noiseSmoothing.getFloat()
        noiseYaw += (noiseTargetYaw - noiseYaw) * smoothing
        noisePitch += (noiseTargetPitch - noisePitch) * smoothing
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
        if (!syncClock()) return current
        ensureTarget(target)
        if (lastCalculatedTick == now) return output
        val consecutive = lastCalculatedTick != Int.MIN_VALUE && now - lastCalculatedTick == 1
        val previousYawSpeed = if (consecutive && previousInputInitialized) abs(Mth.wrapDegrees(current.yaw - previousInputYaw)) * 20f else 0f
        val previousPitchSpeed = if (consecutive && previousInputInitialized) abs(current.pitch - previousInputPitch) * 20f else 0f
        if (!consecutive) {
            movementPending = true
            settledTicks = 0
            yawVelocity = 0f
            pitchVelocity = 0f
        }
        inputYaw = current.yaw
        inputPitch = current.pitch
        previousInputYaw = current.yaw
        previousInputPitch = current.pitch
        previousInputInitialized = true
        lastCalculatedTick = now
        output.yaw = current.yaw
        output.pitch = current.pitch
        if (now < reactionUntil) {
            yawVelocity = 0f
            pitchVelocity = 0f
            settledTicks = 0
            return output
        }
        if (now < postAttackUntil && settings.postAttackMode.value != AdvancedPostAttackMode.TRACK) {
            settledTicks = 0
            yawVelocity = 0f
            pitchVelocity = 0f
            if (settings.postAttackMode.value == AdvancedPostAttackMode.HOLD) {
                output.yaw = current.yaw + Mth.wrapDegrees(holdYaw - current.yaw)
                output.pitch = holdPitch
            } else {
                val player = mc.player!!
                val yaw = Mth.wrapDegrees(player.yRot - current.yaw)
                val pitch = player.xRot - current.pitch
                val distance = hypot(yaw, pitch)
                val factor = if (distance <= 1e-6f) 0f else (returnSpeed / distance).coerceAtMost(1f)
                output.yaw = current.yaw + yaw * factor
                output.pitch = (current.pitch + pitch * factor).coerceIn(-90f, 90f)
            }
            return output
        }
        if (movementPending) beginMovement(current, ideal, totalAngle)
        val progress = ((now - movementStart + 1).toFloat() / movementDuration).coerceIn(0f, 1f)
        var overshootScale = 0f
        if (settings.overshootEnabled.value && now < overshootEnd) {
            overshootScale = sin(progress * (Math.PI * 0.5)).toFloat()
        } else if (settings.overshootEnabled.value && now < recoveryUntil) {
            val recoveryProgress = ((now - recoveryStart + 1).toFloat() / settings.recoveryTicks.getFloat()).coerceIn(0f, 1f)
            overshootScale = 1f - settings.recoveryCurve.evaluate(recoveryProgress)
        }
        var targetYaw = movementYaw + Mth.wrapDegrees(ideal.yaw - movementYaw) * settings.yawTrajectoryCurve.evaluate(progress) + overshootYaw * overshootScale
        var targetPitch = movementPitch + (ideal.pitch - movementPitch) * settings.pitchTrajectoryCurve.evaluate(progress) + overshootPitch * overshootScale
        updateNoise()
        if (settings.noiseEnabled.value) {
            val envelope = settings.noiseCurve.evaluate((totalAngle / settings.responseAngleRange.getFloat()).coerceIn(0f, 1f)) * 2f
            val scale = envelope * if (snapWindow()) settings.attackNoiseScale.getFloat() else 1f
            targetYaw += noiseYaw * settings.yawNoise.getFloat() * scale
            targetPitch += noisePitch * settings.pitchNoise.getFloat() * scale
        }
        if (settings.microEnabled.value) {
            val error = (totalAngle / settings.microErrorRange.getFloat()).coerceIn(0f, 1f)
            val envelope = 4f * error * (1f - error)
            val phase = (now - acquiredTick).toDouble() * Math.PI * 2.0 / settings.microPeriod.value
            targetYaw += sin(phase).toFloat() * settings.microYaw.getFloat() * envelope
            targetPitch += cos(phase).toFloat() * settings.microPitch.getFloat() * envelope
        }
        targetPitch = targetPitch.coerceIn(-90f, 90f)
        val yawError = Mth.wrapDegrees(targetYaw - current.yaw)
        val pitchError = targetPitch - current.pitch
        val recovering = settings.overshootEnabled.value && now >= recoveryStart && now < recoveryUntil || now >= afterAttackRecoveryStart && now < afterAttackRecoveryUntil
        var speedScale = if (settings.policy.value == AdvancedRotationPolicy.HYBRID && !snapWindow()) settings.hybridTrackingScale.getFloat() else 1f
        if (mc.player!!.isFallFlying) speedScale *= settings.flyingSpeedScale.getFloat()
        if (recovering) speedScale *= settings.recoverySpeedScale.getFloat()
        val auraLimit = if (settings.respectBaseSpeed.value) speed.coerceAtLeast(0f) * 20f else Float.POSITIVE_INFINITY
        val maxYaw = minOf(settings.yawSpeed.getFloat(), auraLimit) * speedScale
        val maxPitch = minOf(settings.pitchSpeed.getFloat(), auraLimit) * speedScale
        val range = settings.responseAngleRange.getFloat()
        val desiredYaw = (sign(yawError) * settings.yawResponseCurve.evaluate((abs(yawError) / range).coerceIn(0f, 1f)) * range * settings.yawResponse.getFloat()).coerceIn(-maxYaw, maxYaw)
        val desiredPitch = (sign(pitchError) * settings.pitchResponseCurve.evaluate((abs(pitchError) / range).coerceIn(0f, 1f)) * range * settings.pitchResponse.getFloat()).coerceIn(-maxPitch, maxPitch)
        val yawAcceleration = settings.yawAcceleration.getFloat() * 0.05f
        val pitchAcceleration = settings.pitchAcceleration.getFloat() * 0.05f
        yawVelocity += (desiredYaw - yawVelocity).coerceIn(-yawAcceleration, yawAcceleration)
        pitchVelocity += (desiredPitch - pitchVelocity).coerceIn(-pitchAcceleration, pitchAcceleration)
        yawVelocity = yawVelocity.coerceIn(-maxYaw, maxYaw)
        pitchVelocity = pitchVelocity.coerceIn(-maxPitch, maxPitch)
        if (abs(yawError) < settings.yawDampingZone.getFloat()) yawVelocity *= settings.yawDamping.getFloat()
        if (abs(pitchError) < settings.pitchDampingZone.getFloat()) pitchVelocity *= settings.pitchDamping.getFloat()
        if (recovering) {
            yawVelocity *= settings.recoveryDamping.getFloat()
            pitchVelocity *= settings.recoveryDamping.getFloat()
        }
        var yawStep = yawVelocity * 0.05f
        var pitchStep = pitchVelocity * 0.05f
        if (abs(yawError) <= settings.yawDeadzone.getFloat()) {
            yawStep = 0f
            yawVelocity = 0f
        } else if (sign(yawStep) == sign(yawError) && abs(yawStep) > abs(yawError)) {
            yawStep = yawError
            yawVelocity = 0f
        }
        if (abs(pitchError) <= settings.pitchDeadzone.getFloat()) {
            pitchStep = 0f
            pitchVelocity = 0f
        } else if (sign(pitchStep) == sign(pitchError) && abs(pitchStep) > abs(pitchError)) {
            pitchStep = pitchError
            pitchVelocity = 0f
        }
        output.yaw = current.yaw + yawStep
        output.pitch = (current.pitch + pitchStep).coerceIn(-90f, 90f)
        if (output.pitch == 90f && pitchVelocity > 0f || output.pitch == -90f && pitchVelocity < 0f) pitchVelocity = 0f
        val aligned = abs(deltaYaw) <= settings.yawTolerance.getFloat() && abs(deltaPitch) <= settings.pitchTolerance.getFloat()
        val slow = previousYawSpeed <= settings.settleVelocity.getFloat() && previousPitchSpeed <= settings.settleVelocity.getFloat()
        val phaseComplete = progress >= 1f && (!settings.overshootEnabled.value || now >= recoveryUntil) && now >= afterAttackRecoveryUntil
        settledTicks = if (aligned && slow && phaseComplete && (!settings.requireVisible.value || pointVisible)) (settledTicks + 1).coerceAtMost(40) else 0
        return output
    }

    fun isAttackReady(): Boolean {
        if (!syncClock()) return false
        val target = targetIdentity ?: return false
        val point = resolvedPoint ?: return false
        val sampleTick = lastCalculatedTick
        if (!target.isAlive || lastResolvedTick != sampleTick || now - sampleTick !in 0..1 || movementPending || sampleTick < reactionUntil || sampleTick < postAttackUntil || sampleTick < afterAttackRecoveryUntil) return false
        if (settings.policy.value == AdvancedRotationPolicy.SNAP && !snapWindow()) return false
        if (sampleTick - movementStart + 1 < movementDuration) return false
        if (settings.overshootEnabled.value && sampleTick < recoveryUntil) return false
        if (settledTicks < settings.settleTicks.getInt() || settings.requireVisible.value && !pointVisible) return false
        val eye = cachedEye ?: return false
        val dx = point.x - eye.x
        val dy = point.y - eye.y
        val dz = point.z - eye.z
        val current = RotationManager.getServerRotation()
        if (abs(Mth.wrapDegrees(current.yaw - inputYaw)) * 20f > settings.settleVelocity.getFloat() || abs(current.pitch - inputPitch) * 20f > settings.settleVelocity.getFloat()) return false
        val yaw = Mth.wrapDegrees((Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat() - current.yaw)
        val pitch = (-Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz)))).toFloat() - current.pitch
        return abs(yaw) <= settings.yawTolerance.getFloat() && abs(pitch) <= settings.pitchTolerance.getFloat()
    }

    fun notifyAttack() {
        if (!syncClock() || targetIdentity == null) return
        val current = RotationManager.getServerRotation()
        holdYaw = current.yaw
        holdPitch = current.pitch
        snapUntil = maxOf(snapUntil, now + settings.snapHoldTicks.getInt())
        postAttackUntil = if (settings.postAttackMode.value == AdvancedPostAttackMode.TRACK) Int.MIN_VALUE else now + settings.postAttackTicks.getInt() + 1
        afterAttackRecoveryStart = if (settings.recoveryAfterAttack.value) maxOf(now + 1, postAttackUntil) else Int.MIN_VALUE
        afterAttackRecoveryUntil = if (settings.recoveryAfterAttack.value) afterAttackRecoveryStart + settings.recoveryTicks.getInt() else Int.MIN_VALUE
        movementPending = true
        overshootYaw = 0f
        overshootPitch = 0f
        overshootEnd = Int.MIN_VALUE
        recoveryStart = Int.MIN_VALUE
        recoveryUntil = Int.MIN_VALUE
        settledTicks = 0
    }
}
