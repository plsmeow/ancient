package meow.ancient.client.util.movement

import meow.ancient.client.extensions.mc
import meow.ancient.client.listener.Listener
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.module.modules.movement.AirStuckModule
import meow.ancient.network.SilentPacketQueue
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import java.util.ArrayDeque
import kotlin.math.sqrt

object TeleportPath : Listener() {
    enum class Type { Simple, Step, Vault, Bypass }

    private val positions = ArrayDeque<Vec3>()
    private var owner: Any? = null
    private var mover: Entity? = null
    private var pathType: Type? = null
    private var playerPacketSent: Boolean = false
    private var teleportPacketSent: Boolean = false
    private var vehicleSent: Boolean = false
    private var onGround: Boolean = false
    private var stepDistance: Double = 0.0
    private var yaw: Float = 0f
    private var pitch: Float = 0f
    private var completion: Runnable? = null

    override fun init() {
        EventManager.register(this)
    }

    fun start(
        requester: Any,
        destination: Vec3,
        type: Type,
        repeats: Int,
        distancePerStep: Double,
        ground: Boolean,
        yRot: Float,
        xRot: Float,
        finished: Runnable
    ): Boolean {
        val player = mc.player ?: return false
        if (owner != null || !player.isAlive) return false
        owner = requester
        mover = if (player.isPassenger) player.rootVehicle else player
        pathType = type
        onGround = ground
        stepDistance = if (type == Type.Simple || type == Type.Step) distancePerStep else 0.0
        yaw = yRot
        pitch = xRot
        completion = finished
        val currentMover = mover ?: player
        if (type == Type.Vault) {
            val above = currentMover.position().add(0.0, 129.0, 0.0)
            positions.add(above)
            positions.add(Vec3(destination.x, above.y, destination.z))
            positions.add(destination)
            positions.add(destination.add(0.0, 0.01, 0.0))
        } else {
            val count = if (type == Type.Bypass) maxOf(1, repeats) else 1
            for (i in 0 until count) {
                positions.add(destination)
            }
        }
        return true
    }

    fun active(requester: Any): Boolean = owner === requester

    fun busy(): Boolean = owner != null

    fun retarget(requester: Any, destination: Vec3) {
        if (owner !== requester) return
        val remaining = positions.size
        for (i in 0 until remaining) {
            var position = positions.removeFirst()
            position = if (pathType != Type.Vault) {
                destination
            } else {
                when (remaining - i) {
                    3 -> Vec3(destination.x, position.y, destination.z)
                    2 -> destination
                    1 -> destination.add(0.0, 0.01, 0.0)
                    else -> position
                }
            }
            positions.addLast(position)
        }
    }

    fun aim(requester: Any, yRot: Float, xRot: Float) {
        if (owner === requester) {
            yaw = yRot
            pitch = xRot
        }
    }

    fun cancel(requester: Any) {
        if (owner === requester) {
            clear()
        }
    }

    @Commando
    fun onLeave(event: ClientDisconnectEvent) {
        clear()
        playerPacketSent = false
        teleportPacketSent = false
        vehicleSent = false
    }

    @Commando
    fun onSend(event: PacketSendEvent) {
        if (event.cancel) return
        val player = mc.player ?: return
        val packet = event.packet

        if (packet is ServerboundClientTickEndPacket) {
            if (owner != null && AirStuckModule.isEnabled()) clear()
            if (owner != null && !playerPacketSent && mover === player) {
                sendNext()
            } else if (owner != null && !vehicleSent && mover !== player) {
                sendNext()
            }
            playerPacketSent = false
            teleportPacketSent = false
            vehicleSent = false
            return
        }

        if (packet is ServerboundMoveVehiclePacket) {
            if (owner != null && mover !== player) {
                event.cancel = true
                if (!vehicleSent) sendNext()
            } else {
                vehicleSent = true
            }
        } else if (packet is ServerboundMovePlayerPacket) {
            if (owner != null && mover === player) {
                event.cancel = true
                if (!playerPacketSent) sendNext()
            } else {
                if (teleportPacketSent) {
                    event.cancel = true
                } else {
                    playerPacketSent = true
                }
            }
        }
    }

    private fun sendNext() {
        val player = mc.player ?: run { clear(); return }
        val currentMover = mover ?: run { clear(); return }
        if (currentMover.isRemoved || !player.isAlive) {
            clear()
            return
        }
        var position = positions.firstOrNull() ?: run { clear(); return }
        if (stepDistance > 0.0) {
            val current = currentMover.position()
            val dx = position.x - current.x
            val dy = position.y - current.y
            val dz = position.z - current.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance > stepDistance) {
                val scale = stepDistance / distance
                position = current.add(dx * scale, dy * scale, dz * scale)
            } else {
                positions.removeFirst()
            }
        } else {
            positions.removeFirst()
        }

        currentMover.setPos(position)
        currentMover.deltaMovement = Vec3.ZERO

        if (currentMover === player) {
            val conn = player.connection.connection
            SilentPacketQueue.send(
                conn,
                ServerboundMovePlayerPacket.PosRot(position, yaw, pitch, onGround, player.horizontalCollision)
            )
            playerPacketSent = true
            teleportPacketSent = true
        } else {
            player.setPos(position)
            val conn = player.connection.connection
            SilentPacketQueue.send(
                conn,
                ServerboundMoveVehiclePacket.fromEntity(currentMover)
            )
            vehicleSent = true
        }

        if (positions.isEmpty()) {
            val finished = completion
            clear()
            finished?.run()
        }
    }

    private fun clear() {
        owner = null
        mover = null
        pathType = null
        positions.clear()
        stepDistance = 0.0
        completion = null
    }
}
