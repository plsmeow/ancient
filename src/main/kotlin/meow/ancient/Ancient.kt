package meow.ancient

import meow.ancient.client.command.CommandManager
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.gui.material.ThemeManager
import meow.ancient.client.listener.ListenerManager
import meow.ancient.client.sound.ModSounds
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.inventory.SlownessManager
import meow.ancient.client.util.render.VulkanShapePipelines
import meow.ancient.client.util.render.font.VulkanFontPipelines
import meow.ancient.module.ModuleManager
import net.fabricmc.api.ClientModInitializer

object Ancient : ClientModInitializer {
	override fun onInitializeClient() {
		ModSounds.init()
		ThemeManager.load()
		VulkanFontPipelines.init()
		VulkanShapePipelines.init()
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { SlownessManager.update() }
		ListenerManager.load()
		ModuleManager.load()
		CommandManager.load()
		ConfigManager.loadConfig("default")

		Runtime.getRuntime().addShutdownHook(Thread {
			ConfigManager.saveConfig("default")
			FriendRepository.save()
			ThemeManager.save()
		})
	}
}
