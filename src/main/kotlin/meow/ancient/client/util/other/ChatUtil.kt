package meow.ancient.client.util.other

import meow.ancient.client.extensions.mc
import net.minecraft.network.chat.Component

object ChatUtil {
    fun send(message: String) {
        mc.player?.sendSystemMessage(Component.literal("§8[§dAncient§8] §f$message"))
    }

    fun success(message: String) {
        mc.player?.sendSystemMessage(Component.literal("§8[§dAncient§8] §a$message"))
    }

    fun error(message: String) {
        mc.player?.sendSystemMessage(Component.literal("§8[§dAncient§8] §c$message"))
    }

    fun info(message: String) {
        mc.player?.sendSystemMessage(Component.literal("§8[§dAncient§8] §b$message"))
    }

    fun raw(message: String) {
        mc.player?.sendSystemMessage(Component.literal(message))
    }
}
