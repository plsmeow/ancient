package meow.ancient.client.gui.hud

import meow.ancient.client.extensions.mc
import java.util.ArrayDeque

internal const val HUD_NOTIFICATION_LIFETIME_NANOS = 2_800_000_000L

internal class HudNotificationMessage(
    val name: String,
    val enabled: Boolean,
    val createdAt: Long
)

object HudNotificationCenter {
    private const val MAX_PENDING = 20
    private val pending = ArrayDeque<HudNotificationMessage>(MAX_PENDING)

    @JvmStatic
    @Synchronized
    fun moduleToggled(name: String, enabled: Boolean) {
        if (name.equals("ClickGUI", ignoreCase = true) || mc.player == null || mc.level == null) return
        if (pending.size == MAX_PENDING) pending.removeFirst()
        pending.addLast(HudNotificationMessage(name, enabled, System.nanoTime()))
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
