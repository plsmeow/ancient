/* SPDX-License-Identifier: GPL-3.0-only. See docs/scaffold-port.md. */
package meow.ancient.module.modules.movement

/** One outstanding native placement; a rejected cell gets one delayed retry, then stays blocked. */
class ScaffoldAttempts<T> {
    enum class Result { WAITING, CONFIRMED, REJECTED, IDLE }
    private data class Pending<T>(val target: T, val tick: Int, val deadline: Int)
    private data class Failure(val count: Int, val retryAt: Int)
    private var pending: Pending<T>? = null
    private val failures = HashMap<T, Failure>()
    private var lastAttemptTick = Int.MIN_VALUE
    val target: T? get() = pending?.target

    fun canAttempt(target: T, tick: Int, delay: Int): Boolean {
        val failure = failures[target]
        return pending == null && (lastAttemptTick == Int.MIN_VALUE || tick - lastAttemptTick >= maxOf(1, delay))
            && (failure == null || failure.count < 2 && tick >= failure.retryAt)
    }

    /** Must run before native use, including interactions that get cancelled or fail prediction. */
    fun begin(target: T, tick: Int, timeout: Int) {
        check(pending == null && tick != lastAttemptTick)
        lastAttemptTick = tick
        pending = Pending(target, tick, tick + maxOf(2, timeout))
    }

    fun update(tick: Int, confirmed: Boolean, predicted: Boolean): Result {
        val attempt = pending ?: return Result.IDLE
        if (confirmed) {
            failures.remove(attempt.target); pending = null
            return Result.CONFIRMED
        }
        if ((tick > attempt.tick && !predicted) || tick >= attempt.deadline) {
            reject(tick)
            return Result.REJECTED
        }
        return Result.WAITING
    }

    fun reject(tick: Int) {
        val attempt = pending ?: return
        val count = (failures[attempt.target]?.count ?: 0) + 1
        failures[attempt.target] = Failure(count, tick + 10 * count)
        pending = null
    }

    fun retainFailures(relevant: (T) -> Boolean) { failures.keys.removeIf { !relevant(it) } }
    fun reset() { pending = null; failures.clear(); lastAttemptTick = Int.MIN_VALUE }
}
