package meow.ancient.client.gui.hud

import meow.ancient.client.util.anticheat.AnticheatManager
import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.module.ModuleManager
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.render.ClickGuiModule
import meow.ancient.module.modules.render.HudModule
import meow.ancient.module.setting.KeybindSetting
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult
import org.joml.Matrix4f
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

class HudRenderer(private val serverStats: HudServerStats) {
    private val watermark = HudCard("Ancient", HudForm.WATERMARK)
    private val keybinds = HudCard("Keybinds", HudForm.LIST, "Preview", "K")
    private val potions = HudCard("Effects", HudForm.LIST, "Preview")
    private val target = HudCard("Target", HudForm.TARGET)
    private val staff = HudCard("Staff", HudForm.LIST, "Preview")
    private val arrayList = HudCard("Modules", HudForm.ARRAY, "Modules preview · none enabled")
    private val coords = HudCard("XYZ", HudForm.CHIP)
    private val speed = HudCard("BPS", HudForm.CHIP)
    private val tps = HudCard("TPS", HudForm.CHIP)
    private val ping = HudCard("PING", HudForm.CHIP)
    private val scoreboard = HudCard("Scoreboard", HudForm.SCOREBOARD, "Preview Line", "")
    private val cards = arrayOf(watermark, keybinds, potions, target, staff, arrayList, coords, speed, tps, ping, scoreboard)
    private val elements = arrayOf(HudElement.WATERMARK, HudElement.KEYBINDS, HudElement.POTIONS,
        HudElement.TARGET, HudElement.STAFF, HudElement.ARRAY_LIST, HudElement.COORDS,
        HudElement.SPEED, HudElement.TPS, HudElement.PING, HudElement.SCOREBOARD)
    private val notifications = HudNotifications()
    private val staffDetector = HudStaffDetector()
    private val widthComparator = Comparator<HudRow> { a, b -> b.width.compareTo(a.width) }
    private var level: ClientLevel? = null
    private var player: LocalPlayer? = null
    private var module: HudModule? = null
    private var lastX = 0.0
    private var lastZ = 0.0
    private var moduleFingerprint = Long.MIN_VALUE
    private var coordsText = ""
    private var speedText = ""
    private var tpsText = ""
    private var pingText = ""
    private var fpsText = ""
    private var coordsX = Long.MIN_VALUE
    private var coordsY = Long.MIN_VALUE
    private var coordsZ = Long.MIN_VALUE
    private var speedHundredths = Int.MIN_VALUE
    private var tpsHundredths = Int.MIN_VALUE
    private var latency = Int.MIN_VALUE
    private var fps = Int.MIN_VALUE
    private var targetName = ""
    private var targetHealthTenths = Int.MIN_VALUE
    private var targetAbsorptionTenths = Int.MIN_VALUE
    private var targetHealthText = ""
    private var frameNanos = 0L
    private var editing = false

    fun reset() {
        module?.dragController?.clearInteraction()
        level = null
        player = null
        moduleFingerprint = Long.MIN_VALUE
        coordsX = Long.MIN_VALUE
        coordsY = Long.MIN_VALUE
        coordsZ = Long.MIN_VALUE
        speedHundredths = Int.MIN_VALUE
        tpsHundredths = Int.MIN_VALUE
        latency = Int.MIN_VALUE
        fps = Int.MIN_VALUE
        targetName = ""
        targetHealthTenths = Int.MIN_VALUE
        targetAbsorptionTenths = Int.MIN_VALUE
        frameNanos = 0L
        editing = false
        staffDetector.reset()
        notifications.clear()
        var i = 0
        while (i < cards.size) cards[i++].reset()
    }

