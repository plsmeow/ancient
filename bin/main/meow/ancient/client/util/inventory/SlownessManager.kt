package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.player.ClientInput
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

object SlownessManager {
    class SlowTask(val durationMs: Long, val action: () -> Unit) {
        val startTime = System.currentTimeMillis()
        var executed = false
    }

    private val tasks = ArrayList<SlowTask>()

    fun addTask(durationMs: Long = 75L, action: () -> Unit) {
        val task = SlowTask(durationMs, action)
        tasks.add(task)
        applySlowInput()
    }

    fun isSlowed(): Boolean {
        val now = System.currentTimeMillis()
        return tasks.any { now - it.startTime < it.durationMs }
    }

    fun update() {
        if (tasks.isEmpty()) return
        val now = System.currentTimeMillis()
        val toRun = ArrayList<SlowTask>()
        for (task in tasks) {
            if (!task.executed) {
                task.executed = true
                toRun.add(task)
            }
        }
        for (task in toRun) {
            task.action()
        }
        tasks.removeIf { now - it.startTime >= it.durationMs }
    }

    fun clear() {
        tasks.clear()
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
