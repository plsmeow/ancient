package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.TeleportPath
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.client.util.other.DamageUtils
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.world.entity.EntityType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.AttackRange
import net.minecraft.world.item.component.KineticWeapon
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object SpearKillModule : Module(
    name = "SpearKill",
    category = COMBAT,
    description = "Locks onto target and drives a raised spear towards it"
) {
    val modeSetting = mode("Mode", "Motion", listOf("Motion", "Teleport"))
    val mode by modeSetting

    val retry = boolean("Retry", false)
    val tpBack = boolean("TP Back", false, isVisible = { !retry.value })
    val onlyInstKill = boolean("Only InstKill", false, isVisible = { mode == "Teleport" })
    val damageNotify = boolean("Damage Notify", true)

    val teleportTypeSetting = mode("Teleport Type", "Step", listOf("Step", "Vault", "Bypass"), isVisible = { mode == "Teleport" })
    val teleportType by teleportTypeSetting

    val stepDistance = number("Step Distance", 4.0, 0.1, 10.0, 0.1, 1, isVisible = { mode == "Teleport" && teleportType == "Step" })
    val bypassPackets = number("Bypass Packets", 10.0, 1.0, 50.0, 1.0, 0, isVisible = { mode == "Teleport" && teleportType == "Bypass" })
    val bypassOnGround = boolean("Bypass On Ground", true, isVisible = { mode == "Teleport" && teleportType == "Bypass" })

    val range = number("Range", 32.0, 1.0, 100.0, 1.0, 0)
    val fov = number("FOV", 180.0, 1.0, 360.0, 1.0, 0)

    val splitSpeed = boolean("Split Speed", false, isVisible = { mode == "Motion" })
    val speed = number("Speed", 8.0, 0.1, 20.0, 0.1, 1, isVisible = { mode == "Motion" && !splitSpeed.value })
    val speedXZ = number("Speed XZ", 8.0, 0.0, 20.0, 0.1, 1, isVisible = { mode == "Motion" && splitSpeed.value })
    val speedY = number("Speed Y", 8.0, 0.0, 20.0, 0.1, 1, isVisible = { mode == "Motion" && splitSpeed.value })

    val silent = boolean("Silent", false)
    val prediction = boolean("Prediction", true)
    val predictionTicks = number("Prediction Ticks", 2.0, 0.0, 10.0, 0.1, 1, isVisible = { prediction.value })
    val targets = targets("Targets", setOf("minecraft:player"))


    enum class Phase { Approach, Strike, Return, Finished }

    var target: Entity? = null
        private set
    var targetBox: AABB? = null
        private set
    private var predictionOffset = Vec3.ZERO
    private var usedHand: InteractionHand? = null
    private var lastUseTicks: Int = 0
    private var startPos: Vec3? = null
    private var phase: Phase = Phase.Approach
    private var arrivalTick: Int = 0
    var motion: Vec3? = null
        private set
    private var pendingTeleport: Vec3? = null
    var lockedYaw: Float = 0f
        private set
    var lockedPitch: Float = 0f
        private set

    private var lastTarget: Entity? = null
    private var lastTargetTick: Int = 0
    private var strikeTarget: Entity? = null
    private var lastStrikeTick: Int = 0
    private var damageTarget: Entity? = null
    private var pendingDamage: Float = 0f
    private var pendingDeath: Boolean = false
    private var ownSpearDamage: Boolean = false
    private var mixedDamage: Boolean = false
    private var killNotified: Boolean = false
    private var damageExpiryTick: Int = 0

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        reset()
        clearDamageNotification()
        lastTarget = null
        strikeTarget = null
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        reset()
        clearDamageNotification()
        lastTarget = null
        strikeTarget = null
    }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        if (!inGame || !damageNotify.value) return
        val player = mc.player ?: return
        val level = mc.level ?: return
        val packet = event.packet

        if (packet is ClientboundDamageEventPacket) {
            val entity = level.getEntity(packet.entityId) ?: return
            val isTarget = entity === target || entity === damageTarget
                || (entity === lastTarget && player.tickCount <= lastTargetTick + 15)
            if (!isTarget) return

            if (damageTarget !== entity || player.tickCount > damageExpiryTick) {
                clearDamageNotification()
                damageTarget = entity
                damageExpiryTick = player.tickCount + 5
            }
            if (packet.sourceType.`is`(DamageTypes.SPEAR)) {
                ownSpearDamage = true
            } else {
                mixedDamage = true
            }
        } else if (packet is ClientboundEntityEventPacket) {
            val entity = packet.getEntity(level) ?: return
            val isTarget = entity === target || entity === damageTarget
                || (entity === lastTarget && player.tickCount <= lastTargetTick + 15)
            if (!isTarget) return

            if (packet.eventId.toInt() == EntityEvent.DEATH.toInt()) {
                pendingDeath = true
                if (entity is LivingEntity && (entity.health <= 0 || entity.isDeadOrDying)) {
                    notifySpearKill()
                }
                clearDamageNotification()
            }
        } else if (packet is ClientboundSetEntityDataPacket) {
            val entity = level.getEntity(packet.id) ?: return
            val isTarget = entity === target || entity === damageTarget
                || (entity === lastTarget && player.tickCount <= lastTargetTick + 15)
            if (!isTarget) return

            for (item in packet.packedItems) {
                if (item.id == 9) {
                    val health = item.value as? Float ?: continue
                    if (health <= 0) {
                        pendingDeath = true
                        notifySpearKill()
                        clearDamageNotification()
                    }
                }
            }
        }
    }

    private fun notifySpearKill() {
        if (!ownSpearDamage || mixedDamage || killNotified || damageTarget == null) return
        ChatUtil.send("Killed ${damageTarget?.name?.string}")
        killNotified = true
    }

    private fun clearDamageNotification() {
        damageTarget = null
        pendingDamage = 0f
        pendingDeath = false
        ownSpearDamage = false
        mixedDamage = false
        killNotified = false
        damageExpiryTick = 0
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return

        if (damageTarget != null) {
            if (player.tickCount > damageExpiryTick || !damageNotify.value) {
                clearDamageNotification()
            } else if (pendingDeath && (ownSpearDamage || (damageTarget === strikeTarget && player.tickCount <= lastStrikeTick + 5)) && !mixedDamage && !killNotified) {
                ownSpearDamage = true
                notifySpearKill()
                clearDamageNotification()
            }
        }

        if (!trackSpearUse()) return

        if (phase == Phase.Return) {
            if (retry.value && (target == null || !targetPresent(target!!))) {
                reset()
                return
            }
            returnToStart()
            return
        }

        if (target != null && !targetPresent(target!!)) {
            if (phase == Phase.Strike && tpBack.value && !retry.value) {
                phase = Phase.Return
                returnToStart()
            } else {
                reset()
            }
            return
        }

        if (target == null) {
            target = findTarget()
        }
        val currentTarget = target ?: return

        lastTarget = currentTarget
        lastTargetTick = player.tickCount

        var spear = player.useItem
        val kinetic = spear.get(DataComponents.KINETIC_WEAPON)
        if (ready() && kinetic != null) {
            val attackRange = player.getAttackRangeWith(spear)
            val reach = attackRange.effectiveMaxRange(player) + 2.0
            if (player.distanceTo(currentTarget) <= reach || phase == Phase.Strike) {
                strikeTarget = currentTarget
                lastStrikeTick = player.tickCount
            }
        }

        updatePrediction()
        lockAim()

        if (phase == Phase.Strike) {
            if ((retry.value || tpBack.value) && player.tickCount > arrivalTick) {
                phase = Phase.Return
                returnToStart()
            }
            return
        }

        if (phase == Phase.Finished) return

        if (!movementReady()) {
            TeleportPath.cancel(this)
            pendingTeleport = null
            if (motion != null) player.deltaMovement = Vec3.ZERO
            motion = null
            return
        }

        spear = player.useItem
        when (mode) {
            "Motion" -> {
                val destination = straightStrikePosition(spear)
                if (destination != null) {
                    if (startPos == null && phase == Phase.Approach) startPos = player.position()
                    if (player.position().distanceToSqr(destination) < 0.25) {
                        arrived()
                    } else {
                        moveTowards(destination)
                    }
                } else {
                    if (motion != null) player.deltaMovement = Vec3.ZERO
                    motion = null
                }
            }
            "Teleport" -> updateTeleportDestination(spear)
        }
        lockAim()
    }

    fun shouldOverrideMovement(): Boolean {
        if (!isEnabled() || !inGame || mode != "Motion" || motion == null) return false
        return if (phase == Phase.Return) usingSpear() else movementReady()
    }

    private fun moveTowards(destination: Vec3) {
        val player = mc.player ?: return
        val delta = destination.subtract(player.position())
        if (splitSpeed.value) {
            val horizontalDistance = delta.horizontalDistance()
            val horizontalScale = if (horizontalDistance < 1.0E-6) 0.0 else min(speedXZ.value, horizontalDistance) / horizontalDistance
            motion = Vec3(delta.x * horizontalScale, Mth.clamp(delta.y, -speedY.value, speedY.value), delta.z * horizontalScale)
        } else {
            val distance = delta.length()
            motion = if (distance < 1.0E-6) Vec3.ZERO else delta.scale(min(speed.value, distance) / distance)
        }
        player.deltaMovement = motion ?: Vec3.ZERO
    }

    private fun arrived() {
        val player = mc.player ?: return
        if (motion != null) player.deltaMovement = Vec3.ZERO
        motion = null
        if (phase == Phase.Return) {
            phase = if (retry.value) Phase.Approach else Phase.Finished
            startPos = null
        } else {
            phase = Phase.Strike
            arrivalTick = player.tickCount
            target?.let {
                strikeTarget = it
                lastStrikeTick = player.tickCount
            }
        }
    }

    private fun returnToStart() {
        val target = target
        if (target != null && targetPresent(target)) {
            updatePrediction()
            lockAim()
        }
        val currentStart = startPos ?: run {
            reset()
            return
        }
        val player = mc.player ?: return
        if (mode == "Motion") {
            if (player.position().distanceToSqr(currentStart) < 0.25) {
                arrived()
            } else {
                moveTowards(currentStart)
            }
        } else if (!TeleportPath.active(this)) {
            pendingTeleport = currentStart
            if (teleport(currentStart)) pendingTeleport = null
        }
    }

    private fun trackSpearUse(): Boolean {
        if (!usingSpear()) {
            reset()
            return false
        }
        val player = mc.player ?: return false
        val ticks = player.ticksUsingItem
        val hand = player.usedItemHand
        if (usedHand != hand || ticks < lastUseTicks) reset()
        usedHand = hand
        lastUseTicks = ticks
        return true
    }

    private fun usingSpear(): Boolean {
        val player = mc.player ?: return false
        if (!player.isAlive || player.isPassenger) return false
        if (mc.cameraEntity !== player || !player.isUsingItem) return false
        val spear = player.useItem
        val kinetic = spear.get(DataComponents.KINETIC_WEAPON)
        return spear.`is`(ItemTags.SPEARS) && kinetic != null && kinetic.damageConditions().isPresent
    }

    private fun ready(): Boolean {
        val player = mc.player ?: return false
        if (!player.isAlive || player.isPassenger) return false
        if (mc.cameraEntity !== player || !player.isUsingItem) return false
        val spear = player.useItem
        if (!spear.`is`(ItemTags.SPEARS)) return false
        val kinetic = spear.get(DataComponents.KINETIC_WEAPON) ?: return false
        if (kinetic.damageConditions().isEmpty) return false
        val ticks = player.ticksUsingItem
        return ticks >= kinetic.delayTicks() && ticks <= kinetic.computeDamageUseDuration()
    }

    private fun movementReady(): Boolean {
        if (!usingSpear()) return false
        val player = mc.player ?: return false
        val kinetic = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return false
        val ticks = player.ticksUsingItem
        return ticks >= kinetic.delayTicks() && (retry.value || ticks <= kinetic.computeDamageUseDuration())
    }

    private fun targetPresent(entity: Entity): Boolean {
        val level = mc.level ?: return false
        return entity.level() === level && !entity.isRemoved && entity.isAlive
            && (entity !is LivingEntity || !entity.isDeadOrDying)
    }

    private fun validTarget(entity: Entity): Boolean {
        val player = mc.player ?: return false
        if (entity === player || entity === mc.cameraEntity || !targetPresent(entity)) return false
        if (!targets.isSelected(entity)) return false
        if (player.distanceToSqr(entity) > range.value * range.value) return false
        if (entity is Player && (entity.isCreative || entity.isSpectator)) return false
        return true
    }

    private fun canSelect(entity: Entity): Boolean {
        if (!validTarget(entity)) return false
        val player = mc.player ?: return false
        val direction = entity.boundingBox.center.subtract(player.eyePosition).normalize()
        val cosine = Mth.clamp(player.getViewVector(1f).dot(direction), -1.0, 1.0)
        return cosine >= cos(Math.toRadians(fov.value * 0.5))
    }

    private fun findTarget(): Entity? {
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        var best: Entity? = null
        var bestAngle = Double.MAX_VALUE
        for (entity in level.entitiesForRendering()) {
            if (!canSelect(entity)) continue
            val directionVec = entity.boundingBox.center.subtract(player.eyePosition).normalize()
            val dot = Mth.clamp(player.getViewVector(1f).dot(directionVec), -1.0, 1.0)
            val angle = acos(dot)
            if (angle < bestAngle) {
                bestAngle = angle
                best = entity
            }
        }
        return best
    }

    private fun straightStrikePosition(spear: ItemStack): Vec3? {
        val player = mc.player ?: return null
        val box = targetBox ?: return null
        val origin = player.eyePosition
        val center = box.center
        val toCenter = center.subtract(origin)
        val dist = toCenter.length()
        val direction = if (dist < 1.0E-6) Vec3.directionFromRotation(lockedPitch, lockedYaw) else toCenter.scale(1.0 / dist)

        val attackRange = player.getAttackRangeWith(spear)
        val minReach = attackRange.effectiveMinRange(player)
        val maxReach = attackRange.effectiveMaxRange(player)
        val reach = (minReach + maxReach) * 0.5

        val targetEntity = target ?: return null
        val rayLength = range.value + box.xsize + box.ysize + box.zsize + 10.0
        val hit = box.clip(origin, origin.add(direction.scale(rayLength))).orElse(null)
        val eye = if (hit != null) hit.subtract(direction.scale(reach)) else center.subtract(direction.scale(reach + targetEntity.bbWidth * 0.5))
        return eye.add(0.0, -player.eyeHeight.toDouble(), 0.0)
    }

    private fun straightTeleportPosition(spear: ItemStack): Vec3? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val box = targetBox ?: return null
        val origin = player.eyePosition
        if (box.contains(origin)) return null

        val position = straightStrikePosition(spear) ?: return null
        val body = player.boundingBox.move(position.subtract(player.position()))
        if (body.intersects(box) || !level.noCollision(player, body)) return null
        if (teleportType == "Vault" && !level.noCollision(player, body.move(0.0, 0.01, 0.0))) return null
        return position
    }

    private fun canInstKill(destination: Vec3): Boolean {
        val targetEntity = target ?: return false
        if (targetEntity !is LivingEntity || !targetPresent(targetEntity) || !ready()) return false
        val player = mc.player ?: return false
        if (targetEntity.mainHandItem.has(DataComponents.DEATH_PROTECTION) || targetEntity.offhandItem.has(DataComponents.DEATH_PROTECTION)) return false

        var origin = player.position()
        var ticksUntilStrike = 1
        if (teleportType == "Vault") {
            origin = Vec3(destination.x, origin.y + 129.0, destination.z)
            ticksUntilStrike = 3
        }
        var movement = destination.subtract(origin)
        if (teleportType == "Step") {
            val distance = movement.length()
            ticksUntilStrike = maxOf(1, ceil(distance / stepDistance.value).toInt())
            movement = if (distance < 1.0E-6) Vec3.ZERO else movement.scale((distance - (ticksUntilStrike - 1) * stepDistance.value) / distance)
        }
        val box = targetBox ?: return false
        val direction = box.center.subtract(destination.add(0.0, player.eyeHeight.toDouble(), 0.0)).normalize()
        val damage = DamageUtils.getSpearDamage(player, targetEntity, player.useItem, movement, direction, player.ticksUsingItem + ticksUntilStrike)
        val health = targetEntity.health + targetEntity.absorptionAmount
        return health.isFinite() && health > 0 && damage >= health
    }

    private fun updateTeleportDestination(spear: ItemStack) {
        val destination = straightTeleportPosition(spear) ?: straightStrikePosition(spear)
        if (destination == null) {
            if (TeleportPath.active(this)) TeleportPath.cancel(this)
            pendingTeleport = null
            return
        }
        pendingTeleport = destination
        if (!TeleportPath.active(this)) {
            if (teleport(destination)) {
                pendingTeleport = null
            }
        } else {
            TeleportPath.retarget(this, destination)
            pendingTeleport = null
        }
    }

    private fun teleport(position: Vec3): Boolean {
        if (phase != Phase.Return && onlyInstKill.value && !canInstKill(position)) return false
        val type = when (teleportType) {
            "Step" -> TeleportPath.Type.Step
            "Vault" -> TeleportPath.Type.Vault
            else -> TeleportPath.Type.Bypass
        }
        val player = mc.player ?: return false
        val origin = player.position()
        val started = TeleportPath.start(
            this,
            position,
            type,
            bypassPackets.value.toInt(),
            if (teleportType == "Step") stepDistance.value else 0.0,
            teleportType == "Bypass" && bypassOnGround.value,
            lockedYaw,
            lockedPitch
        ) {
            arrived()
        }
        if (started && phase != Phase.Return && startPos == null) {
            startPos = origin
        }
        return started
    }

    private fun updatePrediction() {
        val targetEntity = target ?: return
        predictionOffset = if (prediction.value) {
            targetEntity.position().subtract(targetEntity.oldPosition()).scale(predictionTicks.value)
        } else {
            Vec3.ZERO
        }
        targetBox = targetEntity.boundingBox.move(predictionOffset)
    }

    private fun updateAim() {
        val box = targetBox ?: return
        val player = mc.player ?: return
        val aim = box.center
        val deltaX = aim.x - player.x
        val deltaY = aim.y - player.eyeY
        val deltaZ = aim.z - player.z
        val horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)

        val targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ)).toFloat()
        val targetPitch = Math.toDegrees(-Math.atan2(deltaY, horizontalDistance)).toFloat()

        val baseYaw = if (silent.value) RotationManager.getServerRotation().yaw else player.yRot
        val basePitch = if (silent.value) RotationManager.getServerRotation().pitch else player.xRot

        val fixed = GCDUtil.applyGCD(Rotation(targetYaw, targetPitch), Rotation(baseYaw, basePitch))
        lockedYaw = fixed.yaw
        lockedPitch = fixed.pitch
    }

    private fun lockAim() {
        updateAim()
        TeleportPath.aim(this, lockedYaw, lockedPitch)
        if (silent.value) {
            RotationManager.setRotation(Rotation(lockedYaw, lockedPitch), clientLook = false)
        } else {
            val player = mc.player ?: return
            player.yRot = lockedYaw
            player.xRot = lockedPitch
            player.yHeadRot = lockedYaw
            player.yBodyRot = lockedYaw
        }
    }

    private fun reset() {
        TeleportPath.cancel(this)
        val player = mc.player
        if (motion != null && player != null) {
            player.deltaMovement = Vec3.ZERO
        }
        target = null
        targetBox = null
        predictionOffset = Vec3.ZERO
        usedHand = null
        lastUseTicks = 0
        startPos = null
        phase = Phase.Approach
        arrivalTick = 0
        motion = null
        pendingTeleport = null
    }
}