    fun update(module: HudModule) {
        this.module = module
        val currentLevel = mc.level ?: return reset()
        val currentPlayer = mc.player ?: return reset()
        if (level !== currentLevel) {
            reset()
            serverStats.reset()
            level = currentLevel
        }
        var horizontalSpeed = 0.0
        if (player === currentPlayer) {
            val dx = currentPlayer.x - lastX
            val dz = currentPlayer.z - lastZ
            horizontalSpeed = sqrt(dx * dx + dz * dz) * 20.0
        }
        player = currentPlayer
        lastX = currentPlayer.x
        lastZ = currentPlayer.z

        watermark.begin(module.isElementEnabled(HudElement.WATERMARK))
        if (watermark.enabled) {
            if ("Client" in module.watermarkElements) watermark.add("Ancient", highlighted = true, key = "client")
            if ("Nickname" in module.watermarkElements) watermark.add(currentPlayer.name.string, key = "player")
            if ("Fps" in module.watermarkElements) {
                val currentFps = mc.fps
                if (fps != currentFps) {
                    fps = currentFps
                    fpsText = "$currentFps FPS"
                }
                watermark.add(fpsText, key = "fps")
            }
        }
        updateModules(module)
        potions.begin(module.isElementEnabled(HudElement.POTIONS))
        if (potions.enabled) {
            for (effect in currentPlayer.activeEffects) {
                val seconds = (effect.duration.coerceAtLeast(0) + 19) / 20
                val duration = if (effect.isInfiniteDuration) "Infinite" else duration(seconds)
                val name = net.minecraft.network.chat.Component.translatable(effect.descriptionId).string
                potions.add("$name ${effect.amplifier + 1}", duration, key = effect.descriptionId)
            }
        }
        updateTarget(module)
        staff.begin(module.isElementEnabled(HudElement.STAFF))
        if (staff.enabled) staffDetector.update(currentLevel, currentPlayer.connection, staff)

        coords.begin(module.isElementEnabled(HudElement.COORDS))
        if (coords.enabled) {
            val x = floor(currentPlayer.x).toLong()
            val y = floor(currentPlayer.y).toLong()
            val z = floor(currentPlayer.z).toLong()
            if (coordsX != x || coordsY != y || coordsZ != z) {
                coordsX = x
                coordsY = y
                coordsZ = z
                coordsText = "$x  $y  $z"
            }
            coords.add(coordsText, key = "coordinates")
        }
        speed.begin(module.isElementEnabled(HudElement.SPEED))
        if (speed.enabled) {
            val hundredths = (horizontalSpeed * 100.0).roundToInt()
            if (speedHundredths != hundredths) {
                speedHundredths = hundredths
                speedText = decimal(hundredths, 100)
            }
            speed.add(speedText, key = "speed")
        }
        tps.begin(module.isElementEnabled(HudElement.TPS))
        if (tps.enabled) {
            val average = serverStats.average()
            val hundredths = if (average.isNaN()) -1 else (average * 100.0).roundToInt()
            if (tpsHundredths != hundredths) {
                tpsHundredths = hundredths
                tpsText = if (hundredths < 0) "Unavailable" else decimal(hundredths, 100)
            }
            tps.add(tpsText, key = "tps")
        }
        ping.begin(module.isElementEnabled(HudElement.PING))
        if (ping.enabled) {
            val currentLatency = currentPlayer.connection.getPlayerInfo(currentPlayer.getUUID())?.latency ?: -1
            if (latency != currentLatency) {
                latency = currentLatency
                pingText = if (latency < 0) "Unavailable" else "$latency ms"
            }
            ping.add(pingText, key = "ping")
        }
        updateScoreboard(module)
    }

