package tech.onetap.util.neuro.rotation;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventTick;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Запись обучающего датасета (.csv) для Neuro ротации.
 * Портирован из ScriptInternal041 (ROKSTAR).
 */
public final class NeuroRecorder {
    private static final int TARGET_STICKY_TICKS = 60;
    private static final int RETENTION_TICKS = 40;
    private static final double MAX_TARGET_DIST = 5.0;
    private static final int FLUSH_INTERVAL = 256;
    private static final String CSV_HEADER = "t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint\n";
    private static final int EXPECTED_COLUMNS = CSV_HEADER.split(",").length;
    public static final int TARGET_DATASET_TICKS = 12000;

    private static final double[] LERP_X = new double[]{0.2, 0.5, 0.8};
    private static final double[] LERP_Y = new double[]{0.15, 0.4, 0.65, 0.9};
    private static final double[] LERP_Z = new double[]{0.2, 0.5, 0.8};

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private BufferedWriter writer;
    private Path currentFile;
    private String datasetName = "session";
    private boolean recording;
    private int sessionTicks;
    private int tickCounter;
    private int attackCount;
    private int retentionTicksLeft;
    private int unwrittenCount;
    private int lastAttackedTargetId = -1;
    private int lastAttackedTick = -1;
    private boolean attackFlag;
    private boolean hasPrevRotation;
    private float prevYaw;
    private float prevPitch;

    public String startRecording(String name) {
        if (this.recording) {
            return "Запись уже идёт: " + this.datasetName;
        }
        try {
            Path dir = NeuroModel.getDataDir();
            Files.createDirectories(dir, new FileAttribute[0]);
            Path file = dir.resolve(name + ".csv");
            boolean isNew = !Files.isRegularFile(file, new LinkOption[0]) || Files.size(file) == 0L;
            if (!isNew) {
                truncateTrailingIncompleteLine(file);
            }
            this.writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (isNew) {
                this.writer.write(CSV_HEADER);
            }
            this.currentFile = file;
        } catch (Exception exception) {
            return "Не открыть файл " + name + ".csv: " + exception.getMessage();
        }

        this.datasetName = name;
        this.unwrittenCount = 0;
        this.retentionTicksLeft = 0;
        this.attackCount = 0;
        this.sessionTicks = 0;
        this.tickCounter = 0;
        this.lastAttackedTick = -1;
        this.lastAttackedTargetId = -1;
        this.hasPrevRotation = false;
        this.attackFlag = false;
        this.recording = true;

        Onetap.getInstance().getEventBus().subscribe(this);
        return null;
    }

    public String stopRecording() {
        if (!this.recording) {
            return "Запись не идёт.";
        }
        Onetap.getInstance().getEventBus().unsubscribe(this);
        this.recording = false;
        closeWriter();

        int totalTicks = this.currentFile == null ? this.sessionTicks : countLines(this.currentFile)[0];
        int allDatasetsTicks = getTotalTicks();
        String needed = formatNeeded(allDatasetsTicks);

        return this.datasetName + ".csv: +" + formatMinutes(this.sessionTicks) + " за сессию, всего "
                + formatMinutes(totalTicks) + " (" + evaluateQuality(totalTicks) + "), ударов "
                + this.attackCount + (needed == null ? "" : ". До обучения не хватает ещё " + needed);
    }

