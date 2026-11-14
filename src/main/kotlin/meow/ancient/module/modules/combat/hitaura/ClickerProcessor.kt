package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.mixin.LivingEntityAccessor
import meow.ancient.module.modules.player.PacketMineToolSpeed
import meow.ancient.module.modules.combat.HitAuraModule.ClickMode
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToLong

class ClickerProcessor(private val thunderHackTiming: Boolean = false) {
    private val clock = AttackClickClock()
    private val cooldown = AttackCooldownClock()
    private val hitDelay = AttackTickClock()

    fun reset() {
        clock.reset()
        cooldown.reset()
        hitDelay.reset()
    }

    fun onUpdate() {
        hitDelay.tick()
    }

    fun onPacketSent(packet: Packet<*>) {
        if (packet is ServerboundAttackPacket || packet is ServerboundPunchPacket
            || packet is ServerboundSetCarriedItemPacket) cooldown.restart(System.nanoTime())
    }

    /** An axe disables blocking without a charged/critical hit; keep the accepted-click rate limit. */
    fun isReadyToBreakShield(ticksAhead: Int = 0): Boolean =
        clock.ready(System.nanoTime(), ticksAhead.coerceIn(0, AttackTiming.SPRINT_PREPARATION_TICKS) * 50_000_000L)

    fun isReadyToClick(
        clickMode: ClickMode,
        cps: Double,
        randomize: Boolean,
        randomSpread: Double,
        preparingNextAttack: Boolean = false,
        weapon: ItemStack? = null,
        predictionTicks: Int = AttackTiming.ATTACK_PREPARATION_TICKS
    ): Boolean {
        if (!inGame) return false
        val localPlayer = mc.player ?: return false
        val ticksAhead = if (preparingNextAttack)
            predictionTicks.coerceIn(1, AttackTiming.SPRINT_PREPARATION_TICKS) else 0
        val lookahead = ticksAhead * 50_000_000L
        val now = System.nanoTime()
        if (!clock.ready(now, lookahead)) return false

        return when (clickMode) {
            ClickMode.MODE_1_9 -> {
                if (thunderHackTiming && !hitDelay.ready(ticksAhead)) return false
                val actual = localPlayer.getAttribute(Attributes.ATTACK_SPEED) ?: return false
                val speed = if (weapon == null || ItemStack.matches(weapon, localPlayer.mainHandItem)) actual.value else {
                    PacketMineToolSpeed.attributeValue(actual, Attributes.ATTACK_SPEED, localPlayer.mainHandItem, weapon)
                }
                val localTicks = (localPlayer as LivingEntityAccessor).`ancient$getAttackStrengthTicker`()
                    + ticksAhead
                val ticks = minOf(localTicks, cooldown.elapsedTicks(now, lookahead))
                if (thunderHackTiming) ThunderHackTiming.charged(ticks, speed)
                else AttackTiming.charge(ticks, speed) >= AttackTiming.COOLDOWN_THRESHOLD
            }
            ClickMode.MODE_1_8 -> {
                true
            }
        }
    }

    fun onAttackExecuted(
        clickMode: ClickMode,
        cps: Double,
        randomize: Boolean,
        randomSpread: Double
    ) {
        val delay = if (clickMode == ClickMode.MODE_1_8) computeNext18Delay(cps, randomize, randomSpread) else 45L
        clock.clicked(System.nanoTime(), delay)
        if (thunderHackTiming && clickMode == ClickMode.MODE_1_9) hitDelay.clicked(ThunderHackTiming.HIT_TICKS)
    }

    private fun computeNext18Delay(cps: Double, randomize: Boolean, randomSpread: Double): Long {
        val safeCps = cps.coerceIn(1.0, 25.0)
        if (!randomize || randomSpread <= 0.01) {
            return (1000.0 / safeCps).roundToLong().coerceAtLeast(25L)
        }

        val rng = ThreadLocalRandom.current()
        val gaussian = rng.nextGaussian() * (randomSpread * 0.65)
        val spike = when {
            rng.nextDouble() < 0.08 -> -randomSpread * 0.85
            rng.nextDouble() < 0.10 -> randomSpread * 0.95
            else -> 0.0
        }

        val effectiveCps = (safeCps + gaussian + spike).coerceIn(1.0, 28.0)
        return (1000.0 / effectiveCps).roundToLong().coerceIn(25L, 1000L)
    }
}
