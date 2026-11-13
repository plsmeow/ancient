package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.ChamsRenderer
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType

object ChamsModule : Module(
    name = "Chams",
    category = RENDER,
    description = "Model silhouette outline, fill and glow for selected entities and first-person hands"
) {
    val entityTypes by picker("Entity Types", setOf("minecraft:player"), MinecraftPickerCatalogs.entities)
    val hands by boolean("Hands", true)
    val self by boolean("Self", false)
    val outline by boolean("Outline", true)
    val fill by boolean("Fill", true)
    val glow by boolean("Glow", true)
    val glowRadius by number("Glow Radius", 10.0, 2.0, 32.0, 0.5, 1, isVisible = { glow })
    val glowStrength by number("Glow Strength", 1.2, 0.1, 3.0, 0.05, 2, isVisible = { glow })
    val lineWidth by number("Line Width", 2.0, 0.5, 6.0, 0.5, 1, isVisible = { outline })
    val fillOpacity by number("Fill Opacity", 0.15, 0.0, 1.0, 0.01, 2, isVisible = { fill })
    val theme by boolean("Theme", true)
    val color by color("Color", Color4b(180, 160, 255), isVisible = { !theme })

    private var cachedSelection: Set<String>? = null
    private val selectedTypes = HashSet<EntityType<*>>()

    fun shouldRender(entity: Entity): Boolean {
        if (!isEnabled() || (!outline && !fill && !glow) || !entity.isAlive || entity.isRemoved) return false
        if (cachedSelection !== entityTypes) {
            cachedSelection = entityTypes
            selectedTypes.clear()
            for (id in entityTypes) MinecraftPickerCatalogs.entities.find(id)?.value?.let(selectedTypes::add)
        }
        if (entity.type !in selectedTypes) return false
        return entity !== mc.player || (self && !mc.options.cameraType.isFirstPerson)
    }

    fun shouldRenderHands(): Boolean = isEnabled() && hands && (outline || fill || glow)

    fun outlineColor(): Color4b = (if (theme) M3Theme.current.primary else color).let {
        if (outline) it else it.alpha(0)
    }

    fun fillColor(): Color4b = (if (theme) M3Theme.current.primary else color).let {
        it.alpha(if (fill) (it.a * fillOpacity).toInt() else 0)
    }

    fun glowColor(): Color4b = (if (theme) M3Theme.current.primary else color).let {
        if (glow) it else it.alpha(0)
    }

    override fun onDisable() {
        ChamsRenderer.close()
    }
}
