package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.movement.TeleportPath
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerTickEndEvent
import meow.ancient.module.Module
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.ceil

object TpAuraModule : Module(
    name = "TpAura",
    category = COMBAT,
    description = "Packet teleport attacks through HitAura, on foot or in a boat"
) {
    enum class TpMode { Simple, Bypass, Vault }
    enum class StepsMode { TP, Steps }

    val tpModeSetting = enum("TP Mode", TpMode.Bypass)
    private val tpMode by tpModeSetting
    private val simpleMode by enum("Simple Mode", StepsMode.TP, isVisible = { tpMode == TpMode.Simple })
    private val bypassMode by enum("Bypass Mode", StepsMode.TP, isVisible = { tpMode == TpMode.Bypass })
    private val boatMode by enum("Boat Mode", StepsMode.TP)
    val maxDistance by number("Max Distance", 200.0, 1.0, 200.0, 1.0, 0)
    private val stepSize by number(
        "Step", 9.9, 0.5, 10.0, 0.1, 1,
        isVisible = {
            (tpMode == TpMode.Simple && simpleMode == StepsMode.Steps) ||
                (tpMode == TpMode.Bypass && bypassMode == StepsMode.Steps) || boatMode == StepsMode.Steps
        }
    )
    private val packets by number("Packets", 10.0, 1.0, 50.0, 1.0, 0, isVisible = { tpMode == TpMode.Bypass })
    private val bypassOnGround by boolean("Bypass OnGround", true, isVisible = { tpMode == TpMode.Bypass })
    private val vaultDelay by number("Vault Delay", 0.0, 0.0, 2000.0, 50.0, 0, unit = "ms", isVisible = { tpMode == TpMode.Vault })
    private val boatGap by number("Boat Gap", 0.15, 0.0, 1.0, 0.05, 2)

    private var origin: Vec3? = null
    private var playerOrigin: Vec3? = null
    private var originYaw = 0f
    private var originPitch = 0f
    private var activePlayer: LocalPlayer? = null
    private var activeLevel: ClientLevel? = null
    private var activeBoat: AbstractBoat? = null
    private var activeMode = TpMode.Simple
    private var activeSteps = false
    private var vaultReturnPending = false
    private var vaultReturnAt = 0L

    var currentTarget: LivingEntity? = null
        private set

    private val renderSteps = ArrayList<Vec3>()
    private var renderBaseBox: AABB? = null
    private var renderDestinationBox: AABB? = null
    private var renderRegistered = false

    val searchRange: Double
        get() {
            val player = mc.player ?: return 0.0
            if (!isEnabled() || !player.isAlive || TeleportPath.busy()) return 0.0
            if (player.isPassenger) {
                val boat = player.vehicle as? AbstractBoat ?: return 0.0
                if (boat.isRemoved || boat.controllingPassenger !== player) return 0.0
            }
            return maxDistance
        }

    fun isPendingVaultReturn(): Boolean = vaultReturnPending

    fun getRenderPosition(entity: Entity): Vec3? {
        if (searchRange <= 0.0 || origin != null) return null
        if (entity !is LivingEntity || !entity.isAlive || entity.isRemoved || entity === mc.player) return null
        val player = mc.player ?: return null
        if (player.isPassenger) {
            val boat = player.vehicle as? AbstractBoat ?: return null
            return boatDestination(boat, entity)
        }
        return entity.position().takeIf { player.distanceToSqr(entity) <= maxDistance * maxDistance }
    }

    fun beforeAttack(entity: Entity): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val destination = getRenderPosition(entity) ?: return false
        val boat = player.vehicle as? AbstractBoat
        val mover = boat ?: player
        val start = mover.position()
        origin = start
        playerOrigin = player.position()
        originYaw = mover.yRot
        originPitch = mover.xRot
        activePlayer = player
        activeLevel = level
        activeBoat = boat
        activeMode = if (boat != null) TpMode.Simple else tpMode
        activeSteps = if (boat != null) boatMode == StepsMode.Steps else when (tpMode) {
            TpMode.Simple -> simpleMode == StepsMode.Steps
            TpMode.Bypass -> bypassMode == StepsMode.Steps
            TpMode.Vault -> false
        }
        currentTarget = entity as LivingEntity
        renderSteps.clear()
        val box = mover.boundingBox
        renderBaseBox = box.move(-mover.x, -mover.y, -mover.z)
        renderDestinationBox = renderBaseBox?.move(destination)
        try {
            if (boat != null) {
                moveBoat(boat, start, destination, originYaw, originPitch, true, player)
            } else {
                val rotation = RotationManager.getServerRotation()
                movePlayer(start, destination, rotation.yaw, rotation.pitch, true, player)
            }
        } catch (error: Throwable) {
            try {
                returnToOrigin()
            } finally {
                throw error
            }
        }
        return true
    }

    fun afterAttack() {
        if (origin == null) return
        if (activeBoat == null && activeMode == TpMode.Vault && vaultDelay > 0.0 && sameContext()) {
            vaultReturnPending = true
            vaultReturnAt = System.currentTimeMillis() + vaultDelay.toLong()
        } else {
            returnToOrigin()
        }
    }

    private fun sameContext(): Boolean = activePlayer === mc.player && activeLevel === mc.level && activePlayer?.isAlive == true

    private fun returnToOrigin() {
        try {
            val start = origin ?: return
            val player = activePlayer ?: return
            if (!sameContext()) return
            val boat = activeBoat
            if (boat != null) {
                if (boat.isRemoved || player.vehicle !== boat) return
                moveBoat(boat, boat.position(), start, originYaw, originPitch, false, player)
                playerOrigin?.let { player.setPos(it) }
            } else {
                if (player.isPassenger) return
                movePlayer(player.position(), start, originYaw, originPitch, false, player)
            }
        } finally {
            clearReturnState()
        }
    }

    @Commando
    fun onTickEnd(event: PlayerTickEndEvent) {
        if (origin != null && !sameContext()) {
            resetState()
            return
        }
        if (vaultReturnPending && System.currentTimeMillis() >= vaultReturnAt) returnToOrigin()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        resetState()
    }

    override fun onEnable() {
        resetState()
        if (!renderRegistered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (!isEnabled() || !inGame || currentTarget?.isAlive != true) return@register
                val color = M3Theme.current.primary
                val stepStyle = GizmoStyle.stroke(color.alpha(130).argb, 1.5f)
                val destinationStyle = GizmoStyle.strokeAndFill(color.alpha(200).argb, 2f, color.alpha(50).argb)
                val baseBox = renderBaseBox ?: return@register
                for (step in renderSteps) Gizmos.cuboid(baseBox.move(step), stepStyle).setAlwaysOnTop()
                renderDestinationBox?.let { Gizmos.cuboid(it, destinationStyle).setAlwaysOnTop() }
            }
            renderRegistered = true
        }
    }

    private fun movePlayer(from: Vec3, to: Vec3, yaw: Float, pitch: Float, forward: Boolean, player: LocalPlayer) {
        if (activeMode == TpMode.Vault) {
            val above = from.add(0.0, 129.0, 0.0)
            repeat(13) {
                player.connection.send(ServerboundMovePlayerPacket.StatusOnly(false, player.horizontalCollision))
            }
            sendPosition(above, yaw, pitch, player)
            sendPosition(Vec3(to.x, above.y, to.z), yaw, pitch, player)
            sendPosition(to, yaw, pitch, player)
            val destination = if (forward) to.add(0.0, 0.01, 0.0) else to
            sendPosition(destination, yaw, pitch, player)
            player.setPos(destination)
            if (forward) renderSteps.add(destination)
            return
        }
        val count = if (activeSteps) maxOf(1, ceil(from.distanceTo(to) / stepSize).toInt()) else 1
        for (i in 1..count) {
            val position = if (i == count) to else from.lerp(to, i / count.toDouble())
            player.setPos(position)
            val repeats = if (activeMode == TpMode.Bypass) packets.toInt() else 1
            repeat(repeats) { sendPosition(position, yaw, pitch, player) }
            if (forward) renderSteps.add(position)
        }
    }

    private fun sendPosition(position: Vec3, yaw: Float, pitch: Float, player: LocalPlayer) {
        val packet = if (activeMode == TpMode.Simple) {
            ServerboundMovePlayerPacket.PosRot(position, yaw, pitch, false, player.horizontalCollision)
        } else {
            ServerboundMovePlayerPacket.Pos(
                position.x, position.y, position.z,
                activeMode == TpMode.Bypass && bypassOnGround, player.horizontalCollision
            )
        }
        player.connection.send(packet)
    }

    private fun boatDestination(boat: AbstractBoat, target: Entity): Vec3? {
        val targetBox = target.boundingBox
        val center = targetBox.center
        val delta = center.subtract(boat.position())
        val distance = delta.length()
        if (distance > maxDistance || distance <= 0.001) return null
        val clearance = radius(targetBox) + radius(boat.boundingBox) + boatGap
        if (distance <= clearance) return null
        return center.subtract(delta.scale(clearance / distance))
    }

    private fun radius(box: AABB): Double = maxOf(box.xsize, box.ysize, box.zsize) * 0.5

    private fun moveBoat(boat: AbstractBoat, from: Vec3, to: Vec3, yaw: Float, pitch: Float, forward: Boolean, player: LocalPlayer) {
        val count = if (activeSteps) maxOf(1, ceil(from.distanceTo(to) / stepSize).toInt()) else 1
        for (i in 1..count) {
            val position = if (i == count) to else from.lerp(to, i / count.toDouble())
            boat.setPos(position)
            boat.yRot = yaw
            boat.xRot = pitch
            boat.positionRider(player)
            player.connection.send(ServerboundMoveVehiclePacket.fromEntity(boat))
            if (forward) renderSteps.add(position)
        }
    }

    private fun clearReturnState() {
        origin = null
        playerOrigin = null
        activePlayer = null
        activeLevel = null
        activeBoat = null
        vaultReturnPending = false
        vaultReturnAt = 0L
    }

    private fun resetState() {
        clearReturnState()
        currentTarget = null
        renderSteps.clear()
        renderBaseBox = null
        renderDestinationBox = null
    }

    override fun onDisable() {
        try {
            returnToOrigin()
        } finally {
            resetState()
        }
    }
}
