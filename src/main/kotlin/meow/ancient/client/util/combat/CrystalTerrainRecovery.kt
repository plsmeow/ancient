package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos

/** Bounds retries without replacing a still usable selected cell on every failed click. */
internal class CrystalTerrainRecovery {
    enum class Decision { CONTINUE, SUSPEND, BACKOFF, ABANDON }
    var suspended = false
        private set
    var retryAt = 0
        private set
    private var unreachableSince: Int? = null
    private var lastStage: CrystalTerrainPlan.Stage? = null
    private var lastMiningPos: BlockPos? = null
    private var lastProgress = 0f
    private var advancedAt = 0
    private var placementAttempts = 0
    private var miningFailures = 0
    private var observedAt: Int? = null
    private var activeTicks = 0

    fun pause(tick: Int) { advancedAt = tick; observedAt = tick }
    fun placementSent() { placementAttempts++ }
    fun miningFailed(tick: Int) { miningFailures++; advancedAt = tick; lastProgress = 0f; retryAt = tick + 4 }

    fun observe(tick: Int, stage: CrystalTerrainPlan.Stage, reachable: Boolean, progress: Float,
                stallTimeout: Int, miningPos: BlockPos? = null): Decision {
        if (stage == CrystalTerrainPlan.Stage.DONE) { suspended = false; return Decision.CONTINUE }
        activeTicks += observedAt?.let { (tick - it).coerceAtLeast(0) } ?: 0
        observedAt = tick
        if (stage != lastStage || miningPos != lastMiningPos) {
            val changedBlock = miningPos != lastMiningPos
            lastStage = stage; lastMiningPos = miningPos; advancedAt = tick; lastProgress = 0f
            if (stage == CrystalTerrainPlan.Stage.BASE || changedBlock) { miningFailures = 0; retryAt = tick }
        }
        if (progress > lastProgress + 0.001f) { advancedAt = tick; lastProgress = progress }
        suspended = !reachable
        if (!reachable) {
            if (unreachableSince == null) unreachableSince = tick
            return if (tick - unreachableSince!! >= 20) Decision.ABANDON else Decision.SUSPEND
        }
        unreachableSince = null
        if (miningFailures >= 3 || placementAttempts >= 3 && stage == CrystalTerrainPlan.Stage.BASE)
            return Decision.ABANDON
        // Server restoration can alternate MINE/BASE forever without a stalled stage.
        if (activeTicks >= (stallTimeout * 3).coerceIn(120, 480)) return Decision.ABANDON
        if (tick - advancedAt >= stallTimeout.coerceIn(20, 160)) return Decision.ABANDON
        return if (tick < retryAt) Decision.BACKOFF else Decision.CONTINUE
    }
}
