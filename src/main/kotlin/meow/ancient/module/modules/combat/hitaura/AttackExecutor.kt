package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.module.modules.combat.AutoMaceModule
import meow.ancient.module.modules.combat.BreachSwapModule
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.MaceKillModule
import meow.ancient.module.modules.combat.TpAuraModule
import meow.ancient.module.modules.combat.hitaura.rotation.impl.NeuroRotation
import meow.ancient.module.modules.combat.hitaura.rotation.impl.AdvancedRotation
import meow.ancient.module.modules.combat.hitaura.rotation.impl.Grim1204Rotation
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
        smartCrits: Boolean,
        ticksAhead: Int = 0,
        sprintResetMode: SprintResetMode = SprintResetMode.OFF
    ): Boolean {
        if (!inGame || HitAuraModule.shouldIgnoreContext()) return false
        if (!ActionCoordinator.canAct(HitAuraModule)) return false
        if (TpAuraModule.isPendingVaultReturn()) return false
        if (RotationUtil.getDistanceToBox(target) > attackRange && TpAuraModule.getRenderPosition(target) == null) return false
        if (HitAuraModule.wallCheck && TpAuraModule.getRenderPosition(target) == null
            && mc.player?.hasLineOfSight(target) != true) return false
        if (BreachSwapModule.isActive() && !BreachSwapModule.isVirtualCooldownReady(ticksAhead)) return false
        if (!AutoMaceModule.isForceReady(target)
            && !clickerProcessor.isReadyToClick(clickMode, cps, randomizeCps, cpsSpread, ticksAhead > 0)) return false
        return if (ticksAhead > 0) criticalProcessor.shouldPrepareAttack(onlyCrits, smartCrits)
            else criticalProcessor.isReadyForAttack(onlyCrits, smartCrits, sprintResetMode)
    }

    fun willClickNextTick(
        target: LivingEntity,
        attackRange: Double,
        clickMode: ClickMode,
        cps: Double,
        randomizeCps: Boolean,
        cpsSpread: Double
    ): Boolean {
        if (!inGame || HitAuraModule.shouldIgnoreContext()) return false
        if (!ActionCoordinator.canAct(HitAuraModule)) return false
        if (TpAuraModule.isPendingVaultReturn()) return false
        if (RotationUtil.getDistanceToBox(target) > attackRange && TpAuraModule.getRenderPosition(target) == null) return false
        if (!AutoMaceModule.isForceReady(target)
            && !clickerProcessor.isReadyToClick(clickMode, cps, randomizeCps, cpsSpread, true)) return false
        return true
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
        throughWalls: Boolean
    ): Boolean {
        if (!shouldPrepareAttack(target, attackRange, clickMode, cps, randomizeCps, cpsSpread, onlyCrits, smartCrits, 0, sprintResetMode)) {
            return false
        }
        val positionAuraReachable = TpAuraModule.getRenderPosition(target) != null
        if (HitAuraModule.rotationMode == HitAuraModule.RotationMode.NEURO &&
            !(rotationProcessor.getStrategy(HitAuraModule.RotationMode.NEURO) as NeuroRotation).isAttackReady()) return false
        if (HitAuraModule.rotationMode == HitAuraModule.RotationMode.ADVANCED &&
            !(rotationProcessor.getStrategy(HitAuraModule.RotationMode.ADVANCED) as AdvancedRotation).isAttackReady()) return false
        if (!positionAuraReachable && HitAuraModule.rotationMode == HitAuraModule.RotationMode.GRIM_1204 &&
            !(rotationProcessor.getStrategy(HitAuraModule.RotationMode.GRIM_1204) as Grim1204Rotation).isLookingAtTarget(target, attackRange, throughWalls)) return false

        val localPlayer = mc.player ?: return false
        val currentGameMode = mc.gameMode ?: return false

        if (rayTrace && !localPlayer.isFallFlying && !positionAuraReachable) {
            val outgoingRotation = RotationManager.getServerRotation()
            val hitsHitbox = RayTraceUtil.isLookingAtEntity(
                target = target,
                rotation = outgoingRotation,
                range = attackRange,
                throughWalls = throughWalls
            )
            if (!hitsHitbox) {
                return false
            }
        }
        var tpAuraMoved = false
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
            tpAuraMoved = TpAuraModule.beforeAttack(target)
            if (positionAuraReachable && !tpAuraMoved) return false
            if (!throughWalls && !localPlayer.hasLineOfSight(target)) return false
            if (MaceKillModule.isEnabled()) {
                MaceKillModule.doCrit()
            }


            val isGrim1204 = HitAuraModule.rotationMode == HitAuraModule.RotationMode.GRIM_1204
            val grim1204 = if (isGrim1204) rotationProcessor.getStrategy(HitAuraModule.RotationMode.GRIM_1204) as? Grim1204Rotation else null
            sprintResetProcessor.preAttack(sprintResetMode)
            grim1204?.sendRotationPacket(localPlayer)
            val swingAnimation = localPlayer.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation
            try {
                currentGameMode.attack(localPlayer, target)
                executed = true
                localPlayer.swing(InteractionHand.MAIN_HAND, swingAnimation, false)
            } finally {
                grim1204?.sendResetPacket(localPlayer)
                if (tpAuraMoved) {
                    TpAuraModule.afterAttack()
                    tpAuraMoved = false
                }
            }
            if (HitAuraModule.rotationMode == HitAuraModule.RotationMode.NEURO) {
                (rotationProcessor.getStrategy(HitAuraModule.RotationMode.NEURO) as NeuroRotation).notifyAttack()
            }
            if (HitAuraModule.rotationMode == HitAuraModule.RotationMode.ADVANCED) {
                (rotationProcessor.getStrategy(HitAuraModule.RotationMode.ADVANCED) as AdvancedRotation).notifyAttack()
            }
            return true
        } finally {
            try {
                if (tpAuraMoved) TpAuraModule.afterAttack()
                if (executed) clickerProcessor.onAttackExecuted(clickMode, cps, randomizeCps, cpsSpread)
            } finally {
                try {
                    if (breachSwapSlot != -1) {
                        BreachSwapModule.swapBack(breachSwapSlot)
                    }
                    if (previousAxeSlot != -1) {
                        ShieldBreakProcessor.swapBack(previousAxeSlot)
                    }
                    if (executed) {
                        BreachSwapModule.onAttackExecuted()
                    }
                } finally {
                    try {
                        AutoMaceModule.afterAttack(executed)
                    } finally {
                        sprintResetProcessor.postAttack(executed)
                    }
                }
            }
        }
    }
}
