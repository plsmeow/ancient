package meow.ancient.client.command.impl

import meow.ancient.client.command.Command
import meow.ancient.client.extensions.safeRun
import meow.ancient.client.util.other.ChatUtil
import kotlin.math.cos
import kotlin.math.sin

object HClipCommand : Command(
    name = "hclip",
    description = "Teleport horizontally forward or backward",
    syntax = "hclip <offset>",
    aliases = listOf("hc")
) {
    override fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            sendUsage()
            return
        }

        val offset = args[0].toDoubleOrNull()
        if (offset == null) {
            ChatUtil.error("Invalid offset number '${args[0]}'")
            return
        }

        safeRun {
            val yawRad = Math.toRadians(player.yRot.toDouble())
            val dx = -sin(yawRad) * offset
            val dz = cos(yawRad) * offset
            player.setPos(player.x + dx, player.y, player.z + dz)
            val sign = if (offset >= 0) "+$offset" else "$offset"
            ChatUtil.success("Teleported $sign blocks horizontally")
        } ?: run {
            ChatUtil.error("Cannot execute hclip: not in game!")
        }
    }
}
