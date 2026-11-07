package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.gui.material.components.CustomTextField
import meow.ancient.client.util.inventory.InventoryActionSource
import meow.ancient.client.util.inventory.SlownessManager
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.event.events.ScreenCloseEvent
import meow.ancient.module.Module
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.client.gui.screens.inventory.BookEditScreen
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.phys.Vec2
import sweetie.evaware.flora.api.Commando
import java.util.ArrayDeque

object InventoryMoveModule : Module(
    name = "Inventory Move",
    category = MOVEMENT,
    description = ""
) {
    val sneak by boolean("Sneak", false, "")

    private val pauseNanos: Long
        get() = if (AnticheatManager.isSwapBypassGrimFast) 50_000_000L else 100_000_000L
    private val packetQueue = ArrayDeque<Packet<*>>()
    private class PendingAction(val action: Runnable, val actionOwner: Any?)
    private val actionQueue = ArrayDeque<PendingAction>()
    private var owner: LocalPlayer? = null
    private var world: ClientLevel? = null
    private var connection: ClientPacketListener? = null
    private var menu: AbstractContainerMenu? = null
    private var pauseUntil = 0L
    private var lastPreparationRequest = 0L
    private var preparing = false
    private val transactions = ArrayDeque<InventoryActionSource>()
    private var flushing = false
    private var movementPaused = false
    private var movementSyncPending = false
    private var closingScreen: AbstractContainerScreen<*>? = null

    private object DisconnectListener {
        @Commando
        fun onDisconnect(event: ClientDisconnectEvent) {
            clearPending()
        }
    }

    init {
        EventManager.register(DisconnectListener)
        ClientTickEvents.END_CLIENT_TICK.register { processPending() }
    }

    override fun onDisable() {
        if (mc.isSameThread) processPending()
        else mc.execute { processPending() }
    }

    fun beginInventoryAction(source: InventoryActionSource): Boolean {
        if (!mc.isSameThread || !validateOwner()) return false
        if (transactions.isNotEmpty()) {
            if (source == InventoryActionSource.INVENTORY && transactions.first == InventoryActionSource.HOTBAR) {
                return false
            }
            transactions.addLast(source)
            return true
        }
        if (source == InventoryActionSource.HOTBAR) {
            if (packetQueue.isNotEmpty()) {
                flushPackets()
                finishClose()
                if (!validateOwner()) return false
            }
            transactions.addLast(source)
            return true
        }
        if (!slownessActive()) {
            processPending()
            if (!validateOwner()) return false
        }
        if (actionQueue.isNotEmpty() || packetQueue.isNotEmpty()) return false
        return beginTransaction()
    }

    fun endInventoryAction(source: InventoryActionSource) {
        if (!validateOwner() || transactions.isEmpty()) return
        check(transactions.removeLast() == source)
        if (transactions.isEmpty() && source == InventoryActionSource.INVENTORY) {
            preparing = false
            pauseUntil = if (slownessActive()) System.nanoTime() + pauseNanos else 0L
            if (slownessActive()) pauseMovement() else restoreMovement()
        }
    }

    fun runInventoryAction(source: InventoryActionSource, action: Runnable, actionOwner: Any? = null) {
        if (!mc.isSameThread) {
            val player = mc.player ?: return
            val listener = player.connection
            val level = mc.level ?: return
            val inventoryOwner = player.containerMenu
            mc.execute {
                if (mc.player === player && mc.level === level && player.connection === listener
                    && player.containerMenu === inventoryOwner && listener.connection.isConnected) {
                    runInventoryAction(source, action, actionOwner)
                }
            }
            return
        }
        if (!validateOwner()) return
        if (transactions.isNotEmpty() || source == InventoryActionSource.HOTBAR) {
            if (!beginInventoryAction(source)) return
            try {
                action.run()
            } finally {
                endInventoryAction(source)
            }
            return
        }
        actionQueue.addLast(PendingAction(action, actionOwner))
        processPending()
    }

    fun cancelInventoryActions(actionOwner: Any) {
        if (!mc.isSameThread) {
            mc.execute { cancelInventoryActions(actionOwner) }
            return
        }
        val actions = actionQueue.iterator()
        while (actions.hasNext()) {
            if (actions.next().actionOwner === actionOwner) actions.remove()
        }
        cancelInventoryPreparation()
    }

    fun cancelInventoryPreparation() {
        if (!mc.isSameThread) {
            mc.execute { cancelInventoryPreparation() }
            return
        }
        if (transactions.isNotEmpty() || actionQueue.isNotEmpty() || packetQueue.isNotEmpty()) return
        preparing = false
        pauseUntil = 0L
        restoreMovement()
    }

    fun isInventoryActionSlowed(): Boolean = SlownessManager.isSlowed() || slownessActive()
        && (transactions.peekFirst() == InventoryActionSource.INVENTORY || System.nanoTime() < pauseUntil)

    fun applySlownessInput(input: ClientInput) {
        if (!isInventoryActionSlowed()) return
        pauseMovement()
        input.keyPresses = Input.EMPTY
        input.moveVector = Vec2.ZERO
    }

    fun processBeforeMovement() {
        processPending()
    }

    fun onMovementSent() {
        if (movementSyncPending) movementSyncPending = false
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        processPending()
        if (!validateOwner()) return
        if (isInventoryActionSlowed()) {
            applySlownessInput(owner!!.input)
            return
        }
        val screen = currentScreen ?: return
        if (!canMoveInScreen(screen)) return
        MoveUtil.updateMovementKeys(includeSneak = sneak)
        if (AutoSprintModule.shouldSprint() && MoveUtil.isMoving()) {
            owner?.isSprinting = true
        }
    }

    @Commando
    fun onPacket(event: PacketSendEvent) {
        if (flushing || transactions.isNotEmpty() || !validateOwner()) return
        if (!slownessActive()) {
            processPending()
            return
        }
        val packet = event.packet
        if (!isContainerActionPacket(packet) || SlownessManager.isExecutingPreparedAction()
            || currentScreen !is AbstractContainerScreen<*>) return
        if (packetQueue.isEmpty() && !MoveUtil.isMoving() && !mc.options.keyJump.isDown
            && !MoveUtil.movementKeys.any(MoveUtil::isKeyPressed)) return
        packetQueue.addLast(packet)
        event.cancel = true
        preparing = true
        lastPreparationRequest = System.nanoTime()
        movementSyncPending = true
        pauseUntil = lastPreparationRequest + pauseNanos
        pauseMovement()
    }

    @Commando
    fun onCloseScreen(event: ScreenCloseEvent) {
        if (flushing || transactions.isNotEmpty() || !validateOwner()) return
        if (!slownessActive()) {
            processPending()
            return
        }
        val screen = event.screen
        if (screen !is AbstractContainerScreen<*>) return
        if (packetQueue.isEmpty() && !MoveUtil.isMoving() && !mc.options.keyJump.isDown
            && !MoveUtil.movementKeys.any(MoveUtil::isKeyPressed) && owner?.isSprinting != true) return
        event.cancel = true
        if (closingScreen != null) return
        closingScreen = screen
        preparing = true
        movementSyncPending = true
        lastPreparationRequest = System.nanoTime()
        pauseUntil = lastPreparationRequest + pauseNanos
        pauseMovement()
    }

    fun shouldPreventSprint(): Boolean = isInventoryActionSlowed()

    fun canMoveInScreen(screen: Screen): Boolean = isEnabled() && mc.isWindowActive && !isTypingScreen(screen)

    private fun slownessActive(): Boolean = isEnabled() && AnticheatManager.isSwapBypassActive

    private fun beginTransaction(): Boolean {
        if (slownessActive()) {
            val now = System.nanoTime()
            lastPreparationRequest = now
            if (movementSyncPending) return false
            if (pauseUntil == 0L) pauseUntil = now + pauseNanos
            preparing = true
            pauseMovement()
            if (now < pauseUntil) return false
        } else {
            pauseUntil = 0L
            preparing = false
        }
        if (!validateOwner()) return false
        transactions.addLast(InventoryActionSource.INVENTORY)
        return true
    }

    private fun processPending() {
        if (!mc.isSameThread) {
            mc.execute { processPending() }
            return
        }
        if (transactions.isNotEmpty() || !validateOwner()) {
            if (!isInventoryActionSlowed()) restoreMovement()
            return
        }
        val now = System.nanoTime()
        if (!slownessActive()) {
            pauseUntil = 0L
            preparing = false
        }
        val hasPackets = packetQueue.isNotEmpty()
        val hasClosing = closingScreen != null
        if ((hasPackets || hasClosing) && (!slownessActive() || now >= pauseUntil)
            && !movementSyncPending) {
            if (hasPackets) flushPackets()
            if (hasClosing) finishClose()
            preparing = false
            pauseUntil = if (slownessActive()) System.nanoTime() + pauseNanos else 0L
            if (!validateOwner()) return
        }
        while (!movementSyncPending && System.nanoTime() >= pauseUntil && actionQueue.isNotEmpty()) {
            if (!beginTransaction()) break
            val action = actionQueue.removeFirst().action
            try {
                action.run()
            } finally {
                endInventoryAction(InventoryActionSource.INVENTORY)
            }
            if (!validateOwner()) return
        }
        if (slownessActive() && actionQueue.isEmpty() && packetQueue.isEmpty() && closingScreen == null
            && transactions.isEmpty() && System.nanoTime() >= pauseUntil
            && (!preparing || System.nanoTime() - lastPreparationRequest >= pauseNanos)) {
            preparing = false
            pauseUntil = 0L
        }
        if (isInventoryActionSlowed()) owner?.input?.let(::applySlownessInput)
        else restoreMovement()
    }

    private fun pauseMovement() {
        val player = mc.player
        movementPaused = true
        MoveUtil.resetMovementKeys()
        player?.isSprinting = false
        player?.input?.let {
            it.keyPresses = Input.EMPTY
            it.moveVector = Vec2.ZERO
        }
    }

    private fun restoreMovement() {
        if (!movementPaused || isInventoryActionSlowed()) return
        val screen = currentScreen
        if (mc.player == null || mc.level == null || !mc.isWindowActive
            || screen != null && !canMoveInScreen(screen)) {
            MoveUtil.resetMovementKeys()
            return
        }
        movementPaused = false
        MoveUtil.updateMovementKeys(includeSneak = screen == null || sneak)
    }

    private fun validateOwner(): Boolean {
        val player = mc.player
        if (player == null || mc.level == null || !player.connection.connection.isConnected) {
            clearPending()
            return false
        }
        if (owner !== player || world !== mc.level || connection !== player.connection || menu !== player.containerMenu) {
            clearPending()
            owner = player
            world = mc.level
            connection = player.connection
            menu = player.containerMenu
        }
        return true
    }

    private fun flushPackets() {
        val listener = connection ?: return
        if (!listener.connection.isConnected) {
            clearPending()
            return
        }
        flushing = true
        try {
            while (packetQueue.isNotEmpty()) listener.send(packetQueue.removeFirst())
        } finally {
            flushing = false
        }
    }

    private fun finishClose() {
        val screen = closingScreen ?: return
        closingScreen = null
        if (currentScreen !== screen || mc.player !== owner || owner?.containerMenu !== menu
            || connection?.connection?.isConnected != true) return
        flushing = true
        try {
            screen.onClose()
        } finally {
            flushing = false
        }
    }

    private fun clearPending() {
        packetQueue.clear()
        actionQueue.clear()
        transactions.clear()
        owner = null
        world = null
        connection = null
        menu = null
        pauseUntil = 0L
        movementSyncPending = false
        lastPreparationRequest = 0L
        preparing = false
        closingScreen = null
        restoreMovement()
    }

    private fun isContainerActionPacket(packet: Packet<*>): Boolean {
        return packet is ServerboundContainerClickPacket
            || packet is ServerboundContainerButtonClickPacket
            || packet is ServerboundSetCreativeModeSlotPacket
            || packet is ServerboundContainerSlotStateChangedPacket
    }

    private fun isTypingScreen(screen: Screen): Boolean {
        return screen is ChatScreen
            || CustomTextField.hasFocus
            || screen is AbstractSignEditScreen
            || screen is AnvilScreen
            || screen is AbstractCommandBlockEditScreen
            || screen is StructureBlockEditScreen
            || screen is BookEditScreen
            || screen.children().any { it is EditBox && it.isFocused }
    }
}