    @EventHandler
    public void onAttackEvent(EventAttack event) {
        if (!this.recording) return;
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living && living != mc.player) {
            this.lastAttackedTargetId = living.getId();
            this.lastAttackedTick = this.tickCounter;
            this.attackFlag = true;
        }
    }

    public void onManualAttack(Entity entity) {
        if (!this.recording) return;
        if (entity instanceof LivingEntity living && living != mc.player) {
            this.lastAttackedTargetId = living.getId();
            this.lastAttackedTick = this.tickCounter;
            this.attackFlag = true;
        }
    }

    @EventHandler
    public void onPlayerTick(EventTick event) {
        if (mc.player == null || mc.world == null || this.writer == null || !this.recording) {
            return;
        }
        ++this.tickCounter;

        Rotation currentRot = RotationComponent.getInstance().isRotating() && RotationComponent.getInstance().targetRotation() != null
                ? RotationComponent.getInstance().targetRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch());

        float yaw = currentRot.getYaw();
        float pitch = currentRot.getPitch();
        float dyaw = this.hasPrevRotation ? MathHelper.wrapDegrees(yaw - this.prevYaw) : 0.0f;
        float dpitch = this.hasPrevRotation ? pitch - this.prevPitch : 0.0f;
        this.prevYaw = yaw;
        this.prevPitch = pitch;
        this.hasPrevRotation = true;

        LivingEntity target = findTarget();
        boolean hasAim = target != null;

        if (target == null) {
            if (this.retentionTicksLeft-- > 0) {
                recordTick(hasAim, yaw, pitch, dyaw, dpitch, null);
            }
            this.attackFlag = false;
        } else {
            this.retentionTicksLeft = RETENTION_TICKS;
            recordTick(hasAim, yaw, pitch, dyaw, dpitch, target);
        }
    }

    private LivingEntity findTarget() {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eyePos = mc.player.getEyePos();

        if (this.lastAttackedTargetId >= 0 && this.tickCounter - this.lastAttackedTick <= TARGET_STICKY_TICKS) {
            Entity entity = mc.world.getEntityById(this.lastAttackedTargetId);
            if (entity instanceof LivingEntity living && living.isAlive() && distanceToBox(eyePos, living.getBoundingBox()) <= MAX_TARGET_DIST) {
                return living;
            }
        }

        LivingEntity closest = null;
        double closestDist = MAX_TARGET_DIST;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            double dist = distanceToBox(eyePos, living.getBoundingBox());
            if (dist <= closestDist && isEntityVisible(living)) {
                closestDist = dist;
                closest = living;
            }
        }
        return closest;
    }

    private void recordTick(boolean hasAim, float yaw, float pitch, float dyaw, float dpitch, LivingEntity target) {
        boolean attackedNow = this.attackFlag;
        this.attackFlag = false;
        if (attackedNow) {
            ++this.attackCount;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Box box = target == null ? null : target.getBoundingBox();
        Vec3d diff = box == null ? Vec3d.ZERO : box.getCenter().subtract(eyePos);
        float gcd = GCDFixer.getGCDValue();

        try {
            this.writer.write(String.format(Locale.ROOT,
                    "%d,%.6f,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.1f,%d,%d\n",
                    this.tickCounter,
                    gcd,
                    hasAim ? 1 : 0,
                    yaw,
                    pitch,
                    dyaw,
                    dpitch,
                    target == null ? 0 : 1,
                    target == null ? -1 : target.getId(),
                    diff.x,
                    diff.y,
                    diff.z,
                    box == null ? 0.0 : box.getLengthX(),
                    box == null ? 0.0 : box.getLengthY(),
                    box == null ? -1.0 : distanceToBox(eyePos, box),
                    target != null && isEntityVisible(target) ? 1 : 0,
                    box != null && isLookingAt(yaw, pitch, box) ? 1 : 0,
                    attackedNow ? 1 : 0,
                    target == null ? -1.0f : target.getHealth(),
                    mc.player.isOnGround() ? 1 : 0,
                    mc.player.isSprinting() ? 1 : 0));

            ++this.sessionTicks;
            if (++this.unwrittenCount >= FLUSH_INTERVAL) {
                this.unwrittenCount = 0;
                this.writer.flush();
            }
        } catch (Exception exception) {
            closeWriter();
        }
    }

    private boolean isLookingAt(float yaw, float pitch, Box box) {
        Vec3d eyePos = mc.player.getEyePos();
        if (box.contains(eyePos)) {
            return true;
        }
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);
        double cosPitch = Math.cos(radPitch);
        Vec3d lookVec = new Vec3d(-Math.sin(radYaw) * cosPitch, -Math.sin(radPitch), Math.cos(radYaw) * cosPitch);
        return box.raycast(eyePos, eyePos.add(lookVec.multiply(MAX_TARGET_DIST))).isPresent();
    }

    private boolean isEntityVisible(LivingEntity living) {
        Box box = living.getBoundingBox();
        for (double x : LERP_X) {
            for (double y : LERP_Y) {
                for (double z : LERP_Z) {
                    Vec3d point = new Vec3d(
                            MathHelper.lerp(x, box.minX, box.maxX),
                            MathHelper.lerp(y, box.minY, box.maxY),
                            MathHelper.lerp(z, box.minZ, box.maxZ));
                    if (isPointVisible(point)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isPointVisible(Vec3d point) {
        return mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(), point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        )).getType() == HitResult.Type.MISS;
    }

    public static double distanceToBox(Vec3d vec, Box box) {
        double dx = Math.max(Math.max(box.minX - vec.x, 0.0), vec.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - vec.y, 0.0), vec.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - vec.z, 0.0), vec.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void closeWriter() {
        try {
            if (this.writer != null) {
                this.writer.flush();
                this.writer.close();
            }
        } catch (Exception ignored) {
        }
        this.writer = null;
    }

    private static void truncateTrailingIncompleteLine(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long size = channel.size();
            ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(size, 8192L));
            channel.read(buffer, size - (long) buffer.capacity());
            byte[] array = buffer.array();
            for (int i = array.length - 1; i >= 0; --i) {
                if (array[i] == 10) {
                    long newSize = size - (long) array.length + (long) i + 1L;
                    if (newSize < size) {
                        channel.truncate(newSize);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static String formatMinutes(int ticks) {
        return String.format(Locale.ROOT, "%.1f мин", (double) ticks / 1200.0);
    }

    public static String formatNeeded(int totalTicks) {
        return totalTicks >= TARGET_DATASET_TICKS ? null : formatMinutes(TARGET_DATASET_TICKS - totalTicks);
    }

    public static String evaluateQuality(int totalTicks) {
        if (totalTicks < 6000) {
            return "мало";
        }
        if (totalTicks < 18000) {
            return "маловато";
        }
        if (totalTicks < 36000) {
            return "нормально";
        }
        return "хорошо";
    }

    public static List<String> listDatasets() {
        List<String> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(NeuroModel.getDataDir())) {
            stream.filter(path -> path.getFileName().toString().endsWith(".csv")).sorted().forEach(path -> {
                int[] stats = countLines(path);
                list.add(path.getFileName().toString().replaceFirst("\\.csv$", "")
                        + " · " + formatMinutes(stats[0])
                        + " · " + evaluateQuality(stats[0])
                        + (stats[1] > 0 ? " · §cбитых строк " + stats[1] + "§r" : ""));
            });
        } catch (Exception ignored) {
        }
        return list;
    }

    public static int getTotalTicks() {
        int total = 0;
        try (Stream<Path> stream = Files.list(NeuroModel.getDataDir())) {
            for (Path path : stream.filter(path -> path.getFileName().toString().endsWith(".csv")).toList()) {
                total += countLines(path)[0];
            }
        } catch (Exception ignored) {
        }
        return total;
    }

    public static int getCorruptedLinesCount() {
        int count = 0;
        try (Stream<Path> stream = Files.list(NeuroModel.getDataDir())) {
            for (Path path : stream.filter(path -> path.getFileName().toString().endsWith(".csv")).toList()) {
                count += countLines(path)[1];
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    public static int getDatasetTicks(String name) {
        Path path = NeuroModel.getDataDir().resolve(name + ".csv");
        return Files.isRegularFile(path, new LinkOption[0]) ? countLines(path)[0] : 0;
    }

    private static int[] countLines(Path path) {
        int valid = 0;
        int corrupted = 0;
        try (Stream<String> stream = Files.lines(path)) {
            Iterator<String> it = stream.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line.isBlank() || line.startsWith("t,")) continue;
                ++valid;
                int cols = 1;
                for (int i = 0; i < line.length(); ++i) {
                    if (line.charAt(i) == ',') ++cols;
                }
                if (cols != EXPECTED_COLUMNS) {
                    ++corrupted;
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{valid, corrupted};
    }

    public String getDatasetName() {
        return this.datasetName;
    }

    public boolean isRecording() {
        return this.recording;
    }

    public int getSessionTicks() {
        return this.sessionTicks;
    }

    public int getRowCount() {
        return this.sessionTicks;
    }
}
