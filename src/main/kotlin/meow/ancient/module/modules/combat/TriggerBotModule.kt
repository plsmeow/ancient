package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerMovementSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule.ClickMode
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import meow.ancient.module.modules.combat.hitaura.ClickerProcessor
import meow.ancient.module.modules.combat.hitaura.CriticalProcessor
import meow.ancient.module.modules.combat.hitaura.ShieldBreakProcessor
import meow.ancient.module.modules.combat.hitaura.SprintResetProcessor
import meow.ancient.module.modules.combat.hitaura.TargetSelector
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import sweetie.evaware.flora.api.Commando

object TriggerBotModule : Module(
    name = "Trigger Bot",
    category = COMBAT,
    description = "Attacks the nearest entity intersecting your view before movement without rotating"
) {
    val targets = targets("Targets", setOf("minecraft:player"))
    val ignoreOn by multiMode("Ignore On", setOf("Gui"), listOf("Use", "Gui"))
    val friendProtection by boolean("Friend Protection", true)
    val attackRange by number("Attack Range", 3.0, 1.0, 6.0, 0.05, 2, unit = "m")
    val throughWalls by boolean("Through Walls", false)
    val breakShield by boolean("Break Shield", true)
    val clickMode by enum("Click Mode", ClickMode.MODE_1_9, nameMapper = { it.displayName })
    val cps by number("CPS", 12.0, 1.0, 20.0, 0.5, 1, isVisible = { clickMode == ClickMode.MODE_1_8 })
    val cpsRandomization by boolean("Randomization", true, isVisible = { clickMode == ClickMode.MODE_1_8 })
    val cpsSpread by number(
        "Spread", 2.0, 0.5, 5.0, 0.5, 1,
        isVisible = { clickMode == ClickMode.MODE_1_8 && cpsRandomization }
    )
    val onlyCrits by boolean("Only Crits", true,
        description = "Wait for at least 0.08 blocks of descent or ready packet crits; allow attacks when crits are impossible")
    val smartCrits by boolean("Smart Crits", true,
        description = "Allow ground attacks without holding jump; disabling waits for a real fall when crits are possible",
        isVisible = { onlyCrits })

    private val targetSelector = TargetSelector()
    private val clickerProcessor = ClickerProcessor()
    private val criticalProcessor = CriticalProcessor()
    private val sprintResetProcessor = SprintResetProcessor()
    private var pendingPlayer: LocalPlayer? = null
    private var pendingLevel: ClientLevel? = null
    private var pendingTick = -1

    var target: LivingEntity? = null
        private set

    private fun clearPendingAttack() {
        pendingPlayer = null
        pendingLevel = null
        pendingTick = -1
    }

    private fun reset() {
        clearPendingAttack()
        target = null
        targetSelector.reset()
        clickerProcessor.reset()
        criticalProcessor.reset()
        sprintResetProcessor.reset()
    }

    override fun onEnable() = reset()

    override fun onDisable() = reset()

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    fun shouldPreventSprint(): Boolean =
        isEnabled() && !HitAuraModule.isEnabled() && sprintResetProcessor.isResettingSprint()

    private fun shouldIgnoreContext(player: LocalPlayer): Boolean =
        "Gui" in ignoreOn && currentScreen != null
            || "Use" in ignoreOn && player.isUsingItem && player.useItem.useAnimation != ItemUseAnimation.NONE

    private fun canUseContext(player: LocalPlayer, level: ClientLevel): Boolean =
        isEnabled() && !HitAuraModule.isEnabled() && mc.player === player && mc.level === level
            && !player.isDeadOrDying && !player.isSpectator && player.connection.connection.isConnected
            && player.connection.hasClientLoaded() && mc.gameMode != null && !shouldIgnoreContext(player)

    private fun selectTarget(player: LocalPlayer, level: ClientLevel): LivingEntity? {
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(attackRange))
        val blockHit = if (throughWalls) null else level.clip(
            ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        )
        val blockDistanceSquared = if (blockHit != null && blockHit.type != HitResult.Type.MISS) {
            start.distanceToSqr(blockHit.location)
        } else Double.POSITIVE_INFINITY
        var nearest: Entity? = null
        var nearestDistanceSquared = attackRange * attackRange
        for (entity in level.entitiesForRendering()) {
            if (entity === player || entity.isRemoved || entity.isSpectator || !entity.isPickable) continue
            if (player.isPassenger && entity.rootVehicle === player.rootVehicle) continue
            val box = entity.boundingBox
            val distanceSquared = if (box.contains(start)) {
                0.0
            } else {
                val intersection = box.clip(start, end)
                if (intersection.isEmpty) continue
                start.distanceToSqr(intersection.get())
            }
            if (distanceSquared >= blockDistanceSquared || distanceSquared > nearestDistanceSquared) continue
            if (nearest != null && distanceSquared == nearestDistanceSquared && entity.id > nearest.id) continue
            nearest = entity
            nearestDistanceSquared = distanceSquared
        }
        val selected = nearest as? LivingEntity ?: return null
        if (selected is Player && selected.isCreative) return null
        if (!targetSelector.isValidTarget(selected, attackRange, 180.0f, targets, true, friendProtection)) return null
        return selected
    }

    private fun shouldUseAxe(target: LivingEntity): Boolean =
        breakShield && ShieldBreakProcessor.isTargetBlocking(target) && ShieldBreakProcessor.findAxeInHotbar() != -1

    private fun isReadyForAttack(target: LivingEntity, ticksAhead: Int = 0): Boolean {
        val useAxe = shouldUseAxe(target)
        if (!useAxe && BreachSwapModule.isActive() && !BreachSwapModule.isVirtualCooldownReady(ticksAhead)) return false
        if ((useAxe || !AutoMaceModule.isForceReady(target))
            && !clickerProcessor.isReadyToClick(clickMode, cps, cpsRandomization, cpsSpread, ticksAhead > 0)) return false
        return if (ticksAhead > 0) criticalProcessor.shouldPrepareAttack(onlyCrits, smartCrits)
            else criticalProcessor.isReadyForAttack(onlyCrits, smartCrits)
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        clearPendingAttack()
        target = null
        val sprintMode = SprintResetMode.fromName(AnticheatManager.sprintReset)
        sprintResetProcessor.prepareAttack(sprintMode, false)
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!canUseContext(player, level)) {
            sprintResetProcessor.reset()
            return
        }
        val selected = selectTarget(player, level)
        if (selected == null) {
            sprintResetProcessor.reset()
            return
        }
        target = selected
        sprintResetProcessor.prepareAttack(
            sprintMode,
            criticalProcessor.shouldStopSprinting(
                onlyCrits,
                clickerProcessor.isReadyToClick(clickMode, cps, cpsRandomization, cpsSpread, true)
            )
        )
        pendingPlayer = player
        pendingLevel = level
        pendingTick = player.tickCount
    }

    @Commando
    fun onPreMovement(event: PlayerPreMovementEvent) {
        val expectedPlayer = pendingPlayer
        val expectedLevel = pendingLevel
        val expectedTarget = target
        val expectedTick = pendingTick
        clearPendingAttack()
        try {
            if (expectedPlayer == null || expectedLevel == null) return
            if (!canUseContext(expectedPlayer, expectedLevel)
                || expectedPlayer.tickCount != expectedTick && expectedPlayer.tickCount != expectedTick + 1) {
                target = null
                return
            }
            val selected = selectTarget(expectedPlayer, expectedLevel)
            target = selected
            if (selected == null || selected !== expectedTarget || expectedLevel.getEntity(selected.id) !== selected) return
            if (!isReadyForAttack(selected)) return
            attack(expectedPlayer, expectedLevel, selected)
        } finally {
            if (HitAuraModule.isEnabled()) sprintResetProcessor.reset()
            else sprintResetProcessor.onPreMovement()
        }
    }


    private fun attack(player: LocalPlayer, level: ClientLevel, target: LivingEntity) {
        val gameMode = mc.gameMode ?: return
        val mode = clickMode
        val attackCps = cps
        val randomize = cpsRandomization
        val spread = cpsSpread
        val sprintMode = SprintResetMode.fromName(AnticheatManager.sprintReset)
        val useAxe = shouldUseAxe(target)
        val forceMaceReady = !useAxe && AutoMaceModule.isForceReady(target)
        if (!sprintResetProcessor.preAttack(sprintMode)) return
        var previousAxeSlot = -1
        var previousBreachSlot = -1
        var executed = false
        try {
            val preparation = AutoMaceModule.beforeAttack()
            if (preparation == AutoMaceModule.AttackPreparation.WAITING
                || forceMaceReady && preparation != AutoMaceModule.AttackPreparation.READY) return
            if (useAxe) {
                previousAxeSlot = ShieldBreakProcessor.swapToAxe(target)
                val axeSlot = ShieldBreakProcessor.findAxeInHotbar()
                if (axeSlot == -1 || player.inventory.selectedSlot != axeSlot) return
            } else if (BreachSwapModule.isActive()) {
                previousBreachSlot = BreachSwapModule.swapToBreachMace()
            }
            if (!canUseContext(player, level) || mc.gameMode !== gameMode
                || level.getEntity(target.id) !== target || selectTarget(player, level) !== target) return
            if (!criticalProcessor.isReadyForAttack(onlyCrits, smartCrits)) return
            if (MaceKillModule.isEnabled()) MaceKillModule.doCrit()

            if (!canUseContext(player, level) || mc.gameMode !== gameMode
                || level.getEntity(target.id) !== target) return
            val animation = player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation
            if (mode == ClickMode.MODE_1_8) {
                player.swing(InteractionHand.MAIN_HAND, animation, false)
                gameMode.attack(player, target)
                executed = true
            } else {
                gameMode.attack(player, target)
                executed = true
                player.swing(InteractionHand.MAIN_HAND, animation, false)
            }
        } finally {
            try {
                if (executed) {
                    clickerProcessor.onAttackExecuted(mode, attackCps, randomize, spread)
                    BreachSwapModule.onAttackExecuted()
                }
            } finally {
                try {
                    if (mc.player === player && previousBreachSlot != -1) {
                        if (player.connection.connection.isConnected) {
                            BreachSwapModule.swapBack(previousBreachSlot)
                        } else {
                            player.inventory.selectedSlot = previousBreachSlot
                        }
                    }
                } finally {
                    try {
                        if (mc.player === player && previousAxeSlot != -1) ShieldBreakProcessor.swapBack(previousAxeSlot)
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
}
