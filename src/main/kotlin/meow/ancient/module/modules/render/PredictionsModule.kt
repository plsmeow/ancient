package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.ProjectionUtil
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.event.events.OverlayRenderEvent
import meow.ancient.event.events.WorldRenderEvent
import meow.ancient.module.Module
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import sweetie.evaware.flora.api.Commando

object PredictionsModule : Module(
    name = "Predictions",
    category = RENDER,
    description = "Показывает траекторию полёта снарядов"
) {
    private val targets = multiMode(
        "Таргеты",
        setOf("Эндерперлы", "Стрелы", "Трезубцы", "Зелья", "Опыт", "Снежки/яйца", "Предметы"),
        listOf("Эндерперлы", "Стрелы", "Трезубцы", "Зелья", "Опыт", "Снежки/яйца", "Предметы")
    )

    private val inHand = boolean("В руке", false)

    private data class Point(val stack: ItemStack, val pos: Vec3, val ticks: Int)

    private val points = ArrayList<Point>()

    @Commando
    fun onWorldRender(event: WorldRenderEvent) {
        points.clear()

        val level = mc.level ?: return

        for (entity in level.entitiesForRendering()) {
            if (!isTargetEnabled(entity)) continue
            if (visible(entity)) continue
            traceProjectile(entity)
        }

        if (inHand.value) {
            val held = createHeldThrowable() ?: return
            traceProjectile(held)
        }
    }

    @Commando
    fun onOverlayRender(event: OverlayRenderEvent) {
        if (points.isEmpty()) return

        val matrix = meow.ancient.client.util.render.engine.controls.MatrixControl.matrix4fStack
        val palette = M3Theme.current
        val font = Fonts.google_sans_regular

        for (point in points) {
            val screen = ProjectionUtil.worldToScreen(point.pos) ?: continue

            val time = point.ticks * 50 / 1000.0
            val text = "%.1f сек".format(time).replace(",", ".")
            val fontSize = 6.75f
            val textW = font.width(text, fontSize)

            val cx = screen.x
            val cy = screen.y

            val padX = 7f
            val padY = 2f
            val itemSize = 8f
            val gap = 2.5f

            val totalW = itemSize + gap + textW + padX * 2f
            val totalH = itemSize + padY * 2f

            val rectX = cx - totalW / 2f
            val rectY = cy - totalH / 2f

            RenderUtils.roundedRect(
                matrix = matrix,
                x = rectX,
                y = rectY,
                width = totalW,
                height = totalH,
                color = palette.surface.with(a = 200),
                radius = 3f,
                pipeline = ClientRenderPipeline.HUD_RECT
            )

            val itemX = rectX + padX
            val itemY = rectY + padY

            val graphics = meow.ancient.client.util.render.RenderUtils.currentGraphics
            if (graphics != null) {
                val pose = graphics.pose()
                pose.pushMatrix()
                pose.translate(itemX, itemY)
                pose.scale(0.5f, 0.5f)
                graphics.fakeItem(point.stack, 0, 0)
                pose.popMatrix()
            }

            font.drawString(
                matrix = matrix,
                text = text,
                x = itemX + itemSize + gap,
                y = rectY + totalH / 2f - font.height(fontSize) / 2f,
                size = fontSize,
                color = palette.onSurface,
                shadow = false,
                pipeline = ClientRenderPipeline.HUD_TEXT
            )
        }
    }

    private fun traceProjectile(entity: Entity) {
        val level = mc.level ?: return
        var motion = entity.deltaMovement
        var pos = entity.position()
        var ticks = 0

        repeat(300) iter@{
            val prevPos = pos
            pos = pos.add(motion)
            motion = calculateMotion(entity, prevPos, motion)

            val result = level.clip(
                ClipContext(prevPos, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)
            )
            if (result.type != HitResult.Type.MISS) {
                pos = result.location
            }

            val palette = M3Theme.current
            val alpha = (Mth.clamp(ticks / 25.0f, 0f, 1f) * 255).toInt()
            val color = palette.primary.alpha(alpha)

            Gizmos.line(prevPos, pos, color.argb, 2f).setAlwaysOnTop()

            val inEntity = level.getEntities(entity, AABB(prevPos, pos).inflate(0.25)) { ent ->
                ent is LivingEntity && ent != mc.player && ent.isAlive
            }.isNotEmpty()

            if (result.type == HitResult.Type.BLOCK || pos.y < -128 || inEntity || result.type == HitResult.Type.ENTITY) {
                recordHit(entity, pos, ticks)
                return
            }
            ticks++
        }
    }

    private fun calculateMotion(entity: Entity, prevPos: Vec3, motion: Vec3): Vec3 {
        val level = mc.level ?: return motion
        val blockPos = BlockPos.containing(prevPos)
        val isInWater = level.getBlockState(blockPos).fluidState.`is`(FluidTags.WATER)

        val multiply = when {
            entity is ThrownTrident -> 0.99
            entity is AbstractArrow && isInWater -> 0.6
            isInWater -> 0.8
            else -> 0.99
        }

        return motion.scale(multiply).add(0.0, -entity.gravity, 0.0)
    }

    private fun recordHit(entity: Entity, pos: Vec3, ticks: Int) {
        val point = when (entity) {
            is ItemEntity -> Point(entity.item, pos, ticks)
            is ThrowableItemProjectile -> Point(entity.item, pos, ticks)
            is AbstractArrow -> entity.pickupItemStackOrigin.takeIf { !it.isEmpty }?.let { Point(it, pos, ticks) } ?: return
            else -> return
        }
        points.add(point)
    }

    private fun visible(entity: Entity): Boolean {
        val posChange = entity.x == entity.xo && entity.y == entity.yo && entity.z == entity.zo
        val itemEntityCheck = entity is ItemEntity && (entity.onGround() || isBoxInBlock(entity.boundingBox.inflate(2.0), Blocks.WATER))
        return posChange || itemEntityCheck
    }

    private fun isBoxInBlock(box: AABB, block: Block): Boolean {
        val level = mc.level ?: return false
        return BlockPos.betweenClosed(box).any { level.getBlockState(it).block == block }
    }

    private fun isTargetEnabled(entity: Entity): Boolean = when (entity) {
        is ThrownTrident -> targets.isSelected("Трезубцы")
        is AbstractArrow -> targets.isSelected("Стрелы")
        is ItemEntity -> targets.isSelected("Предметы")
        is ThrowableItemProjectile -> {
            val stack = entity.item
            when {
                stack.`is`(Items.ENDER_PEARL) -> targets.isSelected("Эндерперлы")
                stack.`is`(Items.SPLASH_POTION) || stack.`is`(Items.LINGERING_POTION) || stack.`is`(Items.POTION) -> targets.isSelected("Зелья")
                stack.`is`(Items.EXPERIENCE_BOTTLE) -> targets.isSelected("Опыт")
                stack.`is`(Items.SNOWBALL) || stack.`is`(Items.EGG) -> targets.isSelected("Снежки/яйца")
                else -> false
            }
        }
        else -> false
    }

    private fun createHeldThrowable(): Entity? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        for (hand in InteractionHand.entries) {
            val stack = player.getItemInHand(hand)
            val entity = throwableFromStack(stack, player, level) ?: continue
            return entity
        }
        return null
    }

    private fun throwableFromStack(
        stack: ItemStack,
        player: net.minecraft.client.player.LocalPlayer,
        level: net.minecraft.client.multiplayer.ClientLevel
    ): ThrowableItemProjectile? {
        val (entity, roll, speed) = when {
            stack.`is`(Items.ENDER_PEARL) && targets.isSelected("Эндерперлы") ->
                Triple(ThrownEnderpearl(level, player, stack), 0f, 1.5f)
            stack.`is`(Items.SNOWBALL) && targets.isSelected("Снежки/яйца") ->
                Triple(Snowball(level, player, stack), 0f, 1.5f)
            stack.`is`(Items.EGG) && targets.isSelected("Снежки/яйца") ->
                Triple(ThrownEgg(level, player, stack), 0f, 1.5f)
            stack.`is`(Items.EXPERIENCE_BOTTLE) && targets.isSelected("Опыт") ->
                Triple(ThrownExperienceBottle(level, player, stack), -20f, 0.7f)
            stack.`is`(Items.SPLASH_POTION) && targets.isSelected("Зелья") ->
                Triple(ThrownSplashPotion(level, player, stack), -20f, 0.5f)
            stack.`is`(Items.LINGERING_POTION) && targets.isSelected("Зелья") ->
                Triple(ThrownLingeringPotion(level, player, stack), -20f, 0.5f)
            else -> return null
        }
        entity.setPos(player.x, player.eyeY - 0.1, player.z)
        entity.shootFromRotation(player, player.xRot, player.yRot, roll, speed, 0f)
        return entity
    }
}
