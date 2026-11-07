package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.event.events.ClientDisconnectEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection
import net.minecraft.world.entity.player.Input
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.phys.Vec2
import sweetie.evaware.flora.api.Commando

object SlownessManager {
    private class SlowTask(
        val owner: LocalPlayer,
        val world: ClientLevel,
        val connection: ClientPacketListener,
        val menu: AbstractContainerMenu,
        val deadline: Long,
        val deferred: Boolean,
        var action: (() -> Unit)?
    ) {
        fun isCurrent(): Boolean = mc.player === owner && mc.level === world
            && owner.connection === connection && connection.connection.isConnected
            && owner.containerMenu === menu
    }

    private val tasks = ArrayList<SlowTask>()
    private var pauseOwner: LocalPlayer? = null
    private var pauseWorld: ClientLevel? = null
    private var pauseConnection: ClientPacketListener? = null
    private var pauseUntil = 0L
    @Volatile
    private var generation = 0
    private var executingTask: SlowTask? = null

    fun isExecutingPreparedAction(): Boolean = executingTask?.let { it.deferred && it.isCurrent() } == true

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        clear()
    }

    fun addTask(durationMs: Long = 75L, action: () -> Unit) {
        schedule(durationMs, true, action)
    }

    fun addImmediateTask(durationMs: Long, action: () -> Unit) {
        schedule(durationMs, false, action)
    }

    private fun schedule(durationMs: Long, deferred: Boolean, action: () -> Unit) {
        val player = mc.player ?: return
        val world = mc.level ?: return
        val connection = player.connection
        val menu = player.containerMenu
        if (!connection.connection.isConnected) return
        if (!mc.isSameThread) {
            val requestGeneration = generation
            mc.execute {
                if (generation == requestGeneration && mc.player === player && mc.level === world
                    && player.connection === connection && player.containerMenu === menu
                    && connection.connection.isConnected) {
                    schedule(durationMs, deferred, action)
                }
            }
            return
        }
        tasks.add(SlowTask(player, world, connection, menu,
            System.nanoTime() + durationMs * 1_000_000L, deferred, action))
        applySlowInput()
    }

    @JvmStatic
    fun onContainerClose(connection: Connection) {
        if (!AnticheatManager.isSwapBypassActive) return
        val player = mc.player ?: return
        val world = mc.level ?: return
        val listener = player.connection
        if (listener.connection !== connection || !connection.isConnected) return
        if (!mc.isSameThread) {
            val requestGeneration = generation
            mc.execute {
                if (generation == requestGeneration && mc.player === player && mc.level === world
                    && player.connection === listener && connection.isConnected) onContainerClose(connection)
            }
            return
        }
        pauseOwner = player
        pauseWorld = world
        pauseConnection = listener
        val durationNanos = if (AnticheatManager.isSwapBypassGrimFast) 60_000_000L else 100_000_000L
        pauseUntil = maxOf(pauseUntil, System.nanoTime() + durationNanos)
        applySlowInput()
    }

    fun isSlowed(): Boolean {
        if (executingTask?.isCurrent() == true) return true
        val now = System.nanoTime()
        if (now < pauseUntil && mc.player === pauseOwner && mc.level === pauseWorld
            && pauseOwner?.connection === pauseConnection
            && pauseConnection?.connection?.isConnected == true) return true
        return tasks.any { it.isCurrent() && (it.action != null || now < it.deadline) }
    }

    fun update() {
        val now = System.nanoTime()
        val currentGeneration = generation
        var remaining = tasks.size
        var index = 0
        while (remaining-- > 0 && generation == currentGeneration) {
            val task = tasks[index]
            if (!task.isCurrent() || task.action == null && now >= task.deadline) {
                tasks.removeAt(index)
                continue
            }
            val action = task.action
            if (action != null && (!task.deferred || now >= task.deadline)) {
                task.action = null
                applySlowInput()
                executingTask = task
                try {
                    action()
                } finally {
                    executingTask = null
                }
            }
            index++
        }
        if (isSlowed()) applySlowInput()
    }

    fun clear() {
        tasks.clear()
        executingTask = null
        pauseOwner = null
        pauseWorld = null
        pauseConnection = null
        pauseUntil = 0L
        generation++
    }

    fun applySlowInput() {
        val player = mc.player ?: return
        mc.options.keySprint.isDown = false
        player.isSprinting = false
        player.input.keyPresses = Input.EMPTY
        player.input.moveVector = Vec2.ZERO
    }

    fun applyToInput(input: ClientInput) {
        if (isSlowed()) {
            input.keyPresses = Input.EMPTY
            input.moveVector = Vec2.ZERO
            mc.player?.isSprinting = false
            mc.options.keySprint.isDown = false
        }
    }
}
