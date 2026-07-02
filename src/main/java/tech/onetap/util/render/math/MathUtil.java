package tech.onetap.util.render.math;

import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.IMinecraft;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MathUtil implements IMinecraft {
    public double PI2 = Math.PI * 2;
    private static final int TABLE_SIZE = 1 << 16; // 65536
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double[] TRIG_TABLE = new double[TABLE_SIZE];

    static {
        for (int i = 0; i < TABLE_SIZE; i++) {
            TRIG_TABLE[i] = Math.sin((double) i * TWO_PI / TABLE_SIZE);
        }
    }

    public static double sin(double radians) {
        int index = (int) (radians * (TABLE_SIZE / TWO_PI)) & (TABLE_SIZE - 1);
        return TRIG_TABLE[index];
    }

    public static double cos(double radians) {
        int index = (int) (radians * (TABLE_SIZE / TWO_PI) + TABLE_SIZE / 4.0) & (TABLE_SIZE - 1);
        return TRIG_TABLE[index];
    }

    public static double random(double min, double max) {
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    public static double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    public static double calc(double value) {
        Window rs = mc.getWindow();
        return value * rs.getScaleFactor() / 2;
    }

    public static double getBps(Entity player) {
        double dx = player.getX() - player.prevX;
        double dy = player.getY() - player.prevY;
        double dz = player.getZ() - player.prevZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return 20.0F * distance;
    }

    public static float random(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }

    public static double getDistanceSq(double x, double y, double z) {
        double d0 = mc.player.getX() - x;
        double d1 = mc.player.getY() - y;
        double d2 = mc.player.getZ() - z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public static double getDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d0 = (x1 - x2);
        double d1 = (y1 - y2);
        double d2 = (z1 - z2);
        return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    public static double getSqrDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d0 = (x1 - x2);
        double d1 = (y1 - y2);
        double d2 = (z1 - z2);
        return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    public static float round(float value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    public static double getDistanceSq(Entity ent) {
        return getDistanceSq(ent.getX(), ent.getY(), ent.getZ());
    }

    public static double roundToDecimal(double n, int point) {
        if (point == 0) {
            return Math.floor(n);
        }
        double factor = Math.pow(10, point);
        return Math.round(n * factor) / factor;
    }

    public static double angle(Vec3d vec3d, Vec3d other) {
        double lengthSq = vec3d.length() * other.length();

        if (lengthSq < 1.0E-4D) {
            return 0.0;
        }

        double dot = vec3d.dotProduct(other);
        double arg = dot / lengthSq;

        if (arg > 1) {
            return 0.0;
        } else if (arg < -1) {
            return 180.0;
        }

        return Math.acos(arg) * 180.0f / Math.PI;
    }

    public static Vec3d fromTo(Vec3d from, double x, double y, double z) {
        return fromTo(from.x, from.y, from.z, x, y, z);
    }

    public static float lerp(float f, float st, float en) {
        return st + f * (en - st);
    }

    public static Vec3d fromTo(double x, double y, double z, double x2, double y2, double z2) {
        return new Vec3d(x2 - x, y2 - y, z2 - z);
    }

    public static float rad(float angle) {
        return (float) (angle * Math.PI / 180);
    }

    public static int clamp(int num, int min, int max) {
        return num < min ? min : Math.min(num, max);
    }

    public static float clamp(float num, float min, float max) {
        return num < min ? min : Math.min(num, max);
    }

    public static double clamp(double num, double min, double max) {
        return num < min ? min : Math.min(num, max);
    }

    public static float sin(float value) {
        return MathHelper.sin(value);
    }

    public static float cos(float value) {
        return MathHelper.cos(value);
    }

    public static float wrapDegrees(float value) {
        return MathHelper.wrapDegrees(value);
    }

    public static double wrapDegrees(double value) {
        return MathHelper.wrapDegrees(value);
    }

    public static double square(double input) {
        return input * input;
    }

    public static double round(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static float wrap(float angle) {
        float wrappedAngle = angle % 360.0f;
        if (wrappedAngle >= 180.0f) {
            wrappedAngle -= 360.0f;
        }
        if (wrappedAngle < -180.0f) {
            wrappedAngle += 360.0f;
        }
        return wrappedAngle;
    }

    public static Vec3d direction(float yaw) {
        return new Vec3d(Math.cos(MathUtil.degToRad(yaw + 90.0f)), 0.0, Math.sin(MathUtil.degToRad(yaw + 90.0f)));
    }

    public static float round(float value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.FLOOR);
        return bd.floatValue();
    }

    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }


    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValue(Map<K, V> map, boolean descending) {
        List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());

        if (descending) {
            list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        } else {
            list.sort(Map.Entry.comparingByValue());
        }

        LinkedHashMap<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public static double degToRad(double deg) {
        return deg * 0.01745329238474369;
    }
}