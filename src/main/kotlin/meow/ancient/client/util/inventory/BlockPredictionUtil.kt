package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/** The visible world includes vanilla prediction; action confirmations must read the server copy. */
object BlockPredictionUtil {
    fun serverState(level: Level, pos: BlockPos): BlockState =
        (level as? ClientLevel)?.blockStatePredictionHandler?.serverVerifiedStates?.get(pos.asLong())?.blockState
            ?: level.getBlockState(pos)

    private data class Before(val sequence: Int, val server: BlockState, val visible: BlockState)

    /** Only used to undo a native interaction whose outbound packet was cancelled locally. */
    class Snapshot internal constructor(private val level: ClientLevel) {
        private val sequence = level.blockStatePredictionHandler.currentSequence() + 1
        private val before = level.blockStatePredictionHandler.serverVerifiedStates.long2ObjectEntrySet().associate {
            it.longKey to Before(it.value.sequence, it.value.blockState, level.getBlockState(BlockPos.of(it.longKey)))
        }

        fun rollback() {
            if (mc.level !== level) return
            val entries = level.blockStatePredictionHandler.serverVerifiedStates.long2ObjectEntrySet().iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                // Reentrant packet handlers may have started a later, independently accepted prediction.
                if (entry.value.sequence != sequence) continue
                val key = entry.longKey
                val old = before[key]
                if (old != null && old.sequence == entry.value.sequence) continue
                val visible = old?.visible ?: entry.value.blockState
                if (old == null) entries.remove()
                else {
                    entry.value.sequence = old.sequence
                    entry.value.blockState = old.server
                }
                level.setBlock(BlockPos.of(key), visible, 19)
            }
        }
    }

    fun snapshot(level: ClientLevel) = Snapshot(level)
}
