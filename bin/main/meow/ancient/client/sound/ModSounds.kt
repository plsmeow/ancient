package meow.ancient.client.sound

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

object ModSounds {
    val MODULE_ENABLE: SoundEvent = register("module_enable")
    val MODULE_DISABLE: SoundEvent = register("module_disable")

    val MODULE_ENABLE1: SoundEvent = register("module_enable1")
    val MODULE_DISABLE1: SoundEvent = register("module_disable1")

    val MODULE_ENABLE2: SoundEvent = register("module_enable2")
    val MODULE_DISABLE2: SoundEvent = register("module_disable2")

    val SPEK: SoundEvent = register("spek")
    val KILL_SOUND: SoundEvent = register("kill_sound")

    private fun register(name: String): SoundEvent {
        val id = Identifier.fromNamespaceAndPath("ancient", name)
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
    }

    fun init() {
    }
}
