package tech.onetap.util.neuro.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.GCDFixer;

/**
 * Собирает RAW-строку тика (21 колонка — формат датасета train_neuro.py)
 * и выводит из окна истории 16 фич — формулы 1:1 с compute_features
 * в тренере, что проверяется selftest'ом (.ai selftest).
 *
 * Одна инстанс — одна траектория (свой игрок или наблюдаемый в дампе),
 * без static-состояния.
 */
public class NeuroFeatureCollector implements IMinecraft {

    private final float[][] ring = new float[NeuroFeatureSchema.SEQ_LEN][NeuroFeatureSchema.RAW_COLUMN_COUNT];
    private int head = 0;
    private int size = 0;

    private boolean hasPrevRotation = false;
    private float prevYaw;
    private float prevPitch;

    public void reset() {
        head = 0;
        size = 0;
        hasPrevRotation = false;
        prevYaw = 0.0f;
        prevPitch = 0.0f;
    }

    public boolean isWarm() {
        return size >= NeuroFeatureSchema.SEQ_LEN;
    }

    /**
     * Добавляет готовую RAW-строку (selftest, восстановление истории).
     */
    public void pushRaw(float[] row) {
        System.arraycopy(row, 0, ring[head], 0, NeuroFeatureSchema.RAW_COLUMN_COUNT);
        head = (head + 1) % NeuroFeatureSchema.SEQ_LEN;
        if (size < NeuroFeatureSchema.SEQ_LEN) size++;
        hasPrevRotation = true;
        prevYaw = row[NeuroFeatureSchema.R_YAW];
        prevPitch = row[NeuroFeatureSchema.R_PITCH];
    }

