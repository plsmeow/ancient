package meow.ancient.module.modules.combat.hitaura.rotation

import meow.ancient.module.Module
import meow.ancient.module.setting.BooleanSetting
import meow.ancient.module.setting.Curve
import meow.ancient.module.setting.CurveSetting
import meow.ancient.module.setting.EnumSetting
import meow.ancient.module.setting.HitboxPoint
import meow.ancient.module.setting.HitboxPointsSetting
import meow.ancient.module.setting.NumberSetting
import meow.ancient.module.setting.Setting

data class AdvancedSettingsTab(val title: String, val settings: List<Setting<*>>)

enum class AdvancedRotationPolicy(val title: String) {
    CONTINUOUS("Continuous"), SNAP("Snap"), HYBRID("Hybrid")
}

enum class AdvancedPostAttackMode(val title: String) {
    TRACK("Track"), HOLD("Hold"), RETURN("Return")
}

enum class AdvancedPointSource(val title: String) {
    AUTOMATIC("Automatic"), MANUAL("Manual")
}

enum class AdvancedAutomaticPoint(val title: String) {
    CENTER("Center"), CHEST("Chest"), HEAD("Head"), NEAREST("Nearest"), MULTIPOINT("Multipoint")
}

enum class AdvancedManualSelection(val title: String) {
    NEAREST("Nearest"), ANGLE("Smallest angle"), CYCLE("Cycle"), RANDOM("Seeded random")
}

class AdvancedRotationSettings(private val module: Module) {
    private val groups = Array(8) { ArrayList<Setting<*>>() }

    private fun <S : Setting<*>> add(tab: Int, setting: S): S {
        groups[tab].add(setting)
        return module.register(setting)
    }

    private fun flag(tab: Int, name: String, value: Boolean, description: String, visible: () -> Boolean = { true }): BooleanSetting =
        add(tab, BooleanSetting("Advanced $name", value, description, visible))

    private fun number(tab: Int, name: String, value: Double, min: Double, max: Double, step: Double, unit: String, description: String, visible: () -> Boolean = { true }): NumberSetting =
        add(tab, NumberSetting("Advanced $name", value, min, max, step, if (step >= 1.0) 0 else if (step >= 0.1) 1 else 2, unit, description, visible))

    private inline fun <reified E : Enum<E>> choice(tab: Int, name: String, value: E, description: String, noinline title: (E) -> String, noinline visible: () -> Boolean = { true }): EnumSetting<E> =
        add(tab, EnumSetting("Advanced $name", value, enumValues<E>(), description, title, visible))

    private fun curve(tab: Int, name: String, value: Curve, x: String, y: String, description: String, visible: () -> Boolean = { true }): CurveSetting =
        add(tab, CurveSetting("Advanced $name", value, x, y, 12, description, visible))

    val policy = choice(0, "Policy", AdvancedRotationPolicy.CONTINUOUS, "When to track the target.", { it.title })
    val snapLeadTicks = number(0, "Snap lead", 2.0, 0.0, 20.0, 1.0, " ticks", "Begin snap tracking this many ticks before attack cooldown readiness.", { policy.value != AdvancedRotationPolicy.CONTINUOUS })
    val snapHoldTicks = number(0, "Snap keep", 2.0, 0.0, 20.0, 1.0, " ticks", "Continue snap tracking after click readiness ends.", { policy.value != AdvancedRotationPolicy.CONTINUOUS })
    val hybridTrackingScale = number(0, "Hybrid idle speed", 0.35, 0.01, 1.0, 0.01, "x", "Tracking speed outside the snap window.", { policy.value == AdvancedRotationPolicy.HYBRID })
    val postAttackMode = choice(0, "After attack", AdvancedPostAttackMode.TRACK, "Track, hold server angles, or temporarily return to camera angles.", { it.title })
    val postAttackTicks = number(0, "After attack duration", 2.0, 0.0, 40.0, 1.0, " ticks", "Duration of post-attack hold or return.", { postAttackMode.value != AdvancedPostAttackMode.TRACK })
    val reactionTicks = number(0, "Reaction delay", 1.0, 0.0, 20.0, 1.0, " ticks", "Wait after acquiring a target before rotating or attacking.")
    val reactionJitterTicks = number(0, "Reaction variation", 0.0, 0.0, 10.0, 1.0, " ticks", "Seeded additional target-acquisition delay.")
    val settleTicks = number(0, "Settle duration", 1.0, 0.0, 20.0, 1.0, " ticks", "Consecutive aligned, slow rotation samples required before attacking.")
    val yawTolerance = number(0, "Yaw attack tolerance", 2.0, 0.05, 15.0, 0.05, "°", "Maximum actual server yaw error for attacks.")
    val pitchTolerance = number(0, "Pitch attack tolerance", 2.0, 0.05, 15.0, 0.05, "°", "Maximum actual server pitch error for attacks.")
    val settleVelocity = number(0, "Settle velocity", 45.0, 0.0, 720.0, 1.0, "°/s", "Maximum yaw or pitch velocity while accumulating settle ticks.")

