package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.gui.material.components.CustomTextField
import meow.ancient.client.util.inventory.InventoryActionSource
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
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.phys.Vec2
import sweetie.evaware.flora.api.Commando
import java.util.ArrayDeque

object InventoryMoveModule : Module(
    name = "Inventory Move",
    category = MOVEMENT,
    description = ""
) {
    val sneak by boolean("Sneak", false, "")

    private const val PAUSE_NANOS = 100_000_000L
    private val packetQueue = ArrayDeque<Packet<*>>()
    private class PendingAction(val action: Runnable, val actionOwner: Any?)
    private val actionQueue = ArrayDeque<PendingAction>()
    private var owner: LocalPlayer? = null
    private var connection: ClientPacketListener? = null
    private var menu: AbstractContainerMenu? = null
    private var pauseUntil = 0L
    private var lastPreparationRequest = 0L
    private var preparing = false
    private val transactions = ArrayDeque<InventoryActionSource>()
    private var movementPaused = false
    private var flushing = false
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
            pauseUntil = if (slownessActive()) System.nanoTime() + PAUSE_NANOS else 0L
            if (slownessActive()) pauseMovement() else restoreMovement()
        }
    }

    fun runInventoryAction(source: InventoryActionSource, action: Runnable, actionOwner: Any? = null) {
        if (!mc.isSameThread) {
            val player = mc.player ?: return
            val listener = player.connection
            val inventoryOwner = player.containerMenu
            mc.execute {
                if (mc.player === player && player.connection === listener
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

    fun isInventoryActionSlowed(): Boolean = slownessActive()
        && (transactions.peekFirst() == InventoryActionSource.INVENTORY || System.nanoTime() < pauseUntil)

    fun applySlownessInput(input: ClientInput) {
        if (!isInventoryActionSlowed()) return
        pauseMovement()
        input.keyPresses = Input.EMPTY
        input.moveVector = Vec2.ZERO
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
        if (AnticheatManager.isSwapBypassActive && screen is AbstractContainerScreen<*>) {
            mc.options.keySprint.isDown = false
            owner?.isSprinting = false
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
        if (!isContainerActionPacket(packet) || currentScreen !is AbstractContainerScreen<*>) return
        if (packet is ServerboundContainerClickPacket && packet.containerInput() == ContainerInput.SWAP) {
            val player = owner ?: return
            val inventoryMenu = menu ?: return
            if (packet.containerId() == inventoryMenu.containerId
                && InventoryActionSource.fromMenuSlot(player, inventoryMenu, packet.slotNum().toInt(),
                    packet.buttonNum().toInt()) == InventoryActionSource.HOTBAR) {
                if (packetQueue.isNotEmpty()) flushPackets()
                return
            }
        }
        if (packetQueue.isEmpty() && !MoveUtil.isMoving() && !mc.options.keyJump.isDown
            && !MoveUtil.movementKeys.any(MoveUtil::isKeyPressed)) return
        packetQueue.addLast(packet)
        event.cancel = true
        preparing = true
        lastPreparationRequest = System.nanoTime()
        pauseUntil = lastPreparationRequest + PAUSE_NANOS
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
        if (screen !is AbstractContainerScreen<*> || packetQueue.isEmpty()) return
        event.cancel = true
        if (closingScreen != null) return
        closingScreen = screen
        preparing = true
        lastPreparationRequest = System.nanoTime()
        pauseUntil = lastPreparationRequest + PAUSE_NANOS
        pauseMovement()
    }

    fun shouldPreventSprint(): Boolean = slownessActive()
        && (isInventoryActionSlowed() || currentScreen is AbstractContainerScreen<*>)

    fun canMoveInScreen(screen: Screen): Boolean = isEnabled() && mc.isWindowActive && !isTypingScreen(screen)

    private fun slownessActive(): Boolean = isEnabled() && AnticheatManager.isSwapBypassActive

    private fun beginTransaction(): Boolean {
        if (slownessActive()) {
            val now = System.nanoTime()
            lastPreparationRequest = now
            if (pauseUntil == 0L) pauseUntil = now + PAUSE_NANOS
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
        if (packetQueue.isNotEmpty() && (!slownessActive() || now >= pauseUntil)) {
            flushPackets()
            finishClose()
            preparing = false
            pauseUntil = if (slownessActive()) System.nanoTime() + PAUSE_NANOS else 0L
            if (!validateOwner()) return
        }
        while (actionQueue.isNotEmpty()) {
            if (!beginTransaction()) break
            val action = actionQueue.removeFirst().action
            try {
                action.run()
            } finally {
                endInventoryAction(InventoryActionSource.INVENTORY)
            }
            if (!validateOwner()) return
        }
        if (slownessActive() && actionQueue.isEmpty() && packetQueue.isEmpty()
            && transactions.isEmpty() && System.nanoTime() >= pauseUntil
            && (!preparing || System.nanoTime() - lastPreparationRequest >= PAUSE_NANOS)) {
            preparing = false
            pauseUntil = 0L
        }
        if (isInventoryActionSlowed()) owner?.input?.let(::applySlownessInput)
        else restoreMovement()
    }

    private fun pauseMovement() {
        movementPaused = true
        MoveUtil.resetMovementKeys()
        mc.player?.isSprinting = false
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
        if (owner !== player || connection !== player.connection || menu !== player.containerMenu) {
            clearPending()
            owner = player
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
        connection = null
        menu = null
        pauseUntil = 0L
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
