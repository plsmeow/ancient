package meow.ancient.module.modules.render

import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Palette
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.ProjectionUtil
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.event.events.OverlayRenderEvent
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f
import sweetie.evaware.flora.api.Commando
import kotlin.math.ceil

object TagsModule : Module(
    name = "Tags",
    category = RENDER,
    description = ""
) {
    val size by number(
        name = "Size",
        value = 1.0,
        min = 0.5,
        max = 2.5,
        step = 0.05,
        precision = 2,
        description = ""
    )

    val self by boolean(
        name = "Self",
        value = true,
        description = ""
    )

    val items by boolean(
        name = "Items",
        value = true,
        description = ""
    )

    val entityTypes by picker("Entity Types", setOf("minecraft:player"), MinecraftPickerCatalogs.entities)
    private var cachedSelection: Set<String>? = null
    private val selectedTypes = HashSet<EntityType<*>>()

    private fun isTarget(entity: Entity): Boolean {
        if (cachedSelection !== entityTypes) {
            cachedSelection = entityTypes
            selectedTypes.clear()
            for (id in entityTypes) {
                MinecraftPickerCatalogs.entities.find(id)?.value?.let(selectedTypes::add)
            }
        }
        if (entity.type !in selectedTypes || !entity.isAlive || entity.isRemoved) return false
        return entity !== mc.player || (self && !mc.options.cameraType.isFirstPerson)
    }

    private fun projectTag(target: Entity, partialTicks: Float): Vector3f? {
        val interpX = Mth.lerp(partialTicks.toDouble(), target.xo, target.x)
        val interpY = Mth.lerp(partialTicks.toDouble(), target.yo, target.y)
        val interpZ = Mth.lerp(partialTicks.toDouble(), target.zo, target.z)
        val attachmentY = target.attachments
            .getNullable(EntityAttachment.NAME_TAG, 0, target.getYRot(partialTicks))
            ?.y ?: (target.bbHeight.toDouble() + 0.25)
        val projected = ProjectionUtil.worldToScreen(interpX, interpY + attachmentY + 0.2, interpZ) ?: return null
        if (projected.x < 0f || projected.x > mc.window.guiScaledWidth || projected.y < 0f || projected.y > mc.window.guiScaledHeight) return null
        return projected
    }

    private val absorptionHp = Color4b(255, 215, 64, 255)

    private class ProjectedTag {
        var target: Entity? = null
        var screenX: Float = 0f
        var screenY: Float = 0f
        var depth: Float = 0f

        fun set(target: Entity, screenX: Float, screenY: Float, depth: Float) {
            this.target = target
            this.screenX = screenX
            this.screenY = screenY
            this.depth = depth
        }
    }

    private val DEPTH_COMPARATOR = Comparator<ProjectedTag> { a, b -> b.depth.compareTo(a.depth) }
    private val tagPool = ArrayList<ProjectedTag>()
    private val activeTags = ArrayList<ProjectedTag>()

    private fun obtainTag(): ProjectedTag {
        return if (tagPool.isNotEmpty()) tagPool.removeAt(tagPool.lastIndex) else ProjectedTag()
    }

    private val equipmentCache = ArrayList<ItemStack>(6)
    private val itemTagPool = ArrayList<ProjectedTag>()
    private val activeItemTags = ArrayList<ProjectedTag>()

    private fun obtainItemTag(): ProjectedTag {
        return if (itemTagPool.isNotEmpty()) itemTagPool.removeAt(itemTagPool.lastIndex) else ProjectedTag()
    }

    fun renderItems(graphics: GuiGraphicsExtractor, partialTicks: Float) {
        if (!isEnabled() || !items) return
        safeRun {
            itemTagPool.addAll(activeItemTags)
            activeItemTags.clear()

            for (target in level.entitiesForRendering()) {
                val player = target as? Player ?: continue
                if (!isTarget(player)) continue
                val projected = projectTag(player, partialTicks) ?: continue
                val tag = obtainItemTag()
                tag.set(player, projected.x, projected.y, projected.z)
                activeItemTags.add(tag)
            }

            if (activeItemTags.isEmpty()) return@safeRun

            activeItemTags.sortWith(DEPTH_COMPARATOR)

            val tagScale = size.toFloat()
            val itemScale = 0.7f * tagScale
            val rectH = 13.5f * tagScale
            val step = 14.0f * itemScale
            val itemSize = 16.0f * itemScale

            val pose = graphics.pose()

            for (tag in activeItemTags) {
                val player = tag.target as? Player ?: continue

                equipmentCache.clear()
                equipmentCache.add(player.getItemBySlot(EquipmentSlot.HEAD))
                equipmentCache.add(player.getItemBySlot(EquipmentSlot.CHEST))
                equipmentCache.add(player.getItemBySlot(EquipmentSlot.LEGS))
                equipmentCache.add(player.getItemBySlot(EquipmentSlot.FEET))
                equipmentCache.add(player.getItemBySlot(EquipmentSlot.MAINHAND))
                equipmentCache.add(player.getItemBySlot(EquipmentSlot.OFFHAND))
                equipmentCache.removeIf { it.isEmpty }

                if (equipmentCache.isEmpty()) continue

                val count = equipmentCache.size
                val totalW = (count - 1) * step + itemSize
                val startX = tag.screenX - totalW * 0.5f
                val rectY = tag.screenY - rectH
                val itemY = rectY - itemSize - 1.5f * tagScale

                for (i in 0 until count) {
                    val stack = equipmentCache[i]
                    val itemX = startX + i * step

                    pose.pushMatrix()
                    try {
                        pose.translate(itemX, itemY)
                        pose.scale(itemScale, itemScale)
                        graphics.fakeItem(stack, 0, 0)
                        graphics.itemDecorations(mc.font, stack, 0, 0)
                    } finally {
                        pose.popMatrix()
                    }
                }
            }
        }
    }

    @Commando
    fun onOverlayRender(event: OverlayRenderEvent) {
        safeRun {
            val partialTicks = event.partialTicks

            tagPool.addAll(activeTags)
            activeTags.clear()

            for (target in level.entitiesForRendering()) {
                if (!isTarget(target)) continue
                val projected = projectTag(target, partialTicks) ?: continue
                val tag = obtainTag()
                tag.set(target, projected.x, projected.y, projected.z)
                activeTags.add(tag)
            }

            if (activeTags.isEmpty()) return@safeRun

            activeTags.sortWith(DEPTH_COMPARATOR)

            val matrix = MatrixControl.matrix4fStack
            val palette = M3Theme.current
            val scale = size.toFloat()
            val font = Fonts.google_sans_medium

            val fontSize = 9.0f * scale
            val badgeFontSize = 8.0f * scale

            val padX = 5.0f * scale
            val gap = 4.0f * scale
            val badgePadX = 3.5f * scale
            val badgeH = 11.0f * scale
            val badgeRadius = 3.0f * scale

            val rectH = 13.5f * scale
            val rectRadius = 4.0f * scale

            val bgColor = palette.surface.with(a = 215)
            val badgeBg = palette.primaryContainer.with(a = 120)

            for (tag in activeTags) {
                val target = tag.target ?: continue
                val nameText = target.name.string

                val living = target as? LivingEntity
                val totalHp = living?.let { AnticheatManager.getHealth(it).coerceAtLeast(0f) } ?: 0f
                val hp = if (living != null && AnticheatManager.isScoreboardHealth) totalHp else (living?.health?.coerceAtLeast(0f) ?: 0f)
                val absorption = if (living != null && AnticheatManager.isScoreboardHealth) 0f else (living?.absorptionAmount?.coerceAtLeast(0f) ?: 0f)
                val hpInt = ceil(totalHp).toInt()
                val hpText = "$hpInt HP"
                val maxHp = maxOf(living?.maxHealth?.coerceAtLeast(1f) ?: 1f, totalHp)
                val hpPct = (hp / maxHp).coerceIn(0f, 1f)
                val hpColor = getHealthColor(hpPct, absorption > 0f, palette)

                val isFriend = target is Player && FriendRepository.isFriend(target)
                val isTargetPlayer = target is Player && TargetRepository.isTarget(target)
                val statusColor = when {
                    isFriend -> Color4b(85, 239, 196, 255)
                    isTargetPlayer -> Color4b(255, 82, 82, 255)
                    else -> null
                }
                val barW = 2.5f * scale
                val barGap = 3.5f * scale
                val totalBarW = if (statusColor != null) barW + barGap else 0f

                val nameW = font.width(nameText, fontSize)
                val hpTextW = if (living != null) font.width(hpText, badgeFontSize) else 0f
                val badgeW = if (living != null) hpTextW + badgePadX * 2f else 0f
                val rectW = padX + nameW + (if (living != null) gap + badgeW else 0f) + totalBarW + padX
                val rectX = tag.screenX - rectW * 0.5f
                val rectY = tag.screenY - rectH

                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = rectX,
                    y = rectY,
                    width = rectW,
                    height = rectH,
                    color = bgColor,
                    radius = rectRadius,
                    pipeline = ClientRenderPipeline.HUD_RECT
                )

                val nameY = rectY + rectH * 0.5f - fontSize * 0.56f
                font.drawString(
                    matrix = matrix,
                    text = nameText,
                    x = rectX + padX,
                    y = nameY,
                    size = fontSize,
                    color = palette.onSurface,
                    shadow = false,
                    pipeline = ClientRenderPipeline.HUD_TEXT
                )
                if (statusColor != null) {
                    val barH = if (living != null) badgeH else (rectH - 4.0f * scale)
                    val barX = rectX + rectW - padX - barW
                    val barY = rectY + (rectH - barH) * 0.5f
                    RenderUtils.roundedRect(
                        matrix = matrix,
                        x = barX,
                        y = barY,
                        width = barW,
                        height = barH,
                        color = statusColor,
                        radius = 1.25f * scale,
                        pipeline = ClientRenderPipeline.HUD_RECT
                    )
                }

                if (living == null) continue

                val badgeX = rectX + rectW - padX - totalBarW - badgeW
                val badgeY = rectY + (rectH - badgeH) * 0.5f

                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = badgeX,
                    y = badgeY,
                    width = badgeW,
                    height = badgeH,
                    color = badgeBg,
                    radius = badgeRadius,
                    pipeline = ClientRenderPipeline.HUD_RECT
                )

                val badgeTextY = badgeY + badgeH * 0.5f - badgeFontSize * 0.56f
                font.drawCenteredString(
                    matrix = matrix,
                    text = hpText,
                    centerX = badgeX + badgeW * 0.5f,
                    y = badgeTextY,
                    size = badgeFontSize,
                    color = hpColor,
                    shadow = false,
                    pipeline = ClientRenderPipeline.HUD_TEXT
                )
            }
        }
    }

    private fun getHealthColor(pct: Float, hasAbsorption: Boolean, palette: M3Palette): Color4b {
        if (hasAbsorption && pct >= 0.99f) {
            return absorptionHp
        }
        return if (pct < 0.5f) {
            palette.error.interpolateTo(palette.tertiary, (pct * 2f).toDouble())
        } else {
            palette.tertiary.interpolateTo(palette.primary, ((pct - 0.5f) * 2f).toDouble())
        }
    }

    fun shouldHideVanillaTag(entity: Entity): Boolean {
        if (!isEnabled() || mc.level == null || !isTarget(entity)) return false
        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        return projectTag(entity, partialTicks) != null
    }
}
