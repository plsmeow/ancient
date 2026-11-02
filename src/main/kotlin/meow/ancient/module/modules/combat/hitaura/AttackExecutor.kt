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
import meow.ancient.module.modules.combat.hitaura.rotation.impl.NeuroRotation
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
        if (HitAuraModule.rotationMode == HitAuraModule.RotationMode.NEURO &&
            !(rotationProcessor.getStrategy(HitAuraModule.RotationMode.NEURO) as NeuroRotation).isAttackReady()) return false

        val localPlayer = mc.player ?: return false
        val currentGameMode = mc.gameMode ?: return false

        if (rayTrace && !localPlayer.isFallFlying) {
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
        }

        val forceMaceReady = AutoMaceModule.isForceReady(target)
        var previousAxeSlot = -1
        var breachSwapSlot = -1
        var executed = false
        try {
            val macePreparation = AutoMaceModule.beforeAttack()
            if (macePreparation == AutoMaceModule.AttackPreparation.WAITING
                || forceMaceReady && macePreparation != AutoMaceModule.AttackPreparation.READY) return false

            if (HitAuraModule.breakShield && ShieldBreakProcessor.isTargetBlocking(target)) {
                previousAxeSlot = ShieldBreakProcessor.swapToAxe(target)
            }
            if (previousAxeSlot == -1 && BreachSwapModule.isActive()) {
                breachSwapSlot = BreachSwapModule.swapToBreachMace()
            }
            val isCritAttack = onlyCrits && (criticalProcessor.isFallingForCrit() || criticalProcessor.isGrimPacketReady())
            if (!sprintResetProcessor.preAttack(sprintResetMode, forceStopSprint = isCritAttack)) return false
            if (MaceKillModule.isEnabled()) {
                MaceKillModule.doCrit()
            }

            val grim = if (HitAuraModule.rotationMode == HitAuraModule.RotationMode.GRIM_1204) {
                rotationProcessor.getStrategy(HitAuraModule.RotationMode.GRIM_1204) as? meow.ancient.module.modules.combat.hitaura.rotation.impl.Grim1204Rotation
            } else null
            grim?.sendRotationPacket(localPlayer)
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
            if (HitAuraModule.rotationMode == HitAuraModule.RotationMode.NEURO) {
                (rotationProcessor.getStrategy(HitAuraModule.RotationMode.NEURO) as NeuroRotation).notifyAttack()
            }
            grim?.sendResetPacket(localPlayer)
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
                    ShieldBreakProcessor.swapBack(previousAxeSlot)
                }
                if (executed) {
                    BreachSwapModule.onAttackExecuted()
                }
                AutoMaceModule.afterAttack(executed)
            }
        }
    }
}
