package meow.ancient.module.setting

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import java.util.Locale

sealed interface PickerPreview {
    fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int)

    class Item(val stack: ItemStack) : PickerPreview {
        override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int) {
            val pose = graphics.pose()
            pose.pushMatrix()
            try {
                pose.translate(x.toFloat(), y.toFloat())
                pose.scale(size / 16f)
                graphics.fakeItem(stack, 0, 0)
            } finally {
                pose.popMatrix()
            }
        }
    }

    class Sprite(val id: Identifier) : PickerPreview {
        override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, size, size)
        }
    }

    class Color(val argb: Int) : PickerPreview {
        override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int) {
            graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, argb)
        }
    }
}

class PickerEntry<T>(val id: String, val label: String, val value: T?, val preview: PickerPreview?) {
    private val searchText = "$label\n$id".lowercase(Locale.ROOT)
    fun matches(query: String): Boolean = searchText.contains(query)
}

class PickerCatalog<T>(val title: String, private val provider: () -> List<PickerEntry<T>>) {
    private var cachedEntries: List<PickerEntry<T>>? = null
    private var index: Map<String, PickerEntry<T>> = emptyMap()

    val entries: List<PickerEntry<T>>
        get() {
            cachedEntries?.let { return it }
            val loaded = provider().distinctBy { it.id }.sortedWith(compareBy({ it.label.lowercase(Locale.ROOT) }, { it.id }))
            index = loaded.associateBy { it.id }
            cachedEntries = loaded
            return loaded
        }

    fun find(id: String): PickerEntry<T>? {
        entries
        return index[id]
    }

    companion object {
        fun of(title: String, options: Collection<String>): PickerCatalog<String> =
            PickerCatalog(title) {
                options.map { PickerEntry(it, it, it, null) }
            }

        fun of(title: String, options: Map<String, String>): PickerCatalog<String> =
            PickerCatalog(title) {
                options.map { (id, label) -> PickerEntry(id, label, id, null) }
            }

        fun of(title: String, vararg options: String): PickerCatalog<String> =
            of(title, options.toList())
    }
}
