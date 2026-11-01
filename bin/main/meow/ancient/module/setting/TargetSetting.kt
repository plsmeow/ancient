package meow.ancient.module.setting

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType

class TargetSetting(
    name: String,
    defaultValue: Set<String> = setOf("minecraft:player"),
    description: String = "",
    isVisible: () -> Boolean = { true }
) : Setting<Set<String>>(name, defaultValue, defaultValue, description, isVisible) {
    private var cachedSelection: Set<String>? = null
    private val selectedTypes = HashSet<EntityType<*>>()

    fun isSelected(id: String): Boolean = value.contains(id)

    fun isSelected(entity: Entity): Boolean {
        if (cachedSelection !== value) {
            cachedSelection = value
            selectedTypes.clear()
            for (id in value) {
                MinecraftPickerCatalogs.entities.find(id)?.value?.let(selectedTypes::add)
            }
        }
        return entity.type in selectedTypes
    }

    fun isSelected(type: EntityType<*>): Boolean {
        if (cachedSelection !== value) {
            cachedSelection = value
            selectedTypes.clear()
            for (id in value) {
                MinecraftPickerCatalogs.entities.find(id)?.value?.let(selectedTypes::add)
            }
        }
        return type in selectedTypes
    }

    fun toggle(id: String) {
        val updated = LinkedHashSet(value)
        if (!updated.add(id)) {
            updated.remove(id)
        }
        set(updated)
    }

    fun selectAll(ids: Collection<String>) {
        val updated = LinkedHashSet(value)
        updated.addAll(ids)
        set(updated)
    }

    fun deselectAll(ids: Collection<String>) {
        val updated = LinkedHashSet(value)
        updated.removeAll(ids.toSet())
        set(updated)
    }

    override fun serialize(): JsonElement {
        val array = JsonArray()
        for (id in value) {
            array.add(id)
        }
        return array
    }

    override fun deserialize(element: JsonElement) {
        if (!element.isJsonArray) return
        val set = LinkedHashSet<String>()
        for (entry in element.asJsonArray) {
            val str = entry.asString
            when (str) {
                "Players", "Naked" -> set.add("minecraft:player")
                "Mobs" -> {
                    set.add("minecraft:zombie")
                    set.add("minecraft:skeleton")
                    set.add("minecraft:creeper")
                    set.add("minecraft:spider")
                }
                "Animals" -> {
                    set.add("minecraft:cow")
                    set.add("minecraft:pig")
                    set.add("minecraft:sheep")
                    set.add("minecraft:chicken")
                }
                "Invisibles" -> {}
                else -> {
                    if (str.startsWith("minecraft:") || str.contains(':')) {
                        set.add(str)
                    } else {
                        set.add("minecraft:$str")
                    }
                }
            }
        }
        set(set)
    }
}
