package tech.onetap.util.math;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.MathUtil;
import tech.onetap.util.rotation.Rotation;

@UtilityClass
public class BestPoint implements IMinecraft {
    private static Vec3d rotationPoint = Vec3d.ZERO;
    private static Vec3d rotationMotion = Vec3d.ZERO;

    public Vec3d getRotationPoint() {
        return rotationPoint;
    }

    public Vec3d getNearestPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double step = 0.1;
        Vec3d bestVec = null;
        double closestDistance = Double.MAX_VALUE;

        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    double dist = mc.player.getEyePos().distanceTo(sample);
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        bestVec = sample;
                    }
                }
            }
        }
        return bestVec;
    }
    public Vec3d getPoint(Entity target) {
        Box box = target.getBoundingBox();

        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;

        double baseX = box.minX + width / 2.0;
        double baseY = box.minY + height * 0.7;
        double baseZ = box.minZ + depth / 2.0;

        double time = System.currentTimeMillis() / 50.0;

        int id = target.getId();


        double offsetX = Math.sin(time + id) * (width * 0.45);

        double offsetY = Math.cos(time * 0.8 + id) * (height * 0.1);

        double offsetZ = Math.cos(time * 1.2 + id) * (depth * 0.45);

        return new Vec3d(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
    }
    public Vec3d getPoint2(Entity target) {
        Box box = target.getBoundingBox();

        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;

        double baseX = box.minX + width / 2.0;
        double baseY = box.minY + height * 0.65;
        double baseZ = box.minZ + depth / 2.0;

        double time = System.currentTimeMillis() / 65.0;

        int id = target.getId();


        double offsetX = Math.sin(time + id) * (width * 0.7);

        double offsetY = Math.cos(time * 0.8 + id) * (height * 0.4);

        double offsetZ = Math.cos(time * 1.2 + id) * (depth * 0.7);

        return new Vec3d(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
    }

    public Vec3d getNearestVisiblePoint(Entity target, Vec3d preferredPoint, double range) {
        if (preferredPoint == null || mc.player == null || mc.world == null) {
            return preferredPoint;
        }

        if (isPointVisible(target, preferredPoint, range)) {
            return preferredPoint;
        }

        Box box = target.getBoundingBox();
        double step = 0.12;
        Vec3d bestPoint = null;
        double bestDistance = Double.MAX_VALUE;

        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    if (!isPointVisible(target, sample, range)) {
                        continue;
                    }

                    double distanceToCurrent = sample.squaredDistanceTo(preferredPoint);
                    if (distanceToCurrent < bestDistance) {
                        bestDistance = distanceToCurrent;
                        bestPoint = sample;
                    }
                }
            }
        }

        return bestPoint != null ? bestPoint : preferredPoint;
    }

    private boolean isPointVisible(Entity target, Vec3d point, double range) {
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(point);
        if (distance > range) {
            return false;
        }

        Vec3d direction = point.subtract(eyePos).normalize();
        if (!RaytraceUtil.rayTrace(direction, distance + 0.2, target.getBoundingBox())) {
            return false;
        }

        var blockHit = RaytraceUtil.raycast(eyePos, point, RaycastContext.ShapeType.COLLIDER, mc.player);
        return blockHit.getType() == HitResult.Type.MISS || eyePos.squaredDistanceTo(blockHit.getPos()) >= eyePos.squaredDistanceTo(point) - 1e-4;
    }

    public static Vec3d getMultipoint(Entity target, double distance) {
        float minMotionXZ = 0.005f;
        float maxMotionXZ = 0.015f;

        float minMotionY = 0.0015f;
        float maxMotionY = 0.015f;

        double lenghtX = target.getBoundingBox().getLengthX();
        double lenghtY = target.getBoundingBox().getLengthY();
        double lenghtZ = target.getBoundingBox().getLengthZ();

        if (rotationMotion.equals(Vec3d.ZERO))
            rotationMotion = new Vec3d(MathUtil.random(-0.02f, 0.02f), MathUtil.random(-0.02f, 0.02f), MathUtil.random(-0.02f, 0.02f));

        if (rotationPoint.equals(Vec3d.ZERO))
            rotationPoint = new Vec3d(0, lenghtY * 0.5, 0);

        rotationPoint = rotationPoint.add(rotationMotion);

        double safeX = (lenghtX - 0.1) / 2f;
        double safeZ = (lenghtZ - 0.1) / 2f;

        if (rotationPoint.x >= safeX)
            rotationMotion = new Vec3d(-MathUtil.random(minMotionXZ, maxMotionXZ), rotationMotion.getY(), rotationMotion.getZ());
        else if (rotationPoint.x <= -safeX)
            rotationMotion = new Vec3d(MathUtil.random(minMotionXZ, maxMotionXZ), rotationMotion.getY(), rotationMotion.getZ());

        if (rotationPoint.y >= lenghtY * 0.75)
            rotationMotion = new Vec3d(rotationMotion.getX(), -MathUtil.random(minMotionY, maxMotionY), rotationMotion.getZ());
        else if (rotationPoint.y <= lenghtY * 0.3)
            rotationMotion = new Vec3d(rotationMotion.getX(), MathUtil.random(minMotionY, maxMotionY), rotationMotion.getZ());

        if (rotationPoint.z >= safeZ)
            rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), -MathUtil.random(minMotionXZ, maxMotionXZ));
        else if (rotationPoint.z <= -safeZ)
            rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), MathUtil.random(minMotionXZ, maxMotionXZ));

        Rotation rotation;

        if (!RaytraceUtil.rayTrace(mc.player.getRotationVector(), distance, target.getBoundingBox())) {
            float halfBox = (float) (lenghtX / 2f) * 0.8f;

            for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.1f) {
                for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.1f) {
                    for (float y1 = (float) (lenghtY * 0.9); y1 >= lenghtY * 0.3; y1 -= 0.1f) {

                        Vec3d v1 = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);

                        rotation = RotationUtil.fromVec3d(v1);
                        if (RaytraceUtil.rayTrace(rotation.toVector(), distance, target.getBoundingBox())) {
                            rotationPoint = new Vec3d(x1, y1, z1);
                            return target.getPos().add(rotationPoint);
                        }
                    }
                }
            }

        }
        return target.getPos().add(rotationPoint);
    }
}
