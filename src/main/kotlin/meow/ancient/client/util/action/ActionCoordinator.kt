package meow.ancient.client.util.action

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.SpearKillModule
import meow.ancient.module.modules.combat.TriggerBotModule
import meow.ancient.module.modules.player.PacketMineModule
import meow.ancient.module.modules.movement.PhaseModule
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.GamePacketTypes
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.tags.ItemTags
import java.util.concurrent.ConcurrentHashMap

object ActionCoordinator {
    private val state = ActionArbiter()
    private val walker = StackWalker.getInstance()
    private val owners = ConcurrentHashMap<String, ActionOwner>()
    private val manual = ActionOwner("Manual", ActionGroup.MANUAL, 1000)
    private val scope = ThreadLocal<ActionOwner>()
    private var player: LocalPlayer? = null
    private var level: ClientLevel? = null

    data class Snapshot(val preferred: String, val committed: String, val slotOwner: String,
        val lastBlocked: String, val blockedCount: Long)

    fun beginTick() {
        if (player !== mc.player || level !== mc.level) {
            state.reset()
            player = mc.player
            level = mc.level
        }
        state.beginTick()
        ClientDiagnostics.ensureInitialized()
    }

    fun resolveOwner(explicit: Any? = null): ActionOwner {
        if (explicit is ActionOwner) return explicit
        if (explicit is Module) return ownerFor(explicit.javaClass.name)
        scope.get()?.let { return it }
        return walker.walk { frames ->
            frames.limit(32).map { it.className }
                .filter { it.startsWith("meow.ancient.module.modules.") && it.substringAfterLast('.').contains("Module") && !it.substringAfterLast('.').startsWith("InventoryMoveModule") }
                .findFirst().map { ownerFor(it) }.orElse(manual)
        }
    }

    fun <T> withOwner(owner: ActionOwner, action: () -> T): T {
        val previous = scope.get()
        scope.set(owner)
        return try { action() } finally {
            if (previous == null) scope.remove() else scope.set(previous)
        }
    }

    private fun ownerFor(className: String): ActionOwner = owners.computeIfAbsent(className) {
        val name = it.substringAfterLast('.').substringBefore('$').removeSuffix("Module")
        val helper = name in setOf("AutoMace", "BreachSwap", "MaceKill", "TpAura")
        when {
            name in setOf("ClickPearl", "ElytraHelper", "Phase") -> ActionOwner(name, ActionGroup.MANUAL, 1000)
            name == "SpearKill" -> ActionOwner(name, ActionGroup.COMBAT, 330)
            name == "HitAura" -> ActionOwner(name, ActionGroup.COMBAT, 320)
            name == "TriggerBot" -> ActionOwner(name, ActionGroup.COMBAT, 300)
            it.contains(".combat.") -> ActionOwner(name, ActionGroup.COMBAT, 310, helper)
            name == "PacketMine" -> ActionOwner(name, ActionGroup.MINING, 200)
            else -> ActionOwner(name, ActionGroup.UTILITY, 100)
        }
    }

    fun manualUseActive(): Boolean {
        val player = mc.player ?: return false
        // SpearKill deliberately steers an already held spear; that use belongs to its combat action.
        if (SpearKillModule.isEnabled() && player.isUsingItem && player.useItem.`is`(ItemTags.SPEARS)) return false
        return player.isUsingItem || mc.options.keyUse.isDown
    }

    private fun preferred(): ActionOwner? = when {
        manualUseActive() -> manual
        PhaseModule.isPreparingThrow() -> resolveOwner(PhaseModule)
        SpearKillModule.isEnabled() && SpearKillModule.target != null -> resolveOwner(SpearKillModule)
        HitAuraModule.isEnabled() && HitAuraModule.currentTarget != null && !HitAuraModule.shouldIgnoreContext() -> resolveOwner(HitAuraModule)
        TriggerBotModule.isEnabled() && TriggerBotModule.target != null -> resolveOwner(TriggerBotModule)
        PacketMineModule.hasPendingAction() -> resolveOwner(PacketMineModule)
        else -> null
    }

    fun canAct(owner: Any? = null): Boolean {
        if (!mc.isSameThread || mc.player == null || mc.level == null) return false
        return state.canAct(resolveOwner(owner), preferred())
    }

    fun claimSlot(owner: ActionOwner) { state.claimSlot(owner) }

    /** A direct attack/use must not accidentally use a tool leased by another action group. */
    private fun ensureSlot(owner: ActionOwner): Boolean {
        val player = mc.player ?: return false
        if (SilentSwapUtil.serverSlot == player.inventory.selectedSlot || state.slotOwner?.group == owner.group) return true
        return SilentSwapUtil.prepareSlot(player.inventory.selectedSlot, owner = owner)
    }

    @JvmStatic
    fun canSendPacket(connection: Connection, packet: Packet<*>): Boolean {
        if (!mc.isSameThread || mc.player?.connection?.connection !== connection || !isSharedAction(packet)) return true
        val owner = resolveOwner()
        if (!canAct(owner)) return false
        return owner.group == ActionGroup.MINING || ensureSlot(owner)
    }

    @JvmStatic
    fun onPacketSent(connection: Connection, packet: Packet<*>) {
        if (!mc.isSameThread || mc.player?.connection?.connection !== connection || !isSharedAction(packet)) return
        state.commit(resolveOwner())
    }

    private fun isSharedAction(packet: Packet<*>): Boolean {
        if (packet is ServerboundPlayerActionPacket) return packet.action in setOf(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
            ServerboundPlayerActionPacket.Action.CHANGE_DESTROY_DIRECTION,
            ServerboundPlayerActionPacket.Action.STAB)
        return when (packet.type()) {
            GamePacketTypes.SERVERBOUND_ATTACK, GamePacketTypes.SERVERBOUND_PUNCH,
            GamePacketTypes.SERVERBOUND_INTERACT, GamePacketTypes.SERVERBOUND_SPECTATOR_ACTION,
            GamePacketTypes.SERVERBOUND_USE_ITEM, GamePacketTypes.SERVERBOUND_USE_ITEM_ON -> true
            else -> false
        }
    }

    fun snapshot(): Snapshot = Snapshot(preferred()?.name ?: "None", state.committed?.name ?: "None",
        state.slotOwner?.name ?: "None", state.lastBlocked, state.blockedCount)

    fun onSlotRestored() { state.releaseSlot() }
}
