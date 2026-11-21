package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.action.ActionCoordinator
import meow.ancient.client.util.combat.PlacementReservations
import meow.ancient.client.util.inventory.InventoryAutomation
import meow.ancient.client.util.inventory.InventoryUtil
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketReceiveEvent
import meow.ancient.event.events.PacketSentEvent
import meow.ancient.event.events.PlayerMovementSentEvent
import meow.ancient.event.events.PlayerPreMovementEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.CrystalAuraModule
import meow.ancient.module.modules.combat.CrystalOptimizerModule
import meow.ancient.module.modules.player.PacketMineModule
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CraftingTableBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object ScaffoldModule : Module("Scaffold", MOVEMENT, "Mio automatic block bridging") {
    private val blocks by picker("Blocks", emptySet(), MinecraftPickerCatalogs.blocks)
    private val selection by mode("Selection", "Any", listOf("Any", "WhiteList", "BlackList"))
    private val scaffoldMode by mode("Mode", "Single", listOf("Single", "Multi"))
    private val tower by mode("Tower", "None", listOf("None", "Plain", "Fast", "GrimV3"))
    private val swap by mode("Swap", "None", listOf("None", "Normal", "Silent"))
    private val delay by number("Delay", 180.0, 0.0, 300.0, 1.0, 0, unit = "ms")
    private val offset by number("Offset", 0.0, 0.0, 3.0, 0.1, 1, isVisible = { scaffoldMode == "Single" })
    private val frequency by number("Frequency", 1.0, 1.0, 9.0, 1.0, 0, isVisible = { scaffoldMode == "Multi" })
    private val size by number("Size", 1.0, 1.0, 4.0, 1.0, 0, isVisible = { scaffoldMode == "Multi" })
    private val safeWalk by boolean("SafeWalk", true)
    private val airPlace by boolean("AirPlace", false)
    private val telly by boolean("Telly", false)
    private val rotate by boolean("Rotate", true)
    private val attack by boolean("Attack", true)
    private val movementSync by boolean("Movement Sync", false)
    private val strictDirection by mode("Strict Direction", "NCP", listOf("Off", "NCP", "Grim"))
    private val render by boolean("Render", true)
    private val lineWidth by number("LineWidth", 1.0, 0.5, 5.0, 0.1, 1, isVisible = { render })
    private val fill by color("Fill", Color4b(146643455), isVisible = { render })
    private val outline by color("Outline", Color4b(-4351489), isVisible = { render })
    private val fade by boolean("Fade", true, isVisible = { render })
    private val fadeTime by number("FadeTime", 1.0, 0.1, 1.0, 0.1, 1, unit = "s", isVisible = { render && fade })

    private val targets = ArrayList<MioScaffoldPlacement.Target>(9)
    private val visual = MioScaffoldRenderer({ isEnabled() && render }, { lineWidth }, { fill },
        { outline }, { fade }, { fadeTime }, { scaffoldMode == "Multi" })
    private var playerContext: LocalPlayer? = null
    private var world: ClientLevel? = null
    private var tellyY = 0.0
    private var slot = -1
    private var crystalId = -1
    private var ready = false
    private var planned = false
    private var executed = false
    private var lastPlacement = 0L
    private var lastAttack = 0L
    @Volatile private var lastClick = 0L
    private var lastRotation = 0L
    private var rotation: Rotation? = null
    private var sending: Packet<*>? = null
    private var accepted = false

    fun hasPendingAction(): Boolean = isEnabled() && !executed && targets.isNotEmpty()

    private fun releaseRotation() {
        rotation = null
        if (RotationManager.rotationOwner == name) RotationManager.resetImmediate()
    }

    private fun clearPlan() {
        targets.clear()
        crystalId = -1
        ready = false
        SilentSwapUtil.releasePreparedSlot(this)
    }

    private fun reset() {
        clearPlan()
        releaseRotation()
        MioScaffoldTower.reset()
        visual.reset()
        PlacementReservations.release(this)
        InventoryMoveModule.cancelInventoryActions(this)
        playerContext = null
        world = null
        planned = false
        executed = false
        slot = -1
        sending = null
        accepted = false
        val now = System.nanoTime()
        lastPlacement = now
        lastAttack = now
        lastClick = 0L
        lastRotation = now
    }

    override fun onEnable() = reset()
    override fun onDisable() = reset()
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando
    fun onReceive(event: PacketReceiveEvent) {
        if (event.packet is ClientboundPlayerPositionPacket) MioScaffoldTower.onCorrection()
    }

    @Commando(priority = -128)
    fun onPacket(event: PacketSentEvent) {
        if (event.packet === sending) accepted = true
        if (event.packet is ServerboundContainerClickPacket) lastClick = System.nanoTime()
    }

    private fun available(): Boolean = InventoryAutomation.available() && !InventoryAutomation.busy()
        && !InventoryMoveModule.isInventoryActionSlowed()

    @Commando(priority = 120)
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return reset()
        val level = mc.level ?: return reset()
        if (playerContext !== player || world !== level) {
            reset()
            playerContext = player
            world = level
            tellyY = player.y
        }
        clearPlan()
        planned = false
        executed = false
        visual.beginTick()
        if (!rotate || System.nanoTime() - lastRotation >= (if (movementSync) 0L else 500_000_000L)) releaseRotation()
        rotation?.let { applyRotation(it) }
    }

    private fun findSlot(player: LocalPlayer, level: ClientLevel): Int {
        val selected = player.inventory.selectedSlot
        var bestScore = MioScaffoldPlacement.blockScore(player, level, player.mainHandItem, selection, blocks)
        if (bestScore > 0) return selected
        if (swap == "None") return -1
        var best = -1
        for (candidate in 0..8) {
            val score = MioScaffoldPlacement.blockScore(player, level, player.inventory.getItem(candidate), selection, blocks)
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    private fun applyRotation(value: Rotation) {
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(this)) {
            val adjusted = if (movementSync) Rotation(value.yaw + 0.008600000429153449f * (mc.player!!.tickCount % 6), value.pitch) else value
            RotationManager.setRotation(adjusted, MoveFixMode.SILENT, gcd = false, keepTicks = 0, returnSpeed = 180f)
        }
    }

    private fun prepare() {
        if (planned) return
        planned = true
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (player !== playerContext || level !== world || !available()) {
            MioScaffoldTower.reset()
            return
        }
        if (!player.input.keyPresses.jump() || player.horizontalCollision) tellyY = player.y
        if (movementSync && player.onGround() && System.nanoTime() - lastClick < 50_000_000L) {
            MioScaffoldTower.reset()
            return
        }
        slot = findSlot(player, level)
        if (slot != -1) {
            MioScaffoldPlacement.targets(player, level, slot, scaffoldMode == "Multi", offset, frequency.toInt(), size.toInt(),
                airPlace, tellyY.takeIf { telly }, attack, movementSync, targets, strictDirection)
            targets.removeIf { PacketMineModule.targetsPosition(it.pos) || PlacementReservations.reserved(it.pos, this) }
        }
        if (attack && System.nanoTime() - lastAttack >= CrystalAuraModule.breakDelay.toLong() * 50_000_000L) {
            for (target in targets) {
                val crystal = level.getEntities(null, AABB(target.pos)).firstOrNull {
                    it is EndCrystal && it.isAlive && !it.isRemoved && it.tickCount >= 2
                        && player.distanceToSqr(it) <= player.entityInteractionRange() * player.entityInteractionRange()
                } as? EndCrystal ?: continue
                crystalId = crystal.id
                break
            }
        }
        if (targets.isEmpty() || !ActionCoordinator.canAct(this)) {
            MioScaffoldTower.reset()
            return
        }
        MioScaffoldTower.update(player, tower, targets.isNotEmpty(), movementSync)
        if (swap == "Normal") {
            InventoryUtil.swapToSlot(slot)
            ready = player.inventory.selectedSlot == slot && SilentSwapUtil.serverSlot == slot
        } else {
            ready = SilentSwapUtil.prepareSlot(slot, 1, this)
        }
        if (rotate && ready) {
            val crystal = level.getEntity(crystalId)
            val aim = if (crystal != null) RotationUtil.getRotationToVec(crystal.boundingBox.center, player.eyePosition)
                else targets.firstOrNull()?.let { MioScaffoldPlacement.rotation(player, level, it, movementSync) }
            if (aim != null) {
                rotation = aim
                lastRotation = System.nanoTime()
                applyRotation(aim)
            }
        }
    }

    fun onBeforeInputUpdate() {
        if (isEnabled() && rotate) prepare()
    }

    @Commando(priority = 120)
    fun onPreMovement(event: PlayerPreMovementEvent) {
        if (!rotate) prepare()
    }

    @Commando(priority = -128)
    fun onMovementSent(event: PlayerMovementSentEvent) {
        if (executed || !ready || !available() || !ActionCoordinator.canAct(this)) return
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (player !== playerContext || level !== world || SilentSwapUtil.serverSlot != slot) return
        executed = true
        ActionCoordinator.withOwner(ActionCoordinator.resolveOwner(this)) {
            try {
                val crystal = level.getEntity(crystalId) as? EndCrystal
                if (crystal != null && crystal.isAlive && !crystal.isRemoved) {
                    val packet = ServerboundAttackPacket(crystal.id)
                    sending = packet
                    accepted = false
                    player.connection.send(packet)
                    if (accepted) {
                        player.resetAttackStrengthTicker()
                        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false)
                        CrystalOptimizerModule.onCrystalAttacked(crystal)
                        lastAttack = System.nanoTime()
                    }
                }
                if (System.nanoTime() - lastPlacement < delay.toLong() * 1_000_000L) return@withOwner
                val yaw = player.yRot
                val pitch = player.xRot
                val sneaking = player.isShiftKeyDown
                val serverRotation = RotationManager.getServerRotation()
                if (rotate) {
                    player.yRot = serverRotation.yaw
                    player.xRot = serverRotation.pitch
                }
                try {
                    for (target in targets) {
                        if (!MioScaffoldPlacement.canPlace(player, level, target.pos, slot, false, playersOnly = false)
                            || PacketMineModule.targetsPosition(target.pos) || PlacementReservations.reserved(target.pos, this)) continue
                        val hit = MioScaffoldPlacement.hit(player, level, target)
                        if (!player.isShiftKeyDown && interactive(level, hit.blockPos)) {
                            sendSneaking(player, true)
                            player.isShiftKeyDown = true
                        }
                        if (InventoryUtil.useItemOn(InteractionHand.MAIN_HAND, hit)) {
                            lastPlacement = System.nanoTime()
                            PlacementReservations.claim(target.pos, this, 1)
                            visual.show(target.pos)
                        }
                        if (player.inventory.getItem(slot).isEmpty) break
                    }
                } finally {
                    player.yRot = yaw
                    player.xRot = pitch
                    if (!sneaking && player.isShiftKeyDown) {
                        sendSneaking(player, false)
                        player.isShiftKeyDown = false
                    }
                }
            } finally {
                sending = null
                SilentSwapUtil.releasePreparedSlot(this)
                ready = false
            }
        }
    }

    private fun interactive(level: ClientLevel, pos: BlockPos): Boolean {
        val block = level.getBlockState(pos).block
        return block is CraftingTableBlock || block is AnvilBlock || block is ButtonBlock
            || block is BasePressurePlateBlock || block is EntityBlock || block is BedBlock
            || block is FenceGateBlock || block is DoorBlock || block is NoteBlock || block is TrapDoorBlock
    }

    private fun sendSneaking(player: LocalPlayer, shift: Boolean) {
        val input = player.input.keyPresses
        player.connection.send(ServerboundPlayerInputPacket(Input(input.forward(), input.backward(), input.left(),
            input.right(), input.jump(), shift, input.sprint())))
    }

    fun shouldClipAtLedge(): Boolean = isEnabled() && safeWalk && mc.player?.isPassenger == false

    fun edgeDepth(original: Float): Float {
        if (!shouldClipAtLedge()) return original
        val player = mc.player ?: return original
        val level = mc.level ?: return original
        val block = (player.mainHandItem.item as? BlockItem)?.block ?: return original
        val shape = block.defaultBlockState().getCollisionShape(level, BlockPos.ZERO)
        return if (shape.isEmpty) original else shape.bounds().ysize.toFloat()
    }

    fun limitMovement(movement: Vec3): Vec3 {
        if (!shouldClipAtLedge() || !movementSync) return movement
        val player = mc.player ?: return movement
        val level = mc.level ?: return movement
        if (!player.onGround()) return movement
        val box = player.boundingBox.move(movement.x, 0.0, movement.z).expandTowards(0.0, -1.0, 0.0)
        return if (level.noCollision(box)) Vec3(0.0, movement.y, 0.0) else movement
    }
}
