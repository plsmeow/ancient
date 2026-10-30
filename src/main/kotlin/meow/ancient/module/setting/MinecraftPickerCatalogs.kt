package meow.ancient.module.setting

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.level.block.Block

object MinecraftPickerCatalogs {
    fun <T : Any> registry(title: String, registry: Registry<T>, label: (T) -> String, preview: (T, Identifier) -> PickerPreview?): PickerCatalog<T> =
        PickerCatalog(title) {
            registry.map { value ->
                val id = checkNotNull(registry.getKey(value))
                PickerEntry(id.toString(), label(value), value, preview(value, id))
            }
        }

    val items: PickerCatalog<Item> = registry("Items", BuiltInRegistries.ITEM, { it.defaultInstance.hoverName.string }) { item, _ ->
        if (item === Items.AIR) null else PickerPreview.Item(item.defaultInstance)
    }

    val blocks: PickerCatalog<Block> = registry("Blocks", BuiltInRegistries.BLOCK, { it.name.string }) { block, id ->
        val item = block.asItem()
        if (item !== Items.AIR) PickerPreview.Item(item.defaultInstance)
        else when (id.path) {
            "water" -> PickerPreview.Item(Items.WATER_BUCKET.defaultInstance)
            "lava" -> PickerPreview.Item(Items.LAVA_BUCKET.defaultInstance)
            "fire", "soul_fire" -> PickerPreview.Item(Items.FLINT_AND_STEEL.defaultInstance)
            "nether_portal", "end_portal", "end_gateway" -> PickerPreview.Item(Items.ENDER_EYE.defaultInstance)
            else -> PickerPreview.Item(Items.BARRIER.defaultInstance)
        }
    }

    val entities: PickerCatalog<EntityType<*>> = registry("Entity Types", BuiltInRegistries.ENTITY_TYPE, { it.description.string }) { type, id ->
        val egg = SpawnEggItem.byId(type).orElse(null)
        if (egg != null) PickerPreview.Item(egg.value().defaultInstance)
        else {
            val item: Item = when (id.path) {
                "player" -> Items.PLAYER_HEAD
                "item" -> Items.CHEST
                "experience_orb" -> Items.EXPERIENCE_BOTTLE
                "lightning_bolt" -> Items.LIGHTNING_ROD.weathering().unaffected()
                "falling_block" -> Items.SAND
                "fishing_bobber" -> Items.FISHING_ROD
                "fireball", "small_fireball" -> Items.FIRE_CHARGE
                "dragon_fireball", "area_effect_cloud" -> Items.DRAGON_BREATH
                "wither_skull" -> Items.WITHER_SKELETON_SKULL
                "ender_dragon" -> Items.DRAGON_HEAD
                "wither" -> Items.NETHER_STAR
                else -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.NAME_TAG)
            }
            PickerPreview.Item(item.defaultInstance)
        }
    }

    val effects: PickerCatalog<MobEffect> = registry("Mob Effects", BuiltInRegistries.MOB_EFFECT, { it.displayName.string }) { _, id ->
        PickerPreview.Sprite(Identifier.fromNamespaceAndPath(id.namespace, "mob_effect/${id.path}"))
    }
}
