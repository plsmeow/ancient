package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.ExplosionDamageUtil
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.movement.HvhTargetPredict
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerMovementSentEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

object CrystalAuraTestModule : Module(
    name = "CrystalAuraTest",
    category = COMBAT,
    description = "Ставит и взрывает кристаллы, выбирает урон и расчищает место"
) {
    private const val RENDER_LIFETIME_MS = 400L
    private const val CRYSTAL_POWER = 12f
    private const val BLACKLIST_TICKS = 60
    private const val DIG_TIMEOUT_TICKS = 30

    private val PLACE_COLOR = Color4b(99, 217, 232)
    private val ACTIVE_COLOR = Color4b(255, 84, 84)
    private val ARMOR_SLOTS = arrayOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    )

    private val serverMode by mode("Режим", "ReallyWorld", listOf("ReallyWorld", "Donut SMP"))
    private val targetRadius by number("Радиус цели", 8.0, 4.0, 12.0, 0.5, 1)
    private val placeRange by number("Дальность установки", 4.5, 3.0, 6.0, 0.1, 1)
    private val breakRange by number("Дальность взрыва", 3.0, 1.0, 6.0, 0.1, 1)
    private val minDamage by number("Мин. урон врагу", 3.0, 1.0, 20.0, 0.5, 1)
    private val maxSelfDamage by number("Макс. урон себе", 8.0, 0.0, 20.0, 0.5, 1)
    private val tickDelay by number("Задержка в тиках", 0.0, 0.0, 5.0, 1.0, 0)
    private val predictTicks by number("Предикт тиков", 2.0, 0.0, 4.0, 1.0, 0)
    private val obsidianBase by boolean("Ставить обсидиан", true)
    private val legit by boolean("Легитный", false)
    private val antiSuicide by boolean("Анти-суицид", true)
    private val autoDig by boolean("Авто подкоп", true)
    private val cobweb by boolean("Ставить паутину", true)
    private val throughWalls by boolean("Действовать через стены", true)
    private val visualize by boolean("Визуализация", true)
    private val saveSelf by boolean("Сейвить себя", false)

    private val configMode by mode("Настройка", "Атака", listOf("Атака", "Наводка", "Защита от взрыва"))
    private val useAnchors by boolean("Использовать якоря", false, isVisible = { configMode == "Атака" })
    private val targetSearch by number(
        "Поиск противника", 10.0, 4.0, 12.0, 0.5, 1,
        isVisible = { configMode == "Атака" }
    )
    private val actionDistance by number(
        "Дистанция действий", 4.0, 2.0, 6.0, 0.1, 1,
        isVisible = { configMode == "Атака" }
    )
    private val attackThreshold by number(
        "Порог атаки", 2.0, 0.5, 20.0, 0.5, 1,
        isVisible = { configMode == "Атака" }
    )
    private val cycleDelay by number(
        "Задержка цикла", 0.0, 0.0, 5.0, 1.0, 0,
        isVisible = { configMode == "Атака" }
    )
    private val leadTarget by number(
        "Опережать цель на", 2.0, 0.0, 6.0, 1.0, 0,
        isVisible = { configMode == "Атака" }
    )
    private val anchorCharges by number(
        "Заряды якоря", 1.0, 1.0, 4.0, 1.0, 0,
        isVisible = { configMode == "Атака" && useAnchors }
    )
    private val prepareBase by boolean("Готовить основание", true, isVisible = { configMode == "Атака" })
    private val returnHand by boolean("Возвращать руку", true, isVisible = { configMode == "Атака" })
    private val inventoryReserve by boolean("Запас из инвентаря", true, isVisible = { configMode == "Атака" })

    private val autoAim by boolean("Автонаведение", true, isVisible = { configMode == "Наводка" })
    private val turnSpeed by number(
        "Темп поворота", 100.0, 10.0, 100.0, 5.0, 0,
        isVisible = { configMode == "Наводка" && autoAim }
    )
    private val aimSpread by number(
        "Разброс прицела", 0.5, 0.0, 2.0, 0.1, 1,
        isVisible = { configMode == "Наводка" && autoAim }
    )
    private val aimThroughWalls by boolean("Наводиться за препятствиями", false, isVisible = { configMode == "Наводка" })

    private val selfDamageAllowance by number(
        "Допустимый урон себе", 8.0, 0.0, 20.0, 0.5, 1,
        isVisible = { configMode == "Защита от взрыва" }
    )
    private val healthReserve by number(
        "Остаток здоровья", 0.5, 0.5, 10.0, 0.5, 1,
        isVisible = { configMode == "Защита от взрыва" }
    )
    private val skipWhen = multiMode(
        "Не ставить при",
        setOf("Использование еды", "Смена тотема"),
        listOf("Использование еды", "Ближний бой", "Смена тотема", "Нет тотемов", "Неполная броня"),
        isVisible = { configMode == "Защита от взрыва" }
    )
    private val visualizePosition by boolean("Визуализация позиции", true)

    private class Damage(val target: Float, val self: Float) {
        val margin: Float get() = target - self
    }

    private class Placement(
        val base: BlockPos,
        val hit: BlockHitResult,
        val support: Boolean,
        val aim: Vec3,
        val damage: Damage
    )

    private class RenderEntry(val pos: BlockPos, val time: Long, val crystal: Boolean)

    private var registered = false

    private var playerRef: LocalPlayer? = null
    private var levelRef: ClientLevel? = null
    private var tick = 0
    private var nextActionTick = 0

    private var currentTarget: Player? = null
    private var pendingBreak: EndCrystal? = null
    private var pendingPlace: Placement? = null

    private var awaitingBase: BlockPos? = null
    private var awaitingBaseTick = 0
    private var anchorPos: BlockPos? = null

    private var diggingPos: BlockPos? = null
    private var digTicks = 0
    private var lastOffhandItem: Item? = null
    private var offhandChangedTick = -100
    private var lastDigTick = -100

    private val breakCooldown = HashMap<Int, Int>()
    private val baseCooldown = HashMap<BlockPos, Int>()
    private val digCooldown = HashMap<BlockPos, Int>()
    private val renderPositions = CopyOnWriteArrayList<RenderEntry>()
    private var damageBoxes: List<Pair<AbstractClientPlayer, AABB>> = emptyList()

    override fun onEnable() {
        resetRuntime()
        if (!registered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (isEnabled() && inGame) renderGizmos()
            }
            registered = true
        }
    }

    override fun onDisable() {
        stopDigging()
        resetRuntime()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        stopDigging()
        resetRuntime()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player
        val level = mc.level
        if (!inGame || player == null || level == null || !isReady(player)) {
            stopDigging()
            resetRuntime()
            return
        }
        if (playerRef !== player || levelRef !== level) {
            resetRuntime()
            playerRef = player
            levelRef = level
        }

        tick++
        val offhandItem = player.offhandItem.item
        if (offhandItem !== lastOffhandItem) {
            lastOffhandItem = offhandItem
            offhandChangedTick = tick
        }
        breakCooldown.entries.removeIf { it.value <= tick }
        baseCooldown.entries.removeIf { it.value <= tick }
        digCooldown.entries.removeIf { it.value <= tick }

        continueDigging(level, player)

        val target = selectTarget(player)
        if (target == null) {
            clearPlan()
            return
        }
        if (currentTarget !== target) {
            clearPlan()
            currentTarget = target
        }
        if (tick < nextActionTick) return
        if (blockedByProtection(player)) {
            pendingBreak = null
            pendingPlace = null
            return
        }
        awaitingBase?.let {
            if (tick >= awaitingBaseTick) {
                if (!level.getBlockState(it).isCrystalBase()) baseCooldown[it] = tick + BLACKLIST_TICKS
                awaitingBase = null
            }
        }

        refreshPrediction(player, target)

        val actFirst = serverMode == "Donut SMP"
        if (!actFirst && breakStep(player, level, target)) return
        if (useAnchors) {
            if (tickAnchors(player, level, target)) {
                nextActionTick = tick + actionCooldown()
                return
            }
        } else {
            val scan = scanPlacements(player, level, target)
            val direct = scan.direct
            if (direct != null) {
                aim(player, direct.aim)
                pendingPlace = direct
                pendingBreak = null
                nextActionTick = tick + actionCooldown()
                return
            }
            if (autoDig && findDigTarget(player, level, target)?.let { startDigging(level, player, it) } == true) {
                nextActionTick = tick + actionCooldown()
                return
            }
            val prepared = scan.support
            if (prepared != null) {
                aim(player, prepared.aim)
                pendingPlace = prepared
                pendingBreak = null
                nextActionTick = tick + actionCooldown()
                return
            }
            if (cobweb && placeWeb(player, level, target)) {
                nextActionTick = tick + actionCooldown()
                return
            }
        }
        if (actFirst && breakStep(player, level, target)) return
        nextActionTick = tick + 1 + actionCooldown()
    }

    @Commando
    fun onMovementSent(event: PlayerMovementSentEvent) {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (playerRef !== player || levelRef !== level) return

        pendingBreak?.let {
            pendingBreak = null
            if (attemptBreak(player, level, it)) return
        }
        pendingPlace?.let {
            pendingPlace = null
            attemptPlace(player, level, it)
        }
    }

    private fun breakStep(player: LocalPlayer, level: ClientLevel, target: Player): Boolean {
        val crystal = findCrystalToBreak(player, level, target)
        if (crystal == null) return false
        aim(player, crystal.position().add(0.0, 0.5, 0.0))
        pendingBreak = crystal
        pendingPlace = null
        nextActionTick = tick + actionCooldown()
        return true
    }

    private fun isReady(player: LocalPlayer): Boolean = player.connection.connection.isConnected &&
        player.isAlive && !player.isSpectator && !player.isPassenger && mc.gameMode != null &&
        mc.gui.screen() == null

    private fun actionCooldown(): Int = tickDelay.toInt() + cycleDelay.toInt()

    private fun attackReach(): Double {
        val configured = maxOf(breakRange, actionDistance)
        val player = mc.player ?: return configured
        val attribute = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
        if (attribute <= 0.0) return configured
        return minOf(configured, attribute - 0.05).coerceAtLeast(1.0)
    }

    private fun clearPlan() {
        currentTarget = null
        pendingBreak = null
        pendingPlace = null
        awaitingBase = null
    }

    private fun resetRuntime() {
        playerRef = null
        levelRef = null
        tick = 0
        nextActionTick = 0
        currentTarget = null
        pendingBreak = null
        pendingPlace = null
        awaitingBase = null
        awaitingBaseTick = 0
        anchorPos = null
        diggingPos = null
        digTicks = 0
        lastOffhandItem = null
        offhandChangedTick = -100
        lastDigTick = -100
        breakCooldown.clear()
        baseCooldown.clear()
        digCooldown.clear()
        renderPositions.clear()
        damageBoxes = emptyList()
    }

    private fun selectTarget(player: LocalPlayer): Player? {
        val level = mc.level ?: return null
        val rangeSq = targetSearch * targetSearch
        var best: Player? = null
        var bestScore = Double.MAX_VALUE
        var bestIsTarget = false
        val eye = player.eyePosition
        val look = player.getViewVector(1.0f)
        for (entity in level.players()) {
            if (!isValidTarget(player, entity, rangeSq)) continue
            val dist = player.distanceToSqr(entity)
            val direction = entity.position().subtract(eye)
            val dot = if (direction.lengthSqr() < 1.0e-6) 0.0 else look.dot(direction.normalize())
            val score = dist + (1.0 - dot) * 4.0
            val isTarget = TargetRepository.isTarget(entity)
            if (best == null || isTarget && !bestIsTarget || isTarget == bestIsTarget && score < bestScore) {
                best = entity
                bestScore = score
                bestIsTarget = isTarget
            }
        }
        return best
    }

    private fun isValidTarget(player: Player, target: Player, rangeSq: Double): Boolean {
        if (target === player || !target.isAlive || target.isSpectator || target.isCreative) return false
        if (player.distanceToSqr(target) > rangeSq) return false
        if (FriendRepository.isFriend(target)) return false
        if (AntiBotModule.isBot(target)) return false
        return true
    }

    private fun refreshPrediction(player: LocalPlayer, target: Player) {
        val level = mc.level ?: run {
            damageBoxes = emptyList()
            return
        }
        val ticks = predictTicks + leadTarget
        val radiusSq = targetRadius * targetRadius
        val rangeSq = targetSearch * targetSearch
        val result = ArrayList<Pair<AbstractClientPlayer, AABB>>(4)
        for (entity in level.players()) {
            if (!isValidTarget(player, entity, rangeSq)) continue
            if (entity.distanceToSqr(target) > radiusSq) continue
            result.add(entity to predictedBox(entity, ticks))
        }
        damageBoxes = result
    }

    private fun findCrystalToBreak(player: LocalPlayer, level: ClientLevel, target: Player): EndCrystal? {
        val reach = attackReach()
        val rangeSq = reach * reach
        val eye = player.eyePosition
        val boxes = damageBoxes
        if (boxes.isEmpty()) return null
        val ignoreLos = ignoreLineOfSight()
        var best: EndCrystal? = null
        var bestDamage: Damage? = null
        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal || !entity.isAlive) continue
            if (breakCooldown.containsKey(entity.id)) continue
            val position = entity.position()
            if (eye.distanceToSqr(entity.boundingBox.center) > rangeSq) continue
            val explosion = position.add(0.0, 0.5, 0.0)
            val self = damageAt(explosion, player, player.boundingBox)
            if (!isSafeSelf(self)) continue
            if (!ignoreLos && !hasLineOfSight(level, player, explosion)) continue
            var targetDamage = 0f
            for ((victim, box) in boxes) {
                val damage = damageAt(explosion, victim, box)
                if (damage > targetDamage) targetDamage = damage
            }
            if (targetDamage < minDamage) continue
            val damage = Damage(targetDamage, self)
            if (isBetter(damage, bestDamage)) {
                best = entity
                bestDamage = damage
            }
        }
        return best
    }

    private class PlacementScan(val direct: Placement?, val support: Placement?)

    private fun scanPlacements(player: LocalPlayer, level: ClientLevel, target: Player): PlacementScan {
        val candidates = damageBoxes
        if (candidates.isEmpty()) return PlacementScan(null, null)
        val obsidianAvailable = obsidianBase && prepareBase && hasItem(player, Items.OBSIDIAN) &&
            hasItem(player, Items.END_CRYSTAL)
        val pending = awaitingBase
        var direct: Placement? = null
        if (pending != null) {
            val placement = evaluateBase(player, level, pending, target, obsidianAvailable, candidates)
            if (placement != null && !placement.support) return PlacementScan(placement, null)
        }
        val allowSupport = pending == null
        val origin = player.blockPosition()
        val radius = placeRange.toInt() + 1
        var support: Placement? = null
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val base = origin.offset(dx, dy, dz)
                    val placement = evaluateBase(player, level, base, target, obsidianAvailable, candidates) ?: continue
                    if (placement.support) {
                        if (allowSupport && isBetterPlacement(placement, support)) support = placement
                    } else {
                        if (isBetterPlacement(placement, direct)) direct = placement
                    }
                }
            }
        }
        return PlacementScan(direct, support)
    }

    private fun isBetterPlacement(candidate: Placement, current: Placement?): Boolean {
        if (current == null) return true
        val delta = candidate.damage.target - current.damage.target
        if (abs(delta) > 1.0e-4f) {
            val candidateAbove = candidate.damage.target >= attackThreshold
            val currentAbove = current.damage.target >= attackThreshold
            if (candidateAbove != currentAbove) return candidateAbove
            return delta > 0f
        }
        if (candidate.support != current.support) return !candidate.support
        return candidate.damage.margin > current.damage.margin
    }

    private fun evaluateBase(
        player: LocalPlayer,
        level: ClientLevel,
        base: BlockPos,
        target: Player,
        obsidianAvailable: Boolean,
        candidates: List<Pair<Player, AABB>>
    ): Placement? {
        val eyes = player.eyePosition
        if (eyes.distanceToSqr(Vec3.atCenterOf(base)) > placeRange * placeRange) return null
        if (!level.worldBorder.isWithinBounds(base)) return null
        if (level.isOutsideBuildHeight(base) || level.isOutsideBuildHeight(base.above(2))) return null
        if (baseCooldown.containsKey(base)) return null

        val targetPosition = target.position()
        val baseState = level.getBlockState(base)
        val solidBase = baseState.isCrystalBase()
        if (!solidBase) {
            if (!obsidianAvailable) return null
            if (!baseState.canBeReplaced() || !level.getFluidState(base).isEmpty) return null
            if (level.getEntities(null, AABB(base)).any { it.isAlive }) return null
        }

        val space = base.above()
        if (!level.getBlockState(space).isAir) return null
        if (!level.getBlockState(space.above()).isAir) return null
        val crystalPos = Vec3.atBottomCenterOf(space)
        val crystalCenter = crystalPos.add(0.0, 1.0, 0.0)
        val reach = attackReach()
        if (eyes.distanceToSqr(crystalCenter) > reach * reach) return null
        if (crystalPos.distanceToSqr(targetPosition) > targetRadius * targetRadius) return null
        if (level.getEntities(null, AABB(space)).any { it.isAlive }) return null
        if (level.getEntities(null, AABB(space.above())).any { it.isAlive }) return null

        val hit = if (solidBase) findHitOnBlock(level, player, base) else findSupportHit(level, player, base)
        if (hit == null) return null

        val self = damageAt(crystalPos, player, player.boundingBox, base)
        if (!isSafeSelf(self)) return null
        var targetDamage = 0f
        for ((victim, box) in candidates) {
            val damage = damageAt(crystalPos, victim, box, base)
            if (damage > targetDamage) targetDamage = damage
        }
        if (targetDamage < minDamage) return null

        return Placement(base, hit, !solidBase, hit.location, Damage(targetDamage, self))
    }

    private fun findHitOnBlock(level: ClientLevel, player: LocalPlayer, pos: BlockPos): BlockHitResult? {
        val eyes = player.eyePosition
        val rangeSq = placeRange * placeRange
        for (side in Direction.values()) {
            val hitVec = faceCenter(pos, side)
            if (!facesEye(eyes, hitVec, side)) continue
            if (eyes.distanceToSqr(hitVec) > rangeSq) continue
            val clip = level.clip(ClipContext(eyes, hitVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            if (clip.type == HitResult.Type.BLOCK && clip.blockPos == pos) return clip
        }
        if (throughWalls) {
            return BlockHitResult(faceCenter(pos, Direction.UP), Direction.UP, pos, false)
        }
        return null
    }

    private fun findSupportHit(level: ClientLevel, player: LocalPlayer, pos: BlockPos): BlockHitResult? {
        val eyes = player.eyePosition
        val rangeSq = placeRange * placeRange
        val ignoreLos = ignoreLineOfSight()
        for (side in Direction.values()) {
            val neighbour = pos.relative(side.opposite)
            val state = level.getBlockState(neighbour)
            if (state.canBeReplaced() || state.isAir || state.hasBlockEntity()) continue
            val hitVec = faceCenter(neighbour, side)
            if (!facesEye(eyes, hitVec, side)) continue
            if (eyes.distanceToSqr(hitVec) > rangeSq) continue
            if (!ignoreLos) {
                val clip = level.clip(ClipContext(eyes, hitVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                if (clip.type != HitResult.Type.BLOCK || clip.blockPos != neighbour) continue
            }
            return BlockHitResult(hitVec, side, neighbour, false)
        }
        val below = pos.below()
        val belowState = level.getBlockState(below)
        if (!belowState.canBeReplaced() && !belowState.isAir && !belowState.hasBlockEntity()) {
            val hitVec = Vec3(below.x + 0.5, below.y + 1.0, below.z + 0.5)
            if (eyes.distanceToSqr(hitVec) <= rangeSq) return BlockHitResult(hitVec, Direction.UP, below, false)
        }
        return null
    }

    private fun findDigTarget(player: LocalPlayer, level: ClientLevel, target: Player): BlockPos? {
        val eyes = player.eyePosition
        val rangeSq = actionDistance * actionDistance
        val feet = target.blockPosition()

        val underFeet = feet.below()
        if (tick - lastDigTick >= 20 && isDiggable(level, underFeet) && !digCooldown.containsKey(underFeet) &&
            eyes.distanceToSqr(Vec3.atCenterOf(underFeet)) <= rangeSq
        ) {
            return underFeet
        }

        for (dx in -2..2) {
            for (dy in -1..1) {
                for (dz in -2..2) {
                    val base = feet.offset(dx, dy, dz)
                    if (!level.getBlockState(base).isCrystalBase()) continue
                    val obstruction = when {
                        !level.getBlockState(base.above()).isAir -> base.above()
                        !level.getBlockState(base.above(2)).isAir -> base.above(2)
                        else -> continue
                    }
                    if (!isDiggable(level, obstruction) || digCooldown.containsKey(obstruction)) continue
                    if (eyes.distanceToSqr(Vec3.atCenterOf(obstruction)) > rangeSq) continue
                    return obstruction
                }
            }
        }
        return null
    }

    private fun isDiggable(level: ClientLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        if (state.isAir || state.isCrystalBase()) return false
        if (!state.fluidState.isEmpty) return false
        if (state.getDestroySpeed(level, pos) < 0f) return false
        return true
    }

    private fun placeWeb(player: LocalPlayer, level: ClientLevel, target: Player): Boolean {
        if (!hasItem(player, Items.COBWEB)) return false
        val base = target.blockPosition()
        val pos = if (level.getBlockState(base).canBeReplaced()) base else base.above()
        if (!level.getBlockState(pos).canBeReplaced()) return false
        if (!level.getFluidState(pos).isEmpty) return false
        if (player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > actionDistance * actionDistance) return false
        if (level.getEntities(null, AABB(pos)).any { it.isAlive && it !== target }) return false
        val hit = findSupportHit(level, player, pos) ?: return false
        if (!placeItem(player, Items.COBWEB, hit)) return false
        baseCooldown[pos] = tick + 20
        renderPositions.add(0, RenderEntry(pos, System.currentTimeMillis(), false))
        return true
    }

    private fun tickAnchors(player: LocalPlayer, level: ClientLevel, target: Player): Boolean {
        val pos = anchorPos
        if (pos != null) {
            val state = level.getBlockState(pos)
            if (!state.`is`(Blocks.RESPAWN_ANCHOR)) {
                anchorPos = null
                return false
            }
            val hit = findHitOnBlock(level, player, pos) ?: return false
            val charge = state.getValue(RespawnAnchorBlock.CHARGE)
            if (charge >= anchorCharges.toInt()) {
                if (!canDetonate(player)) {
                    anchorPos = null
                    return false
                }
                return detonateAnchor(player, hit, pos)
            }
            if (!anchorWorth(pos, player, target)) {
                anchorPos = null
                return false
            }
            return placeItem(player, Items.GLOWSTONE, hit)
        }
        val spot = findAnchorSpot(player, level, target) ?: return false
        anchorPos = spot.first
        return placeItem(player, Items.RESPAWN_ANCHOR, spot.second)
    }

    private fun findAnchorSpot(player: LocalPlayer, level: ClientLevel, target: Player): Pair<BlockPos, BlockHitResult>? {
        val origin = player.blockPosition()
        val rangeSq = placeRange * placeRange
        val eyes = player.eyePosition
        val radius = placeRange.toInt() + 1
        var best: Pair<BlockPos, BlockHitResult>? = null
        var bestDamage: Damage? = null
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val pos = origin.offset(dx, dy, dz)
                    if (!level.worldBorder.isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) continue
                    if (!level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty) continue
                    if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > rangeSq) continue
                    if (level.getEntities(null, AABB(pos)).any { it.isAlive }) continue
                    val hit = findSupportHit(level, player, pos) ?: continue
                    val self = ExplosionDamageUtil.anchorDamage(pos, player)
                    if (!isSafeSelf(self)) continue
                    val damage = ExplosionDamageUtil.anchorDamage(pos, target)
                    if (damage < minDamage) continue
                    val record = Damage(damage, self)
                    if (isBetter(record, bestDamage)) {
                        best = pos to hit
                        bestDamage = record
                    }
                }
            }
        }
        return best
    }

    private fun anchorWorth(pos: BlockPos, player: LocalPlayer, target: Player): Boolean {
        val self = ExplosionDamageUtil.anchorDamage(pos, player)
        if (!isSafeSelf(self)) return false
        return ExplosionDamageUtil.anchorDamage(pos, target) >= minDamage
    }

    private fun canDetonate(player: LocalPlayer): Boolean = (0..8).any {
        val stack = player.inventory.getItem(it)
        !stack.isEmpty && !stack.`is`(Items.GLOWSTONE)
    }

    private fun detonateAnchor(player: LocalPlayer, hit: BlockHitResult, pos: BlockPos): Boolean {
        val saved = player.inventory.selectedSlot
        val slot = (0..8).firstOrNull {
            it != saved && !player.inventory.getItem(it).isEmpty && !player.inventory.getItem(it).`is`(Items.GLOWSTONE)
        } ?: saved.takeIf {
            !player.inventory.getItem(it).isEmpty && !player.inventory.getItem(it).`is`(Items.GLOWSTONE)
        } ?: return false

        val used = if (slot == saved) {
            detonateInHand(player, hit)
        } else if (legit) {
            InventoryUtil.swapToSlot(slot)
            val result = detonateInHand(player, hit)
            if (returnHand && player.inventory.selectedSlot == slot) InventoryUtil.swapToSlot(saved)
            result
        } else {
            var result = false
            InventoryUtil.withSilentSlot(slot) { result = detonateInHand(player, hit) }
            result
        }
        if (used) {
            baseCooldown[pos] = tick + 20
            renderPositions.add(0, RenderEntry(pos, System.currentTimeMillis(), false))
        }
        return used
    }

    private fun detonateInHand(player: LocalPlayer, hit: BlockHitResult): Boolean {
        val gameMode = mc.gameMode ?: return false
        if (!player.containerMenu.carried.isEmpty) return false
        val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        if (!result.consumesAction()) return false
        player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
        return true
    }

    private fun attemptBreak(player: LocalPlayer, level: ClientLevel, crystal: EndCrystal): Boolean {
        if (!ActionCoordinator.canAct(this)) return false
        if (!isReady(player)) return false
        if (!crystal.isAlive || level.getEntity(crystal.id) !== crystal) return false
        if (breakCooldown.containsKey(crystal.id)) return false
        val reach = attackReach()
        if (player.eyePosition.distanceToSqr(crystal.boundingBox.center) > reach * reach) return false
        val gameMode = mc.gameMode ?: return false
        player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
        gameMode.attack(player, crystal)
        breakCooldown[crystal.id] = tick + 4
        renderPositions.add(0, RenderEntry(crystal.blockPosition().below(), System.currentTimeMillis(), true))
        nextActionTick = tick + actionCooldown()
        return true
    }

    private fun attemptPlace(player: LocalPlayer, level: ClientLevel, placement: Placement): Boolean {
        if (!ActionCoordinator.canAct(this)) return false
        if (!isReady(player)) return false
        val item = if (placement.support) Items.OBSIDIAN else Items.END_CRYSTAL
        if (placement.support && !obsidianBase) return false
        if (!hasItem(player, item)) return false

        if (placement.support) {
            val state = level.getBlockState(placement.base)
            if (!state.canBeReplaced() || !level.getFluidState(placement.base).isEmpty) return false
        } else {
            if (!level.getBlockState(placement.base).isCrystalBase()) return false
            if (!level.getBlockState(placement.base.above()).isAir) return false
        }
        if (!placeItem(player, item, placement.hit)) {
            baseCooldown[placement.base] = tick + BLACKLIST_TICKS
            nextActionTick = tick + 2 + actionCooldown()
            return false
        }

        if (placement.support) {
            awaitingBase = placement.base
            awaitingBaseTick = tick + 20
            baseCooldown[placement.base] = tick + 4
            nextActionTick = tick + 3 + actionCooldown()
        } else {
            baseCooldown[placement.base] = tick + 40
            nextActionTick = tick + 1 + actionCooldown()
        }
        renderPositions.add(0, RenderEntry(placement.base, System.currentTimeMillis(), !placement.support))
        return true
    }

    private fun placeItem(player: LocalPlayer, item: Item, hit: BlockHitResult): Boolean {
        if (player.isSpectator || !player.containerMenu.carried.isEmpty) return false
        if (player.offhandItem.`is`(item)) return interact(player, InteractionHand.OFF_HAND, hit)

        var slot = InventoryUtil.findHotbarSlot(item)
        if (slot !in 0..8) {
            if (!inventoryReserve) return false
            val inventorySlot = InventoryUtil.findInventorySlot(item)
            if (inventorySlot !in 9..35) return false
            val reserveSlot = InventoryUtil.findBestSlotInHotBar()
            if (reserveSlot !in 0..8) return false
            InventoryUtil.swapSlots(inventorySlot, reserveSlot)
            slot = InventoryUtil.findHotbarSlot(item)
            if (slot !in 0..8) return false
        }

        val saved = player.inventory.selectedSlot
        if (legit) {
            InventoryUtil.swapToSlot(slot)
            val placed = interact(player, InteractionHand.MAIN_HAND, hit)
            if (returnHand && saved != slot && player.inventory.selectedSlot == slot) InventoryUtil.swapToSlot(saved)
            return placed
        }
        var placed = false
        InventoryUtil.withSilentSlot(slot) {
            placed = InventoryUtil.useItemOnPacket(InteractionHand.MAIN_HAND, hit)
        }
        return placed
    }

    private fun interact(player: LocalPlayer, hand: InteractionHand, hit: BlockHitResult): Boolean {
        val gameMode = mc.gameMode ?: return false
        if (mc.player !== player || !player.connection.connection.isConnected) return false
        if (!player.containerMenu.carried.isEmpty) return false
        val result = gameMode.useItemOn(player, hand, hit)
        if (!result.consumesAction()) return false
        player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
        return true
    }

    private fun startDigging(level: ClientLevel, player: LocalPlayer, pos: BlockPos): Boolean {
        val gameMode = mc.gameMode ?: return false
        if (pos == diggingPos) return true
        aim(player, Vec3.atCenterOf(pos))
        val side = diggingSide(player, pos)
        if (!gameMode.startDestroyBlock(pos, side)) {
            digCooldown[pos] = tick + BLACKLIST_TICKS
            return false
        }
        diggingPos = pos
        digTicks = 0
        lastDigTick = tick
        player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
        return true
    }

    private fun continueDigging(level: ClientLevel, player: LocalPlayer) {
        val pos = diggingPos ?: return
        val gameMode = mc.gameMode ?: run {
            diggingPos = null
            return
        }
        if (level.getBlockState(pos).isAir) {
            diggingPos = null
            digTicks = 0
            return
        }
        aim(player, Vec3.atCenterOf(pos))
        val side = diggingSide(player, pos)
        gameMode.continueDestroyBlock(pos, side)
        if (++digTicks > DIG_TIMEOUT_TICKS) {
            digCooldown[pos] = tick + BLACKLIST_TICKS
            stopDigging()
        }
    }

    private fun stopDigging() {
        if (diggingPos != null) mc.gameMode?.stopDestroyBlock()
        diggingPos = null
        digTicks = 0
    }

    private fun diggingSide(player: LocalPlayer, pos: BlockPos): Direction {
        val dx = player.x - (pos.x + 0.5)
        val dy = player.eyeY - (pos.y + 0.5)
        val dz = player.z - (pos.z + 0.5)
        val ax = abs(dx)
        val ay = abs(dy)
        val az = abs(dz)
        return when {
            ay >= ax && ay >= az -> if (dy > 0) Direction.UP else Direction.DOWN
            ax >= az -> if (dx > 0) Direction.EAST else Direction.WEST
            else -> if (dz > 0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun blockedByProtection(player: LocalPlayer): Boolean {
        if (skipWhen.isSelected("Использование еды") && player.isUsingItem) return true
        if (skipWhen.isSelected("Ближний бой") && HitAuraModule.isEnabled() && HitAuraModule.currentTarget != null) return true
        if (skipWhen.isSelected("Смена тотема") && tick - offhandChangedTick <= 3) return true
        if (skipWhen.isSelected("Нет тотемов") && !hasItem(player, Items.TOTEM_OF_UNDYING)) return true
        if (skipWhen.isSelected("Неполная броня") && ARMOR_SLOTS.any { player.getItemBySlot(it).isEmpty }) return true
        return false
    }

    private fun aim(player: LocalPlayer, point: Vec3) {
        if (configMode == "Наводка" && !autoAim) return
        val desired = RotationUtil.getRotationToVec(point, player.eyePosition)
        if (configMode == "Наводка" && autoAim) {
            val spread = aimSpread.toFloat() / 10f
            if (spread > 0f) {
                val random = ThreadLocalRandom.current()
                desired.yaw += random.nextFloat(-spread, spread)
                desired.pitch += random.nextFloat(-spread, spread)
            }
        }
        val base = Rotation(player.yRot, player.xRot)
        val factor = (if (configMode == "Наводка") turnSpeed.toFloat() else 100f) / 100f
        val step = Rotation(
            base.yaw + Mth.wrapDegrees(desired.yaw - base.yaw) * factor,
            (base.pitch + (desired.pitch - base.pitch) * factor).coerceIn(-90.0f, 90.0f)
        )
        RotationManager.setRotation(step, moveFix = MoveFixMode.SILENT, keepTicks = 2)
    }

    private fun damageAt(explosion: Vec3, victim: Player, box: AABB, obsidianPos: BlockPos? = null): Float {
        val factory = ExplosionDamageUtil.getOverridingHitFactory(
            obsidianPos,
            if (obsidianPos != null) Blocks.OBSIDIAN.defaultBlockState() else null,
            false
        )
        return ExplosionDamageUtil.explosionDamage(victim, box.center, box, explosion, CRYSTAL_POWER, factory)
    }

    private fun isSafeSelf(selfDamage: Float): Boolean {
        val player = mc.player ?: return false
        if (selfDamage <= 0f) return true
        if (selfDamage > maxSelfDamage) return false
        val health = player.health + player.absorptionAmount
        if (antiSuicide && selfDamage >= health) return false
        if (saveSelf) {
            if (selfDamage > selfDamageAllowance) return false
            if (selfDamage > health - healthReserve) return false
        }
        return true
    }

    private fun isBetter(candidate: Damage, current: Damage?): Boolean {
        if (current == null) return true
        val candidateAbove = candidate.target >= attackThreshold
        val currentAbove = current.target >= attackThreshold
        if (candidateAbove != currentAbove) return candidateAbove
        if (candidate.target > current.target + 1.0e-4f) return true
        if (abs(candidate.target - current.target) <= 1.0e-4f) return candidate.margin > current.margin
        return false
    }

    private fun predictedCenter(target: Player, ticks: Double): Vec3 =
        if (ticks <= 0.0) target.position() else HvhTargetPredict.predict(target, ticks)

    private fun predictedBox(target: Player, ticks: Double): AABB {
        val center = predictedCenter(target, ticks)
        return target.boundingBox.move(center.x - target.x, center.y - target.y, center.z - target.z)
    }

    private fun hasLineOfSight(level: ClientLevel, player: Player, point: Vec3): Boolean =
        level.clip(ClipContext(player.eyePosition, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            .type == HitResult.Type.MISS

    private fun ignoreLineOfSight(): Boolean = throughWalls || aimThroughWalls

    private fun hasItem(player: LocalPlayer, item: Item): Boolean {
        if (player.offhandItem.`is`(item)) return true
        for (slot in 0..35) {
            if (player.inventory.getItem(slot).`is`(item)) return true
        }
        return false
    }

    private fun BlockState.isCrystalBase(): Boolean = `is`(Blocks.OBSIDIAN) || `is`(Blocks.BEDROCK)

    private fun faceCenter(pos: BlockPos, side: Direction): Vec3 = Vec3(
        pos.x + 0.5 + side.stepX * 0.5,
        pos.y + 0.5 + side.stepY * 0.5,
        pos.z + 0.5 + side.stepZ * 0.5
    )

    private fun facesEye(eyes: Vec3, hitVec: Vec3, side: Direction): Boolean {
        val dot = (eyes.x - hitVec.x) * side.stepX + (eyes.y - hitVec.y) * side.stepY + (eyes.z - hitVec.z) * side.stepZ
        return dot > 0.0
    }

    private fun renderGizmos() {
        val now = System.currentTimeMillis()
        renderPositions.removeIf { now - it.time >= RENDER_LIFETIME_MS }

        if (visualize) {
            for (entry in renderPositions) {
                val fade = (1f - (now - entry.time).toFloat() / RENDER_LIFETIME_MS).coerceIn(0f, 1f)
                val stroke = PLACE_COLOR.alpha((210 * fade).toInt())
                val style = GizmoStyle.strokeAndFill(stroke.argb, 2f, stroke.alpha((60 * fade).toInt()).argb)
                Gizmos.cuboid(entry.pos, style).setAlwaysOnTop()
                if (entry.crystal) Gizmos.cuboid(entry.pos.above(), style).setAlwaysOnTop()
            }
        }

        if (visualizePosition) {
            val base = pendingPlace?.base ?: awaitingBase
            if (base != null) {
                Gizmos.cuboid(base, GizmoStyle.stroke(ACTIVE_COLOR.argb, 2f)).setAlwaysOnTop()
                Gizmos.cuboid(base.above(), GizmoStyle.stroke(ACTIVE_COLOR.alpha(160).argb, 1.5f)).setAlwaysOnTop()
            }
            anchorPos?.let { Gizmos.cuboid(it, GizmoStyle.stroke(ACTIVE_COLOR.argb, 2f)).setAlwaysOnTop() }
        }
    }
}
