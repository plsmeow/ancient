package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.network.SilentPacketQueue
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
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/** Execute before movement, using the last accepted server pose and accepted attack clocks. */
class AttackExecutor(
    private val clickerProcessor: ClickerProcessor,
    private val criticalProcessor: CriticalProcessor,
    private val sprintResetProcessor: SprintResetProcessor,
    private val rotationProcessor: AuraRotationProcessor
) {
    enum class WaitReason { IDLE, CONTEXT, PRIORITY, RANGE, WALL, COOLDOWN, CRIT, ROTATION, SLOT, READY, ATTACK }
    var waitReason = WaitReason.IDLE
        private set
    private var macePreparation: AutoMaceModule.AttackPreparation? = null
    private var sending: Packet<*>? = null
    private var accepted = false
    private var sentRotation: Rotation? = null
    private var sentPosition: Vec3? = null

    private fun wait(reason: WaitReason): Boolean { waitReason = reason; return false }

    fun onPacketSent(packet: Packet<*>) {
        if (packet === sending) accepted = true
        if (packet is ServerboundMovePlayerPacket) {
            val player = mc.player ?: return
            val rotation = sentRotation ?: Rotation(player.yRot, player.xRot)
            sentRotation = Rotation(packet.getYRot(rotation.yaw), packet.getXRot(rotation.pitch))
            val position = sentPosition ?: player.position()
            sentPosition = Vec3(packet.getX(position.x), packet.getY(position.y), packet.getZ(position.z))
        }
    }

    fun reset() {
        cancelPreparation()
        sentRotation = null
        sentPosition = null
        waitReason = WaitReason.IDLE
    }

    private fun lookingAt(target: LivingEntity, range: Double, walls: Boolean, rotation: Rotation? = null): Boolean {
        val player = mc.player ?: return false
        val eyes = sentPosition?.add(0.0, player.eyeHeight.toDouble(), 0.0) ?: player.eyePosition
        return RayTraceUtil.isLookingAtEntity(target, rotation ?: sentRotation ?: Rotation(player.yRot, player.xRot), range, walls, eyes)
    }

    private fun grimAttackRotation(target: LivingEntity): Rotation? {
        val grim = rotationProcessor.getStrategy(HitAuraModule.rotationMode) as? Grim1204Rotation ?: return null
        val rotation = grim.getAttackRotation(target) ?: return null
        val player = mc.player ?: return null
        val previous = sentRotation ?: Rotation(player.yRot, player.xRot)
        return if (HitAuraModule.gcdFix) GCDUtil.applyGCD(rotation, previous) else rotation.normalizeTo(previous)
    }

    private fun sendAccepted(player: LocalPlayer, packet: Packet<*>): Boolean {
        accepted = false
        sending = packet
        return try {
            player.connection.send(packet)
            accepted
        } finally {
            sending = null
        }
    }

    private fun serverRange(target: LivingEntity, range: Double): Boolean {
        val player = mc.player ?: return false
        val eyes = sentPosition?.add(0.0, player.eyeHeight.toDouble(), 0.0) ?: player.eyePosition
        return RotationUtil.getDistanceToBox(target, eyes) <= range
    }

    fun geometryReady(target: LivingEntity, range: Double, rayTrace: Boolean, throughWalls: Boolean): Boolean {
        if (TpAuraModule.getRenderPosition(target) != null) return true
        if (!serverRange(target, range)) return wait(WaitReason.RANGE)
        val mode = HitAuraModule.rotationMode
        val strategy = rotationProcessor.getStrategy(mode)
        if (strategy is NeuroRotation && !strategy.isAttackReady()
            || strategy is AdvancedRotation && !strategy.isAttackReady()) return wait(WaitReason.ROTATION)
        val grimRotation = if (strategy is Grim1204Rotation)
            grimAttackRotation(target) ?: return wait(WaitReason.ROTATION) else null
        // Packet-only Grim can prepare a hit without pointing the ordinary movement packets at it.
        if ((rayTrace || strategy is Grim1204Rotation)
            && !lookingAt(target, range, throughWalls && !HitAuraModule.wallCheck, grimRotation)) return wait(WaitReason.ROTATION)
        return true
    }

    fun cancelPreparation(releaseWeapon: Boolean = true) {
        if (macePreparation != null) {
            macePreparation = null
            AutoMaceModule.afterAttack(false)
        }
        if (releaseWeapon) SilentSwapUtil.releasePreparedSlot(HitAuraModule)
    }

    fun isBreakingShield(target: LivingEntity): Boolean = HitAuraModule.breakShield
        && ShieldBreakProcessor.isTargetBlocking(target) && ShieldBreakProcessor.findAxeInHotbar() >= 0

    private fun weaponSlot(target: LivingEntity, ticksAhead: Int = 0): Int {
        if (isBreakingShield(target)) return ShieldBreakProcessor.findAxeInHotbar()
        val mace = AutoMaceModule.readyMaceSlot(ticksAhead)
        if (mace >= 0) return mace
        if (BreachSwapModule.isActive()) return BreachSwapModule.findBreachMaceSlot()
        return mc.player?.inventory?.selectedSlot ?: -1
    }

    private fun smashReady(target: LivingEntity, ticksAhead: Int = 0): Boolean {
        if (isBreakingShield(target)) return false
        if (AutoMaceModule.isSmashReady(ticksAhead)) return true
        val player = mc.player ?: return false
        val slot = weaponSlot(target, ticksAhead)
        val stack = player.inventory.getItem(slot)
        return stack.`is`(Items.MACE) && !player.isFallFlying && player.fallDistance > 1.5
    }

    private fun canReach(target: LivingEntity, range: Double): Boolean {
        if (!inGame || HitAuraModule.shouldIgnoreContext()) return wait(WaitReason.CONTEXT)
        if (!ActionCoordinator.canAct(HitAuraModule)) return wait(WaitReason.PRIORITY)
        if (TpAuraModule.isPendingVaultReturn()) return wait(WaitReason.CONTEXT)
        if (TpAuraModule.getRenderPosition(target) != null) return true
        if (RotationUtil.getDistanceToBox(target) > range) return wait(WaitReason.RANGE)
        if ((HitAuraModule.wallCheck || !HitAuraModule.throughWalls)
            && RayTraceUtil.findVisiblePoint(target, range) == null) return wait(WaitReason.WALL)
        return true
    }

    private fun clickReady(target: LivingEntity, mode: ClickMode, cps: Double, randomize: Boolean,
                           spread: Double, ticksAhead: Int): Boolean {
        if (isBreakingShield(target)) return clickerProcessor.isReadyToBreakShield(ticksAhead) || wait(WaitReason.COOLDOWN)
        val forceMace = AutoMaceModule.isForceReady(target, ticksAhead)
        if (forceMace) return true
        if (!AutoMaceModule.isSmashReady(ticksAhead) && BreachSwapModule.isActive()
            && !BreachSwapModule.isVirtualCooldownReady(ticksAhead)) return wait(WaitReason.COOLDOWN)
        val player = mc.player ?: return false
        val slot = weaponSlot(target, ticksAhead)
        if (slot !in 0..8) return wait(WaitReason.SLOT)
        return clickerProcessor.isReadyToClick(mode, cps, randomize, spread, ticksAhead > 0,
            player.inventory.getItem(slot), ticksAhead) || wait(WaitReason.COOLDOWN)
    }

    fun shouldPrepareAttack(target: LivingEntity, attackRange: Double, clickMode: ClickMode,
        cps: Double, randomizeCps: Boolean, cpsSpread: Double, onlyCrits: Boolean, smartCrits: Boolean,
        ticksAhead: Int = 0, sprintResetMode: SprintResetMode = SprintResetMode.OFF): Boolean {
        if (!canReach(target, attackRange)
            || !clickReady(target, clickMode, cps, randomizeCps, cpsSpread, ticksAhead)) return false
        if (!isBreakingShield(target)) {
            val smash = smashReady(target, ticksAhead)
            val critical = if (ticksAhead > 0) criticalProcessor.shouldPrepareAttack(onlyCrits, smartCrits, smash)
                else criticalProcessor.isReadyForAttack(onlyCrits, smartCrits, sprintResetMode, smash)
            if (!critical) return wait(WaitReason.CRIT)
        }
        waitReason = WaitReason.READY
        return true
    }

    fun willClickNextTick(target: LivingEntity, attackRange: Double, clickMode: ClickMode,
        cps: Double, randomizeCps: Boolean, cpsSpread: Double): Boolean = canReach(target, attackRange)
        && clickReady(target, clickMode, cps, randomizeCps, cpsSpread, 1)

    /** Every helper uses this same slot decision, including axe priority over a mace. */
    fun prepareWeapon(target: LivingEntity, ticksAhead: Int = 0): Boolean {
        val slot = weaponSlot(target, ticksAhead)
        if (slot !in 0..8) return wait(WaitReason.SLOT)
        if (!SilentSwapUtil.prepareSlot(slot, 2, HitAuraModule)) return wait(WaitReason.SLOT)
        return true
    }

    fun prepareAttack(target: LivingEntity, range: Double): Boolean {
        if (!canReach(target, range) || !prepareWeapon(target)) return false
        macePreparation = if (isBreakingShield(target)) AutoMaceModule.AttackPreparation.NONE else AutoMaceModule.beforeAttack()
        return macePreparation != AutoMaceModule.AttackPreparation.WAITING || wait(WaitReason.SLOT)
    }

    fun tryAttack(target: LivingEntity, attackRange: Double, clickMode: ClickMode, cps: Double,
        randomizeCps: Boolean, cpsSpread: Double, onlyCrits: Boolean, smartCrits: Boolean,
        sprintResetMode: SprintResetMode, rayTrace: Boolean, throughWalls: Boolean): Boolean {
        if (!shouldPrepareAttack(target, attackRange, clickMode, cps, randomizeCps, cpsSpread,
                onlyCrits, smartCrits, 0, sprintResetMode)) return false
        val teleport = TpAuraModule.getRenderPosition(target) != null
        val rotationMode = HitAuraModule.rotationMode
        val neuro = rotationProcessor.getStrategy(rotationMode) as? NeuroRotation
        val advanced = rotationProcessor.getStrategy(rotationMode) as? AdvancedRotation
        val grim = rotationProcessor.getStrategy(rotationMode) as? Grim1204Rotation
        val walls = throughWalls && !HitAuraModule.wallCheck
        if (!geometryReady(target, attackRange, rayTrace, throughWalls)) return false
        val breakingShield = isBreakingShield(target)

        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val gameMode = mc.gameMode ?: return false
        var moved = false
        var previousAxeSlot = -1
        var previousBreachSlot = -1
        var executed = false
        var restoreRotation: Rotation? = null
        try {
            val preparation = macePreparation ?: if (breakingShield) AutoMaceModule.AttackPreparation.NONE else AutoMaceModule.beforeAttack()
            if (preparation == AutoMaceModule.AttackPreparation.WAITING) return wait(WaitReason.SLOT)
            val slot = weaponSlot(target)
            if (SilentSwapUtil.serverSlot != slot) return wait(WaitReason.SLOT)
            if (breakingShield) previousAxeSlot = ShieldBreakProcessor.swapToAxe(target)
            else if (preparation != AutoMaceModule.AttackPreparation.READY && BreachSwapModule.isActive())
                previousBreachSlot = BreachSwapModule.swapToBreachMace()

            moved = TpAuraModule.beforeAttack(target)
            if (teleport && !moved) return wait(WaitReason.CONTEXT)
            if (!canReach(target, attackRange)) return false
            if (mc.player !== player || mc.level !== level || level.getEntity(target.id) !== target || !target.isAlive)
                return wait(WaitReason.CONTEXT)
            if (!breakingShield) {
                if (MaceKillModule.isEnabled()) MaceKillModule.doCrit()
                if (!sprintResetProcessor.preAttack(sprintResetMode)) return wait(WaitReason.CRIT)
                // Recheck after equipment, teleport and sprint packets; a prediction is never authorization to hit.
                if (!criticalProcessor.isReadyForAttack(onlyCrits, smartCrits, SprintResetMode.OFF, smashReady(target)))
                    return wait(WaitReason.CRIT)
            }
            if (!serverRange(target, attackRange)) return wait(WaitReason.RANGE)
            if (grim != null) {
                val rotation = grimAttackRotation(target) ?: return wait(WaitReason.ROTATION)
                if (!lookingAt(target, attackRange, walls, rotation)) return wait(WaitReason.ROTATION)
                val previous = sentRotation?.copy() ?: Rotation(player.yRot, player.xRot)
                if (rotation != previous) {
                    restoreRotation = previous
                    if (!sendAccepted(player, ServerboundMovePlayerPacket.Rot(
                            rotation.yaw, rotation.pitch, player.onGround(), player.horizontalCollision)))
                        return wait(WaitReason.PRIORITY)
                }
            }
            // The actual accepted angle, rather than the prepared aim, authorizes the attack.
            if ((rayTrace || grim != null) && !lookingAt(target, attackRange, walls)) return wait(WaitReason.ROTATION)
            gameMode.ensureHasSentCarriedItem()
            executed = sendAccepted(player, ServerboundAttackPacket(target.id))
            if (!executed) return wait(WaitReason.PRIORITY)
            // Local effects and cooldown are applied only after all packet cancellation checks pass.
            player.attack(target)
            player.resetAttackStrengthTicker()
            player.swing(InteractionHand.MAIN_HAND, player.inventory.getItem(slot).attackAnimation, false)
            neuro?.notifyAttack()
            advanced?.notifyAttack()
            waitReason = WaitReason.ATTACK
            return true
        } finally {
            try {
                val restore = restoreRotation
                if (restore != null && sentRotation != restore && mc.player === player && mc.level === level
                    && player.connection.connection.isConnected) {
                    // Restore even if a listener cancels the attack after accepting the spoofed angle.
                    SilentPacketQueue.send(player.connection.connection, ServerboundMovePlayerPacket.Rot(
                        restore.yaw, restore.pitch, player.onGround(), player.horizontalCollision))
                }
            } finally {
                try {
                    if (moved) TpAuraModule.afterAttack()
                    if (executed) {
                        clickerProcessor.onAttackExecuted(clickMode, cps, randomizeCps, cpsSpread)
                        BreachSwapModule.onAttackExecuted()
                    }
                } finally {
                    try {
                        if (previousBreachSlot >= 0) BreachSwapModule.swapBack(previousBreachSlot)
                        if (previousAxeSlot >= 0) ShieldBreakProcessor.swapBack(previousAxeSlot)
                    } finally {
                        try {
                            macePreparation = null
                            AutoMaceModule.afterAttack(executed)
                        } finally {
                            sprintResetProcessor.postAttack(executed && !breakingShield)
                            if (executed) SilentSwapUtil.releasePreparedSlot(HitAuraModule)
                        }
                    }
                }
            }
        }
    }
}
