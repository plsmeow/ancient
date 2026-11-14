package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/** Clears a base cell and, when needed, the cell above it before placing obsidian. */
internal class CrystalTerrainPlan(val pos: BlockPos, val targetId: Int, miningOrder: List<BlockPos> = listOf(pos)) {
    private val miningOrder = miningOrder.toMutableList()
    private val cleared = HashSet<BlockPos>()
    private var basePlaced = false
    val miningPos: BlockPos? get() = miningOrder.firstOrNull { it !in cleared }
    val miningBlocks: List<BlockPos> get() = miningOrder
    val clearedStates = mapOf(pos to Blocks.AIR.defaultBlockState(), pos.above() to Blocks.AIR.defaultBlockState())
    val builtStates = clearedStates + (pos to Blocks.OBSIDIAN.defaultBlockState())
    val recovery = CrystalTerrainRecovery()
    enum class Stage { MINE, BASE, CONFIRM, DONE }
    var stage = Stage.MINE
        private set
    var miningStarted = false
        private set
    var priorityBoosted = false
        private set
    private var sentTick = 0

    fun miningStartAccepted() { miningStarted = true }
    fun boostPriority() { priorityBoosted = true }

    fun observeServerState(blockPos: BlockPos, state: BlockState) {
        if (blockPos == pos) basePlaced = state.`is`(Blocks.OBSIDIAN)
        if (blockPos in miningOrder) {
            if (state.isAir || blockPos == pos && basePlaced) cleared.add(blockPos)
            else cleared.remove(blockPos)
        }
        stage = when {
            miningPos != null -> Stage.MINE
            basePlaced -> Stage.DONE
            stage == Stage.MINE || stage == Stage.DONE -> Stage.BASE
            else -> stage
        }
    }

    fun placementSent(tick: Int) {
        check(stage == Stage.BASE)
        sentTick = tick
        stage = Stage.CONFIRM
    }

    fun retry(tick: Int, timeout: Int) {
        if (stage == Stage.CONFIRM && tick - sentTick >= timeout) stage = Stage.BASE
    }

    fun restartMining(blockPos: BlockPos = pos) {
        check(stage == Stage.BASE)
        if (blockPos !in miningOrder) miningOrder.add(0, blockPos)
        cleared.remove(blockPos)
        stage = Stage.MINE
    }
}