    val yawSpeed = number(1, "Yaw speed", 360.0, 10.0, 3600.0, 5.0, "°/s", "Maximum horizontal rotation speed.")
    val pitchSpeed = number(1, "Pitch speed", 240.0, 10.0, 1800.0, 5.0, "°/s", "Maximum vertical rotation speed.")
    val yawAcceleration = number(1, "Yaw acceleration", 1800.0, 20.0, 18000.0, 20.0, "°/s²", "Horizontal velocity change limit.")
    val pitchAcceleration = number(1, "Pitch acceleration", 1200.0, 20.0, 12000.0, 20.0, "°/s²", "Vertical velocity change limit.")
    val yawResponse = number(1, "Yaw response", 12.0, 0.5, 60.0, 0.5, "/s", "Horizontal error-to-velocity gain.")
    val pitchResponse = number(1, "Pitch response", 10.0, 0.5, 60.0, 0.5, "/s", "Vertical error-to-velocity gain.")
    val yawDamping = number(1, "Yaw damping", 0.75, 0.05, 1.0, 0.01, "x", "Horizontal velocity retained inside the damping zone.")
    val pitchDamping = number(1, "Pitch damping", 0.75, 0.05, 1.0, 0.01, "x", "Vertical velocity retained inside the damping zone.")
    val yawDampingZone = number(1, "Yaw damping zone", 4.0, 0.0, 30.0, 0.1, "°", "Horizontal error below which damping applies.")
    val pitchDampingZone = number(1, "Pitch damping zone", 3.0, 0.0, 30.0, 0.1, "°", "Vertical error below which damping applies.")
    val yawDeadzone = number(1, "Yaw deadzone", 0.03, 0.0, 3.0, 0.01, "°", "Stop horizontal corrections inside this error.")
    val pitchDeadzone = number(1, "Pitch deadzone", 0.03, 0.0, 3.0, 0.01, "°", "Stop vertical corrections inside this error.")
    val respectBaseSpeed = flag(1, "Use aura speed limit", true, "Also cap axis speed by the aura's degrees-per-tick speed.")
    val flyingSpeedScale = number(1, "Elytra speed scale", 1.0, 0.1, 2.0, 0.05, "x", "Scale axis speed while the local player is fall-flying.")

    val yawResponseCurve = curve(2, "Yaw response curve", Curve.LINEAR, "Angular error", "Speed demand", "Shape horizontal velocity demand across the configured angular range.")
    val pitchResponseCurve = curve(2, "Pitch response curve", Curve.LINEAR, "Angular error", "Speed demand", "Shape vertical velocity demand across the configured angular range.")
    val responseAngleRange = number(2, "Response angle range", 90.0, 1.0, 180.0, 1.0, "°", "Angular error corresponding to the right edge of response curves.")
    val trajectoryTicks = number(2, "Trajectory duration", 3.0, 1.0, 40.0, 1.0, " ticks", "Duration of target-acquisition and post-attack trajectories.")
    val yawTrajectoryCurve = curve(2, "Yaw trajectory", Curve.EASE_OUT, "Movement progress", "Yaw progress", "Horizontal approach path from the acquisition angle to the moving target.")
    val pitchTrajectoryCurve = curve(2, "Pitch trajectory", Curve.EASE_OUT, "Movement progress", "Pitch progress", "Vertical approach path from the acquisition angle to the moving target.")
    val scaleTrajectoryByAngle = flag(2, "Scale trajectory duration", false, "Scale trajectory duration with the initial angular distance.")
    val trajectoryAngleRange = number(2, "Trajectory angle range", 90.0, 1.0, 180.0, 1.0, "°", "Angular distance that uses the full trajectory duration.", { scaleTrajectoryByAngle.value })
    val noiseCurve = curve(2, "Noise envelope", Curve.CONSTANT, "Angular error", "Noise amplitude", "Twice the curve value scales angular noise over the response range.", { noiseEnabled.value })
    val recoveryCurve = curve(2, "Recovery curve", Curve.EASE_OUT, "Recovery progress", "Recovery progress", "Remove overshoot offsets along this curve.", { overshootEnabled.value })

