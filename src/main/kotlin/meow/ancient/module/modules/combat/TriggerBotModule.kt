package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.movement.GroundSpoofModule
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.world.entity.EntityType
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.AmbientCreature
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object TriggerBotModule : Module(
    name = "Trigger Bot",
    category = COMBAT,
    description = "Automatically attacks entities in crosshairs"
) {
    val distance = number("Distance", 3.0, 2.0, 6.0, 0.1, 1)
    val targets = targets("Targets", setOf("minecraft:player"))
    val raycastCheck = boolean("Raycast Check", true)
    val noWallHit = boolean("Through Walls", false)
    val pauseEating = boolean("Pause Eating", true)
    val onlyCriticals = boolean("Only Crits", true)
    val onlySpace = boolean("Only With Space", false)
    val breakShield = boolean("Break Shield", true)
    val forceBreakShield = boolean("Force Break Shield", true, isVisible = { breakShield.value })

    private val AXES: List<Item> = listOf(
        Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
        Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE
    )

    var target: LivingEntity? = null
        private set
    private var ticksToAttack = 0

    override fun onDisable() {
        ticksToAttack = 0
        target = null
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        if (HitAuraModule.isEnabled()) return

        if (ticksToAttack > 0) ticksToAttack--

        updateTarget()

        val currentTarget = target ?: return

        val player = mc.player ?: return
        if (canAttack(currentTarget)) {
            val previousSlot = swapToAxe(currentTarget)

            if (MaceKillModule.isEnabled()) {
                MaceKillModule.doCrit()
            }
            mc.gameMode?.attack(player, currentTarget)
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation, false)

            if (previousSlot != -1) {
                swapBack(previousSlot)
            }

            if (!AutoMaceModule.isForceReady(currentTarget) && !isForceBreakShieldReady(currentTarget)) {
                ticksToAttack = 10
            }
        }
    }

    private fun updateTarget() {
        val player = mc.player ?: run { target = null; return }
        val level = mc.level ?: run { target = null; return }

        var best: LivingEntity? = null
        var bestDist = Double.MAX_VALUE
        val eyePos = player.eyePosition
        val lookVec = player.lookAngle
        val reach = distance.value

        val targeted = mc.crosshairPickEntity
        if (targeted is LivingEntity && isValidEntity(targeted, reach)) {
            val dist = eyePos.distanceTo(targeted.position())
            if (dist <= reach && (noWallHit.value || player.hasLineOfSight(targeted))) {
                best = targeted
                bestDist = dist
            }
        }

        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || !isValidEntity(entity, reach)) continue

            val aimed = isRayTraceIntersecting(eyePos, lookVec, reach, entity.boundingBox.inflate(0.1))
            if (!aimed && raycastCheck.value) continue

            val dist = eyePos.distanceTo(entity.position())
            if (dist <= reach && dist < bestDist) {
                if (!noWallHit.value && !player.hasLineOfSight(entity)) continue
                best = entity
                bestDist = dist
            }
        }

        this.target = best
    }

    private fun isRayTraceIntersecting(start: Vec3, direction: Vec3, distance: Double, box: AABB): Boolean {
        val end = start.add(direction.scale(distance))
        return box.clip(start, end).isPresent
    }

    fun canAttack(target: LivingEntity): Boolean {
        val player = mc.player ?: return false

        val eating = player.isUsingItem && !player.useItem.`is`(Items.SHIELD)
        if (eating && pauseEating.value) return false
        if (!target.isAlive) return false

        val dist = player.eyePosition.distanceTo(target.position())
        if (dist > distance.value) return false

        if (!noWallHit.value && !player.hasLineOfSight(target)) return false

        if (raycastCheck.value && mc.crosshairPickEntity !== target) {
            val eyePos = player.eyePosition
            val lookVec = player.lookAngle
            if (!isRayTraceIntersecting(eyePos, lookVec, distance.value, target.boundingBox.inflate(0.1))) {
                return false
            }
        }

        if (isForceBreakShieldReady(target)) return canCritical()

        if (!AutoMaceModule.isForceReady(target)) {
            if (player.getAttackStrengthScale(0.5f) < 0.98f) return false
            if (ticksToAttack > 0) return false
        }

        return canCritical()
    }

    fun canCritical(): Boolean {
        if (!onlyCriticals.value) return true

        if (GroundSpoofModule.isEnabled() && GroundSpoofModule.mode == "False") return true
        val targetEntity = target
        if (targetEntity != null && AutoMaceModule.isForceReady(targetEntity)) return true

        val player = mc.player ?: return false
        if (onlySpace.value && !mc.options.keyJump.isDown) return false

        return player.fallDistance > 0.0f && !player.onGround() && !player.onClimbable() && !player.isInWater
    }

    private fun isValidEntity(entity: LivingEntity, reach: Double): Boolean {
        if (!entity.isAlive || entity === mc.player || entity is ArmorStand) return false
        val player = mc.player ?: return false

        if (!targets.isSelected(entity)) return false
        if (entity is Player && (entity.isCreative || entity.isSpectator)) return false

        return player.eyePosition.distanceTo(entity.position()) <= reach
    }

    fun isTargetBlocking(target: LivingEntity?): Boolean {
        if (target == null || !target.isUsingItem) return false
        return target.useItem.`is`(Items.SHIELD)
    }

    fun isForceBreakShieldReady(target: LivingEntity): Boolean {
        if (!breakShield.value || !forceBreakShield.value || !isTargetBlocking(target)) return false
        return findAxeInHotbar() != -1
    }

    fun swapToAxe(target: LivingEntity): Int {
        if (!breakShield.value || !isTargetBlocking(target)) return -1
        val axeSlot = findAxeInHotbar()
        val player = mc.player ?: return -1
        if (axeSlot == -1 || player.inventory.selectedSlot == axeSlot) return -1

        val previousSlot = player.inventory.selectedSlot
        selectSlot(axeSlot)
        return previousSlot
    }

    fun swapBack(previousSlot: Int) {
        if (previousSlot in 0..8) {
            selectSlot(previousSlot)
        }
    }

    fun findAxeInHotbar(): Int {
        val player = mc.player ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (AXES.any { stack.`is`(it) }) return i
        }
        return -1
    }

    private fun selectSlot(slot: Int) {
        val player = mc.player ?: return
        if (slot in 0..8) {
            player.inventory.selectedSlot = slot
            player.connection.send(ServerboundSetCarriedItemPacket(slot))
        }
    }
}