    private fun updateModules(module: HudModule) {
        var fingerprint = 1L
        var i = 0
        val modules = ModuleManager.modules
        while (i < modules.size) {
            val entry = modules[i++]
            if (entry === ClickGuiModule) continue
            fingerprint = fingerprint * 31L + entry.name.hashCode()
            fingerprint = fingerprint * 31L + if (entry.state) entry.key.toLong() + 2L else 0L
        }
        val showKeybinds = module.isElementEnabled(HudElement.KEYBINDS)
        val showArrayList = module.isElementEnabled(HudElement.ARRAY_LIST)
        fingerprint = fingerprint * 31L + if (showKeybinds) 1L else 0L
        fingerprint = fingerprint * 31L + if (showArrayList) 1L else 0L
        if (fingerprint == moduleFingerprint) return
        moduleFingerprint = fingerprint
        keybinds.begin(showKeybinds)
        arrayList.begin(showArrayList)
        i = 0
        while (i < modules.size) {
            val entry = modules[i++]
            if (!entry.state || entry === ClickGuiModule) continue
            if (showArrayList) arrayList.add(entry.name)
            if (showKeybinds && entry.key != InputConstants.UNKNOWN.value) keybinds.add(entry.name, KeybindSetting.formatKey(entry.key))
        }
        if (arrayList.rowCount > 1) arrayList.rows.subList(0, arrayList.rowCount).sortWith(widthComparator)
    }

    private fun updateTarget(module: HudModule) {
        val auraTarget = if (HitAuraModule.isEnabled()) HitAuraModule.currentTarget else null
        val rayTraceTarget = if (module.targetRayTrace) {
            mc.crosshairPickEntity ?: (mc.hitResult as? EntityHitResult)?.entity
        } else null
        val selected: Entity? = auraTarget ?: rayTraceTarget
        val activeTarget = selected?.takeIf {
            !it.isRemoved && it.level() === level && it.isAlive
        }
        val entity = activeTarget ?: if (mc.gui.screen() is ChatScreen) mc.player else null
        target.begin(module.isElementEnabled(HudElement.TARGET) && entity != null)
        if (!target.enabled || entity == null) return
        target.skin = (entity as? AbstractClientPlayer)?.skin
        val name = entity.name.string
        if (targetName != name) {
            targetName = name
            target.initial = if (name.isEmpty()) "" else String(Character.toChars(name.codePointAt(0)))
        }
        target.hasHealth = entity is LivingEntity
        if (entity !is LivingEntity) {
            target.add(name, key = "target")
            target.healthFraction = 0f
            target.absorptionFraction = 0f
            return
        }
        val totalHp = AnticheatManager.getHealth(entity).coerceAtLeast(0f)
        val health: Float
        val absorption: Float
        if (AnticheatManager.isScoreboardHealth) {
            health = totalHp
            absorption = 0f
        } else {
            health = entity.health.coerceAtLeast(0f)
            absorption = entity.absorptionAmount.coerceAtLeast(0f)
        }
        val maxHealth = maxOf(entity.maxHealth.coerceAtLeast(1f), health + absorption)
        val barTotal = maxOf(maxHealth, health + absorption)
        val healthTenths = (health * 10f).roundToInt()
        val absorptionTenths = (absorption * 10f).roundToInt()
        if (targetHealthTenths != healthTenths || targetAbsorptionTenths != absorptionTenths) {
            targetHealthTenths = healthTenths
            targetAbsorptionTenths = absorptionTenths
            val hp = "${decimal(healthTenths, 10)} HP"
            targetHealthText = if (absorption > 0f) "$hp  +${decimal(absorptionTenths, 10)}" else hp
        }
        target.add(name, targetHealthText, key = "target")
        target.healthFraction = (health / barTotal).coerceIn(0f, 1f)
        target.absorptionFraction = (absorption / barTotal).coerceIn(0f, 1f - target.healthFraction)
    }

