package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/** A damage-approved terrain block must be replaced before another site may be mined. */
internal class CrystalTerrainPlan(val pos: BlockPos, val targetId: Int) {
    enum class Stage { MINE, BASE, CONFIRM, DONE }
    var stage = Stage.MINE
        private set
    var miningStarted = false
        private set
    private var sentTick = 0

    fun miningStartAccepted() { miningStarted = true }

    fun observeServerState(state: BlockState) {
        if (state.`is`(Blocks.OBSIDIAN)) stage = Stage.DONE
        else if (state.isAir && stage == Stage.MINE) stage = Stage.BASE
    }

    fun placementSent(tick: Int) {
        check(stage == Stage.BASE)
        sentTick = tick
        stage = Stage.CONFIRM
    }

    fun retry(tick: Int, timeout: Int) {
        if (stage == Stage.CONFIRM && tick - sentTick >= timeout) stage = Stage.BASE
    }

    fun restartMining() {
        check(stage == Stage.BASE)
        stage = Stage.MINE
    }
}
