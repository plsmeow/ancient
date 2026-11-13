package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos

internal class CrystalAuraDiagnostics {
    enum class Reason(val text: String) {
        IDLE("Поиск позиции"), NO_WORLD("Нет подключения"), NO_TARGET("Нет цели"),
        NO_CRYSTALS("Нет кристаллов"), NO_OBSIDIAN("Нет обсидиана"), NO_TOOL("Нет подходящего инструмента в хотбаре"),
        NO_TERRAIN("Нет подходящего блока для AutoMine"), DAMAGE("Недостаточный урон"),
        UNREACHABLE("Блок вне досягаемости или копание запрещено"), SLOW_TERRAIN("Блок ломается слишком долго"),
        TARGET_MOVING("Цель уходит из позиции"), SELF_DAMAGE("Слишком большой урон по себе"),
        MINE_FACE("Закрыта грань для копания"), PLACE_FACE("Недоступна грань последующего плейса"),
        CRYSTAL_BLOCKED("Недоступен последующий кристалл"), PRIORITY("Чужой приоритет"),
        CONTEXT("Действия приостановлены"), TOOL_SWAP("Ожидание смены слота"), ROTATION("Ожидание прицеливания"),
        SERVER("Ожидание подтверждения сервера"), DELAY("Ожидание Delay"),
        MINING("Копание"), BASE("Установка обсидиана"), PLACE("Установка кристалла"), BREAK("Ломание кристалла")
    }
    data class Snapshot(val reason: Reason, val detail: String = "", val pos: BlockPos? = null,
                        val damage: Float = 0f, val predictedDamage: Float = 0f, val miningTicks: Int = 0,
                        val predictionTicks: Double = 0.0, val progress: Float = 0f)
    var snapshot = Snapshot(Reason.IDLE)
        private set

    fun reset() { snapshot = Snapshot(Reason.IDLE) }
    fun status(reason: Reason, detail: String = "", pos: BlockPos? = null, progress: Float = 0f) {
        val old = snapshot
        snapshot = if (pos != null && pos == old.pos) old.copy(reason = reason, detail = detail, progress = progress)
            else Snapshot(reason, detail, pos, progress = progress)
    }
    fun candidate(pos: BlockPos, damage: Float, predictedDamage: Float, miningTicks: Int, predictionTicks: Double) {
        snapshot = snapshot.copy(pos = pos, damage = damage, predictedDamage = predictedDamage,
            miningTicks = miningTicks, predictionTicks = predictionTicks)
    }

    class Search {
        var terrain = 0
        var tools = 0
        var accessible = 0
        var speed = 0
        var space = 0
        var damage = 0
        var predictedDamage = 0
        var safe = 0
        var mineFaces = 0
        var placeFaces = 0
        var crystalFaces = 0
        fun reason(): Reason = when {
            terrain == 0 -> Reason.NO_TERRAIN
            tools == 0 -> Reason.NO_TOOL
            accessible == 0 -> Reason.UNREACHABLE
            speed == 0 -> Reason.SLOW_TERRAIN
            space == 0 -> Reason.CRYSTAL_BLOCKED
            damage == 0 -> Reason.DAMAGE
            predictedDamage == 0 -> Reason.TARGET_MOVING
            safe == 0 -> Reason.SELF_DAMAGE
            mineFaces == 0 -> Reason.MINE_FACE
            placeFaces == 0 -> Reason.PLACE_FACE
            crystalFaces == 0 -> Reason.CRYSTAL_BLOCKED
            else -> Reason.IDLE
        }
    }
}
