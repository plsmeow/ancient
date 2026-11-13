package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.components.AdvancedSettingsWindow
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerMovementSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.event.events.PacketSendEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.multiplayer.ClientLevel
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.hitaura.*
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedRotationSettings
import meow.ancient.module.modules.combat.hitaura.rotation.impl.AdvancedRotation
import meow.ancient.module.setting.ActionSetting
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object HitAuraModule : Module(
    name = "HitAura",
    category = COMBAT,
    description = "Aim and recheck attacks before movement; 1.9 cooldown threshold is 95%"
) {
    enum class TargetPriority(val displayName: String) {
        DISTANCE("Distance"),
        HEALTH("Health"),
        FOV("FOV"),
        HURT_TIME("HurtTime"),
        ARMOR("Armor");

        companion object {
            val names: List<String> = entries.map { it.displayName }
            fun fromName(name: String): TargetPriority =
                entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: DISTANCE
        }
    }

    enum class TargetMode(val displayName: String) {
        SINGLE("Single"),
        SWITCH("Switch");

        companion object {
            val names: List<String> = entries.map { it.displayName }
            fun fromName(name: String): TargetMode =
                entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: SINGLE
        }
    }

    enum class RotationMode(val displayName: String) {
        VANILLA("Vanilla"),
        SNAP("Snap"),
        SPOOKY_TIME("SpookyTime"),
        TEST("Test"),
        TEST2("Test2"),
        SLOTH_070926("Sloth 07.09.26"),
        WELLMINE("Wellmine old"),
        NO_ROT("NoRot"),
        LONY_GRIEF("LonyGrief"),
        FUNTIME("Funtime"),
        UNIVERSAL("Universal"),
        GRIM_1204("Grim 1.20.4"),
        GRIM_FUN("GrimFun"),
        MLSAC("MlsAC"),
        NEURO("Neuro"),
        ADVANCED("Advanced");

        companion object {
            val names: List<String> = entries.map { it.displayName }
            fun fromName(name: String): RotationMode =
                entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: VANILLA
        }
    }

    enum class AimPointMode(val displayName: String) {
        MULTIPOINT("Multipoint"),
        NEAREST("Nearest"),
        HEAD("Head"),
        CHEST("Chest");

        companion object {
            val names: List<String> = entries.map { it.displayName }
            fun fromName(name: String): AimPointMode =
                entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: MULTIPOINT
        }
    }

    enum class ClickMode(val displayName: String) {
        MODE_1_9("1.9"),
        MODE_1_8("1.8");

        companion object {
            val names: List<String> = entries.map { it.displayName }
            fun fromName(name: String): ClickMode =
                entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: MODE_1_9
        }
    }

    enum class SprintResetMode(val displayName: String) {
        LEGIT("Legit"),
        PACKET("Packet"),
        LB("LB"),
        OFF("Off");

        companion object {
            val names: List<String> = entries.map { it.displayName }
            fun fromName(name: String): SprintResetMode =
                entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: PACKET
        }
    }

    // Target selection & Priority & FOV
    val targets = targets(
        name = "Targets",
        value = setOf("minecraft:player")
    )
    val ignoreOn by multiMode(
        name = "Ignore On",
        value = setOf("Gui"),
        modes = listOf("Use", "Gui")
    )
    val friendProtection by boolean(
        name = "Friend Protection",
        value = true,
        description = ""
    )
    val targetMode by enum(
        name = "Target Mode",
        defaultValue = TargetMode.SINGLE,
        nameMapper = { it.displayName },
        description = ""
    )
    val priority by enum(
        name = "Priority",
        defaultValue = TargetPriority.DISTANCE,
        nameMapper = { it.displayName },
        description = ""
    )
    val fov by number(
        name = "FOV",
        value = 180.0,
        min = 15.0,
        max = 180.0,
        step = 1.0,
        precision = 0,
        unit = "°",
        description = ""
    )

    // Distances & RayTrace
    val attackRange by number(
        name = "Attack Range",
        value = 3.0,
        min = 1.0,
        max = 6.0,
        step = 0.05,
        precision = 2,
        unit = "m",
        description = ""
    )
    val aimRange by number(
        name = "Aim Range",
        value = 1.0,
        min = 0.0,
        max = 6.0,
        step = 0.05,
        precision = 2,
        unit = "m",
        description = "Additional distance beyond attack range to begin aiming"
    )
    val rayTrace by boolean(
        name = "RayTrace",
        value = true,
        description = ""
    )
    val wallCheck by boolean(
        name = "WallCheck",
        value = true,
        description = "Requires a clear view before attacking, including when RayTrace is disabled"
    )
    val throughWalls get() = !wallCheck

    val breakShield by boolean(
        name = "Break Shield",
        value = true,
        description = ""
    )

    val elytraTarget by boolean(
        name = "ElytraTarget",
        value = false,
        description = ""
    )
    val elytraDistance by number(
        name = "Elytra Dist",
        value = 100.0,
        min = 10.0,
        max = 300.0,
        step = 5.0,
        precision = 0,
        unit = "m",
        isVisible = { elytraTarget }
    )
    val elytraPredict by boolean(
        name = "Elytra Predict",
        value = true,
        isVisible = { elytraTarget }
    )
    val elytraPredictTicks by number(
        name = "Predict Ticks",
        value = 3.0,
        min = 1.0,
        max = 10.0,
        step = 0.5,
        precision = 1,
        isVisible = { elytraTarget && elytraPredict }
    )
    val elytraFreeze by boolean(
        name = "Freeze",
        value = false,
        isVisible = { elytraTarget }
    )

    // Rotations, GCD & MoveFix
    val rotationMode by enum(
        name = "Rotation",
        defaultValue = RotationMode.VANILLA,
        nameMapper = { it.displayName },
        description = ""
    )
    val neuroSnap by boolean(
        name = "Neuro Snap",
        value = false,
        isVisible = { rotationMode == RotationMode.NEURO }
    )
    val neuroSnapTicks by number(
        name = "Neuro Snap Ticks",
        value = 2.0,
        min = 1.0,
        max = 10.0,
        step = 1.0,
        precision = 0,
        isVisible = { rotationMode == RotationMode.NEURO && neuroSnap }
    )
    val aimPoint by enum(
        name = "Aim Point",
        defaultValue = AimPointMode.MULTIPOINT,
        nameMapper = { it.displayName },
        description = ""
    )
    val rotationSpeed by number(
        name = "Rot Speed",
        value = 75.0,
        min = 10.0,
        max = 180.0,
        step = 1.0,
        precision = 0,
        unit = "°",
        description = "",
        isVisible = { rotationMode != RotationMode.SNAP }
    )
    val gcdFix by boolean(
        name = "GCD Fix",
        value = true,
        description = ""
    )
    val moveFix by enum(
        name = "Move Fix",
        defaultValue = MoveFixMode.SILENT,
        nameMapper = { it.displayName },
        description = ""
    )

    // Click Mode 1.8 / 1.9 & CPS & Randomization
    val clickMode by enum(
        name = "Click Mode",
        defaultValue = ClickMode.MODE_1_9,
        nameMapper = { it.displayName },
        description = ""
    )
    val cps by number(
        name = "CPS",
        value = 12.0,
        min = 1.0,
        max = 20.0,
        step = 0.5,
        precision = 1,
        description = "",
        isVisible = { clickMode == ClickMode.MODE_1_8 }
    )
    val cpsRandomization by boolean(
        name = "Randomization",
        value = true,
        description = "",
        isVisible = { clickMode == ClickMode.MODE_1_8 }
    )
    val cpsSpread by number(
        name = "Spread",
        value = 2.0,
        min = 0.5,
        max = 5.0,
        step = 0.5,
        precision = 1,
        description = "",
        isVisible = { clickMode == ClickMode.MODE_1_8 && cpsRandomization }
    )

    // Criticals & Sprint Reset
    val onlyCrits by boolean(
        name = "Only Crits",
        value = true,
        description = "Wait for at least 0.08 blocks of descent or ready packet crits; allow attacks when crits are impossible"
    )
    val smartCrits by boolean(
        name = "Smart Crits",
        value = true,
        description = "Allow ground attacks without holding jump; disabling waits for a real fall when crits are possible",
        isVisible = { onlyCrits }
    )
    val advancedSettings = AdvancedRotationSettings(this)
    private val advancedSettingsButton = register(ActionSetting(
        "Настройка Advanced",
        action = { ClickGuiScreen.openEditor(AdvancedSettingsWindow(advancedSettings)) },
        description = "Configure Advanced rotation, curves and manual hitbox points",
        isVisible = { rotationMode == RotationMode.ADVANCED }
    ))


    // Modular processors
    val targetSelector = TargetSelector()
    val rotationProcessor = AuraRotationProcessor(advancedSettings)
    val criticalProcessor = CriticalProcessor()
    val clickerProcessor = ClickerProcessor()
    val sprintResetProcessor = SprintResetProcessor()
    val attackExecutor = AttackExecutor(
        clickerProcessor = clickerProcessor,
        criticalProcessor = criticalProcessor,
        sprintResetProcessor = sprintResetProcessor,
        rotationProcessor = rotationProcessor
    )

    private var pendingTarget: LivingEntity? = null
    private var pendingPlayer: LocalPlayer? = null
    private var pendingLevel: ClientLevel? = null
    private var pendingTick = -1

    var predictedAimPoint: Vec3? = null
        private set

    private fun clearPendingAttack() {
        pendingTarget = null
        pendingPlayer = null
        pendingLevel = null
        pendingTick = -1
    }

    val currentTarget: LivingEntity?
        get() = targetSelector.currentTarget

    fun shouldPreventSprint(): Boolean {
        if (!isEnabled()) return false
        return sprintResetProcessor.isResettingSprint()
    }

    fun shouldIgnoreContext(): Boolean {
        if ("Gui" in ignoreOn && currentScreen != null) return true
        val player = mc.player ?: return true
        return "Use" in ignoreOn && player.isUsingItem && player.useItem.useAnimation != ItemUseAnimation.NONE
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        clearPendingAttack()
        targetSelector.reset()
        rotationProcessor.reset()
        clickerProcessor.reset()
        criticalProcessor.reset()
        sprintResetProcessor.reset()
        predictedAimPoint = null
        RotationManager.resetImmediate()
    }

    override fun onEnable() {
        clearPendingAttack()
        targetSelector.reset()
        rotationProcessor.reset()
        clickerProcessor.reset()
        criticalProcessor.reset()
        sprintResetProcessor.reset()
    }

    override fun onDisable() {
        clearPendingAttack()
        targetSelector.reset()
        rotationProcessor.reset()
        clickerProcessor.reset()
        criticalProcessor.reset()
        sprintResetProcessor.reset()
        predictedAimPoint = null
        RotationManager.resetImmediate()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        clearPendingAttack()
        predictedAimPoint = null
        if (!ActionCoordinator.canAct(this)) return
        val sprintMode = SprintResetMode.fromName(AnticheatManager.sprintReset)
        sprintResetProcessor.prepareAttack(sprintMode, false)
        sprintResetProcessor.onUpdate()
        safeRun {
            if (player.isDeadOrDying) {
                targetSelector.reset()
                return@safeRun
            }

            if (shouldIgnoreContext()) {
                targetSelector.reset()
                rotationProcessor.reset()
                RotationManager.resetImmediate()
                return@safeRun
            }

            val rotMode = rotationMode
            val maxReach = if (rotMode == RotationMode.GRIM_1204) attackRange.coerceAtMost(3.0) else attackRange
            val effectiveAimRange = maxOf(
                if (player.isFallFlying && elytraTarget) elytraDistance.coerceAtLeast(maxReach) else maxReach + aimRange,
                TpAuraModule.searchRange
            )
            val priorityMode = priority
            val tMode = targetMode

            val target = targetSelector.updateTarget(
                aimRange = effectiveAimRange,
                fov = fov.toFloat(),
                priority = priorityMode,
                targetMode = tMode,
                allowedTargets = targets,
                throughWalls = throughWalls,
                ignoreFriends = friendProtection
            ) ?: run {
                sprintResetProcessor.reset()
                if (rotMode == RotationMode.ADVANCED) {
                    (rotationProcessor.getStrategy(rotMode) as AdvancedRotation).onTargetLost()
                }
                if (rotMode == RotationMode.NEURO) rotationProcessor.returnNeuroRotation(moveFix, gcdFix)
                if (rotMode == RotationMode.GRIM_1204) rotationProcessor.getStrategy(rotMode).reset()
                return@safeRun
            }

            val pointMode = aimPoint
            val moveFixMode = moveFix
            val cMode = clickMode

            val readyNextAttack = if (rotMode == RotationMode.ADVANCED) {
                attackExecutor.willClickNextTick(target, maxReach, cMode, cps, cpsRandomization, cpsSpread)
            } else attackExecutor.shouldPrepareAttack(
                target = target,
                attackRange = maxReach,
                clickMode = cMode,
                cps = cps,
                randomizeCps = cpsRandomization,
                cpsSpread = cpsSpread,
                onlyCrits = onlyCrits,
                smartCrits = smartCrits,
                ticksAhead = 1
            )
            sprintResetProcessor.prepareAttack(
                sprintMode,
                criticalProcessor.shouldStopSprinting(
                    onlyCrits,
                    attackExecutor.willClickNextTick(target, maxReach, cMode, cps, cpsRandomization, cpsSpread)
                )
            )
            val overrideAimPoint = if (elytraTarget && player.isFallFlying && elytraPredict) {
                val targetVel = target.deltaMovement
                target.position().add(
                    targetVel.x * elytraPredictTicks,
                    targetVel.y * elytraPredictTicks + target.eyeHeight * 0.75,
                    targetVel.z * elytraPredictTicks
                )
            } else null
            predictedAimPoint = overrideAimPoint

            if (elytraTarget && elytraFreeze && player.isFallFlying) {
                val distToTarget = player.position().distanceTo(target.position())
                if (distToTarget < 5.0) {
                    player.deltaMovement = net.minecraft.world.phys.Vec3(0.0, player.deltaMovement.y.coerceAtLeast(-0.05), 0.0)
                }
            }

            rotationProcessor.processRotation(
                target = target,
                rotationMode = rotMode,
                aimPointMode = pointMode,
                rotationSpeed = rotationSpeed.toFloat(),
                gcdEnabled = gcdFix,
                moveFixMode = moveFixMode,
                shouldSnapNow = readyNextAttack,
                overrideAimPoint = overrideAimPoint
            )

            pendingTarget = target
            pendingPlayer = player
            pendingLevel = level
            pendingTick = player.tickCount
        }
    }

    @Commando
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val target = pendingTarget
        val expectedPlayer = pendingPlayer
        val expectedLevel = pendingLevel
        val expectedTick = pendingTick
        clearPendingAttack()
        try {
            if (target == null) return
            if (!isEnabled()) return
            safeRun {
                if (player !== expectedPlayer || level !== expectedLevel || player.isDeadOrDying) return@safeRun
                if (player.tickCount != expectedTick && player.tickCount != expectedTick + 1) return@safeRun
                if (currentTarget !== target || level.getEntity(target.id) !== target || shouldIgnoreContext()) return@safeRun

                val rotMode = rotationMode
                val maxReach = if (rotMode == RotationMode.GRIM_1204) attackRange.coerceAtMost(3.0) else attackRange
                if (!targetSelector.isValidTarget(target, maxReach, fov.toFloat(), targets, throughWalls, friendProtection)) return@safeRun
                val enforceRayTrace = rayTrace && !player.isFallFlying && rotMode != RotationMode.NO_ROT && rotMode != RotationMode.GRIM_1204
                val sResetMode = SprintResetMode.fromName(AnticheatManager.sprintReset)
                attackExecutor.tryAttack(
                    target = target,
                    attackRange = maxReach,
                    clickMode = clickMode,
                    cps = cps,
                    randomizeCps = cpsRandomization,
                    cpsSpread = cpsSpread,
                    onlyCrits = onlyCrits,
                    smartCrits = smartCrits,
                    sprintResetMode = sResetMode,
                    rayTrace = enforceRayTrace,
                    throughWalls = throughWalls
                )
            }
        } finally {
            sprintResetProcessor.onPreMovement()
        }
    }


}
