package meow.ancient.module.modules.player

import kotlin.math.ceil

internal class PacketMineProgress {
    enum class Phase { START, MINING, CONFIRM, REBREAK }

    var phase = Phase.START
        private set
    var damage = 0f
        private set
    var rebreakPrimed = false
        private set
    private var miningTicks = 0
    private var readyTick: Long? = null
    private var stopTick = 0L

    fun start() {
        check(phase == Phase.START)
        phase = Phase.MINING
    }

    @JvmOverloads
    fun advance(delta: Float, tick: Long, threshold: Float = 1f): Boolean {
        if (phase != Phase.MINING) return false
        miningTicks++
        // Vanilla recalculates elapsed damage with the current tool/effects at STOP. Recompute
        // both slowdowns and acknowledged efficiency increases without a startup wait.
        val currentDelta = if (delta.isFinite()) delta.coerceAtLeast(0f) else 0f
        damage = (currentDelta * miningTicks).coerceAtMost(1f)
        val finishAt = if (threshold.isFinite()) threshold.coerceIn(0.7f, 1f) else 1f
        if (damage + 1.0E-6f < finishAt) {
            readyTick = null
            return false
        }
        if (finishAt < 1f) return true
        val ready = readyTick
        if (ready == null) readyTick = tick
        return ready != null && tick > ready
    }

    fun stop(tick: Long) {
        check(phase == Phase.MINING || phase == Phase.REBREAK)
        phase = Phase.CONFIRM
        stopTick = tick
    }

    @JvmOverloads
    fun confirmAir(rebreak: Boolean, allowMining: Boolean = false): Boolean {
        if (phase == Phase.REBREAK && rebreak) return true
        if (!rebreak || phase != Phase.CONFIRM && !(allowMining && phase == Phase.MINING)) return false
        phase = Phase.REBREAK
        damage = 1f
        rebreakPrimed = true
        return true
    }

    fun enoughTime(elapsedNanos: Long, delta: Float, threshold: Float): Boolean {
        if (!delta.isFinite() || delta <= 0f || elapsedNanos < 0L) return false
        val fraction = if (threshold.isFinite()) threshold.coerceIn(0.7f, 1f) else 1f
        val ticks = ceil(fraction.toDouble() / delta) + if (fraction >= 1f) 1 else 0
        return elapsedNanos / 50_000_000L >= ticks
    }

    fun timedOut(tick: Long, timeout: Long): Boolean = phase == Phase.CONFIRM && tick - stopTick >= timeout

    fun confirmationAge(tick: Long): Long = if (phase == Phase.CONFIRM) (tick - stopTick).coerceAtLeast(0) else 0

    fun retry() {
        if (!rebreakPrimed) {
            restart()
            return
        }
        phase = Phase.REBREAK
        damage = 1f
        stopTick = 0L
    }

    fun restart() {
        phase = Phase.START
        damage = 0f
        rebreakPrimed = false
        miningTicks = 0
        readyTick = null
        stopTick = 0L
    }
}
