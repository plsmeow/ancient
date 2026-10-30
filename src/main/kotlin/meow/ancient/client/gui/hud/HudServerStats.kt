package meow.ancient.client.gui.hud

class HudServerStats {
    private val samples = DoubleArray(10)
    private var count = 0
    private var next = 0
    private var sum = 0.0
    private var lastTime = 0L
    private var lastGameTime = 0L

    @Synchronized
    fun reset() {
        count = 0
        next = 0
        sum = 0.0
        lastTime = 0L
        lastGameTime = 0L
    }

    @Synchronized
    fun record(gameTime: Long, now: Long = System.nanoTime()) {
        if (lastTime != 0L) {
            val elapsed = now - lastTime
            val ticks = gameTime - lastGameTime
            if (ticks < 0L) {
                reset()
            } else if (elapsed > 0L) {
                val sample = (ticks.toDouble() * 1_000_000_000.0 / elapsed).coerceIn(0.0, 20.0)
                if (count == samples.size) sum -= samples[next] else count++
                samples[next] = sample
                sum += sample
                next = (next + 1) % samples.size
            }
        }
        lastTime = now
        lastGameTime = gameTime
    }

    @Synchronized
    fun average(now: Long = System.nanoTime()): Double {
        return if (count == 0 || now - lastTime > 10_000_000_000L) Double.NaN else sum / count
    }
}