    /**
     * Собирает RAW-строку тика из состояния актора и цели и кладёт в историю.
     *
     * @param actor    чья ротация описывается (свой игрок или наблюдаемый)
     * @param target   цель актора, null если нет
     * @param aimPoint точа прицеливания (для рекордера — центр хитбокса,
     * @return копия собранной RAW-строки (слот кольца переиспользуется)
     */
    public float[] pushFrame(LivingEntity actor, LivingEntity target, Vec3d aimPoint,
                             boolean clean, long t) {
        float[] row = ring[head];

        float yaw = MathHelper.wrapDegrees(actor.getYaw());
        float pitch = actor.getPitch();

        float dyaw = 0.0f;
        float dpitch = 0.0f;
        if (hasPrevRotation) {
            dyaw = MathHelper.wrapDegrees(yaw - prevYaw);
            dpitch = pitch - prevPitch;
        }
        prevYaw = yaw;
        prevPitch = pitch;
        hasPrevRotation = true;

        row[NeuroFeatureSchema.R_T] = t;
        row[NeuroFeatureSchema.R_GCD] = GCDFixer.getGCDValue();
        row[NeuroFeatureSchema.R_CLEAN] = clean ? 1.0f : 0.0f;
        row[NeuroFeatureSchema.R_YAW] = yaw;
        row[NeuroFeatureSchema.R_PITCH] = pitch;
        row[NeuroFeatureSchema.R_DYAW] = dyaw;
        row[NeuroFeatureSchema.R_DPITCH] = dpitch;

        if (target != null) {
            Vec3d eye = actor.getEyePos();
            Vec3d diff = aimPoint.subtract(eye);
            Box box = target.getBoundingBox();

            row[NeuroFeatureSchema.R_HAS] = 1.0f;
            row[NeuroFeatureSchema.R_TID] = target.getId();
            row[NeuroFeatureSchema.R_RX] = (float) diff.x;
            row[NeuroFeatureSchema.R_RY] = (float) diff.y;
            row[NeuroFeatureSchema.R_RZ] = (float) diff.z;
            row[NeuroFeatureSchema.R_BW] = (float) (box.maxX - box.minX);
            row[NeuroFeatureSchema.R_BH] = (float) (box.maxY - box.minY);
            row[NeuroFeatureSchema.R_DIST] = (float) distanceToBox(eye, box);
            row[NeuroFeatureSchema.R_VIS] = isVisible(eye, aimPoint, actor) ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_ON] = target.isOnGround() ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_ATK] =
                    actor instanceof PlayerEntity player
                            && player.getAttackCooldownProgress(0.0f) >= 1.0f ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_HP] = target.getHealth();
        } else {
            row[NeuroFeatureSchema.R_HAS] = 0.0f;
            row[NeuroFeatureSchema.R_TID] = -1.0f;
            row[NeuroFeatureSchema.R_RX] = 0.0f;
            row[NeuroFeatureSchema.R_RY] = 0.0f;
            row[NeuroFeatureSchema.R_RZ] = 0.0f;
            row[NeuroFeatureSchema.R_BW] = 0.0f;
            row[NeuroFeatureSchema.R_BH] = 0.0f;
            row[NeuroFeatureSchema.R_DIST] = 0.0f;
            row[NeuroFeatureSchema.R_VIS] = 0.0f;
            row[NeuroFeatureSchema.R_ON] = 0.0f;
            row[NeuroFeatureSchema.R_ATK] = 0.0f;
            row[NeuroFeatureSchema.R_HP] = 0.0f;
        }

        row[NeuroFeatureSchema.R_GROUND] = actor.isOnGround() ? 1.0f : 0.0f;
        row[NeuroFeatureSchema.R_SPRINT] = actor.isSprinting() ? 1.0f : 0.0f;

        head = (head + 1) % NeuroFeatureSchema.SEQ_LEN;
        if (size < NeuroFeatureSchema.SEQ_LEN) size++;
        // Копия: слот кольца переиспользуется на следующем тике
        return row.clone();
    }


    /**
     * Выводит фичи из всего окна истории в плоский вход модели.
     * Порядок: от старого к новому, как fillFlat у тренера. Незанятые слоты
     * непрогретого окна — нули.
     */
    public void collect(float[] flat) {
        java.util.Arrays.fill(flat, 0.0f);
        int read = (head + NeuroFeatureSchema.SEQ_LEN - size) % NeuroFeatureSchema.SEQ_LEN;
        for (int i = 0; i < size; i++) {
            int slot = (read + i) % NeuroFeatureSchema.SEQ_LEN;
            computeFeatures(ring[slot], flat, i * NeuroFeatureSchema.FEATURE_COUNT);
        }
    }

    /**
     * Фичи одной RAW-строки. Публично — для selftest-сравнения с тренером.
     */
    public static void computeFeatures(float[] row, float[] dest, int offset) {
        float yaw = row[NeuroFeatureSchema.R_YAW];
        float pitch = row[NeuroFeatureSchema.R_PITCH];
        float rx = row[NeuroFeatureSchema.R_RX];
        float ry = row[NeuroFeatureSchema.R_RY];
        float rz = row[NeuroFeatureSchema.R_RZ];
        boolean has = row[NeuroFeatureSchema.R_HAS] > 0.5f;

        double hxz = Math.sqrt(rx * rx + rz * rz);
        double d3 = Math.sqrt(rx * rx + ry * ry + rz * rz);
        float desiredYaw = wrapDeg((float) (Math.toDegrees(Math.atan2(rz, rx)) - 90.0));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(ry, Math.sqrt(rx * rx + rz * rz)));

        dest[offset + NeuroFeatureSchema.F_DYAW] = row[NeuroFeatureSchema.R_DYAW];
        dest[offset + NeuroFeatureSchema.F_DPITCH] = row[NeuroFeatureSchema.R_DPITCH];
        dest[offset + NeuroFeatureSchema.F_PITCH] = pitch;

        dest[offset + NeuroFeatureSchema.F_ERR_YAW] = has ? wrapDeg(desiredYaw - yaw) : 0.0f;
        dest[offset + NeuroFeatureSchema.F_ERR_PITCH] = has ? desiredPitch - pitch : 0.0f;
        dest[offset + NeuroFeatureSchema.F_HALF_YAW] = has
                ? (float) Math.toDegrees(Math.atan2(row[NeuroFeatureSchema.R_BW] * 0.5, Math.max(hxz, 0.05)))
                : 0.0f;
        dest[offset + NeuroFeatureSchema.F_HALF_PITCH] = has
                ? (float) Math.toDegrees(Math.atan2(row[NeuroFeatureSchema.R_BH] * 0.5, Math.max(d3, 0.05)))
                : 0.0f;
        dest[offset + NeuroFeatureSchema.F_DIST] = has
                ? Math.max(row[NeuroFeatureSchema.R_DIST], 0.0f) : 0.0f;
        dest[offset + NeuroFeatureSchema.F_HP] = has
                ? MathHelper.clamp(row[NeuroFeatureSchema.R_HP] / 20.0f, 0.0f, 2.0f) : 0.0f;
        dest[offset + NeuroFeatureSchema.F_BH] = has ? row[NeuroFeatureSchema.R_BH] : 0.0f;

        dest[offset + NeuroFeatureSchema.F_HAS] = has ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.F_VIS] = row[NeuroFeatureSchema.R_VIS] > 0.5f ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.F_ON] = row[NeuroFeatureSchema.R_ON] > 0.5f ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.F_ATK] = row[NeuroFeatureSchema.R_ATK] > 0.5f ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.F_GROUND] = row[NeuroFeatureSchema.R_GROUND] > 0.5f ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.F_SPRINT] = row[NeuroFeatureSchema.R_SPRINT] > 0.5f ? 1.0f : 0.0f;
    }

    /** wrapDegrees с семантикой fmod из тренера (np.fmod → симметричный [-180, 180)). */
    public static float wrapDeg(float value) {
        float f = value % 360.0f;
        if (f >= 180.0f) f -= 360.0f;
        if (f < -180.0f) f += 360.0f;
        return f;
    }

    private static double distanceToBox(Vec3d eye, Box box) {
        double dx = MathHelper.clamp(eye.x, box.minX, box.maxX) - eye.x;
        double dy = MathHelper.clamp(eye.y, box.minY, box.maxY) - eye.y;
        double dz = MathHelper.clamp(eye.z, box.minZ, box.maxZ) - eye.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean isVisible(Vec3d eye, Vec3d point, LivingEntity actor) {
        HitResult hit = RaytraceUtil.raycast(eye, point, RaycastContext.ShapeType.COLLIDER, actor);
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }
}
