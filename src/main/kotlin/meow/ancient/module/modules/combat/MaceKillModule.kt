package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import sweetie.evaware.flora.api.Commando
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

object MaceKillModule : Module(
    name = "MaceKill",
    category = COMBAT,
    description = "Mace damage exploit via vertical packets"
) {
    val modeSetting = mode("Mode", "Default", listOf("Default", "Custom", "Calculate", "Matrix", "NCP", "Sample"))
    val mode by modeSetting
    val fallHeight = number("Fall Height", 20.0, 5.0, 150.0, 1.0, 0)
    val packetCount = number("Packet Count", 2.0, 1.0, 15.0, 1.0, 0, isVisible = { mode == "Custom" })
    val heightSeparator = number("Height Separator", 8.9, 0.1, 20.0, 0.1, 1, isVisible = { mode == "NCP" })
    val customAttackDelay = boolean("Custom Delay", true)
    val attackDelay = number("Attack Delay", 5.0, 0.0, 20.0, 1.0, 0, isVisible = { customAttackDelay.value })

    val funskyAuto = boolean("Funsky Auto", false)
    val funskyMode = mode("Funsky Mode", "Hit", listOf("Hit", "4.5s", "5s"), isVisible = { funskyAuto.value })
    val funskyChatTimer = boolean("Debug Timer", false, isVisible = { funskyAuto.value && funskyMode.value != "Hit" })
    val funskyForceFirst = boolean("Force 1", true, isVisible = { funskyAuto.value && funskyMode.value != "Hit" })

    @JvmField
    var cancelCrit = false
    @JvmField
    var killAuraTriggered = false

    private var funskyWaitingSmash = false
    private var funskyFirstSmash = false
    private var funskyHitRegistered = false
    private var funskyGraceTicks = 0
    private var funskyPrevHurtTime = 0
    private var funskyTarget: LivingEntity? = null
    private var funskySmashTicks = 1000
    private var lastFunskyTimerValue = -1.0

    var attackTicks = 0
        private set

    fun resetAttackDelay() {
        attackTicks = attackDelay.value.toInt()
    }

    override fun onEnable() {
        attackTicks = 0
        resetFunskyState()
    }

    override fun onDisable() {
        resetFunskyState()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        if (attackTicks > 0) attackTicks--
        if (funskySmashTicks < 1000) funskySmashTicks++

        if (funskyChatTimer.value && isFunskyActive() && funskyMode.value != "Hit") {
            val delay = getFunskySmashDelayTicks()
            if (funskySmashTicks < delay) {
                val remaining = (delay - funskySmashTicks) * 0.05
                val displayValue = (remaining * 10.0).roundToInt() / 10.0
                if (displayValue != lastFunskyTimerValue) {
                    lastFunskyTimerValue = displayValue
                    ChatUtil.send("§e$displayValue")
                }
            } else if (lastFunskyTimerValue != 0.0) {
                lastFunskyTimerValue = 0.0
            }
        } else if (lastFunskyTimerValue != -1.0) {
            lastFunskyTimerValue = -1.0
        }
    }

    fun isFunskySmashReady(): Boolean =
        funskyFirstSmash || funskySmashTicks >= getFunskySmashDelayTicks()

    private fun getFunskySmashDelayTicks(): Int = when (funskyMode.value) {
        "4.5s" -> 90
        "5s" -> 100
        else -> 0
    }

    fun isFunskyActive(): Boolean = isEnabled() && funskyAuto.value

    fun isFunskyWaitingSmash(): Boolean = funskyWaitingSmash

    fun isFunskyTimerMode(): Boolean = funskyMode.value == "4.5s" || funskyMode.value == "5s"

    fun updateFunskyState(target: LivingEntity?) {
        if (!isFunskyActive() || target == null || !target.isAlive) {
            resetFunskyState()
            return
        }
        if (target !== funskyTarget) {
            funskyTarget = target
            funskyWaitingSmash = false
            funskyFirstSmash = funskyForceFirst.value && isFunskyTimerMode()
            funskyHitRegistered = false
            funskyGraceTicks = 0
            funskyPrevHurtTime = 0
            funskySmashTicks = 1000
            return
        }

        if (funskyGraceTicks > 0) funskyGraceTicks--
        val hurtTime = target.hurtTime
        if (!funskyWaitingSmash && funskyGraceTicks == 0) {
            if (hurtTime > 0 && funskyPrevHurtTime == 0) {
                funskyHitRegistered = true
            }
            if (funskyHitRegistered && hurtTime == 0 && funskyPrevHurtTime > 0) {
                funskyWaitingSmash = true
                funskyHitRegistered = false
            }
        }
        funskyPrevHurtTime = hurtTime
    }

    fun onFunskySmash() {
        funskyWaitingSmash = false
        funskyFirstSmash = false
        funskyHitRegistered = false
        funskyGraceTicks = 10
        funskySmashTicks = 0

        if (funskyChatTimer.value && isFunskyActive() && funskyMode.value != "Hit") {
            ChatUtil.send("§aReady")
            lastFunskyTimerValue = -1.0
        }
    }

    private fun resetFunskyState() {
        funskyTarget = null
        funskyWaitingSmash = false
        funskyFirstSmash = false
        funskyHitRegistered = false
        funskyGraceTicks = 0
        funskyPrevHurtTime = 0
        funskySmashTicks = 1000
        lastFunskyTimerValue = -1.0
    }

    fun doCrit() {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!isEnabled()) return
        if (player.isInLava || player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) return

        val height = determineHeight()
        val x = player.x
        val y = player.y
        val z = player.z

        when (mode) {
            "Default" -> {
                sendTpPacket(x, y + height, z, false)
                sendTpPacket(x, y, z, false)
            }
            "Custom" -> runIterationMode(x, y, z, height, packetCount.value.toInt(), 0)
            "Calculate" -> runIterationMode(x, y, z, height, calcIterations(height), 0)
            "Sample" -> runIterationMode(x, y, z, height, calcIterations(height), 1)
            "Matrix" -> {
                if (height > 10) {
                    val times = ceil(abs(height / 10.0)).toInt()
                    for (i in 0 until times) {
                        sendTpPacket(x, y, z, false)
                    }
                } else {
                    for (i in 0 until 2) {
                        sendTpPacket(x, y, z, player.onGround())
                    }
                }
                sendTpPacket(x, y + height, z, false)
                sendTpPacket(x, y, z, false)
            }
            "NCP" -> {
                val targetY = y + height
                val distance = abs(y - targetY)
                val packets = (distance / heightSeparator.value).toInt()
                for (i in 0 until packets) {
                    sendTpPacket(x, y, z, false)
                }
                sendTpPacket(x, targetY, z, false)
                for (i in 0 until packets) {
                    sendTpPacket(x, y, z, false)
                }
            }
        }
    }

    private fun calcIterations(height: Int): Int =
        if (height < 11) 1 else if (height < 31) 2 else ceil(height / 10.0).toInt()

    private fun runIterationMode(x: Double, y: Double, z: Double, height: Int, iterations: Int, decrement: Int) {
        val player = mc.player ?: return
        val count = (iterations - decrement).coerceAtLeast(1)
        if (!player.onGround()) {
            for (i in 0 until count) {
                sendTpPacket(x, y, z, false)
            }
            sendTpPacket(x, y + height, z, false)
            for (i in 0 until count) {
                sendTpPacket(x, y, z, false)
            }
        } else {
            val rise = 0.5
            for (i in 0 until count) {
                sendTpPacket(x, y + rise, z, false)
                player.setPos(x, y + rise, z)
            }
            sendTpPacket(x, y + height, z, false)
            for (i in 0 until count) {
                sendTpPacket(x, y + rise, z, false)
                player.setPos(x, y + rise, z)
            }
        }
    }

    private fun determineHeight(): Int {
        val player = mc.player ?: return 0
        val level = mc.level ?: return 0
        val boundingBox = player.boundingBox
        for (i in fallHeight.value.toInt() downTo 1) {
            val newBB = boundingBox.move(0.0, i.toDouble(), 0.0)
            var noCollision = true
            for (shape in level.getBlockCollisions(player, newBB)) {
                if (shape.isEmpty) continue
                noCollision = false
                break
            }
            if (noCollision) return i
        }
        return 0
    }

    private fun sendTpPacket(x: Double, y: Double, z: Double, onGround: Boolean) {
        val player = mc.player ?: return
        player.connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, onGround, player.horizontalCollision))
        player.connection.send(ServerboundClientTickEndPacket.INSTANCE)
    }
}