    fun render() {
        val graphics = RenderUtils.currentGraphics ?: return
        val module = module ?: return
        if (mc.level !== level || mc.player == null || mc.gui.hud.isHidden) {
            HudSettingsPopup.close()
            module.dragController.clearInteraction()
            frameNanos = 0L
            return
        }
        val now = System.nanoTime()
        val delta = if (frameNanos == 0L) 1f / 60f else ((now - frameNanos) * 1e-9).toFloat().coerceIn(0.0001f, 0.1f)
        frameNanos = now
        val chat = mc.gui.screen() is ChatScreen
        if (editing != chat) {
            editing = chat
            if (chat) {
                potions.setPreview("Preview", duration(Random.nextInt(30, 601)))
                scoreboard.setPreview("Preview Line", "")
            }
            if (!chat) module.dragController.clearInteraction()
        }
        updateTarget(module)
        val scale = module.scale.toFloat()
        val screenWidth = graphics.guiWidth().toFloat()
        val screenHeight = graphics.guiHeight().toFloat()
        val controller = module.dragController
        controller.beginFrame(screenWidth, screenHeight, delta)
        var i = 0
        while (i < cards.size) cards[i++].animate(delta, chat,
            (screenWidth - 14f) / scale, (screenHeight - 14f) / scale)
        notifications.update(delta, chat && module.isElementEnabled(HudElement.NOTIFICATIONS), module.notificationLimit.toInt())
        defaults(screenWidth, screenHeight, scale)
        val matrix = MatrixControl.matrix4fStack
        i = 0
        while (i < cards.size) {
            val card = cards[i]
            val drag = controller.place(elements[i], card.width * scale, card.height * scale,
                card.defaultX, card.defaultY, card.isVisible, delta)
            drag.hitTest = card.hitTest
            card.draw(matrix, drag.x, drag.y, scale, drag.alignment)
            if (chat && card.isVisible && (drag.hovered || drag.dragging)) drawEditBounds(matrix, drag, scale)
            i++
        }
        val notificationWidth = notifications.width * scale
        val notificationHeight = notifications.height * scale
        val notificationDrag = controller.place(HudElement.NOTIFICATIONS, notificationWidth, notificationHeight,
            screenWidth - 7f - notificationWidth, screenHeight - 57f - notificationHeight,
            module.isElementEnabled(HudElement.NOTIFICATIONS) && notifications.isVisible, delta)
        if (module.isElementEnabled(HudElement.NOTIFICATIONS) && notifications.isVisible) {
            notifications.draw(matrix, notificationDrag.x, notificationDrag.y, scale, notificationDrag.alignment)
            if (chat && (notificationDrag.hovered || notificationDrag.dragging)) drawEditBounds(matrix, notificationDrag, scale)
        }
        HudSettingsPopup.render(delta)
    }

    private fun defaults(screenWidth: Float, screenHeight: Float, scale: Float) {
        val gap = 5f
        watermark.defaultX = 7f
        watermark.defaultY = 7f
        var topX = if (watermark.isVisible) 7f + watermark.width * scale + gap else 7f
        var topY = 7f
        var topBottom = if (watermark.isVisible) 7f + watermark.height * scale else 7f
        if (ping.isVisible && topX + ping.width * scale > screenWidth - 7f - arrayList.width * scale - gap) {
            topX = 7f
            topY = topBottom + gap
        }
        ping.defaultX = topX
        ping.defaultY = topY
        if (ping.isVisible) {
            topX += ping.width * scale + gap
            topBottom = maxOf(topBottom, topY + ping.height * scale)
        }
        if (tps.isVisible && topX + tps.width * scale > screenWidth - 7f - arrayList.width * scale - gap) {
            topX = 7f
            topY = topBottom + gap
        }
        tps.defaultX = topX
        tps.defaultY = topY
        if (tps.isVisible) topBottom = maxOf(topBottom, topY + tps.height * scale)
        keybinds.defaultX = 7f
        keybinds.defaultY = topBottom + gap
        staff.defaultX = 7f
        staff.defaultY = keybinds.defaultY + if (keybinds.isVisible) keybinds.height * scale + gap else 0f
        arrayList.defaultX = screenWidth - 7f - arrayList.width * scale
        arrayList.defaultY = 7f
        potions.defaultX = screenWidth - 7f - potions.width * scale
        potions.defaultY = 7f + if (arrayList.isVisible) arrayList.height * scale + gap else 0f
        coords.defaultX = 7f
        coords.defaultY = screenHeight - 28f - coords.height * scale
        speed.defaultX = 7f + if (coords.isVisible) coords.width * scale + gap else 0f
        speed.defaultY = screenHeight - 28f - speed.height * scale
        if (speed.defaultX + speed.width * scale > screenWidth * 0.5f - 95f) {
            speed.defaultX = 7f
            speed.defaultY = coords.defaultY - speed.height * scale - gap
        }
        target.defaultX = (screenWidth - target.width * scale) * 0.5f
        target.defaultY = screenHeight - 79f - target.height * scale
        scoreboard.defaultX = screenWidth - 7f - scoreboard.width * scale
        scoreboard.defaultY = (screenHeight - scoreboard.height * scale) * 0.5f
    }

