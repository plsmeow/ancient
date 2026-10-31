package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.chunk.status.ChunkStatus
import kotlin.math.floor

object BlockEspModule : Module(
    name = "BlockESP",
    category = RENDER,
    description = "Highlights selected blocks and block entities"
) {
    val blocks = picker(
        name = "Blocks",
        value = setOf(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:ender_chest",
            "minecraft:barrel",
            "minecraft:spawner"
        ),
        catalog = MinecraftPickerCatalogs.blocks
    )
    val range = number("Range", 64.0, 16.0, 128.0, 1.0, 0)
    val color = color("Color", Color4b(255, 170, 0, 255))
    val filled = boolean("Filled", true)

    private var registered = false
    private var cachedSelection: Set<String>? = null
    private val selectedBlocks = HashSet<Block>()

    private fun updateSelection() {
        val current = blocks.value
        if (cachedSelection != current) {
            cachedSelection = current.toSet()
            selectedBlocks.clear()
            for (idStr in current) {
                val id = Identifier.tryParse(idStr) ?: continue
                BuiltInRegistries.BLOCK.getOptional(id).ifPresent(selectedBlocks::add)
            }
        }
    }

    override fun onEnable() {
        if (!registered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (!isEnabled() || !inGame) return@register
                val level = mc.level ?: return@register
                val player = mc.player ?: return@register

                updateSelection()
                if (selectedBlocks.isEmpty()) return@register

                val maxDist = range.value
                val maxDistSq = maxDist * maxDist
                val px = player.x
                val py = player.y
                val pz = player.z

                val minChunkX = floor((px - maxDist) / 16.0).toInt()
                val maxChunkX = floor((px + maxDist) / 16.0).toInt()
                val minChunkZ = floor((pz - maxDist) / 16.0).toInt()
                val maxChunkZ = floor((pz + maxDist) / 16.0).toInt()

                val strokeColor = color.value
                val fillColor = strokeColor.alpha(60)
                val style = if (filled.value) {
                    GizmoStyle.strokeAndFill(strokeColor.argb, 2f, fillColor.argb)
                } else {
                    GizmoStyle.stroke(strokeColor.argb, 2f)
                }

                val hasNonEntityBlock = selectedBlocks.any { it !is EntityBlock }

                for (cx in minChunkX..maxChunkX) {
                    for (cz in minChunkZ..maxChunkZ) {
                        val chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false) ?: continue
                        if (!hasNonEntityBlock) {
                            for (pos in chunk.blockEntitiesPos) {
                                if (pos.distToCenterSqr(px, py, pz) > maxDistSq) continue
                                val state = chunk.getBlockState(pos)
                                if (state.block in selectedBlocks) {
                                    Gizmos.cuboid(pos, style)
                                }
                            }
                        } else {
                            chunk.findBlocks({ it.block in selectedBlocks }) { pos, _ ->
                                if (pos.distToCenterSqr(px, py, pz) <= maxDistSq) {
                                    Gizmos.cuboid(pos, style)
                                }
                            }
                        }
                    }
                }
            }
            registered = true
        }
    }
}
