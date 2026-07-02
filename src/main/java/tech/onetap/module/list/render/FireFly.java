package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.render.providers.ColorProvider;

import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@ModuleInformation(moduleName = "Fire Fly", moduleDesc = "Светлячки вокруг игрока", moduleCategory = ModuleCategory.RENDER)
public class FireFly extends Module {

    public final SliderSetting count = new SliderSetting("Количество", 20, 10, 30, 1);
    public final BooleanSetting themeColor = new BooleanSetting("Цвет от темы", true);

    private static final float SPEED = 0.35f;
    private static final float SPAWN_RADIUS = 35f;
    private static final int TRAIL_LENGTH = 70;

    private static class TrailPoint {
        double x, y, z;

        TrailPoint(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class FireFlyEntity {
        double x, y, z;
        double prevX, prevY, prevZ;
        double velX, velY, velZ;

        final int baseRandomColor;

        long spawnTime;
        final List<TrailPoint> trail = new ArrayList<>();
        final int maxTrailLength;
        double targetVelX, targetVelY, targetVelZ;
        long lastDirectionChange;
        final Random random = new Random();

        FireFlyEntity(double x, double y, double z, double velX, double velY, double velZ,
                      int baseRandomColor, int maxTrailLength) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.velX = velX;
            this.velY = velY;
            this.velZ = velZ;
            this.targetVelX = velX;
            this.targetVelY = velY;
            this.targetVelZ = velZ;
            this.baseRandomColor = baseRandomColor;
            this.spawnTime = System.currentTimeMillis();
            this.lastDirectionChange = System.currentTimeMillis();
            this.maxTrailLength = maxTrailLength;
        }

        void update(float speedMult, float maxSpeed, Vec3d playerPos) {
            prevX = x;
            prevY = y;
            prevZ = z;

            long timeSinceChange = System.currentTimeMillis() - lastDirectionChange;
            if (timeSinceChange > 2000 + random.nextInt(2000)) {
                double angle = Math.toRadians(random.nextDouble() * 360);
                double pitch = Math.toRadians((random.nextDouble() - 0.5) * 40);

                targetVelX = -Math.sin(angle) * Math.cos(pitch) * speedMult;
                targetVelY = Math.sin(pitch) * speedMult * 0.3;
                targetVelZ = Math.cos(angle) * Math.cos(pitch) * speedMult;

                lastDirectionChange = System.currentTimeMillis();
            }

            double dx = playerPos.x - x;
            double dy = playerPos.y + 1.0 - y;
            double dz = playerPos.z - z;
            double distToPlayer = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distToPlayer > SPAWN_RADIUS) {
                targetVelX += (dx / distToPlayer) * speedMult * 0.15;
                targetVelY += (dy / distToPlayer) * speedMult * 0.15;
                targetVelZ += (dz / distToPlayer) * speedMult * 0.15;
            }

            double lerpFactor = 0.02;
            velX += (targetVelX - velX) * lerpFactor;
            velY += (targetVelY - velY) * lerpFactor;
            velZ += (targetVelZ - velZ) * lerpFactor;

            double wobble = 0.03;
            velX += (random.nextDouble() - 0.5) * wobble;
            velY += (random.nextDouble() - 0.5) * wobble;
            velZ += (random.nextDouble() - 0.5) * wobble;

            velX = MathHelper.clamp(velX, -maxSpeed, maxSpeed);
            velY = MathHelper.clamp(velY, -maxSpeed, maxSpeed);
            velZ = MathHelper.clamp(velZ, -maxSpeed, maxSpeed);

            x += velX;
            y += velY;
            z += velZ;

            trail.add(0, new TrailPoint(x, y, z));

            while (trail.size() > maxTrailLength) {
                trail.remove(trail.size() - 1);
            }
        }

        boolean isDead(double px, double py, double pz) {
            double dx = x - px;
            double dy = y - py;
            double dz = z - pz;
            return dx * dx + dy * dy + dz * dz > 80 * 80;
        }

        double getInterpolatedX(float tickDelta) { return MathHelper.lerp(tickDelta, prevX, x); }
        double getInterpolatedY(float tickDelta) { return MathHelper.lerp(tickDelta, prevY, y); }
        double getInterpolatedZ(float tickDelta) { return MathHelper.lerp(tickDelta, prevZ, z); }