    val noiseEnabled = flag(3, "Correlated noise", false, "Enable smooth seeded angular noise.")
    val yawNoise = number(3, "Yaw noise amplitude", 0.15, 0.0, 3.0, 0.01, "°", "Maximum horizontal noise amplitude.", { noiseEnabled.value })
    val pitchNoise = number(3, "Pitch noise amplitude", 0.1, 0.0, 3.0, 0.01, "°", "Maximum vertical noise amplitude.", { noiseEnabled.value })
    val noiseFrequency = number(3, "Noise frequency", 2.0, 0.1, 10.0, 0.1, " Hz", "Frequency of seeded noise target changes, sampled at 20 Hz.", { noiseEnabled.value })
    val noiseSmoothing = number(3, "Noise smoothing", 0.25, 0.01, 1.0, 0.01, "x", "Per-tick interpolation toward the next noise target.", { noiseEnabled.value })
    val noiseCorrelation = number(3, "Noise correlation", 0.35, -1.0, 1.0, 0.01, "x", "Signed coupling between horizontal and vertical noise.", { noiseEnabled.value })
    val attackNoiseScale = number(3, "Ready noise scale", 0.2, 0.0, 1.0, 0.01, "x", "Reduce noise inside the click or snap window.", { noiseEnabled.value })
    val microEnabled = flag(3, "Micro corrections", false, "Add alternating corrections that fade to zero at exact alignment.")
    val microYaw = number(3, "Micro yaw", 0.08, 0.0, 1.0, 0.01, "°", "Horizontal micro-correction amplitude.", { microEnabled.value })
    val microPitch = number(3, "Micro pitch", 0.06, 0.0, 1.0, 0.01, "°", "Vertical micro-correction amplitude.", { microEnabled.value })
    val microPeriod = number(3, "Micro period", 8.0, 2.0, 80.0, 1.0, " ticks", "Period of the deterministic micro-correction oscillation.", { microEnabled.value })
    val microErrorRange = number(3, "Micro error range", 2.0, 0.1, 15.0, 0.1, "°", "Micro corrections taper out beyond this angular error.", { microEnabled.value })

    val pointSource = choice(4, "Point source", AdvancedPointSource.AUTOMATIC, "Select automatic points or the edited normalized hitbox points.", { it.title })
    val automaticPoint = choice(4, "Automatic point", AdvancedAutomaticPoint.CHEST, "Automatic hitbox point selection method.", { it.title }, { pointSource.value == AdvancedPointSource.AUTOMATIC })
    val manualSelection = choice(4, "Manual point selection", AdvancedManualSelection.NEAREST, "How to choose among edited points.", { it.title }, { pointSource.value == AdvancedPointSource.MANUAL })
    val hitboxPoints = add(4, HitboxPointsSetting("Advanced Hitbox points", listOf(HitboxPoint(0.5, 0.55, 0.5)), 32, "Normalized points inside the target's actual bounding box.", { pointSource.value == AdvancedPointSource.MANUAL }))
    val pointMargin = number(4, "Hitbox margin", 0.1, 0.0, 0.45, 0.01, "x", "Inset automatic points and bound configured offsets inside the hitbox.")
    val preferredHeight = number(4, "Preferred height", 0.55, 0.0, 1.0, 0.01, "x", "Preferred normalized height when scoring automatic multipoints.", { pointSource.value == AdvancedPointSource.AUTOMATIC && automaticPoint.value == AdvancedAutomaticPoint.MULTIPOINT })
    val pointHoldTicks = number(4, "Point hold", 4.0, 0.0, 40.0, 1.0, " ticks", "Keep the selected normalized point before reevaluating alternatives.")
    val pointSwitchThreshold = number(4, "Point switch threshold", 0.1, 0.0, 4.0, 0.01, "", "Score improvement required to switch a held nearest, angle, or multipoint selection.")
    val preferVisible = flag(4, "Prefer visible points", false, "Prioritize unobstructed points when selecting multipoints or manual points.")
    val requireVisible = flag(4, "Require visible point", false, "Prevent attacks when the resolved point is blocked by world geometry.")
    val switchOnOccluded = flag(4, "Switch blocked points", true, "Reevaluate a held point immediately when world geometry blocks it.", { preferVisible.value || requireVisible.value })
    val pointSmoothing = number(4, "Point smoothing", 1.0, 0.01, 1.0, 0.01, "x", "Per-tick interpolation in normalized hitbox space; follows moving boxes without lag.")

