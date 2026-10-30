package meow.ancient.client.util.render.font

import com.mojang.blaze3d.platform.NativeImage
import meow.ancient.client.util.other.fromAssets
import meow.ancient.client.util.render.LinearDynamicTexture
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

object VulkanFontAtlas {
    private val textureCache = ConcurrentHashMap<String, Identifier>()

    fun getOrRegister(fontName: String, pngPath: String): Identifier {
        return textureCache.computeIfAbsent(fontName) {
            val safeName = fontName.lowercase().replace('-', '_')
            val identifier = Identifier.fromNamespaceAndPath("ancient", "textures/font/$safeName.png")

            val stream = fromAssets(pngPath) ?: error("Font texture not found for Vulkan: $pngPath")
            val image = stream.use { NativeImage.read(it) }

            val dynamicTexture = LinearDynamicTexture({ "ancient_font_$safeName" }, image)
            Minecraft.getInstance().textureManager.register(identifier, dynamicTexture)
            identifier
        }
    }
}
