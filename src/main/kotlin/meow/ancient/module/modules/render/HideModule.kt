package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.hud.HudNotificationCenter
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.ModuleManager
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundChatPacket
import sweetie.evaware.flora.api.Commando

object HideModule : Module(
    name = "Hide",
    category = RENDER,
    description = "Скрывает клиент"
) {
    @JvmField
    @Volatile
    var isActive: Boolean = false

    private val savedStates = HashMap<String, Boolean>()
    private var tickCounter = 0
    private var chatCleared = false

    override fun onEnable() {
        isActive = true

        if (currentScreen === ClickGuiScreen) {
            mc.gui.setScreen(null)
        }

        savedStates.clear()
        for (module in ModuleManager.modules) {
            if (module === this) continue
            savedStates[module.name] = module.isEnabled()
            if (module.isEnabled()) {
                module.setEnabled(false)
            }
        }

        tickCounter = 0
        chatCleared = false
        HudNotificationCenter.clear()

        mc.execute {
            mc.updateTitle()
        }
    }

    override fun onDisable() {
        isActive = false

        for (module in ModuleManager.modules) {
            if (module === this) continue
            val wasEnabled = savedStates[module.name]
            if (wasEnabled == true) {
                module.setEnabled(true)
            }
        }
        savedStates.clear()

        mc.execute {
            mc.updateTitle()
        }
    }

    @Commando
    fun onPacketSend(event: PacketSendEvent) {
        if (!isActive) return

        when (val packet = event.packet) {
            is ServerboundChatPacket -> {
                if (packet.message.trim().equals("%show", ignoreCase = true)) {
                    event.cancel = true
                    setEnabled(false)
                }
            }
            is ServerboundChatCommandPacket -> {
                if (packet.command.trim().equals("%show", ignoreCase = true)) {
                    event.cancel = true
                    setEnabled(false)
                }
            }
        }
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!isActive) return

        tickCounter++
        if (!chatCleared && tickCounter >= 200) {
            chatCleared = true
            mc.gui.hud.chat.clearMessages(false)
        }
    }
}
