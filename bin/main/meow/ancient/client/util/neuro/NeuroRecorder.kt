package meow.ancient.client.util.neuro

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.EventManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PacketSendEvent
import meow.ancient.event.events.PlayerTickEndEvent
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.io.BufferedWriter
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import sweetie.evaware.flora.api.Commando

class NeuroRecorder {
    companion object {
        private const val TARGET_STICKY_TICKS = 60
        private const val RETENTION_TICKS = 40
        private const val MAX_TARGET_DIST = 5.0
        private const val FLUSH_INTERVAL = 256
        private const val CSV_HEADER = "t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint\n"
        private val EXPECTED_COLUMNS = CSV_HEADER.split(",").size
        const val TARGET_DATASET_TICKS = 12000
        private val DATASET_NAME = Regex("[\\p{L}\\p{N}_.-]+")

        private val LERP_X = doubleArrayOf(0.2, 0.5, 0.8)
        private val LERP_Y = doubleArrayOf(0.15, 0.4, 0.65, 0.9)
        private val LERP_Z = doubleArrayOf(0.2, 0.5, 0.8)

        fun formatMinutes(ticks: Int): String = String.format(Locale.ROOT, "%.1f мин", ticks / 1200.0)

        fun formatNeeded(totalTicks: Int): String? =
            if (totalTicks >= TARGET_DATASET_TICKS) null else formatMinutes(TARGET_DATASET_TICKS - totalTicks)

        fun evaluateQuality(totalTicks: Int): String = when {
            totalTicks < 6000 -> "мало"
            totalTicks < 18000 -> "маловато"
            totalTicks < 36000 -> "нормально"
            else -> "хорошо"
        }

        fun listDatasets(): List<String> {
            val list = mutableListOf<String>()
            try {
                Files.list(NeuroModel.getDataDir()).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".csv") }.sorted().forEach { path ->
                        val stats = countLines(path)
                        list += path.fileName.toString().removeSuffix(".csv") +
                            " · " + formatMinutes(stats[0]) +
                            " · " + evaluateQuality(stats[0]) +
                            if (stats[1] > 0) " · §cбитых строк ${stats[1]}§r" else ""
                    }
                }
            } catch (_: Exception) {}
            return list
        }

        fun getTotalTicks(): Int {
            var total = 0
            try {
                Files.list(NeuroModel.getDataDir()).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".csv") }
                        .forEach { total += countLines(it)[0] }
                }
            } catch (_: Exception) {}
            return total
        }

        fun getCorruptedLinesCount(): Int {
            var corrupted = 0
            try {
                Files.list(NeuroModel.getDataDir()).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".csv") }
                        .forEach { corrupted += countLines(it)[1] }
                }
            } catch (_: Exception) {}
            return corrupted
        }

        fun getDatasetTicks(name: String): Int {
            if (!isDatasetNameValid(name)) return 0
            val path = NeuroModel.getDataDir().resolve("$name.csv")
            return if (Files.isRegularFile(path)) countLines(path)[0] else 0
        }

        private fun isDatasetNameValid(name: String): Boolean =
            name != "." && name != ".." && DATASET_NAME.matches(name)

        fun distanceToBox(vec: Vec3, box: AABB): Double {
            val dx = maxOf(box.minX - vec.x, 0.0, vec.x - box.maxX)
            val dy = maxOf(box.minY - vec.y, 0.0, vec.y - box.maxY)
            val dz = maxOf(box.minZ - vec.z, 0.0, vec.z - box.maxZ)
            return Math.sqrt(dx * dx + dy * dy + dz * dz)
        }

        private fun countLines(path: Path): IntArray {
            var valid = 0
            var corrupted = 0
            try {
                Files.lines(path).use { stream ->
                    stream.forEach { line ->
                        if (line.isBlank() || line.startsWith("t,")) return@forEach
                        valid++
                        val cols = 1 + line.count { it == ',' }
                        if (cols != EXPECTED_COLUMNS) corrupted++
                    }
                }
            } catch (_: Exception) {}
            return intArrayOf(valid, corrupted)
        }

        private fun truncateTrailingIncompleteLine(path: Path) {
            FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
                var position = channel.size()
                val buffer = ByteBuffer.allocate(minOf(position, 8192L).toInt())
                while (position > 0) {
                    val length = minOf(position, buffer.capacity().toLong()).toInt()
                    position -= length
                    buffer.clear().limit(length)
                    while (buffer.hasRemaining()) {
                        if (channel.read(buffer, position + buffer.position()) < 0) {
                            throw java.io.IOException("Не прочитать конец файла")
                        }
                    }
                    for (index in length - 1 downTo 0) {
                        if (buffer[index] == 10.toByte()) {
                            channel.truncate(position + index + 1L)
                            return
                        }
                    }
                }
                channel.truncate(0L)
            }
        }
    }

    private var writer: BufferedWriter? = null
    private var currentFile: Path? = null
    private var datasetName = "session"
    @Volatile
    var isRecording = false
        private set
    private var sessionTicks = 0
    private var tickCounter = 0
    private var attackCount = 0
    private var retentionTicksLeft = 0
    private var unwrittenCount = 0
    private var lastAttackedTargetId = -1
    private var lastAttackedTick = -1
    private var attackFlag = false
    private var hasPrevRotation = false
    private var prevYaw = 0.0f
    private var prevPitch = 0.0f

    @Synchronized
    fun startRecording(name: String): String? {
        if (isRecording) return "Запись уже идёт: $datasetName"
        if (!isDatasetNameValid(name)) return "Недопустимое имя датасета: $name"
        return try {
            val dir = NeuroModel.getDataDir()
            Files.createDirectories(dir)
            val file = dir.resolve("$name.csv")
            if (Files.isRegularFile(file) && Files.size(file) > 0L) truncateTrailingIncompleteLine(file)
            val isNew = !Files.isRegularFile(file) || Files.size(file) == 0L
            writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            if (isNew) writer!!.write(CSV_HEADER)
            writer!!.flush()
            currentFile = file
            datasetName = name
            unwrittenCount = 0
            retentionTicksLeft = 0
            attackCount = 0
            sessionTicks = 0
            tickCounter = 0
            lastAttackedTick = -1
            lastAttackedTargetId = -1
            hasPrevRotation = false
            attackFlag = false
            isRecording = true
            EventManager.register(this)
            null
        } catch (e: Exception) {
            stopCapture()
            "Не открыть файл $name.csv: ${e.message}"
        }
    }

    @Synchronized
    fun stopRecording(): String {
        if (!isRecording) return "Запись не идёт."
        stopCapture()
        val totalTicks = currentFile?.let { countLines(it)[0] } ?: sessionTicks
        val allDatasetsTicks = getTotalTicks()
        val needed = formatNeeded(allDatasetsTicks)
        return "$datasetName.csv: +${formatMinutes(sessionTicks)} за сессию, всего " +
            "${formatMinutes(totalTicks)} (${evaluateQuality(totalTicks)}), ударов $attackCount" +
            (if (needed != null) ". До обучения не хватает ещё $needed" else "")
    }

    @Commando(priority = -128)
    @Synchronized
    fun onPacketSend(event: PacketSendEvent) {
        if (!isRecording || event.cancel) return
        val packet = event.packet as? ServerboundAttackPacket ?: return
        val entity = mc.level?.getEntity(packet.entityId()) as? LivingEntity ?: return
        if (entity === mc.player) return
        lastAttackedTargetId = entity.id
        lastAttackedTick = tickCounter
        attackFlag = true
    }

    @Commando
    @Synchronized
    fun onDisconnect(event: ClientDisconnectEvent) {
        stopCapture()
    }

    @Commando
    @Synchronized
    fun onPlayerTick(event: PlayerTickEndEvent) {
        if (!isRecording) return
        if (!inGame || writer == null) {
            stopCapture()
            return
        }
        tickCounter++

        val player = mc.player ?: return
        val rotation = RotationManager.currentRotation
        val yaw = rotation?.yaw ?: player.yRot
        val pitch = rotation?.pitch ?: player.xRot
        val dyaw = if (hasPrevRotation) Mth.wrapDegrees(yaw - prevYaw) else 0.0f
        val dpitch = if (hasPrevRotation) pitch - prevPitch else 0.0f
        prevYaw = yaw
        prevPitch = pitch
        hasPrevRotation = true

        val target = findTarget()
        if (target == null) {
            if (retentionTicksLeft > 0) {
                retentionTicksLeft--
                recordTick(false, yaw, pitch, dyaw, dpitch, null)
            }
            attackFlag = false
        } else {
            retentionTicksLeft = RETENTION_TICKS
            recordTick(true, yaw, pitch, dyaw, dpitch, target)
        }
    }

    private fun findTarget(): LivingEntity? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val eyePos = player.eyePosition

        if (lastAttackedTargetId >= 0 && tickCounter - lastAttackedTick <= TARGET_STICKY_TICKS) {
            val entity = level.getEntity(lastAttackedTargetId)
            if (entity is LivingEntity && entity.isAlive && distanceToBox(eyePos, entity.boundingBox) <= MAX_TARGET_DIST) {
                return entity
            }
        }

        var closest: LivingEntity? = null
        var closestDist = MAX_TARGET_DIST
        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || entity == player || !entity.isAlive) continue
            val dist = distanceToBox(eyePos, entity.boundingBox)
            if (dist <= closestDist && isEntityVisible(entity)) {
                closestDist = dist
                closest = entity
            }
        }
        return closest
    }

    private fun recordTick(hasAim: Boolean, yaw: Float, pitch: Float, dyaw: Float, dpitch: Float, target: LivingEntity?) {
        val attackedNow = attackFlag.also { attackFlag = false }
        if (attackedNow) attackCount++

        val player = mc.player ?: return
        val eyePos = player.eyePosition
        val box = target?.boundingBox
        val relativeX = if (box != null) (box.minX + box.maxX) * 0.5 - eyePos.x else 0.0
        val relativeY = if (box != null) (box.minY + box.maxY) * 0.5 - eyePos.y else 0.0
        val relativeZ = if (box != null) (box.minZ + box.maxZ) * 0.5 - eyePos.z else 0.0
        val gcd = GCDUtil.getGCD()

        try {
            writer!!.write(String.format(Locale.ROOT,
                "%d,%.6f,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.1f,%d,%d\n",
                tickCounter, gcd,
                if (hasAim) 1 else 0,
                yaw, pitch, dyaw, dpitch,
                if (target != null) 1 else 0,
                target?.id ?: -1,
                relativeX, relativeY, relativeZ,
                box?.xsize ?: 0.0, box?.ysize ?: 0.0,
                if (box != null) distanceToBox(eyePos, box) else -1.0,
                if (target != null && isEntityVisible(target)) 1 else 0,
                if (box != null && isLookingAt(yaw, pitch, box)) 1 else 0,
                if (attackedNow) 1 else 0,
                target?.health ?: -1.0f,
                if (player.onGround()) 1 else 0,
                if (player.isSprinting) 1 else 0
            ))
            sessionTicks++
            if (++unwrittenCount >= FLUSH_INTERVAL) {
                unwrittenCount = 0
                writer!!.flush()
            }
        } catch (_: Exception) { stopCapture() }
    }

    private fun isLookingAt(yaw: Float, pitch: Float, box: AABB): Boolean {
        val player = mc.player ?: return false
        val eyePos = player.eyePosition
        if (box.contains(eyePos)) return true
        val radYaw = Math.toRadians(yaw.toDouble())
        val radPitch = Math.toRadians(pitch.toDouble())
        val cosPitch = Math.cos(radPitch)
        val look = Vec3(-Math.sin(radYaw) * cosPitch, -Math.sin(radPitch), Math.cos(radYaw) * cosPitch)
        return box.clip(eyePos, eyePos.add(look.scale(MAX_TARGET_DIST))).isPresent
    }

    private fun isEntityVisible(living: LivingEntity): Boolean {
        val box = living.boundingBox
        val level = mc.level ?: return false
        val player = mc.player ?: return false
        for (x in LERP_X) for (y in LERP_Y) for (z in LERP_Z) {
            val point = Vec3(
                Mth.lerp(x, box.minX, box.maxX),
                Mth.lerp(y, box.minY, box.maxY),
                Mth.lerp(z, box.minZ, box.maxZ)
            )
            val ctx = net.minecraft.world.level.ClipContext(
                player.eyePosition, point,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
            )
            if (level.clip(ctx).type == net.minecraft.world.phys.HitResult.Type.MISS) return true
        }
        return false
    }

    private fun stopCapture() {
        isRecording = false
        attackFlag = false
        try {
            EventManager.unregister(this)
        } finally {
            closeWriter()
        }
    }

    private fun closeWriter() {
        val output = writer ?: return
        writer = null
        try { output.flush() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    fun getDatasetName(): String = datasetName
    fun getSessionTicks(): Int = sessionTicks
}
