package meow.ancient.client.gui.hud

import meow.ancient.client.extensions.mc
import java.util.ArrayDeque
import meow.ancient.module.modules.render.HideModule

internal const val HUD_NOTIFICATION_LIFETIME_NANOS = 2_800_000_000L

internal class HudNotificationMessage(
    val name: String,
    val detail: String,
    val enabled: Boolean,
    val createdAt: Long
)

object HudNotificationCenter {
    private const val MAX_PENDING = 20
    private val pending = ArrayDeque<HudNotificationMessage>(MAX_PENDING)

    private fun enqueue(name: String, detail: String, enabled: Boolean) {
        if (mc.player == null || mc.level == null) return
        if (pending.size == MAX_PENDING) pending.removeFirst()
        pending.addLast(HudNotificationMessage(name, detail, enabled, System.nanoTime()))
    }

    @JvmStatic
    @Synchronized
    fun moduleToggled(name: String, enabled: Boolean) {
        if (name.equals("ClickGUI", ignoreCase = true) || name.equals("Hide", ignoreCase = true) || HideModule.isActive) return
        enqueue(name, if (enabled) "Enabled" else "Disabled", enabled)
    }

    @JvmStatic
    @Synchronized
    fun settingToggled(name: String, enabled: Boolean) {
        enqueue(name, if (enabled) "Enabled" else "Disabled", enabled)
    }

    @JvmStatic
    @Synchronized
    fun push(name: String, detail: String, enabled: Boolean) {
        enqueue(name, detail, enabled)
    }

    @Synchronized
    internal fun poll(now: Long): HudNotificationMessage? {
        while (pending.isNotEmpty()) {
            val message = pending.removeFirst()
            if (now - message.createdAt < HUD_NOTIFICATION_LIFETIME_NANOS) return message
        }
        return null
    }

    @Synchronized
    fun clear() {
        pending.clear()
    }
}
