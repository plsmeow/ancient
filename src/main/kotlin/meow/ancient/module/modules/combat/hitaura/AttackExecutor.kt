package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.extensions.sendPacket
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.module.modules.combat.AutoMaceModule
import meow.ancient.module.modules.combat.BreachSwapModule
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.MaceKillModule
import meow.ancient.module.modules.combat.CriticalsModule
import meow.ancient.module.modules.combat.TriggerBotModule
import meow.ancient.module.modules.combat.HitAuraModule.ClickMode
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity

class AttackExecutor(
    private val clickerProcessor: ClickerProcessor,
    private val criticalProcessor: CriticalProcessor,
    private val sprintResetProcessor: SprintResetProcessor,
    private val rotationProcessor: AuraRotationProcessor
) {
    fun shouldPrepareAttack(
        target: LivingEntity,
        attackRange: Double,
        clickMode: ClickMode,
        cps: Double,
        randomizeCps: Boolean,
        cpsSpread: Double,
        onlyCrits: Boolean,
        smartCrits: Boolean
    ): Boolean {
        if (!inGame || HitAuraModule.shouldIgnoreContext()) return false
        if (RotationUtil.getDistanceToBox(target) > attackRange) return false
        if (BreachSwapModule.isActive() && !BreachSwapModule.isVirtualCooldownReady()) return false
        if (!AutoMaceModule.isForceReady(target)
            && !clickerProcessor.isReadyToClick(clickMode, cps, randomizeCps, cpsSpread)) return false
        return criticalProcessor.isReadyForAttack(onlyCrits, smartCrits)
    }

    fun tryAttack(
        target: LivingEntity,
        attackRange: Double,
        clickMode: ClickMode,
        cps: Double,
        randomizeCps: Boolean,
        cpsSpread: Double,
        onlyCrits: Boolean,
        smartCrits: Boolean,
        sprintResetMode: SprintResetMode,
        rayTrace: Boolean,
        throughWalls: Boolean,
        checkAimAngle: Boolean
    ): Boolean {
        if (!shouldPrepareAttack(target, attackRange, clickMode, cps, randomizeCps, cpsSpread, onlyCrits, smartCrits)) {
            return false
        }

        val localPlayer = mc.player ?: return false
        val currentGameMode = mc.gameMode ?: return false

        if (rayTrace) {
            val currentRot = RotationManager.getServerRotation()
            val hitsCurrentHitbox = RayTraceUtil.isLookingAtEntity(
                target = target,
                rotation = currentRot,
                range = attackRange,
                throughWalls = throughWalls
            )
            if (!hitsCurrentHitbox) {
                return false
            }

            val previousRot = RotationManager.getPreviousServerRotation()
            val hitsServerHitbox = RayTraceUtil.isLookingAtEntity(
                target = target,
                rotation = previousRot,
                range = attackRange,
                throughWalls = throughWalls
            )
            if (!hitsServerHitbox) {
                return false
            }
        } else if (checkAimAngle && !rotationProcessor.isAimedAtTarget(target, 28.0f)) {
            return false
        }

        val forceMaceReady = AutoMaceModule.isForceReady(target)
        var previousAxeSlot = -1
        var breachSwapSlot = -1
        var executed = false
        var packetSprintReset = false
        try {
            val macePreparation = AutoMaceModule.beforeAttack()
            if (macePreparation == AutoMaceModule.AttackPreparation.WAITING
                || forceMaceReady && macePreparation != AutoMaceModule.AttackPreparation.READY) return false

            if (HitAuraModule.breakShield && TriggerBotModule.isTargetBlocking(target)) {
                previousAxeSlot = TriggerBotModule.swapToAxe(target)
            }
            if (previousAxeSlot == -1 && BreachSwapModule.isActive()) {
                breachSwapSlot = BreachSwapModule.swapToBreachMace()
            }
            val isCritAttack = onlyCrits && criticalProcessor.isFallingForCrit()
            sprintResetProcessor.preAttack(sprintResetMode, forceStopSprint = isCritAttack)
            if (MaceKillModule.isEnabled()) {
                MaceKillModule.doCrit()
            }
            if (CriticalsModule.isEnabled()) {
                CriticalsModule.killAuraTriggered = true
                CriticalsModule.doCrit()
            }

            val swingAnimation = localPlayer.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation
            if (clickMode == ClickMode.MODE_1_8) {
                localPlayer.swing(InteractionHand.MAIN_HAND, swingAnimation, false)
                currentGameMode.attack(localPlayer, target)
                executed = true
            } else {
                currentGameMode.attack(localPlayer, target)
                executed = true
                localPlayer.swing(InteractionHand.MAIN_HAND, swingAnimation, false)
            }
            return true
        } finally {
            CriticalsModule.killAuraTriggered = false
            try {
                if (executed) clickerProcessor.onAttackExecuted(clickMode, cps, randomizeCps, cpsSpread)
                if (executed) sprintResetProcessor.postAttack(sprintResetMode)
            } finally {
                if (breachSwapSlot != -1) {
                    BreachSwapModule.swapBack(breachSwapSlot)
                }
                if (previousAxeSlot != -1) {
                    TriggerBotModule.swapBack(previousAxeSlot)
                }
                if (executed) {
                    BreachSwapModule.onAttackExecuted()
                }
                AutoMaceModule.afterAttack(executed)
            }
        }
    }
}
