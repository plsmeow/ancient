package meow.ancient.client.util.render

import meow.ancient.client.extensions.CLIENT_ID
import meow.ancient.client.extensions.LOGGER
import meow.ancient.client.render.texture.TextureStorage
import meow.ancient.client.render.texture.loader.TextureLoaders
import meow.ancient.client.render.texture.texture.ColorMode
import meow.ancient.client.render.texture.texture.GLTexture
import meow.ancient.client.render.texture.texture.TextureFiltering
import meow.ancient.client.render.texture.texture.TextureWrapping
import meow.ancient.client.util.other.fromAssets

object TextureLoader {
    private val loadQueue: MutableMap<String, String> = HashMap()
    private var bootstrapped = false

    fun register(name: String, path: String) {
        if (bootstrapped) {
            loadSingle(name, path)
        } else {
            loadQueue[name] = path
        }
    }

    fun load() {
        if (bootstrapped) return

        val start = System.currentTimeMillis()
        loadQueue.forEach { (name, path) ->
            loadSingle(name, path)
        }
        loadQueue.clear()
        bootstrapped = true

        LOGGER.info("Loaded textures in {} ms", System.currentTimeMillis() - start)
    }

    private fun loadSingle(name: String, path: String) {
        val resourcePath = "assets/$CLIENT_ID/$path"
        val stream = fromAssets(resourcePath)

        if (stream == null) {
            LOGGER.warn("Texture file not found: {}", path)
            return
        }

        try {
            stream.use {
                val info = TextureLoaders.INPUT_STREAM.load(
                    it,
                    ColorMode.RGBA,
                    TextureFiltering.SMOOTH,
                    TextureWrapping.DEFAULT
                )
                val texture = GLTexture.of(name, info)
                TextureStorage.addTexture(name, texture)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to load texture: {}", name, e)
        }
    }

    fun get(name: String): GLTexture? = TextureStorage.getTexture(name)
}
