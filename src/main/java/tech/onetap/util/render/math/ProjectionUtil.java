package tech.onetap.util.render.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ProjectionUtil {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static Vector2f project(double x, double y, double z) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3d cameraPos = camera.getPos();

        Quaternionf cameraRotation = mc.getEntityRenderDispatcher().getRotation();
        cameraRotation = cameraRotation.conjugate(new Quaternionf());

        Vector3f result3f = new Vector3f(
                (float) (x - cameraPos.x),
                (float) (y - cameraPos.y),
                (float) (z - cameraPos.z)
        );

        result3f.rotate(cameraRotation);

        if (mc.options.getBobView().getValue()) {
            if (mc.getCameraEntity() instanceof AbstractClientPlayerEntity playerentity) {
                calculateViewBobbing(playerentity, result3f);
            }
        }

        double fov = mc.gameRenderer.getFov(camera, mc.getRenderTickCounter().getTickDelta(true), true);
        return calculateScreenPosition(result3f, fov);
    }

    public static Vector2f project(Vec3d vec3d) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3d cameraPos = camera.getPos();

        Quaternionf cameraRotation = mc.getEntityRenderDispatcher().getRotation();
        cameraRotation = cameraRotation.conjugate(new Quaternionf());

        Vector3f result3f = new Vector3f(
                (float) (vec3d.x - cameraPos.x),
                (float) (vec3d.y - cameraPos.y),
                (float) (vec3d.z - cameraPos.z)
        );

        result3f.rotate(cameraRotation);

        if (mc.options.getBobView().getValue()) {
            if (mc.getCameraEntity() instanceof AbstractClientPlayerEntity playerentity) {
                calculateViewBobbing(playerentity, result3f);
            }
        }

        double fov = mc.gameRenderer.getFov(camera, mc.getRenderTickCounter().getTickDelta(true), true);
        return calculateScreenPosition(result3f, fov);
    }

    private static void calculateViewBobbing(AbstractClientPlayerEntity playerEntity, Vector3f result3f) {
        float f = playerEntity.distanceMoved - playerEntity.lastDistanceMoved;
        float g = -(playerEntity.distanceMoved + f * mc.getRenderTickCounter().getTickDelta(true));
        float h = MathHelper.lerp(mc.getRenderTickCounter().getTickDelta(true), playerEntity.prevStrideDistance, playerEntity.strideDistance);

        float angle = Math.abs(MathHelper.cos(g * 3.1415927F - 0.2F) * h) * 5.0F;
        angle *= ((float) Math.PI / 180F);

        Quaternionf quaternion = new Quaternionf().setAngleAxis(angle, 1.0F, 0.0F, 0.0F);
        quaternion.conjugate();
        result3f.rotate(quaternion);

        float angle1 = MathHelper.sin(g * 3.1415927F) * h * 3.0F;
        angle1 *= ((float) Math.PI / 180F);

        Quaternionf quaternion1 = new Quaternionf().setAngleAxis(angle1, 0.0F, 0.0F, 1.0F);
        quaternion1.conjugate();
        result3f.rotate(quaternion1);

        Vector3f bobTranslation = new Vector3f(MathHelper.sin(g * 3.1415927F) * h * 0.5F, 0, 0.0f);
        bobTranslation.y = -bobTranslation.y;
        result3f.add(bobTranslation);
    }

    private static Vector2f calculateScreenPosition(Vector3f result3f, double fov) {
        Window window = mc.getWindow();
        float width = window.getScaledWidth() / 2.0F;
        float height = window.getScaledHeight() / 2.0F;
        float x = result3f.x;
        float y = result3f.y;
        float z = result3f.z;

        float scaleFactor = height / (z * (float) Math.tan(Math.toRadians(fov / 2.0F)));
        if (z < 0.0F) {
            float screenX = -x * scaleFactor + width;
            float screenY = y * scaleFactor + height;
            return new Vector2f(screenX, screenY);
        }
        return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
    }
}