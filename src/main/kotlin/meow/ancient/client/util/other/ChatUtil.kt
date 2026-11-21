package meow.ancient.client.util.other

import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Palette
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object ChatUtil {
    fun send(message: String) {
        val theme = M3Theme.current
        post(message, theme, theme.onSurface)
    }

    fun success(message: String) {
        val theme = M3Theme.current
        post(message, theme, theme.tertiary)
    }

    fun error(message: String) {
        val theme = M3Theme.current
        post(message, theme, theme.error)
    }

    fun info(message: String) {
        val theme = M3Theme.current
        post(message, theme, theme.secondary)
    }

    fun raw(message: String) {
        val theme = M3Theme.current
        post(message, theme, theme.onSurface, false)
    }

    private fun post(message: String, theme: M3Palette, color: Color4b, prefixed: Boolean = true) {
        val player = mc.player ?: return
        val content = format(message, theme, color)
        if (!prefixed) {
            player.sendSystemMessage(content)
            return
        }
        val brackets = Style.EMPTY.withColor(theme.outline.toTextColor())
        player.sendSystemMessage(Component.literal("[").setStyle(brackets)
            .append(Component.literal("Ancient").setStyle(Style.EMPTY.withColor(theme.primary.toTextColor())))
            .append(Component.literal("] ").setStyle(brackets))
            .append(content))
    }

    private fun format(message: String, theme: M3Palette, color: Color4b): Component {
        val defaultStyle = Style.EMPTY.withColor(color.toTextColor())
        var index = message.indexOf('§')
        if (index < 0) return Component.literal(message).setStyle(defaultStyle)
        val result = Component.empty()
        var style = defaultStyle
        var start = 0
        while (index < message.length - 1) {
            if (message[index] != '§') {
                index++
                continue
            }
            val nextStyle = when (message[index + 1].lowercaseChar()) {
                '0', '8' -> Style.EMPTY.withColor(theme.outline.toTextColor())
                '1', '5', '9', 'd' -> Style.EMPTY.withColor(theme.primary.toTextColor())
                '2', '6', 'a', 'e' -> Style.EMPTY.withColor(theme.tertiary.toTextColor())
                '3', 'b' -> Style.EMPTY.withColor(theme.secondary.toTextColor())
                '4', 'c' -> Style.EMPTY.withColor(theme.error.toTextColor())
                '7' -> Style.EMPTY.withColor(theme.onSurfaceVariant.toTextColor())
                'f' -> Style.EMPTY.withColor(theme.onSurface.toTextColor())
                'k' -> style.withObfuscated(true)
                'l' -> style.withBold(true)
                'm' -> style.withStrikethrough(true)
                'n' -> style.withUnderlined(true)
                'o' -> style.withItalic(true)
                'r' -> defaultStyle
                else -> {
                    index++
                    continue
                }
            }
            if (index > start) result.append(Component.literal(message.substring(start, index)).setStyle(style))
            style = nextStyle
            index += 2
            start = index
        }
        if (start < message.length) result.append(Component.literal(message.substring(start)).setStyle(style))
        return result
    }
}
