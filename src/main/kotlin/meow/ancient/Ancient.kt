package meow.ancient

import meow.ancient.client.command.CommandManager
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.listener.ListenerManager
import meow.ancient.client.util.render.VulkanShapePipelines
import meow.ancient.client.util.render.font.VulkanFontPipelines
import meow.ancient.module.ModuleManager
import net.fabricmc.api.ClientModInitializer

object Ancient : ClientModInitializer {
	override fun onInitializeClient() {
		VulkanFontPipelines.init()
		VulkanShapePipelines.init()
		ListenerManager.load()
		ModuleManager.load()
		CommandManager.load()
		ConfigManager.loadConfig("default")

		Runtime.getRuntime().addShutdownHook(Thread {
			ConfigManager.saveConfig("default")
		})
	}
}
