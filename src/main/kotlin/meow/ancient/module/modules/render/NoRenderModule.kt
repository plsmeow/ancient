package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.util.render.NoRenderParticleState
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.TerrainParticle
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntityType

object NoRenderModule : Module(
    name = "NoRender",
    category = RENDER,
    description = "Disables rendering of selected overlays, HUD elements, world features, and entities"
) {
    val overlays by checkboxPicker(
        "Overlays",
        listOf(
            "Portal",
            "Spyglass",
            "Nausea",
            "Pumpkin",
            "Powdered Snow",
            "Fire",
            "Liquid",
            "In-Wall",
            "Vignette",
            "Gui Background",
            "Totem Animation",
            "Eating Particles",
            "Enchantment Glint"
        )
    )

    val hudElements by checkboxPicker(
        "HUD",
        listOf(
            "Boss Bar",
            "Scoreboard",
            "Crosshair",
            "Title",
            "Held Item Name",
            "Potion Icons",
            "Signatures"
        )
    )

    val worldElements by checkboxPicker(
        "World",
        listOf(
            "Weather",
            "World Border",
            "Blindness",
            "Darkness",
            "Fog",
            "Enchantment Table Book",
            "Sign Text",
            "Beacon Beams",
            "Falling Blocks",
            "Firework Explosions"
        )
    )

    val entityElements by checkboxPicker(
        "Entity",
        listOf(
            "Armor",
            "Invisibility",
            "Glowing",
            "Dead Entities",
            "Nametags"
        )
    )

    val entities by picker("Entities", emptySet(), MinecraftPickerCatalogs.entities)
    val particles by picker("Particles", emptySet(), MinecraftPickerCatalogs.particles)
    val blockEntities by picker("Block Entities", emptySet(), MinecraftPickerCatalogs.blockEntities)

    fun noPortalOverlay(): Boolean = isEnabled() && "Portal" in overlays
    fun noSpyglassOverlay(): Boolean = isEnabled() && "Spyglass" in overlays
    fun noNausea(): Boolean = isEnabled() && "Nausea" in overlays
    fun noPumpkinOverlay(): Boolean = isEnabled() && "Pumpkin" in overlays
    fun noPowderedSnowOverlay(): Boolean = isEnabled() && "Powdered Snow" in overlays
    fun noFireOverlay(): Boolean = isEnabled() && "Fire" in overlays
    fun noLiquidOverlay(): Boolean = isEnabled() && "Liquid" in overlays
    fun noInWallOverlay(): Boolean = isEnabled() && "In-Wall" in overlays
    fun noVignette(): Boolean = isEnabled() && "Vignette" in overlays
    fun noGuiBackground(): Boolean = isEnabled() && "Gui Background" in overlays
    fun noTotemAnimation(): Boolean = isEnabled() && "Totem Animation" in overlays
    fun noEatParticles(): Boolean = isEnabled() && "Eating Particles" in overlays
    fun noEnchantGlint(): Boolean = isEnabled() && "Enchantment Glint" in overlays

    fun noBossBar(): Boolean = isEnabled() && "Boss Bar" in hudElements
    fun noScoreboard(): Boolean = isEnabled() && "Scoreboard" in hudElements
    fun noCrosshair(): Boolean = isEnabled() && "Crosshair" in hudElements
    fun noTitle(): Boolean = isEnabled() && "Title" in hudElements
    fun noHeldItemName(): Boolean = isEnabled() && "Held Item Name" in hudElements
    fun noPotionIcons(): Boolean = isEnabled() && "Potion Icons" in hudElements
    fun noSignatures(): Boolean = isEnabled() && "Signatures" in hudElements

    fun noWeather(): Boolean = isEnabled() && "Weather" in worldElements
    fun noWorldBorder(): Boolean = isEnabled() && "World Border" in worldElements
    fun noBlindness(): Boolean = isEnabled() && "Blindness" in worldElements
    fun noDarkness(): Boolean = isEnabled() && "Darkness" in worldElements
    fun noFog(): Boolean = isEnabled() && "Fog" in worldElements
    fun noEnchTableBook(): Boolean = isEnabled() && "Enchantment Table Book" in worldElements
    fun noSignText(): Boolean = isEnabled() && "Sign Text" in worldElements
    fun noBeaconBeams(): Boolean = isEnabled() && "Beacon Beams" in worldElements
    fun noFallingBlocks(): Boolean = isEnabled() && "Falling Blocks" in worldElements
    fun noFireworkExplosions(): Boolean = isEnabled() && "Firework Explosions" in worldElements

    fun noArmor(): Boolean = isEnabled() && "Armor" in entityElements
    fun noInvisibility(): Boolean = isEnabled() && "Invisibility" in entityElements
    fun noGlowing(): Boolean = isEnabled() && "Glowing" in entityElements
    fun noDeadEntities(): Boolean = isEnabled() && "Dead Entities" in entityElements
    fun noNametags(): Boolean = isEnabled() && "Nametags" in entityElements

    fun noEntity(entity: Entity): Boolean =
        isEnabled() && (BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() in entities
            || (noFallingBlocks() && entity is net.minecraft.world.entity.item.FallingBlockEntity))

    fun noParticle(particle: ParticleType<*>): Boolean =
        isEnabled() && (BuiltInRegistries.PARTICLE_TYPE.getKey(particle).toString() in particles
            || (noFireworkExplosions() && (particle === ParticleTypes.FIREWORK || particle === ParticleTypes.FLASH)))

    fun noParticle(particle: Particle): Boolean {
        if (!isEnabled()) return false
        // Breaking/hitting blocks and firework trails can bypass the particle factory.
        val type = (particle as NoRenderParticleState).`ancient$getParticleType`()
            ?: if (particle is TerrainParticle) ParticleTypes.BLOCK else return false
        return noParticle(type)
    }

    fun noBlockEntity(blockEntity: BlockEntityType<*>): Boolean =
        isEnabled() && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity).toString() in blockEntities
}