        int getPulseAlpha() {
            long age = System.currentTimeMillis() - spawnTime;
            double pulse = 0.8 + 0.2 * Math.sin(age / 200.0);
            return (int) (pulse * 255);
        }

        float getLifeAlpha() {
            long age = System.currentTimeMillis() - spawnTime;
            long fadeInDuration = 1000;
            if (age < fadeInDuration) return age / (float) fadeInDuration;
            return 1.0f;
        }
    }

    private final List<FireFlyEntity> particles = new ArrayList<>();
    private final Random random = new Random();
    private final WorldRenderEvents.Last listener = context -> {
        if (isEnabled()) {
            onRender3D(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
        }
    };
    private boolean registered = false;

    @Override
    public void onEnable() {
        super.onEnable();
        particles.clear();
        if (!registered) {
            WorldRenderEvents.LAST.register(listener);
            registered = true;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        particles.clear();
    }

    @Subscribe
    private void onPlayerTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        Vec3d playerPos = mc.player.getPos();
        float speedMult = SPEED;
        float maxSpeed = speedMult * 1.5f;

        particles.forEach(p -> p.update(speedMult, maxSpeed, playerPos));
        particles.removeIf(p -> p.isDead(playerPos.x, playerPos.y, playerPos.z));

        int targetCount = (int) count.getValue();

        while (particles.size() > targetCount) {
            particles.remove(particles.size() - 1);
        }

        while (particles.size() < targetCount) {
            spawnParticle(playerPos);
        }
    }

    private void spawnParticle(Vec3d playerPos) {
        double distance = random.nextDouble() * (SPAWN_RADIUS - 5) + 5;
        double yawRad = Math.toRadians(random.nextDouble() * 360);
        double xOffset = -Math.sin(yawRad) * distance;
        double zOffset = Math.cos(yawRad) * distance;
        double yOffset = (random.nextDouble() - 0.3) * 8 + 1;

        double velocitySpeed = SPEED;
        double velocityYaw = Math.toRadians(random.nextDouble() * 360);
        double velocityPitch = Math.toRadians((random.nextDouble() - 0.5) * 60);

        double velX = -Math.sin(velocityYaw) * Math.cos(velocityPitch) * velocitySpeed;
        double velY = Math.sin(velocityPitch) * velocitySpeed * 0.5;
        double velZ = Math.cos(velocityYaw) * Math.cos(velocityPitch) * velocitySpeed;

        int[] randomColors = {0xFFFFD700, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFFFF69B4, 0xFFFFA500, 0xFF00BFFF};
        int randomColor = randomColors[random.nextInt(randomColors.length)];

        particles.add(new FireFlyEntity(
                playerPos.x + xOffset,
                playerPos.y + yOffset,
                playerPos.z + zOffset,
                velX, velY, velZ,
                randomColor,
                TRAIL_LENGTH
        ));
    }

    private void onRender3D(MatrixStack stack, Camera camera, float tickDelta) {
        if (mc.player == null || mc.world == null || particles.isEmpty()) return;

        Vec3d cameraPos = camera.getPos();
        boolean useTheme = themeColor.getValue();
        int clrTheme = ColorProvider.getThemeColor();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, net.minecraft.util.Identifier.of("mre", "images/glow.png"));
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFuncSeparate(770, 1, 1, 0);
        RenderSystem.enableDepthTest();

        BufferBuilder buffer = null;

        stack.push();
        stack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f globalMatrix = stack.peek().getPositionMatrix();

        for (FireFlyEntity particle : particles) {
            float lifeAlpha = particle.getLifeAlpha();
            if (lifeAlpha <= 0.01f || particle.trail.size() < 2) continue;

            int renderColor = useTheme ? clrTheme : particle.baseRandomColor;
            double px = particle.getInterpolatedX(tickDelta);
            double py = particle.getInterpolatedY(tickDelta);
            double pz = particle.getInterpolatedZ(tickDelta);

            List<Vec3d> points = new ArrayList<>();
            points.add(new Vec3d(px, py, pz));
            for (TrailPoint p : particle.trail) points.add(new Vec3d(p.x, p.y, p.z));

            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d current = points.get(i);
                Vec3d next = points.get(i + 1);

                float t1 = (float) i / (points.size() - 1);
                float t2 = (float) (i + 1) / (points.size() - 1);

                float w1 = 0.12f * (1.0f - t1);
                float w2 = 0.12f * (1.0f - t2);

                float alpha1 = (1.0f - t1) * (1.0f - t1) * lifeAlpha * 0.6f;
                float alpha2 = (1.0f - t2) * (1.0f - t2) * lifeAlpha * 0.6f;

                if (alpha1 <= 0.01f && alpha2 <= 0.01f) continue;

                Vec3d dir = current.subtract(next);
                if (dir.lengthSquared() < 0.0001) continue;

                Vec3d camToCur = cameraPos.subtract(current);
                Vec3d cross1 = dir.crossProduct(camToCur);
                if (cross1.lengthSquared() < 0.0001) continue;
                Vec3d right1 = cross1.normalize().multiply(w1);

                Vec3d camToNext = cameraPos.subtract(next);
                Vec3d cross2 = dir.crossProduct(camToNext);
                if (cross2.lengthSquared() < 0.0001) continue;
                Vec3d right2 = cross2.normalize().multiply(w2);

                int c1 = ColorProvider.setAlpha(renderColor, (int) (alpha1 * 255));
                int c2 = ColorProvider.setAlpha(renderColor, (int) (alpha2 * 255));

                if (buffer == null) buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                buffer.vertex(globalMatrix, (float)(current.x + right1.x), (float)(current.y + right1.y), (float)(current.z + right1.z)).texture(0, t1).color(c1);
                buffer.vertex(globalMatrix, (float)(current.x - right1.x), (float)(current.y - right1.y), (float)(current.z - right1.z)).texture(1, t1).color(c1);
                buffer.vertex(globalMatrix, (float)(next.x - right2.x), (float)(next.y - right2.y), (float)(next.z - right2.z)).texture(1, t2).color(c2);
                buffer.vertex(globalMatrix, (float)(next.x + right2.x), (float)(next.y + right2.y), (float)(next.z + right2.z)).texture(0, t2).color(c2);
            }
        }
        stack.pop();