    private fun isBlankScore(text: String): Boolean =
        text.trim().isEmpty() || text.all { it == '§' || it.isWhitespace() }

    private fun updateScoreboard(module: HudModule) {
        val currentLevel = level ?: return
        val scoreboardService = currentLevel.scoreboard
        val scoreboardObj = scoreboardService.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR)
        val isChat = mc.gui.screen() is ChatScreen
        val enabled = module.isElementEnabled(HudElement.SCOREBOARD) && (scoreboardObj != null || isChat)
        scoreboard.begin(enabled)
        if (!enabled) return

        if (scoreboardObj != null) {
            scoreboard.setTitle(scoreboardObj.displayName.string)

            val rawList = scoreboardService.listPlayerScores(scoreboardObj)
                .filter { !it.isHidden }
                .sortedWith(Comparator { a, b -> b.value.compareTo(a.value) })
                .take(15)

            val lines = ArrayList<String>()
            for (score in rawList) {
                val team = scoreboardService.getPlayersTeam(score.owner)
                val lineText = net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team, score.ownerName()).string
                val detail = score.display()?.string ?: ""
                val full = if (detail.isNotEmpty()) "$lineText $detail" else lineText
                lines.add(full)
            }

            while (lines.isNotEmpty() && isBlankScore(lines.first())) {
                lines.removeAt(0)
            }
            while (lines.isNotEmpty() && isBlankScore(lines.last())) {
                lines.removeAt(lines.lastIndex)
            }

            var prevBlank = false
            for ((index, text) in lines.withIndex()) {
                val blank = isBlankScore(text)
                if (blank && prevBlank) continue
                prevBlank = blank
                scoreboard.add(if (blank) "" else text, "", key = "score_$index")
            }
        }
    }

    private fun drawEditBounds(matrix: Matrix4f, drag: HudDrag, scale: Float) {
        val palette = M3Theme.current
        val color = palette.primary.alpha(if (drag.dragging) 185 else 95)
        RenderUtils.roundedRectWithBorder(matrix, drag.x, drag.y, drag.width, drag.height,
            palette.stateHover.alpha(if (drag.dragging) 14 else 8), 5f * scale, 0.7f * scale,
            color, ClientRenderPipeline.HUD_RECT)
        var i = 0
        while (i < 3) {
            RenderUtils.roundedRect(matrix, drag.x + (3f + i * 2.5f) * scale, drag.y + 2.5f * scale,
                scale, scale, color, 0.5f * scale, ClientRenderPipeline.HUD_RECT)
            i++
        }
    }

    private fun duration(seconds: Int): String {
        val remainder = seconds % 60
        return "${seconds / 60}:${if (remainder < 10) "0" else ""}$remainder"
    }

    private fun decimal(value: Int, divisor: Int): String {
        val fraction = value % divisor
        return "${value / divisor}.${if (divisor == 100 && fraction < 10) "0" else ""}$fraction"
    }
}
