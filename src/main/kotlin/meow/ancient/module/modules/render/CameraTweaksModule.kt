package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.module.Module

object CameraTweaksModule : Module(
    name = "CameraTweaks",
    category = RENDER,
    description = "Custom camera distance and anti-collision clipping"
) {
    val cameraClip by boolean("Camera Clip", true, "Prevents third-person camera from colliding with blocks")
    val distance by number("Distance", 4.0, 1.0, 20.0, 0.5, 1, "Custom third-person camera distance")
}
