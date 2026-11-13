package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import sweetie.evaware.flora.api.Commando
import java.util.concurrent.ConcurrentLinkedQueue

object VelocityModule : Module(
    name = "Velocity",
    category = COMBAT,
    description = "Controls incoming knockback and Grim ping cancellation."
) {
    val mode by mode(
        name = "Mode",
        value = "Grim",
        modes = listOf("Cancel", "Grim", "Grim 2.3.71")
    )
    val fix by boolean(
        name = "Fix",
        value = false,
        isVisible = { mode == "Grim" }
    )
    val fixDelay by number(
        name = "Fix Delay",
        value = 100.0,
        min = 0.0,
        max = 1000.0,
        step = 10.0,
        precision = 0,
        unit = "ms",
        isVisible = { mode == "Grim" && fix }
    )

    @Volatile
    private var lastVelocityTime = 0L

    @Volatile
    private var velocityCanceled = false

    @Volatile
    private var cancelNextVelocity = false

    @Volatile
    private var delay = false

    @Volatile
    private var needClick = false

    @Volatile
    private var waitForPing = false

    @Volatile
    private var waitForUpdate = false

    @Volatile
    private var hitResult: BlockHitResult? = null

    @Volatile
    private var shouldSkip = false

    @Volatile
    private var freezeTicks = 0

    @Volatile
    private var delayTicks = 0

    @Volatile
    private var tickCounter = 0

    @Volatile
    private var clearWaitAtTick = -1

    @Volatile
    private var blockUpdatePos: BlockPos? = null

    private val incomingQueue = ConcurrentLinkedQueue<Packet<*>>()
    private const val MAX_FREEZE_TICKS = 20
    private const val MAX_DELAY_TICKS = 60

    override fun onEnable() = reset()

    override fun onDisable() {
        flushIncomingQueue()
        reset()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        reset()
    }

    fun shouldFreezeTick(): Boolean = mode == "Grim 2.3.71" && waitForUpdate

    private fun isLocalVelocity(packet: Packet<*>, player: net.minecraft.client.player.LocalPlayer): Boolean =
        packet is ClientboundSetEntityMotionPacket && packet.id == player.id ||
            packet is ClientboundExplodePacket && packet.playerKnockback().isPresent

    private fun reset() {
        velocityCanceled = false
        lastVelocityTime = 0L
        cancelNextVelocity = false
        delay = false
        needClick = false
        waitForPing = false
        waitForUpdate = false
        hitResult = null
        shouldSkip = false
        freezeTicks = 0
        delayTicks = 0
        clearWaitAtTick = -1
        blockUpdatePos = null
        incomingQueue.clear()
    }

    private fun flushIncomingQueue() {
        val listener = mc.connection ?: return
        while (true) {
            val packet = incomingQueue.poll() ?: return
            @Suppress("UNCHECKED_CAST")
            (packet as Packet<net.minecraft.network.PacketListener>).handle(listener)
        }
    }

    @Commando
    fun onPacketSend(event: PacketSendEvent) {
        if (mode != "Grim 2.3.71") return
        when (val packet = event.packet) {
            is ServerboundInteractPacket,
            is ServerboundAttackPacket,
            is ServerboundSpectatorActionPacket,
            is ServerboundUseItemOnPacket -> shouldSkip = true

            is ServerboundMovePlayerPacket -> {
                if (packet.hasPosition() && waitForUpdate) event.cancel = true
            }

            is ServerboundPongPacket -> {
                if (waitForPing) clearWaitAtTick = tickCounter + 1
            }
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (mode != "Grim 2.3.71") return
        tickCounter++
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        if (waitForUpdate) {
            val updatePos = blockUpdatePos
            if (updatePos != null) {
                blockUpdatePos = null
                if (updatePos == player.blockPosition()) {
                    waitForPing = true
                    needClick = false
                }
            }
        }

        if (needClick && !shouldSkip && !player.isUsingItem) {
            val eyePos = player.getEyePosition(1.0f)
            val targetPos = eyePos.add(0.0, -4.5, 0.0)
            val trace = player.level().clip(
                ClipContext(eyePos, targetPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
            )
            if (trace.type == HitResult.Type.BLOCK && trace.blockPos.relative(trace.direction) == player.blockPosition()) {
                hitResult = trace
            }
        }

        hitResult?.let { trace ->
            delay = false
            delayTicks = 0
            flushIncomingQueue()

            val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, trace)
            if (result is InteractionResult.Success && result.shouldSwing()) {
                player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false)
            }

            if (RotationManager.getServerRotation().pitch != 90.0f) {
                player.connection.send(
                    ServerboundMovePlayerPacket.Rot(
                        RotationManager.getServerRotation().yaw,
                        90.0f,
                        player.onGround(),
                        player.horizontalCollision
                    )
                )
            } else {
                player.connection.send(
                    ServerboundMovePlayerPacket.StatusOnly(
                        player.onGround(),
                        player.horizontalCollision
                    )
                )
            }

            freezeTicks = 0
            waitForUpdate = true
            hitResult = null
            needClick = false
        }

        if (delay) {
            delayTicks++
            if (delayTicks > MAX_DELAY_TICKS) {
                delay = false
                delayTicks = 0
                needClick = false
                flushIncomingQueue()
            }
        }

        if (waitForUpdate) {
            freezeTicks++
            if (freezeTicks > MAX_FREEZE_TICKS) {
                waitForUpdate = false
                waitForPing = false
                needClick = false
            }
        }

        if (clearWaitAtTick >= 0 && tickCounter >= clearWaitAtTick) {
            clearWaitAtTick = -1
            waitForUpdate = false
            waitForPing = false
        }

        shouldSkip = false
    }

    @Commando
    fun onPacketReceive(event: PacketReceiveEvent) {
        val player = mc.player
        if (player == null || mc.connection == null) {
            reset()
            return
        }

        val packet = event.packet

        if (mode == "Grim 2.3.71") {
            if (packet is ClientboundBlockUpdatePacket && waitForUpdate) {
                blockUpdatePos = packet.pos
                return
            }

            if (waitForUpdate) return

            if (delay) {
                incomingQueue.add(packet)
                event.cancel = true
                return
            }

            if (packet is ClientboundDamageEventPacket && packet.entityId == player.id) {
                cancelNextVelocity = true
            } else if (cancelNextVelocity && isLocalVelocity(packet, player)) {
                event.cancel = true
                delay = true
                delayTicks = 0
                cancelNextVelocity = false
                needClick = true
            }
            return
        }

        when (packet) {
            is ClientboundSetEntityMotionPacket -> {
                if (packet.id != player.id) return
                event.cancel = true
                if (mode == "Grim") {
                    velocityCanceled = true
                    lastVelocityTime = System.currentTimeMillis()
                }
            }
            is ClientboundPingPacket -> {
                if (mode != "Grim") return
                if (!fix) {
                    event.cancel = true
                    return
                }
                if (!velocityCanceled) return
                if (System.currentTimeMillis() - lastVelocityTime < fixDelay.toLong()) {
                    event.cancel = true
                } else {
                    velocityCanceled = false
                }
            }
            is ClientboundExplodePacket -> {
                val knockback = packet.playerKnockback()
                if (knockback.isPresent) {
                    val vec = knockback.get()
                    vec.x = 0.0
                    vec.y = 0.0
                    vec.z = 0.0
                }
            }
        }
    }
}
