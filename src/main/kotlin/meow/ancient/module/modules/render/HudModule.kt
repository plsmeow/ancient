package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.gui.hud.HudDragController
import meow.ancient.client.gui.hud.HudElement
import meow.ancient.client.gui.hud.HudRenderer
import meow.ancient.client.gui.hud.HudServerStats
import meow.ancient.client.gui.hud.HudSettingsPopup
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.OverlayRenderEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.setting.Setting
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import sweetie.evaware.flora.api.Commando

object HudModule : Module("HUD", RENDER, "Material HUD with live player and server information") {
    private val elementLabels = listOf("Watermark", "Keybind", "Potions", "TargetHUD", "StaffList",
        "ArrayList", "Coords", "Speed (BPS)", "TPS", "Ping", "Notifications", "Scoreboard")
    val elementsMultiMode = multiMode("Elements", elementLabels.toSet(), elementLabels)
    val elements by elementsMultiMode
    private val watermarkElementsSetting = multiMode("Watermark Parts", setOf("Client", "Nickname", "Fps"),
        listOf("Client", "Nickname", "Fps"), isVisible = { false })
    val watermarkElements by watermarkElementsSetting
    private val targetRayTraceSetting = boolean("RayTrace", false, isVisible = { false })
    val targetRayTrace by targetRayTraceSetting
    private val notificationLimitSetting = number("Notification Limit", 7.0, 1.0, 20.0, 1.0, 0, isVisible = { false })
    val notificationLimit by notificationLimitSetting
    private val scaleSetting = number("Scale", 0.75, 0.5, 1.5, 0.05, 2)
    val scale by scaleSetting
    private val blurSetting = boolean("Background Blur", false, description = "Apply Kawase blur behind HUD elements")
    val blur by blurSetting
    private val blurRadiusSetting = number("Blur Radius", 3.0, 1.0, 8.0, 0.5, 1, isVisible = { blur })
    val blurRadius by blurRadiusSetting
    private val blurIterationsSetting = number("Blur Quality", 4.0, 1.0, 6.0, 1.0, 0, isVisible = { blur })
    val blurIterations by blurIterationsSetting
    val dragController = HudDragController(this)

    fun isElementEnabled(element: HudElement): Boolean = elements.contains(elementLabels[element.ordinal])

    fun elementSettings(element: HudElement): List<Setting<*>> = when (element) {
        HudElement.WATERMARK -> listOf(watermarkElementsSetting)
        HudElement.TARGET -> listOf(targetRayTraceSetting)
        HudElement.NOTIFICATIONS -> listOf(notificationLimitSetting)
        else -> emptyList()
    }

    private val serverStats = HudServerStats()
    private val renderer = HudRenderer(serverStats)

    override fun onEnable() {
        dragController.clearInteraction()
        serverStats.reset()
        renderer.reset()
    }

    override fun onDisable() {
        HudSettingsPopup.close()
        dragController.clearInteraction()
        serverStats.reset()
        renderer.reset()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        renderer.update(this)
    }

    @Commando
    fun onOverlay(event: OverlayRenderEvent) {
        if (currentScreen !is ChatScreen) renderer.render()
    }

    fun renderChatOverlay() {
        renderer.render()
    }

    @Commando
    fun onPacket(event: PacketReceiveEvent) {
        when (val packet = event.packet) {
            is ClientboundSetTimePacket -> serverStats.record(packet.gameTime())
            is ClientboundLoginPacket, is ClientboundRespawnPacket -> serverStats.reset()
        }
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        HudSettingsPopup.close()
        dragController.clearInteraction()
        serverStats.reset()
        renderer.reset()
    }
}
