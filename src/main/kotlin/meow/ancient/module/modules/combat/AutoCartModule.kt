package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import sweetie.evaware.flora.api.Commando

object AutoCartModule : Module(
    name = "Auto Cart",
    category = COMBAT,
    description = "Places rail and TNT minecart at crosshair block"
) {
    val modeSetting = mode("Mode", "Pre-Rail", listOf("Pre-Rail", "Insta-Cart"))
    val mode by modeSetting

    private enum class State {
        IDLE,
        PLACING_RAIL,
        DRAWING_BOW,
        SHOOTING,
        PLACING_MINECART,
        INSTA_DRAWING_BOW,
        INSTA_SHOOTING,
        INSTA_PLACING_RAIL,
        INSTA_PLACING_MINECART,
        DONE
    }

    private var currentState = State.IDLE
    private var actionTimer = 0
    private var originalSlot = 0
    private var railSlot = -1
    private var bowSlot = -1
    private var tntMinecartSlot = -1
    private var targetHit: BlockHitResult? = null
    private var bowStarted = false
    private var originalPitch = 0f
    private var originalYaw = 0f

    override fun onEnable() {
        if (!inGame) {
            setEnabled(false)
            return
        }
        try {
            execute()
        } catch (_: Exception) {
            setEnabled(false)
        }
    }

    override fun onDisable() {
        reset()
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        if (currentState != State.IDLE) {
            processTick()
        }
    }

    private fun execute() {
        val player = mc.player ?: return
        val inventory = player.inventory

        tntMinecartSlot = findTNTMinecart(inventory)
        railSlot = findRail(inventory)
        bowSlot = findFlameBow(inventory)

        if (tntMinecartSlot == -1 || railSlot == -1 || bowSlot == -1) {
            setEnabled(false)
            return
        }

        val hit = mc.hitResult
        if (hit == null || hit.type != HitResult.Type.BLOCK) {
            setEnabled(false)
            return
        }

        targetHit = hit as BlockHitResult
        originalSlot = inventory.selectedSlot
        originalPitch = player.xRot
        originalYaw = player.yRot
        actionTimer = 0
        bowStarted = false

        currentState = if (mode == "Pre-Rail") State.PLACING_RAIL else State.INSTA_DRAWING_BOW
    }

    private fun processTick() {
        val player = mc.player ?: run { reset(); return }
        val gameMode = mc.gameMode ?: run { reset(); return }
        val level = mc.level ?: run { reset(); return }

        actionTimer++

        try {
            if (mode == "Pre-Rail") {
                processPreRailMode()
            } else {
                processInstaCartMode()
            }
        } catch (_: Exception) {
            reset()
        }
    }

    private fun processPreRailMode() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val hit = targetHit ?: return

        when (currentState) {
            State.PLACING_RAIL -> {
                if (actionTimer == 1) {
                    selectSlot(railSlot)
                    gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
                    currentState = State.DRAWING_BOW
                    actionTimer = 0
                }
            }

            State.DRAWING_BOW -> {
                if (actionTimer == 1) {
                    selectSlot(bowSlot)
                    mc.options.keyUse.setDown(true)
                    bowStarted = true
                    currentState = State.SHOOTING
                    actionTimer = 0
                }
            }

            State.SHOOTING -> {
                if (actionTimer == 5) {
                    player.xRot = player.xRot - 12.0f
                } else if (actionTimer == 6) {
                    if (bowStarted) {
                        mc.options.keyUse.setDown(false)
                        bowStarted = false
                    }
                } else if (actionTimer >= 7) {
                    player.xRot = originalPitch
                    player.yRot = originalYaw
                    currentState = State.PLACING_MINECART
                    actionTimer = 0
                }
            }

            State.PLACING_MINECART -> {
                if (actionTimer == 1) {
                    selectSlot(tntMinecartSlot)
                    mc.options.keyUse.setDown(true)
                } else if (actionTimer == 2) {
                    mc.options.keyUse.setDown(false)
                    currentState = State.DONE
                    actionTimer = 0
                }
            }

            State.DONE -> {
                if (actionTimer >= 1) {
                    selectSlot(originalSlot)
                    reset()
                    setEnabled(false)
                }
            }

            else -> {}
        }
    }

    private fun processInstaCartMode() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val hit = targetHit ?: return

        when (currentState) {
            State.INSTA_DRAWING_BOW -> {
                if (actionTimer == 1) {
                    selectSlot(bowSlot)
                    mc.options.keyUse.setDown(true)
                    bowStarted = true
                    currentState = State.INSTA_SHOOTING
                    actionTimer = 0
                }
            }

            State.INSTA_SHOOTING -> {
                if (actionTimer == 5) {
                    player.xRot = player.xRot - 12.0f
                } else if (actionTimer == 6) {
                    if (bowStarted) {
                        mc.options.keyUse.setDown(false)
                        bowStarted = false
                    }
                } else if (actionTimer >= 7) {
                    player.xRot = originalPitch
                    player.yRot = originalYaw
                    currentState = State.INSTA_PLACING_RAIL
                    actionTimer = 0
                }
            }

            State.INSTA_PLACING_RAIL -> {
                if (actionTimer == 1) {
                    selectSlot(railSlot)
                    gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
                    currentState = State.INSTA_PLACING_MINECART
                    actionTimer = 0
                }
            }

            State.INSTA_PLACING_MINECART -> {
                if (actionTimer == 1) {
                    selectSlot(tntMinecartSlot)
                    mc.options.keyUse.setDown(true)
                } else if (actionTimer == 2) {
                    mc.options.keyUse.setDown(false)
                    currentState = State.DONE
                    actionTimer = 0
                }
            }

            State.DONE -> {
                if (actionTimer >= 1) {
                    selectSlot(originalSlot)
                    reset()
                    setEnabled(false)
                }
            }

            else -> {}
        }
    }

    private fun reset() {
        if (bowStarted) {
            mc.options.keyUse.setDown(false)
        }
        currentState = State.IDLE
        actionTimer = 0
        bowStarted = false
        targetHit = null
        originalPitch = 0f
        originalYaw = 0f
    }

    private fun selectSlot(slot: Int) {
        val player = mc.player ?: return
        if (slot in 0..8) {
            player.inventory.selectedSlot = slot
            player.connection.send(ServerboundSetCarriedItemPacket(slot))
        }
    }

    private fun findTNTMinecart(inventory: Inventory): Int {
        for (i in 0..8) {
            val stack = inventory.getItem(i)
            if (stack.`is`(Items.TNT_MINECART)) return i
        }
        return -1
    }

    private fun findRail(inventory: Inventory): Int {
        for (i in 0..8) {
            val stack = inventory.getItem(i)
            val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            if (id.contains("rail")) return i
        }
        return -1
    }

    private fun findFlameBow(inventory: Inventory): Int {
        for (i in 0..8) {
            val stack = inventory.getItem(i)
            if (stack.`is`(Items.BOW)) {
                val enchantments = stack.get(DataComponents.ENCHANTMENTS) ?: continue
                if (enchantments.keySet().any { it.`is`(Enchantments.FLAME) }) {
                    return i
                }
            }
        }
        return -1
    }
}