    val predictionEnabled = flag(5, "Target prediction", false, "Predict target motion unless the aura supplies an elytra aim override.")
    val predictionTicks = number(5, "Prediction lead", 1.0, 0.0, 10.0, 0.1, " ticks", "Motion extrapolation horizon.", { predictionEnabled.value })
    val predictionSmoothing = number(5, "Velocity smoothing", 0.5, 0.01, 1.0, 0.01, "x", "Per-tick interpolation of observed target motion.", { predictionEnabled.value })
    val horizontalPrediction = number(5, "Horizontal prediction", 1.0, 0.0, 2.0, 0.05, "x", "Scale horizontal motion extrapolation.", { predictionEnabled.value })
    val verticalPrediction = number(5, "Vertical prediction", 1.0, 0.0, 2.0, 0.05, "x", "Scale vertical motion extrapolation.", { predictionEnabled.value })
    val predictAcceleration = flag(5, "Predict acceleration", false, "Include smoothed per-tick changes in target velocity.", { predictionEnabled.value })
    val accelerationPrediction = number(5, "Acceleration prediction", 0.5, 0.0, 2.0, 0.05, "x", "Scale the acceleration contribution to predicted displacement.", { predictionEnabled.value && predictAcceleration.value })
    val maxPredictionDistance = number(5, "Prediction distance limit", 1.0, 0.0, 6.0, 0.05, " blocks", "Bound total predicted displacement.", { predictionEnabled.value })
    val relativePrediction = flag(5, "Relative motion prediction", false, "Subtract local player motion from target prediction.", { predictionEnabled.value })
    val offsetX = number(5, "Hitbox X offset", 0.0, -0.5, 0.5, 0.01, "x", "Normalized horizontal point offset, clamped inside the hitbox.")
    val offsetY = number(5, "Hitbox Y offset", 0.0, -0.5, 0.5, 0.01, "x", "Normalized vertical point offset, clamped inside the hitbox.")
    val offsetZ = number(5, "Hitbox Z offset", 0.0, -0.5, 0.5, 0.01, "x", "Normalized depth point offset, clamped inside the hitbox.")

    val overshootEnabled = flag(6, "Overshoot", false, "Permit a seeded, bounded overshoot on sufficiently large trajectories.")
    val overshootChance = number(6, "Overshoot chance", 0.2, 0.0, 1.0, 0.01, "x", "Probability of overshooting a new trajectory.", { overshootEnabled.value })
    val yawOvershoot = number(6, "Yaw overshoot", 0.8, 0.0, 8.0, 0.1, "°", "Horizontal overshoot beyond the target in the approach direction.", { overshootEnabled.value })
    val pitchOvershoot = number(6, "Pitch overshoot", 0.4, 0.0, 8.0, 0.1, "°", "Vertical overshoot beyond the target in the approach direction.", { overshootEnabled.value })
    val overshootThreshold = number(6, "Overshoot minimum angle", 15.0, 0.0, 180.0, 1.0, "°", "Minimum initial angular movement that can overshoot.", { overshootEnabled.value })
    val overshootTicks = number(6, "Overshoot duration", 1.0, 1.0, 20.0, 1.0, " ticks", "Hold overshoot after the approach trajectory completes.", { overshootEnabled.value })
    val recoveryAfterAttack = flag(6, "Recovery after attack", false, "Apply the recovery speed and damping phase after each successful attack.")
    val recoveryTicks = number(6, "Recovery duration", 3.0, 1.0, 30.0, 1.0, " ticks", "Duration of overshoot or post-attack recovery.", { overshootEnabled.value || recoveryAfterAttack.value })
    val recoverySpeedScale = number(6, "Recovery speed", 0.7, 0.05, 2.0, 0.05, "x", "Scale axis velocity limits during recovery.", { overshootEnabled.value || recoveryAfterAttack.value })
    val recoveryDamping = number(6, "Recovery damping", 0.7, 0.05, 1.0, 0.01, "x", "Additional velocity retention during recovery.", { overshootEnabled.value || recoveryAfterAttack.value })

    val visibleRotation = flag(7, "Visible rotation", false, "Apply the final GCD-corrected angles to the local camera through the aura pipeline.")
    val returnSpeed = number(7, "Return speed", 40.0, 1.0, 180.0, 1.0, "°/tick", "Maximum return movement toward camera angles.")
    val keepTicks = number(7, "Keep ticks", 1.0, 0.0, 20.0, 1.0, " ticks", "Keep the last server rotation before the manager returns it to the camera.")

    val tabs: List<AdvancedSettingsTab> = listOf(
        AdvancedSettingsTab("Timing", groups[0].toList()),
        AdvancedSettingsTab("Axes", groups[1].toList()),
        AdvancedSettingsTab("Curves", groups[2].toList()),
        AdvancedSettingsTab("Noise", groups[3].toList()),
        AdvancedSettingsTab("Hitbox", groups[4].toList()),
        AdvancedSettingsTab("Prediction", groups[5].toList()),
        AdvancedSettingsTab("Recovery", groups[6].toList()),
        AdvancedSettingsTab("Output", groups[7].toList())
    )
    val allSettings: Set<Setting<*>> = LinkedHashSet<Setting<*>>().apply {
        for (group in groups) addAll(group)
    }
}
