package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.extensions.movementForward
import meow.ancient.client.extensions.movementSideways
import meow.ancient.client.util.movement.EntityExtrapolation
import meow.ancient.client.util.movement.HvhTargetPredict
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object SpeedModule : Module(
    name = "Speed",
    category = MOVEMENT,
    description = "Increases player movement speed in various modes"
) {
    val mode by mode(
        name = "Mode",
        value = "Contact",
        modes = listOf("Contact", "Vulcan", "Vanilla", "Polar", "MetaHVH")
    )

    val boost by number("Boost", 8.0, 1.0, 20.0, 0.1, 1, isVisible = { mode == "Contact" })
    val targetRange by number("Target Range", 3.0, 0.5, 10.0, 0.1, 1, isVisible = { mode == "Contact" })
    val contactRange by number("Contact Range", 0.5, 0.1, 2.0, 0.1, 1, isVisible = { mode == "Contact" })
    val playersOnly by boolean("Players Only", true, isVisible = { mode == "Contact" })
    val contactOnlyWhileMoving by boolean("Only While Moving", true, isVisible = { mode == "Contact" })
    val onlyWithAura by boolean("Only With Aura", false, isVisible = { mode == "Contact" })
    val predict by boolean("Predict", true, isVisible = { mode == "Contact" })
    val predictStrength by number("Predict", 2.0, 0.1, 10.0, 0.1, 1, isVisible = { mode == "Contact" && predict })

    val vulcanOnlyWhileMoving by boolean("Only While Moving", true, isVisible = { mode == "Vulcan" })

    val polarDive by boolean("Rotation", false, isVisible = { mode == "Polar" })

    val vanillaSpeed by number("Speed", 1.18, 1.05, 20.0, 0.05, 2, isVisible = { mode == "Vanilla" })
    val waterFix by boolean("Water Fix", false, isVisible = { mode == "Vanilla" })
    val hvhTarget by boolean("HvH Target", false, isVisible = { mode == "Vanilla" })
    val hvhPredictMode by mode("Predict Mode", "Simple", listOf("Simple", "Extrapolation"), isVisible = { mode == "Vanilla" && hvhTarget })
    val hvhPredictStrength by number("Predict", 4.0, 0.5, 20.0, 0.1, 1, isVisible = { mode == "Vanilla" && hvhTarget })
    val hvhRender by boolean("Render Predict", true, isVisible = { mode == "Vanilla" && hvhTarget })
    val leave by boolean("Leave", false, isVisible = { mode == "Vanilla" && hvhTarget })
    val leaveDistance by number("Leave Dist", 8.0, 4.0, 20.0, 0.5, 1, unit = "b", isVisible = { mode == "Vanilla" && hvhTarget && leave })
    val attackDistance by number("Attack Dist", 4.0, 1.0, 6.0, 0.1, 1, unit = "b", isVisible = { mode == "Vanilla" && hvhTarget && leave })

    val metaHvhSpeed by number("Base Speed", 0.36, 0.1, 1.0, 0.01, 2, isVisible = { mode == "MetaHVH" })
    val metaHvhTarget by boolean("HvH Target", false, isVisible = { mode == "MetaHVH" })
    val metaHvhPredictStrength by number("Predict", 4.0, 0.5, 20.0, 0.1, 1, isVisible = { mode == "MetaHVH" && metaHvhTarget })
    val hvhSearchRange by number(
        "HVH Search Range", 200.0, 1.0, 200.0, 1.0, 0, unit = "b",
        isVisible = { (mode == "Vanilla" && hvhTarget) || (mode == "MetaHVH" && metaHvhTarget) }
    )

    private const val VANILLA_DEFAULT_SPEED = 0.2873
    private const val HVH_TARGET_ZONE = 0.6

    private var lastHvhTarget: LivingEntity? = null
    private var renderTarget: LivingEntity? = null
    private var renderPredicted: Vec3? = null
    private var renderLeaveTarget: Vec3? = null
    private var polarLastSlot = -1
    private var renderRegistered = false

    private val extrapolator = EntityExtrapolation()

    fun targetSearchRange(): Double =
        if (isEnabled() && ((mode == "Vanilla" && hvhTarget) || (mode == "MetaHVH" && metaHvhTarget))) {
            hvhSearchRange
        } else {
            0.0
        }

    override fun onEnable() {
        super.onEnable()
        if (!renderRegistered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (!isEnabled() || !inGame || mode != "Vanilla" || !hvhTarget || !hvhRender) return@register
                val target = renderTarget ?: return@register
                val predicted = renderPredicted ?: return@register
                if (!target.isAlive) return@register

                val player = mc.player ?: return@register
                val halfWidth = target.bbWidth / 2.0
                val hitbox = AABB(
                    predicted.x - halfWidth, predicted.y, predicted.z - halfWidth,
                    predicted.x + halfWidth, predicted.y + target.bbHeight, predicted.z + halfWidth
                )

                val boxStyle = GizmoStyle.stroke(Color4b(90, 140, 255, 200).argb, 2.0f)
                Gizmos.cuboid(hitbox, boxStyle)
                Gizmos.line(player.position(), predicted, Color4b(90, 140, 255, 200).argb, 2.0f)

                val leavePos = renderLeaveTarget
                if (leavePos != null) {
                    val leaveBox = AABB(
                        leavePos.x - 0.3, leavePos.y - 0.3, leavePos.z - 0.3,
                        leavePos.x + 0.3, leavePos.y + 0.3, leavePos.z + 0.3
                    )
                    val leaveStyle = GizmoStyle.stroke(Color4b(255, 120, 90, 220).argb, 2.0f)
                    Gizmos.cuboid(leaveBox, leaveStyle)
                    Gizmos.line(player.position(), leavePos, Color4b(255, 120, 90, 220).argb, 2.0f)
                }
            }
            renderRegistered = true
        }
    }

    override fun onDisable() {
        HvhTargetPredict.reset(lastHvhTarget)
        lastHvhTarget = null
        renderTarget = null
        renderPredicted = null
        renderLeaveTarget = null
        extrapolator.clear()

        val player = mc.player
        if (polarLastSlot != -1 && player != null) {
            player.inventory.selectedSlot = polarLastSlot
        }
        polarLastSlot = -1
        RotationManager.resetImmediate()
        super.onDisable()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        when (mode) {
            "Vulcan" -> handleVulcan()
            "Vanilla" -> handleVanilla()
            "Polar" -> handlePolar()
            "MetaHVH" -> handleMetaHvh()
            else -> handleContact()
        }
    }

    private fun handleVanilla() {
        val player = mc.player ?: return
        if (!MoveUtil.isMoving()) return

        var speed = vanillaSpeed * VANILLA_DEFAULT_SPEED
        val speedEffect = player.getEffect(MobEffects.SPEED)
        if (speedEffect != null) {
            val value = (speedEffect.amplifier + 1) * 0.205
            speed += speed * value
        }

        if (waterFix && player.isInWater) {
            speed = 4.5 * VANILLA_DEFAULT_SPEED
        }

        if (hvhTarget) {
            val target = HitAuraModule.currentTarget
            if (HitAuraModule.isEnabled() && target != null) {
                if (lastHvhTarget != null && lastHvhTarget !== target) {
                    HvhTargetPredict.reset(lastHvhTarget)
                    extrapolator.clear()
                }
                lastHvhTarget = target

                val toTarget = target.position().subtract(player.position())
                val horizontalDistSq = toTarget.x * toTarget.x + toTarget.z * toTarget.z

                val targetPos = if (hvhPredictMode == "Extrapolation") {
                    extrapolator.update(target)
                    extrapolator.getExtrapolatedPos(target, hvhPredictStrength.toFloat(), false)
                } else {
                    HvhTargetPredict.predict(target, hvhPredictStrength)
                }

                val cooldownReady = player.getAttackStrengthScale(0.5f) >= 0.92f

                if (leave && !cooldownReady) {
                    val desiredDist = leaveDistance
                    if (horizontalDistSq < desiredDist * desiredDist) {
                        val fromTarget = player.position().subtract(targetPos)
                        val dir = getDirectionToPoint(Vec3.ZERO, Vec3(fromTarget.x, 0.0, fromTarget.z), speed)
                        player.deltaMovement = Vec3(dir[0], player.deltaMovement.y, dir[1])
                        renderTarget = target
                        renderPredicted = targetPos
                        val len = hypot(fromTarget.x, fromTarget.z)
                        renderLeaveTarget = if (len < 1.0e-6) targetPos else {
                            val k = desiredDist / len
                            Vec3(targetPos.x - fromTarget.x * k, targetPos.y, targetPos.z - fromTarget.z * k)
                        }
                        return
                    }
                } else if (leave) {
                    val desiredDist = attackDistance
                    if (horizontalDistSq > desiredDist * desiredDist) {
                        val dir = getDirectionToPoint(player.position(), targetPos, speed)
                        player.deltaMovement = Vec3(dir[0], player.deltaMovement.y, dir[1])
                        renderTarget = target
                        renderPredicted = targetPos
                        val pdx = targetPos.x - player.x
                        val pdz = targetPos.z - player.z
                        val plen = hypot(pdx, pdz)
                        renderLeaveTarget = if (plen < 1.0e-6) player.position() else {
                            val k = desiredDist / plen
                            Vec3(player.x + pdx * k, player.y, player.z + pdz * k)
                        }
                        return
                    }
                    player.deltaMovement = Vec3(0.0, player.deltaMovement.y, 0.0)
                    renderTarget = target
                    renderPredicted = targetPos
                    renderLeaveTarget = null
                    return
                }

                val dx = targetPos.x - player.x
                val dz = targetPos.z - player.z
                val distToPoint = sqrt(dx * dx + dz * dz)

                if (distToPoint <= HVH_TARGET_ZONE) {
                    player.deltaMovement = Vec3(0.0, player.deltaMovement.y, 0.0)
                } else {
                    val moveSpeed = min(speed, distToPoint)
                    val dir = getDirectionToPoint(player.position(), targetPos, moveSpeed)
                    player.deltaMovement = Vec3(dir[0], player.deltaMovement.y, dir[1])
                }

                renderTarget = target
                renderPredicted = targetPos
                renderLeaveTarget = null
                return
            }
        }

        val change = transformVanillaStrafe(speed, player)
        player.deltaMovement = Vec3(change[0], player.deltaMovement.y, change[1])
        renderTarget = null
        renderPredicted = null
        renderLeaveTarget = null
    }

    private fun transformVanillaStrafe(speed: Double, player: LocalPlayer): DoubleArray {
        val forward = Math.signum(player.input.movementForward)
        val side = Math.signum(player.input.movementSideways)
        var yaw = player.yRot

        if (forward == 0.0f && side == 0.0f) return doubleArrayOf(0.0, 0.0)

        var strafe = 90.0f * side
        if (forward != 0.0f) strafe *= forward * 0.5f

        yaw -= strafe
        if (forward < 0.0f) yaw -= 180.0f
        val yawRadians = Math.toRadians(yaw.toDouble())

        return doubleArrayOf(-sin(yawRadians) * speed, cos(yawRadians) * speed)
    }

    private fun handleVulcan() {
        val player = mc.player ?: return
        if (vulcanOnlyWhileMoving && !MoveUtil.isMoving()) return
        if (!player.onGround() || player.horizontalCollision) return
        if (mc.options.keyJump.isDown) return

        player.jumpFromGround()
        player.deltaMovement = Vec3(player.deltaMovement.x, 0.1, player.deltaMovement.z)
        MoveUtil.setSpeed(0.40)
    }

    private fun handlePolar() {
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (!player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA) && player.isFallFlying) {
            player.stopFallFlying()
        }

        var elytraSlot = -1
        for (i in 0 until 9) {
            if (player.inventory.getItem(i).`is`(Items.ELYTRA)) {
                elytraSlot = i
                break
            }
        }
        if (elytraSlot == -1) return

        val kp = player.input.keyPresses
        val input = Input(kp.forward(), kp.backward(), kp.left(), kp.right(), true, kp.shift(), kp.sprint())
        player.connection.send(ServerboundPlayerInputPacket(input))

        if (polarDive) {
            var yaw = mc.gameRenderer.mainCamera().yRot()
            if (mc.options.cameraType.isMirrored) yaw -= 180.0f
            RotationManager.setRotation(Rotation(yaw, 55.0f), moveFix = MoveFixMode.FREE)
        }

        if (player.onGround() && !player.isInWater && !player.isInLava && !player.isPassenger) {
            player.jumpFromGround()
        }

        if (polarLastSlot == -1) polarLastSlot = player.inventory.selectedSlot
        if (player.inventory.selectedSlot != elytraSlot) {
            player.inventory.selectedSlot = elytraSlot
            return
        }

        if (player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)) {
            if (!player.onGround() && !player.isFallFlying && !player.isInWater && !player.isInLava) {
                player.connection.send(
                    ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
                )
                player.startFallFlying()
            }
            return
        }

        player.connection.send(
            ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN)
        )
        mc.gameMode?.startPrediction(level) { sequence ->
            val rotation = RotationManager.getServerRotation()
            RotationManager.lockOutgoingRotation()
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, rotation.yaw, rotation.pitch)
        }
        player.connection.send(
            ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
        )
        player.startFallFlying()
        player.connection.send(
            ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN)
        )
    }

    private fun handleMetaHvh() {
        val player = mc.player ?: return
        val base = metaHvhSpeed.toFloat()
        val offhandName = player.offhandItem.hoverName.string

        var applied = 0.0f
        val speedEffect = player.getEffect(MobEffects.SPEED)
        if (speedEffect != null) {
            val amplifier = speedEffect.amplifier
            if (amplifier == 1) {
                applied = base * 1.155f
                if (offhandName.contains("Ломтик Дыни")) {
                    applied = 0.41755f
                }
            } else if (amplifier == 0) {
                applied = base
            }
        } else {
            applied = base * 0.68f
        }

        if (player.hasEffect(MobEffects.SLOWNESS)) {
            applied *= 0.835f
        }

        if (!player.onGround()) {
            applied *= 1.435f
        }

        if (metaHvhTarget) {
            val target = HitAuraModule.currentTarget
            if (HitAuraModule.isEnabled() && target != null) {
                if (lastHvhTarget != null && lastHvhTarget !== target) {
                    HvhTargetPredict.reset(lastHvhTarget)
                }
                lastHvhTarget = target

                val targetPos = HvhTargetPredict.predict(target, metaHvhPredictStrength)
                val dx = targetPos.x - player.x
                val dz = targetPos.z - player.z
                val distToPoint = sqrt(dx * dx + dz * dz)

                if (distToPoint <= HVH_TARGET_ZONE) {
                    player.deltaMovement = Vec3(0.0, player.deltaMovement.y, 0.0)
                } else {
                    val moveSpeed = min(applied.toDouble(), distToPoint)
                    val dir = getDirectionToPoint(player.position(), targetPos, moveSpeed)
                    player.deltaMovement = Vec3(dir[0], player.deltaMovement.y, dir[1])
                }
                return
            }
        }

        MoveUtil.setSpeed(applied.toDouble())
    }

    private fun handleContact() {
        val player = mc.player ?: return
        val level = mc.level ?: return

        val auraTarget = if (onlyWithAura) {
            if (!HitAuraModule.isEnabled()) return
            HitAuraModule.currentTarget ?: return
        } else null

        val contactBox = player.boundingBox.inflate(contactRange)
        var contactCount = 0

        for (entity in level.entitiesForRendering()) {
            if (!isValidTarget(entity, player)) continue
            if (contactBox.intersects(entity.boundingBox)) contactCount++
        }

        if (contactCount <= 0) return
        if (contactOnlyWhileMoving && !MoveUtil.isMoving()) return

        val motionBoost = boost * 0.01 * contactCount
        if (motionBoost <= 0.0) return

        val nearest = if (onlyWithAura && auraTarget != null) auraTarget else findNearestTarget(targetRange, player, level)
        if (nearest == null) return

        var targetPos = nearest.position()
        if (predict) {
            val targetMotion = nearest.deltaMovement
            val horizontalMotionSq = targetMotion.x * targetMotion.x + targetMotion.z * targetMotion.z
            if (horizontalMotionSq > 1.0e-4) {
                targetPos = targetPos.add(targetMotion.x * predictStrength, 0.0, targetMotion.z * predictStrength)
            }
        }

        val direction = getDirectionToPoint(player.position(), targetPos, motionBoost)
        player.deltaMovement = player.deltaMovement.add(direction[0], 0.0, direction[1])
    }

    private fun findNearestTarget(maxRange: Double, player: LocalPlayer, level: net.minecraft.client.multiplayer.ClientLevel): Entity? {
        var nearest: Entity? = null
        var bestDistanceSq = Double.MAX_VALUE
        val maxDistanceSq = maxRange * maxRange

        for (entity in level.entitiesForRendering()) {
            if (!isValidTarget(entity, player)) continue

            val dx = entity.x - player.x
            val dz = entity.z - player.z
            val distanceSq = dx * dx + dz * dz

            if (distanceSq <= maxDistanceSq && distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                nearest = entity
            }
        }

        return nearest
    }

    private fun isValidTarget(entity: Entity, player: LocalPlayer): Boolean {
        if (entity === player || !entity.isAlive) return false
        if (playersOnly && entity !is Player) return false
        return entity is LivingEntity || entity is AbstractBoat
    }

    private fun getDirectionToPoint(from: Vec3, to: Vec3, speedValue: Double): DoubleArray {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val length = sqrt(dx * dx + dz * dz)
        if (length < 1.0e-6) return doubleArrayOf(0.0, 0.0)
        return doubleArrayOf(dx / length * speedValue, dz / length * speedValue)
    }
}
