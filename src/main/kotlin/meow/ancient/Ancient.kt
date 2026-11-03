package meow.ancient

import meow.ancient.client.command.CommandManager
import meow.ancient.client.config.AutoSaveManager
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.gui.material.ThemeManager
import meow.ancient.client.listener.ListenerManager
import meow.ancient.client.sound.ModSounds
import meow.ancient.client.util.inventory.SlownessManager
import meow.ancient.client.util.render.VulkanShapePipelines
import meow.ancient.client.util.render.font.VulkanFontPipelines
import meow.ancient.event.EventManager
import meow.ancient.module.ModuleManager
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.hitaura.rotation.impl.NeuroRotation
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object Ancient : ClientModInitializer {
	override fun onInitializeClient() {
		ModSounds.init()
		ThemeManager.load()
		VulkanFontPipelines.init()
		VulkanShapePipelines.init()
        EventManager.register(SlownessManager)
		ClientTickEvents.END_CLIENT_TICK.register {
			SlownessManager.update()
			AutoSaveManager.onTick()
		}
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
			SlownessManager.clear()
			AutoSaveManager.saveAll()
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register {
			SlownessManager.clear()
			stopNeuroRecording()
			AutoSaveManager.saveAll()
		}
		ListenerManager.load()
		ModuleManager.load()
		CommandManager.load()
		ConfigManager.loadConfig("default")

		Runtime.getRuntime().addShutdownHook(Thread {
			stopNeuroRecording()
			AutoSaveManager.saveAll()
		})
	}

	private fun stopNeuroRecording() {
		val recorder = (HitAuraModule.rotationProcessor.getStrategy(HitAuraModule.RotationMode.NEURO) as NeuroRotation).recorder
		if (recorder.isRecording) recorder.stopRecording()
	}
}
