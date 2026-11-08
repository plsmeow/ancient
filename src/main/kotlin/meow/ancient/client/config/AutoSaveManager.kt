package meow.ancient.client.config

import meow.ancient.client.gui.material.ThemeManager
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.macro.MacroRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.client.util.staff.StaffRepository
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import sweetie.evaware.flora.api.Commando
import meow.ancient.module.modules.render.HideModule

object AutoSaveManager {
    private const val INTERVAL_MS = 10 * 60 * 1000L
    private var lastSaveTime = System.currentTimeMillis()

    init {
        EventManager.register(this)
    }

    fun saveAll() {
        if (HideModule.isActive) return
        try {
            ConfigManager.saveConfig("default")
            FriendRepository.save()
            TargetRepository.save()
            MacroRepository.save()
            ThemeManager.save()
            StaffRepository.save()
        } catch (_: Exception) {
        }
    }

    fun onTick() {
        val now = System.currentTimeMillis()
        if (now - lastSaveTime >= INTERVAL_MS) {
            lastSaveTime = now
            saveAll()
        }
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        saveAll()
    }
}
