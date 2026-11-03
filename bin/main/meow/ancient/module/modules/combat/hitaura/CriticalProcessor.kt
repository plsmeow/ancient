package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.combat.IdealHitUtils
import meow.ancient.module.modules.combat.CriticalsModule
import meow.ancient.event.events.PacketSendEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

class CriticalProcessor {
    private var movementPlayer: LocalPlayer? = null
    private var movementLevel: ClientLevel? = null
    private var observingMovement = false
    private var pendingMovement: PacketSendEvent? = null
    private var hasSentMovement = false
    private var hasSentPosition = false
    private var sentOnGround = true
    private var sentY = 0.0
    private var sentFallDistance = 0.0

    fun reset() {
        resetMovementHistory()
    }

    private fun resetMovementHistory() {
        movementPlayer = null
        movementLevel = null
        observingMovement = false
        pendingMovement = null
        hasSentMovement = false
        hasSentPosition = false
        sentOnGround = true
        sentY = 0.0
        sentFallDistance = 0.0
    }

    private fun beginMovementObservation() {
        if (movementPlayer !== mc.player || movementLevel !== mc.level) resetMovementHistory()
        movementPlayer = mc.player
        movementLevel = mc.level
        pendingMovement = null
        observingMovement = movementPlayer != null && movementLevel != null
    }

    private fun recordPacket(event: PacketSendEvent) {
        if (observingMovement && event.packet is ServerboundMovePlayerPacket) pendingMovement = event
    }

    private fun commitMovement() {
        observingMovement = false
        val event = pendingMovement
        pendingMovement = null
        if (movementPlayer !== mc.player || movementLevel !== mc.level) {
            resetMovementHistory()
            return
        }
        if (event == null || event.cancel) return
        val player = movementPlayer ?: return
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return
        if (packet.hasPosition()) {
            val y = packet.getY(sentY)
            if (packet.isOnGround || !hasSentPosition || y > sentY || player.onGround()) {
                sentFallDistance = 0.0
            } else {
                sentFallDistance += sentY - y
            }
            sentY = y
            hasSentPosition = true
        } else if (packet.isOnGround || player.onGround()) {
            sentFallDistance = 0.0
        }
        sentOnGround = packet.isOnGround
        hasSentMovement = true
    }

    companion object {
        private val movementObserver = CriticalProcessor()

        @JvmStatic
        fun onBeginMovementObservation() = movementObserver.beginMovementObservation()

        @JvmStatic
        fun onPacketSend(event: PacketSendEvent) = movementObserver.recordPacket(event)

        @JvmStatic
        fun onMovementSent() = movementObserver.commitMovement()
    }

    fun isReadyForAttack(onlyCrits: Boolean, smartCrits: Boolean): Boolean {
        if (!inGame) return false
        if (!onlyCrits) return true
        return CriticalsModule.canPrepareCrit() || IdealHitUtils.canAttack(
            smartCrits, movementObserver.sentOnGround, movementObserver.sentFallDistance,
            movementObserver.hasSentMovement && movementObserver.movementPlayer === mc.player
                && movementObserver.movementLevel === mc.level
        )
    }

    fun shouldPrepareAttack(onlyCrits: Boolean, smartCrits: Boolean): Boolean {
        if (!inGame) return false
        return !onlyCrits || CriticalsModule.canPrepareCrit() || IdealHitUtils.canPrepareAttack(smartCrits)
    }
}
