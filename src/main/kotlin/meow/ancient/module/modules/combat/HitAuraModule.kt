package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.hitaura.*
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemUseAnimation
import sweetie.evaware.flora.api.Commando

object HitAuraModule : Module(
    name = "HitAura",
    category = COMBAT,
    description = ""
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
        VULCAN("Vulcan"),
        FUNTIME("Funtime"),
        UNIVERSAL("Universal"),
        GRIM_1204("Grim 1.20.4"),
        GRIM_FUN("GrimFun"),
        MLSAC("MlsAC");

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
        PACKET("Packet"),
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
        value = 4.2,
        min = 1.0,
        max = 8.0,
        step = 0.05,
        precision = 2,
        unit = "m",
        description = ""
    )
    val rayTrace by boolean(
        name = "RayTrace",
        value = true,
        description = ""
    )
    val throughWalls by boolean(
        name = "Through Walls",
        value = false,
        description = ""
    )

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
        name = "Elytra Distance",
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
    val aimPoint by enum(
        name = "Aim Point",
        defaultValue = AimPointMode.MULTIPOINT,
        nameMapper = { it.displayName },
        description = ""
    )
    val rotationSpeed by number(
        name = "Rotation Speed",
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
        name = "Random Spread",
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
        description = ""
    )
    val smartCrits by boolean(
        name = "Smart Crits",
        value = true,
        description = "",
        isVisible = { onlyCrits }
    )
    val sprintReset by enum(
        name = "Sprint Reset",
        defaultValue = SprintResetMode.PACKET,
        nameMapper = { it.displayName },
        description = ""
    )

    // Modular processors
    val targetSelector = TargetSelector()
    val rotationProcessor = AuraRotationProcessor()
    val criticalProcessor = CriticalProcessor()
    val clickerProcessor = ClickerProcessor()
    val sprintResetProcessor = SprintResetProcessor()
    val attackExecutor = AttackExecutor(
        clickerProcessor = clickerProcessor,
        criticalProcessor = criticalProcessor,
        sprintResetProcessor = sprintResetProcessor,
        rotationProcessor = rotationProcessor
    )

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
        targetSelector.reset()
        rotationProcessor.reset()
        clickerProcessor.reset()
        sprintResetProcessor.reset()
        RotationManager.resetImmediate()
    }

    override fun onEnable() {
        targetSelector.reset()
        rotationProcessor.reset()
        clickerProcessor.reset()
        sprintResetProcessor.reset()
    }

    override fun onDisable() {
        targetSelector.reset()
        rotationProcessor.reset()
        clickerProcessor.reset()
        sprintResetProcessor.reset()
        RotationManager.resetImmediate()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        safeRun {
            if (player.isDeadOrDying) {
                targetSelector.reset()
                return@safeRun
            }

            sprintResetProcessor.onTick()

            if (shouldPreventSprint()) {
                player.isSprinting = false
            }

            if (shouldIgnoreContext()) {
                targetSelector.reset()
                rotationProcessor.reset()
                sprintResetProcessor.reset()
                RotationManager.resetImmediate()
                return@safeRun
            }

            val rotMode = rotationMode
            val maxReach = if (rotMode == RotationMode.GRIM_1204) attackRange.coerceAtMost(3.0) else attackRange
            val effectiveAimRange = if (player.isFallFlying && elytraTarget) elytraDistance.coerceAtLeast(maxReach)
                else aimRange.coerceAtLeast(maxReach)
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
            ) ?: return@safeRun

            val pointMode = aimPoint
            val moveFixMode = moveFix
            val cMode = clickMode
            val sResetMode = sprintReset

            val readyToAttackNow = attackExecutor.shouldPrepareAttack(
                target = target,
                attackRange = maxReach,
                clickMode = cMode,
                cps = cps,
                randomizeCps = cpsRandomization,
                cpsSpread = cpsSpread,
                onlyCrits = onlyCrits,
                smartCrits = smartCrits
            )
            val overrideAimPoint = if (elytraTarget && player.isFallFlying && elytraPredict) {
                val targetVel = target.deltaMovement
                target.position().add(
                    targetVel.x * elytraPredictTicks,
                    targetVel.y * elytraPredictTicks + target.eyeHeight * 0.75,
                    targetVel.z * elytraPredictTicks
                )
            } else null

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
                shouldSnapNow = readyToAttackNow,
                overrideAimPoint = overrideAimPoint
            )

            val enforceRayTrace = rayTrace || rotMode == RotationMode.GRIM_1204

            attackExecutor.tryAttack(
                target = target,
                attackRange = maxReach,
                clickMode = cMode,
                cps = cps,
                randomizeCps = cpsRandomization,
                cpsSpread = cpsSpread,
                onlyCrits = onlyCrits,
                smartCrits = smartCrits,
                sprintResetMode = sResetMode,
                rayTrace = enforceRayTrace,
                throughWalls = throughWalls,
                checkAimAngle = rotMode != RotationMode.SNAP
            )
        }
    }
}
