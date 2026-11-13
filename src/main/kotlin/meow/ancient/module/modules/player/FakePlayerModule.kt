package meow.ancient.module.modules.player

import com.mojang.authlib.GameProfile
import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.RemotePlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import sweetie.evaware.flora.api.Commando
import java.util.UUID

class FakePlayerEntity(
    private val clientLevel: ClientLevel,
    profile: GameProfile,
    private val copyInv: Boolean,
    private val isKnockbackEnabled: () -> Boolean
) : RemotePlayer(clientLevel, profile) {

    override fun getPlayerInfo(): PlayerInfo? {
        val player = mc.player ?: return null
        return mc.connection?.getPlayerInfo(player.uuid)
    }

    fun spawn() {
        val player = mc.player ?: return

        unsetRemoved()

        val look = player.lookAngle
        val spawnX = player.x + look.x * 2.0
        val spawnY = player.y
        val spawnZ = player.z + look.z * 2.0

        setPos(spawnX, spawnY, spawnZ)
        xo = spawnX
        yo = spawnY
        zo = spawnZ

        yRot = player.yRot + 180f
        xRot = 0f
        yRotO = yRot
        xRotO = xRot
        yHeadRot = yRot
        yHeadRotO = yHeadRot
        yBodyRot = yRot
        yBodyRotO = yBodyRot

        attributes.assignAllValues(player.attributes)
        pose = player.pose

        if (copyInv) {
            for (slot in EquipmentSlot.entries) {
                setItemSlot(slot, player.getItemBySlot(slot).copy())
            }
        }

        setId(clientLevel.nextEntityId())
        clientLevel.addEntity(this)
    }

    fun despawn() {
        discard()
    }

    fun setInitialHealth(value: Float) {
        if (value > maxHealth) {
            getAttribute(Attributes.MAX_HEALTH)?.setBaseValue(value.toDouble())
        }
        health = value
    }

    fun applyDamage(source: DamageSource, damage: Float) {
        if (isRemoved || isDeadOrDying) return

        val amount = if (damage.isNaN() || damage.isInfinite()) Float.MAX_VALUE else damage
        if (amount <= 0f) return

        val fullDamage: Boolean
        if (damageCooldownTime > 10 && !source.`is`(DamageTypeTags.BYPASSES_COOLDOWN)) {
            if (amount <= lastHurt) return
            val difference = amount - lastHurt
            lastHurt = amount
            setHealth((health - difference).coerceAtLeast(0f))
            fullDamage = false
        } else {
            lastHurt = amount
            damageCooldownTime = 20
            hurtDuration = 10
            hurtTime = hurtDuration
            setHealth((health - amount).coerceAtLeast(0f))
            fullDamage = true
        }

        if (fullDamage) {
            if (!source.`is`(DamageTypeTags.NO_KNOCKBACK)) {
                val sourcePosition = source.sourcePosition
                knockback(0.4, sourcePosition?.x ?: 0.0, sourcePosition?.z ?: 0.0, source, amount, false)
            }
            if (!isDeadOrDying) {
                playHurtSound(source)
            }
        }

        if (isDeadOrDying) {
            makeSound(getDeathSound())
            die(source)
        }
    }

    override fun knockback(power: Double, xd: Double, zd: Double, source: DamageSource, damage: Float, comesFromEffect: Boolean) {
        if (!isKnockbackEnabled()) return
        super.knockback(power, xd, zd, source, damage, comesFromEffect)
    }

    override fun tickDeath() {
        super.tickDeath()
        if (!isRemoved && deathTime >= 20) {
            discard()
        }
    }
}

private fun ClientLevel.nextEntityId(): Int =
    (entitiesForRendering().maxOfOrNull { it.id } ?: 0) + 1

object FakePlayerModule : Module(
    name = "FakePlayer",
    category = PLAYER,
    description = "Spawns a client-side fake player for testing"
) {
    val playerName = text("Name", "FakePlayer", 32, "Name of the fake player")
    val health = number("Health", 20.0, 1.0, 100.0, 1.0, 0)
    val copyInventory = boolean("Copy Inventory", true, "Copies armor and held items from local player")
    val knockback = boolean("Knockback", true, "Pushes the fake player away when it takes damage")

    private var fakePlayer: FakePlayerEntity? = null

    override fun onEnable() {
        spawnFakePlayer()
    }

    override fun onDisable() {
        removeFakePlayer()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val fake = fakePlayer
        if (fake != null && !fake.isRemoved && fake.level() === mc.level) return
        spawnFakePlayer()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        fakePlayer = null
    }

    private fun spawnFakePlayer() {
        mc.player ?: return
        val level = mc.level ?: return
        removeFakePlayer()

        val fake = FakePlayerEntity(
            level,
            GameProfile(UUID.randomUUID(), playerName.value),
            copyInventory.value,
            isKnockbackEnabled = { knockback.value }
        )
        fake.spawn()
        fake.setInitialHealth(health.value.toFloat())
        fakePlayer = fake
    }

    private fun removeFakePlayer() {
        fakePlayer?.despawn()
        fakePlayer = null
    }
}