        for (FireFlyEntity particle : particles) {
            float lifeAlpha = particle.getLifeAlpha();
            if (lifeAlpha <= 0.01f) continue;

            float pulseFloat = particle.getPulseAlpha() / 255f;
            float finalAlpha = pulseFloat * lifeAlpha;

            if (finalAlpha <= 0.01f) continue;

            int renderColor = useTheme ? clrTheme : particle.baseRandomColor;

            double px = particle.getInterpolatedX(tickDelta);
            double py = particle.getInterpolatedY(tickDelta);
            double pz = particle.getInterpolatedZ(tickDelta);

            stack.push();
            stack.translate(px - cameraPos.x, py - cameraPos.y, pz - cameraPos.z);
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            Matrix4f localMatrix = stack.peek().getPositionMatrix();

            if (buffer == null) buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            drawQuad(buffer, localMatrix, 0.35f, renderColor, finalAlpha * 0.6f);
            drawQuad(buffer, localMatrix, 0.22f, renderColor, finalAlpha);
            drawQuad(buffer, localMatrix, 0.10f, 0xFFFFFFFF, finalAlpha);

            stack.pop();
        }

        if (buffer != null) {
            BuiltBuffer builtBuffer = buffer.end();
            BufferRenderer.drawWithGlobalProgram(builtBuffer);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, 0);
    }

    private void drawQuad(BufferBuilder buffer, Matrix4f matrix, float size, int color, float alphaMod) {
        if (alphaMod <= 0.01f) return;
        int finalColor = ColorProvider.setAlpha(color, (int) (alphaMod * 255));
        buffer.vertex(matrix, -size, -size, 0).texture(0, 0).color(finalColor);
        buffer.vertex(matrix, -size,  size, 0).texture(0, 1).color(finalColor);
        buffer.vertex(matrix,  size,  size, 0).texture(1, 1).color(finalColor);
        buffer.vertex(matrix,  size, -size, 0).texture(1, 0).color(finalColor);
    }
}