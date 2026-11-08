package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.sound.ModSounds
import meow.ancient.module.Module
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent

object ClientSoundsModule : Module(
    name = "ClientSounds",
    category = RENDER,
    description = "Plays sound on module toggle"
) {
    val soundMode = mode("Sound Mode", "First", listOf("First", "Second", "Third"))
    val volume = number("Volume", 1.0, 0.1, 2.0, 0.1, 1)
    val pitch = number("Pitch", 1.0, 0.5, 2.0, 0.1, 1)

    private var lastSound: SoundInstance? = null

    fun play(enabled: Boolean) {
        if (!isEnabled() || HideModule.isActive) return
        val soundManager = mc.soundManager ?: return

        lastSound?.let { soundManager.stop(it) }

        val soundToPlay: SoundEvent = when (soundMode.value) {
            "Second" -> if (enabled) ModSounds.MODULE_ENABLE1 else ModSounds.MODULE_DISABLE1
            "Third" -> if (enabled) ModSounds.MODULE_ENABLE2 else ModSounds.MODULE_DISABLE2
            else -> if (enabled) ModSounds.MODULE_ENABLE else ModSounds.MODULE_DISABLE
        }

        val instance = SimpleSoundInstance.forUI(soundToPlay, pitch.value.toFloat(), volume.value.toFloat())
        lastSound = instance
        soundManager.play(instance)
    }
}
