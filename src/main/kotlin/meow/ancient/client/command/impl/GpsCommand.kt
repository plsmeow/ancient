package meow.ancient.client.command.impl

import meow.ancient.client.command.Command
import meow.ancient.client.util.gps.GpsRenderer
import meow.ancient.client.util.other.ChatUtil

object GpsCommand : Command(
    name = "gps",
    description = "Points a compass arrow at the given coordinates",
    syntax = "gps <x> <z> | gps off"
) {
    override fun execute(args: Array<String>) {
        if (args.firstOrNull()?.equals("off", ignoreCase = true) == true) {
            GpsRenderer.disable()
            ChatUtil.info("GPS disabled")
            return
        }
        if (args.size < 2) {
            sendUsage()
            return
        }
        val x = args[0].toDoubleOrNull()
        val z = args[1].toDoubleOrNull()
        if (x == null || z == null) {
            ChatUtil.error("Coordinates must be numbers")
            return
        }
        GpsRenderer.setTarget(x, z)
        ChatUtil.success("GPS set to ${x.toInt()}, ${z.toInt()}")
    }
}
