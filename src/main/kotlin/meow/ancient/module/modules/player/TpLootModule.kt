package meow.ancient.module.modules.player

import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.TeleportPath
import meow.ancient.client.util.other.ChatUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object TpLootModule : Module(
    name = "TpLoot",
    category = PLAYER,
    description = "Teleports to selected dropped items, then leaves or returns to the starting position."
) {
    val tpMode = mode("Teleport Type", "Bypass", listOf("Simple", "Step", "Vault", "Bypass"))
    val stepDistance = number("Step Distance", 4.0, 0.1, 10.0, 0.1, 1, isVisible = { tpMode.value == "Step" })
    val bypassPackets = number("Bypass Packets", 10.0, 0.0, 50.0, 1.0, 0, isVisible = { tpMode.value == "Bypass" })
    val bypassOnGround = boolean("Bypass On Ground", true, isVisible = { tpMode.value == "Bypass" })
    val searchRadius = number("Search Radius", 100.0, 5.0, 500.0, 5.0, 0)
    val autoLeave = boolean("Auto Leave", false)
    val leaveCommand = text("Leave Command", "hub", isVisible = { autoLeave.value })
    val items = picker("Items", emptySet(), MinecraftPickerCatalogs.items)

    private val SAFE_OFFSETS = arrayOf(
        doubleArrayOf(0.0, 0.0),
        doubleArrayOf(0.5, 0.0),
        doubleArrayOf(-0.5, 0.0),
        doubleArrayOf(0.0, 0.5),
        doubleArrayOf(0.0, -0.5),
        doubleArrayOf(0.5, 0.5),
        doubleArrayOf(-0.5, -0.5),
        doubleArrayOf(0.5, -0.5),
        doubleArrayOf(-0.5, 0.5)
    )

    private const val TP_DELAY_MS = 500L
    private const val VAULT_DELAY_MS = 500L

    private val itemSpawnTime = HashMap<Int, Long>()
    private var startPos: Vec3? = null
    private var deathPos: Vec3? = null
    private var targetItemId = -1
    private var arrived = false
    private var returnPending = false
    private var purpose = Purpose.None
    private var lastTpTime = 0L
    private var lastVaultTime = 0L

    private var cachedSelection: Set<String>? = null
    private val selectedItems = HashSet<Item>()

    private enum class Purpose { None, Loot, Return, Death }

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) {
        resetState()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (player.connection == null) return

        if (!player.isAlive || player.health <= 0f) {
            if (deathPos == null) {
                deathPos = player.position()
                ChatUtil.info("Saved death position.")
            }
            TeleportPath.cancel(this)
            purpose = Purpose.None
            targetItemId = -1
            arrived = false
            returnPending = false
            return
        }

        if (purpose != Purpose.None) {
            if (TeleportPath.active(this)) return
            if (purpose == Purpose.Loot) {
                targetItemId = -1
                arrived = false
                returnPending = startPos != null
            }
            purpose = Purpose.None
        }
        if (TeleportPath.busy()) return

        if (deathPos != null) {
            if (System.currentTimeMillis() - lastTpTime >= TP_DELAY_MS) {
                teleport(deathPos!!, Purpose.Death) {
                    deathPos = null
                    ChatUtil.info("Returned to death position.")
                }
            }
            return
        }

        if (targetItemId != -1 && arrived && checkTargetPickup()) return
        if (returnPending && startPos != null) {
            val returnTarget = startPos!!
            teleport(returnTarget, Purpose.Return) {
                startPos = null
                returnPending = false
                ChatUtil.info("Returned to starting position.")
            }
            return
        }

        findLoot()
    }

    private fun findLoot() {
        val player = mc.player ?: return
        val level = mc.level ?: return
        val playerPos = player.position()
        val radius = searchRadius.value
        val box = AABB(
            playerPos.x - radius, playerPos.y - radius, playerPos.z - radius,
            playerPos.x + radius, playerPos.y + radius, playerPos.z + radius
        )
        val itemsList = level.getEntitiesOfClass(ItemEntity::class.java, box) { item ->
            item.isAlive && matchesItem(item)
        }
        if (itemsList.isEmpty()) return

        val now = System.currentTimeMillis()
        for (item in itemsList) {
            itemSpawnTime.putIfAbsent(item.id, now)
        }

        val target = itemsList.minByOrNull { it.position().distanceToSqr(playerPos) } ?: return
        val safeTarget = safePos(target.position()) ?: return
        if (playerPos.distanceToSqr(safeTarget) < 0.04) return
        if (now - (itemSpawnTime[target.id] ?: now) < TP_DELAY_MS) return
        if (tpMode.value == "Vault" && now - lastVaultTime < VAULT_DELAY_MS) return

        val previousTarget = targetItemId
        val previouslyArrived = arrived
        targetItemId = target.id
        arrived = false
        if (teleport(safeTarget, Purpose.Loot) { arrived = true }) {
            if (startPos == null) {
                startPos = if (player.isPassenger) player.rootVehicle?.position() ?: playerPos else playerPos
            }
            if (tpMode.value == "Vault") lastVaultTime = now
        } else {
            targetItemId = previousTarget
            arrived = previouslyArrived
        }
    }

    private fun checkTargetPickup(): Boolean {
        val level = mc.level ?: return false
        val target = level.getEntity(targetItemId)
        if (target is ItemEntity && target.isAlive) return false

        ChatUtil.info("Target item disappeared.")
        itemSpawnTime.remove(targetItemId)
        targetItemId = -1
        arrived = false
        if (autoLeave.value) {
            val command = leaveCommand.value.removePrefix("/")
            mc.player?.connection?.sendCommand(command)
            ChatUtil.info("Left using /$command.")
            toggle()
        } else if (startPos != null) {
            returnPending = true
            teleport(startPos!!, Purpose.Return) {
                startPos = null
                returnPending = false
                ChatUtil.info("Returned to starting position.")
            }
        }
        return true
    }

    private fun teleport(position: Vec3, nextPurpose: Purpose, completion: () -> Unit): Boolean {
        val player = mc.player ?: return false
        var type = when (tpMode.value) {
            "Simple" -> TeleportPath.Type.Simple
            "Step" -> TeleportPath.Type.Step
            "Vault" -> TeleportPath.Type.Vault
            else -> TeleportPath.Type.Bypass
        }
        val packets = bypassPackets.value.toInt()
        if (type == TeleportPath.Type.Bypass && packets == 0) {
            type = TeleportPath.Type.Simple
        }
        val onGround = if (type == TeleportPath.Type.Bypass) {
            bypassOnGround.value
        } else {
            type == TeleportPath.Type.Simple && player.onGround()
        }
        purpose = nextPurpose
        val started = TeleportPath.start(
            this,
            position,
            type,
            packets,
            if (type == TeleportPath.Type.Step) stepDistance.value else 0.0,
            onGround,
            player.yRot,
            player.xRot
        ) {
            purpose = Purpose.None
            completion()
        }
        if (!started) {
            purpose = Purpose.None
            return false
        }
        lastTpTime = System.currentTimeMillis()
        return true
    }

    private fun safePos(position: Vec3): Vec3? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val mover = if (player.isPassenger) player.rootVehicle ?: player else player
        for (offset in SAFE_OFFSETS) {
            val candidate = if (offset[0] == 0.0 && offset[1] == 0.0) position else position.add(offset[0], 0.0, offset[1])
            val box = mover.boundingBox.move(candidate.x - mover.x, candidate.y - mover.y, candidate.z - mover.z)
            if (level.noCollision(mover, box)
                && (!level.getBlockState(BlockPos.containing(candidate.x, candidate.y - 1.0, candidate.z)).isAir
                    || !level.getBlockState(BlockPos.containing(candidate.x, candidate.y - 0.1, candidate.z)).isAir)) {
                return candidate
            }
        }
        return null
    }

    private fun matchesItem(entity: ItemEntity): Boolean {
        val selected = items.value
        if (selected.isEmpty()) return true
        if (cachedSelection != selected) {
            cachedSelection = HashSet(selected)
            selectedItems.clear()
            for (id in selected) {
                MinecraftPickerCatalogs.items.find(id)?.value?.let(selectedItems::add)
            }
        }
        val item = entity.item.item
        return item in selectedItems || BuiltInRegistries.ITEM.getKey(item).toString() in selected
    }

    private fun resetState() {
        TeleportPath.cancel(this)
        startPos = null
        deathPos = null
        targetItemId = -1
        arrived = false
        returnPending = false
        purpose = Purpose.None
        itemSpawnTime.clear()
        lastTpTime = 0L
        lastVaultTime = 0L
    }
}
