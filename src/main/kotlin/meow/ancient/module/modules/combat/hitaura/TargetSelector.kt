package meow.ancient.module.modules.combat.hitaura
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.target.TargetRepository
import meow.ancient.module.modules.combat.AntiBotModule

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.module.modules.combat.HitAuraModule.TargetMode
import meow.ancient.module.modules.combat.HitAuraModule.TargetPriority
import meow.ancient.module.setting.TargetSetting
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

class TargetSelector {
    var currentTarget: LivingEntity? = null
        private set

    private var switchIndex: Int = 0
    private var lastSwitchTime: Long = 0L
    private val candidateList = ArrayList<LivingEntity>()


    fun reset() {
        currentTarget = null
        switchIndex = 0
        candidateList.clear()
    }

    fun updateTarget(
        aimRange: Double,
        fov: Float,
        priority: TargetPriority,
        targetMode: TargetMode,
        allowedTargets: TargetSetting,
        throughWalls: Boolean,
        ignoreFriends: Boolean = true
    ): LivingEntity? {
        if (!inGame) {
            currentTarget = null
            candidateList.clear()
            return null
        }

        if (targetMode == TargetMode.SINGLE) {
            val locked = currentTarget
            if (locked != null && isValidTarget(locked, aimRange, fov, allowedTargets, throughWalls, ignoreFriends)) {
                val lockedIsTarget = locked is Player && TargetRepository.isTarget(locked)
                val hasAnyPriorityTarget = mc.level?.entitiesForRendering()?.any {
                    it is Player && TargetRepository.isTarget(it) && isValidTarget(it, aimRange, fov, allowedTargets, throughWalls, ignoreFriends)
                } ?: false

                if (lockedIsTarget || !hasAnyPriorityTarget) {
                    return locked
                }
            }
        }

        val currentLevel = mc.level ?: run {
            currentTarget = null
            candidateList.clear()
            return null
        }

        candidateList.clear()
        for (entity in currentLevel.entitiesForRendering()) {
            if (entity is LivingEntity && isValidTarget(entity, aimRange, fov, allowedTargets, throughWalls, ignoreFriends)) {
                candidateList.add(entity)
            }
        }

        if (candidateList.isEmpty()) {
            currentTarget = null
            return null
        }

        val comparator = getComparator(priority)

        currentTarget = when (targetMode) {
            TargetMode.SINGLE -> candidateList.minWithOrNull(comparator)
            TargetMode.SWITCH -> {
                candidateList.sortWith(comparator)
                val now = System.currentTimeMillis()
                if (now - lastSwitchTime >= 350L) {
                    switchIndex = (switchIndex + 1) % candidateList.size
                    lastSwitchTime = now
                }
                candidateList[switchIndex % candidateList.size]
            }
        }

        return currentTarget
    }

    fun isValidTarget(
        entity: Entity,
        maxRange: Double,
        fov: Float,
        allowedTargets: TargetSetting,
        throughWalls: Boolean,
        ignoreFriends: Boolean = true
    ): Boolean {
        val localPlayer = mc.player ?: return false
        if (entity === localPlayer) return false
        if (entity !is LivingEntity) return false
        if (!entity.isAlive || entity.isRemoved || entity.health <= 0.0f) return false
        if (entity is ArmorStand) return false
        if (entity.isSpectator) return false
        if (entity is Player) {
            if (ignoreFriends && FriendRepository.isFriend(entity)) return false
            if (AntiBotModule.isBot(entity)) return false
        }

        if (!throughWalls && !localPlayer.hasLineOfSight(entity)) {
            return false
        }

        if (!allowedTargets.isSelected(entity)) return false

        val dist = RotationUtil.getDistanceToBox(entity)
        if (dist > maxRange) return false

        if (!RotationUtil.isInFov(entity, fov)) return false

        return true
    }


    companion object {
        private val DISTANCE = Comparator<LivingEntity> { a, b ->
            RotationUtil.getDistanceToBox(a).compareTo(RotationUtil.getDistanceToBox(b))
        }
        private val HEALTH = Comparator<LivingEntity> { a, b ->
            (a.health + a.absorptionAmount).compareTo(b.health + b.absorptionAmount)
        }
        private val FOV = Comparator<LivingEntity> { a, b ->
            RotationUtil.getFovToEntity(a).compareTo(RotationUtil.getFovToEntity(b))
        }
        private val HURTIME = Comparator<LivingEntity> { a, b ->
            val res = a.hurtTime.compareTo(b.hurtTime)
            if (res != 0) res else RotationUtil.getDistanceToBox(a).compareTo(RotationUtil.getDistanceToBox(b))
        }
        private val ARMOR = Comparator<LivingEntity> { a, b ->
            val res = a.armorValue.compareTo(b.armorValue)
            if (res != 0) res else RotationUtil.getDistanceToBox(a).compareTo(RotationUtil.getDistanceToBox(b))
        }

        fun getComparator(priority: TargetPriority): Comparator<LivingEntity> {
            val baseComparator = when (priority) {
                TargetPriority.DISTANCE -> DISTANCE
                TargetPriority.HEALTH -> HEALTH
                TargetPriority.FOV -> FOV
                TargetPriority.HURT_TIME -> HURTIME
                TargetPriority.ARMOR -> ARMOR
            }

            return Comparator { a, b ->
                val aTarget = a is Player && TargetRepository.isTarget(a)
                val bTarget = b is Player && TargetRepository.isTarget(b)
                if (aTarget != bTarget) {
                    if (aTarget) -1 else 1
                } else {
                    baseComparator.compare(a, b)
                }
            }
        }
    }
}
