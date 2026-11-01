package meow.ancient.module.modules.player

import com.mojang.authlib.GameProfile
import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import sweetie.evaware.flora.api.Commando
import java.util.UUID

class FakePlayerEntity(
    level: ClientLevel,
    profile: GameProfile,
    private val copyInv: Boolean
) : RemotePlayer(level, profile) {

    override fun getPlayerInfo(): PlayerInfo? {
        val player = mc.player ?: return null
        return mc.connection?.getPlayerInfo(player.uuid)
    }

    fun spawn() {
        val player = mc.player ?: return
        val level = mc.level ?: return

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

        level.addEntity(this)
    }

    fun despawn() {
        mc.level?.removeEntity(id, Entity.RemovalReason.DISCARDED)
        setRemoved(Entity.RemovalReason.DISCARDED)
    }
}

object FakePlayerModule : Module(
    name = "FakePlayer",
    category = PLAYER,
    description = "Spawns a client-side fake player for testing"
) {
    val playerName = text("Name", "FakePlayer", 32, "Name of the fake player")
    val health = number("Health", 20.0, 1.0, 100.0, 1.0, 0)
    val copyInventory = boolean("Copy Inventory", true, "Copies armor and held items from local player")

    private var fakePlayer: FakePlayerEntity? = null

    override fun onEnable() {
        if (!inGame) return
        val level = mc.level ?: return

        val profile = GameProfile(UUID.randomUUID(), playerName.value)
        val fake = FakePlayerEntity(level, profile, copyInventory.value)
        fake.health = health.value.toFloat()
        fake.spawn()
        fakePlayer = fake
    }

    override fun onDisable() {
        fakePlayer?.despawn()
        fakePlayer = null
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        fakePlayer?.let { fake ->
            if (fake.isAlive) {
                fake.tick()
            }
        }
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        fakePlayer = null
    }
}
