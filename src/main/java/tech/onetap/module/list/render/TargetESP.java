package tech.onetap.module.list.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.lines.VertexUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.MathUtil;
import tech.onetap.util.render.providers.ColorProvider;

import java.util.LinkedList;

@ModuleInformation(moduleName = "Target ESP", moduleDesc = "Визуальный эффект на текущей цели", moduleCategory = ModuleCategory.RENDER)
public class TargetESP extends Module {

    private static final Identifier GLOW_TEXTURE = Identifier.of("mre", "images/glow.png");

    private final ModeSetting mode = new ModeSetting("Режим", "Призраки", "Призраки", "Духи", "Маркер", "Круг", "Кристаллы", "Box");
    private final BooleanSetting redOnAuraHit = new BooleanSetting("Покраснение при ударе", true);
    private final BooleanSetting twoColorsTheme = new BooleanSetting("Два цвета", true);
    private final BooleanSetting fillBox = new BooleanSetting("Заливка", true).setVisible(() -> mode.is("Box"));
    private final BooleanSetting leaveParticles = new BooleanSetting("Оставлять партиклы", true).setVisible(() -> mode.is("Духи"));
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", false).setVisible(() -> mode.is("Celestial"));

    private final SliderSetting crystalSize = new SliderSetting("Размер кристаллов", 0.12f, 0.01f, 0.5f, 0.01f).setVisible(() -> mode.is("Кристаллы"));
    private final SliderSetting crystalSpeed = new SliderSetting("Скорость вращения", 2.5f, 0.0f, 10.0f, 0.1f).setVisible(() -> mode.is("Кристаллы"));
    private final SliderSetting crystalCount = new SliderSetting("Кол-во кристаллов", 6f, 1f, 12f, 1f).setVisible(() -> mode.is("Кристаллы"));

    private final Animation animation = new Animation(Easing.EXPO_OUT, 500);

    private Entity lastTarget = null;
    private float rotationAngle = 0.0F;
    private float rotationSpeed = 0.0F;
    private boolean isReversing = false;
    private boolean registered = false;

    private float animationNurik = 0;
    private long currentTime = System.currentTimeMillis();
    private final LinkedList<Vec3d> targetHistory = new LinkedList<>();

    private final WorldRenderEvents.Last listener = context -> {
        onRenderWorldLast(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
    };

    @Override
    public void onEnable() {
        if (!registered) {
            WorldRenderEvents.LAST.register(listener);
            registered = true;
        }
        super.onEnable();
    }

    private void onRenderWorldLast(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!isEnabled()) return;

        Entity target = Onetap.getInstance().getModuleStorage().get(KillAura.class).getTarget();

        if (target != null && target != mc.player && !(target instanceof ArmorStandEntity)) {
            if (lastTarget != target) {
                targetHistory.clear();
            }
            lastTarget = target;
            animation.run(1);
        } else {
            animation.run(0);
            if (animation.getValue() == 0) {
                lastTarget = null;
                targetHistory.clear();
            }
        }

        if (lastTarget == null || animation.getValue() <= 0.01) return;

        switch (mode.getValue()) {
            case "Маркер" -> drawMarker(matrices, camera, tickDelta);
            case "Круг" -> drawJelloMode(matrices, camera, tickDelta);
            case "Призраки" -> drawGhosts(matrices, camera, tickDelta);
            case "Духи" -> drawSpirits(matrices, camera, tickDelta);
            case "Кристаллы" -> drawCrystals(matrices, camera, tickDelta);
            case "Box" -> drawBoxMode(matrices, camera, tickDelta);
        }
    }

    private float getAuraHurtFactor(Entity entity) {
        if (redOnAuraHit.getValue() && entity instanceof LivingEntity living) {
            return MathHelper.clamp(living.hurtTime / 10.0f, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    private int mixWithHurt(int baseColor, float hurt) {
        if (hurt <= 0.0f) return baseColor;
        return interpolateColor(baseColor, 0xFFFF5555, hurt);
    }

    private int interpolateColor(int color1, int color2, float factor) {
        if (factor <= 0.0F) return color1;
        if (factor >= 1.0F) return color2;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        return (0xFF << 24) | ((int) (r1 + (r2 - r1) * factor) << 16) | ((int) (g1 + (g2 - g1) * factor) << 8) | (int) (b1 + (b2 - b1) * factor);
    }

    private int getGradient(int color1, int color2, float factor) {
        return interpolateColor(color1, color2, factor);
    }

    private double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    private void updateRotation() {
        if (!isReversing) {
            rotationSpeed += 0.01F;
            if (rotationSpeed > 2.3F) {
                rotationSpeed = 2.3F;
                isReversing = true;
            }
        } else {
            rotationSpeed -= 0.01F;
            if (rotationSpeed < -2.3F) {
                rotationSpeed = -2.3F;
                isReversing = false;
            }
        }
        rotationAngle += rotationSpeed;
        rotationAngle %= 360.0F;
    }

    private void drawMarker(MatrixStack matrices, Camera camera, float tickDelta) {
        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta);
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) + lastTarget.getHeight() / 2.0;
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta);
        Vec3d camPos = camera.getPos();

        matrices.push();
        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        float hurtFactor = getAuraHurtFactor(lastTarget);
        float baseScale = (float) (0.125f * animation.getValue());
        float scale = baseScale * (1.0f + 0.25f * hurtFactor);
        matrices.scale(-scale, -scale, scale);

        updateRotation();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationAngle));

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, Identifier.of("mre", "images/target.png"));
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        float alpha = (float) animation.getValue();
        float size = 6.5f;
        int color = mixWithHurt(ColorProvider.getThemeColor(), hurtFactor);
        color = ColorProvider.setAlpha(color, (int) (alpha * 255));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(mat, -size, size, 0.0f).texture(0.0f, 1.0f).color(color);
        buffer.vertex(mat, size, size, 0.0f).texture(1.0f, 1.0f).color(color);
        buffer.vertex(mat, size, -size, 0.0f).texture(1.0f, 0.0f).color(color);
        buffer.vertex(mat, -size, -size, 0.0f).texture(0.0f, 0.0f).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        matrices.pop();
    }

    private void drawJelloMode(MatrixStack matrices, Camera camera, float tickDelta) {
        double tPosX = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta) - camera.getPos().x;
        double tPosY = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) - camera.getPos().y;
        double tPosZ = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta) - camera.getPos().z;

        float height = lastTarget.getHeight() + 0.3f;
        double duration = 2500.0;
        double elapsed = (System.currentTimeMillis() % duration);
        boolean side = elapsed > duration / 2.0;
        double progress = elapsed / (duration / 2.0);

        if (side) {
            --progress;
        } else {
            progress = 1 - progress;
        }

        progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
        double eased = (height / 1.5) * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1);

        matrices.push();
        matrices.translate(tPosX, tPosY, tPosZ);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();

        if (mc.player.canSee(lastTarget)) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
        }

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        int color = ColorProvider.getThemeColor();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i <= 360; ++i) {
            double angle = Math.toRadians(i);
            float x = (float) (Math.cos(angle) * lastTarget.getWidth());
            float z = (float) (Math.sin(angle) * lastTarget.getWidth());
            int coreColor = ColorProvider.setAlpha(color, 1);
            buffer.vertex(matrix, x, (float) (height * progress + eased), z).color(coreColor);
            buffer.vertex(matrix, x, (float) (height * progress), z).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; ++i) {
            double angle = Math.toRadians(i);
            float x = (float) (Math.cos(angle) * lastTarget.getWidth());
            float z = (float) (Math.sin(angle) * lastTarget.getWidth());
            buffer.vertex(matrix, x, (float) (height * progress), z).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.enableCull();
        if (mc.player.canSee(lastTarget)) RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        matrices.pop();
    }

    private void drawGhosts(MatrixStack matrices, Camera camera, float tickDelta) {
        float tProgress = (float) animation.getValue();
        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta) - camera.getPos().getX();
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) - camera.getPos().getY() + (lastTarget.getHeight() / 2.0);
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta) - camera.getPos().getZ();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        float hurt = getAuraHurtFactor(lastTarget);
        int c1 = mixWithHurt(ColorProvider.getThemeColor(), hurt);
        int c2 = mixWithHurt(ColorProvider.getThemeColorTwo(), hurt);
        int finalConstantColor = c1;

        matrices.push();
        matrices.translate(x, y, z);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float time = (System.currentTimeMillis() % 2500) / 2500f * (float) Math.PI * 2f;
        float radius = lastTarget.getWidth() * 1.65f;
        int trailSegments = 76;

        float electronBaseScale = 0.25f * tProgress;

        for (int i = 0; i < 3; i++) {
            float offset = i * ((float) Math.PI / 1.5f);
            float currentOrbitTime = time * 4.0f + offset;

            for (int j = 1; j <= trailSegments; j++) {
                float trailTime = currentOrbitTime - ((j / (float) trailSegments) * 4f);
                float fade = 1.0f - (j / (float) (trailSegments + 1));

                float tx = (float) (radius * Math.cos(trailTime) * Math.cos(offset) - radius * Math.sin(trailTime) * Math.sin(offset) * 0.5f);
                float ty = (float) (radius * Math.sin(trailTime) * 0.8f);
                float tz = (float) (radius * Math.cos(trailTime) * Math.sin(offset) + radius * Math.sin(trailTime) * Math.cos(offset) * 0.5f);

                int trailAlpha = (int) (tProgress * 180f * fade * fade);
                int currentTrailColor = finalConstantColor;
                if (twoColorsTheme.getValue()) {
                    currentTrailColor = getGradient(c1, c2, (float) (Math.sin(trailTime * 1.5f + i) * 0.5f + 0.5f));
                }
                drawQuad(buffer, matrices, camera, tx, ty, tz, electronBaseScale * (0.4f + 0.6f * fade), ColorProvider.setAlpha(currentTrailColor, trailAlpha));
            }

            float ex = (float) (radius * Math.cos(currentOrbitTime) * Math.cos(offset) - radius * Math.sin(currentOrbitTime) * Math.sin(offset) * 0.5f);
            float ey = (float) (radius * Math.sin(currentOrbitTime) * 0.8f);
            float ez = (float) (radius * Math.cos(currentOrbitTime) * Math.sin(offset) + radius * Math.sin(currentOrbitTime) * Math.cos(offset) * 0.5f);

            int currentHeadColor = finalConstantColor;
            if (twoColorsTheme.getValue()) {
                currentHeadColor = getGradient(c1, c2, (float) (Math.sin(currentOrbitTime * 1.5f + i) * 0.5f + 0.5f));
            }
            drawQuad(buffer, matrices, camera, ex, ey, ez, electronBaseScale, ColorProvider.setAlpha(currentHeadColor, (int) (tProgress * 255f)));
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
    }

    private void drawSpirits(MatrixStack matrices, Camera camera, float tickDelta) {
        double currentX = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta);
        double currentY = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta);
        double currentZ = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta);

        targetHistory.addFirst(new Vec3d(currentX, currentY, currentZ));
        while (targetHistory.size() > 45) {
            targetHistory.removeLast();
        }

        int themeColor = ColorProvider.getThemeColor();
        float hurtFactor = getAuraHurtFactor(lastTarget);
        int finalColor = mixWithHurt(themeColor, hurtFactor);

        if (twoColorsTheme.getValue()) {
            int secondThemeColor = interpolateColor(themeColor, 0xFFFFFFFF, 0.5f);
            int colorSecond = mixWithHurt(secondThemeColor, hurtFactor);
            finalColor = getGradient(finalColor, colorSecond, (float) (Math.sin(System.currentTimeMillis() / 500.0) * 0.5 + 0.5));
        }

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 1, 0, 1);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Vec3d camPos = camera.getPos();
        matrices.push();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float height = lastTarget.getHeight();
        float[] heights = {0.1f, height / 2.0f, height}; // Точки: Нога, Центр, Голова

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < targetHistory.size(); j++) {
                Vec3d pos = targetHistory.get(j);

                double drawX = pos.x;
                double drawY = pos.y;
                double drawZ = pos.z;

                if (!leaveParticles.getValue()) {
                    // Если партиклы не остаются в мире, привязываем след к текущей позиции игрока
                    // Добавляем волновое смещение, чтобы след развивался при движении/стоянии
                    float waveX = MathHelper.sin((System.currentTimeMillis() % 2000) / 200f + j * 0.2f + i) * (j * 0.02f);
                    float waveZ = MathHelper.cos((System.currentTimeMillis() % 2000) / 200f + j * 0.2f + i) * (j * 0.02f);
                    drawX = currentX + waveX;
                    drawY = currentY;
                    drawZ = currentZ + waveZ;
                }

                matrices.push();
                matrices.translate(
                        drawX - camPos.x,
                        drawY - camPos.y + heights[i],
                        drawZ - camPos.z
                );

                // Физика хвоста: старые точки становятся меньше
                float progress = 1.0f - (j / (float) targetHistory.size());
                float scale = (float) (animation.getValue() * 0.3f * progress);

                // Делаем основную точку (голову следа) значительно крупнее
                if (j == 0) {
                    scale *= 0.5f;
                }

                matrices.scale(scale, scale, scale);
                matrices.multiply(camera.getRotation());

                // Затухание хвоста
                int alpha = (int) (animation.getValue() * 255F * progress);
                int alphaColor = ColorProvider.setAlpha(finalColor, alpha);
                Matrix4f m = matrices.peek().getPositionMatrix();

                float hs = 1.0f;
                buffer.vertex(m, -hs, hs, 0.0f).texture(0.0f, 1.0f).color(alphaColor);
                buffer.vertex(m, hs, hs, 0.0f).texture(1.0f, 1.0f).color(alphaColor);
                buffer.vertex(m, hs, -hs, 0.0f).texture(1.0f, 0.0f).color(alphaColor);
                buffer.vertex(m, -hs, -hs, 0.0f).texture(0.0f, 0.0f).color(alphaColor);
                matrices.pop();
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableCull();
    }

    private void drawCrystals(MatrixStack matrices, Camera camera, float tickDelta) {
        float tProgress = (float) animation.getValue();
        double targetX = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta);
        double targetY = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta);
        double targetZ = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta);
        Vec3d camPos = camera.getPos();

        float hurtFactor = getAuraHurtFactor(lastTarget);
        int themeColor = ColorProvider.getThemeColor();
        int baseColor = mixWithHurt(themeColor, hurtFactor);
        int secondColor = mixWithHurt(interpolateColor(themeColor, 0xFFFFFFFF, 0.5f), hurtFactor);

        float width = lastTarget.getWidth() * 1.6f;
        float timeOffset = (System.currentTimeMillis() % 4000) / 4000f * 360f;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        if (mc.player.canSee(lastTarget)) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();

        int numLayers = 4;
        float crystalsPerLayer = (float) crystalCount.getValue();
        float speed = (float) crystalSpeed.getValue();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int crystalIdx = 0;
        for (int layer = 0; layer < numLayers; layer++) {
            float baseHeight = lastTarget.getHeight() * ((layer + 0.5f) / numLayers);
            for (int i = 0; i < 360; i += (360 / crystalsPerLayer)) {
                float val = 1.2f - 0.5f * tProgress;
                float angle = i + (layer * 25f) + timeOffset * speed;
                float sin = (float) (Math.sin(Math.toRadians(angle)) * (width * val));
                float cos = (float) (Math.cos(Math.toRadians(angle)) * (width * val));

                float crystalAppearProgress = Math.max(0, Math.min(1, (tProgress - (crystalIdx * 0.03f)) / (1.0f - (crystalIdx * 0.03f))));
                if (crystalAppearProgress >= 0.01f) {
                    float size = (float) (crystalSize.getValue() * crystalAppearProgress);
                    float heightOffset = baseHeight + ((1.0f - crystalAppearProgress) * -0.5f);

                    matrices.push();
                    matrices.translate(targetX - camPos.x + sin, targetY - camPos.y + heightOffset, targetZ - camPos.z + cos);
                    Vector3f directionToTarget = new Vector3f((float) -sin, (float) (lastTarget.getHeight() / 2.0 - heightOffset), (float) -cos).normalize();
                    matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), directionToTarget));

                    int currentCrystalColor = baseColor;
                    if (twoColorsTheme.getValue()) {
                        currentCrystalColor = getGradient(baseColor, secondColor, (float) (Math.sin(Math.toRadians(angle) + crystalIdx) * 0.5f + 0.5f));
                    }
                    drawSolidCrystalToBuffer(buffer, matrices, size, ColorProvider.setAlpha(currentCrystalColor, (int) (255 * tProgress * crystalAppearProgress)));
                    matrices.pop();
                }
                crystalIdx++;
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        BufferBuilder glowBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        crystalIdx = 0;
        for (int layer = 0; layer < numLayers; layer++) {
            float baseHeight = lastTarget.getHeight() * ((layer + 0.5f) / numLayers);
            for (int i = 0; i < 360; i += (360 / crystalsPerLayer)) {
                float angle = i + (layer * 25f) + timeOffset * speed;
                float sin = (float) (Math.sin(Math.toRadians(angle)) * (width * (1.2f - 0.5f * tProgress)));
                float cos = (float) (Math.cos(Math.toRadians(angle)) * (width * (1.2f - 0.5f * tProgress)));

                float crystalAppearProgress = Math.max(0, Math.min(1, (tProgress - (crystalIdx * 0.03f)) / (1.0f - (crystalIdx * 0.03f))));
                if (crystalAppearProgress >= 0.01f) {
                    float heightOffset = baseHeight + ((1.0f - crystalAppearProgress) * -0.5f);

                    int currentGlowColor = baseColor;
                    if (twoColorsTheme.getValue()) {
                        currentGlowColor = getGradient(baseColor, secondColor, (float) (Math.sin(Math.toRadians(angle) + crystalIdx) * 0.5f + 0.5f));
                    }
                    float bloomAlpha = tProgress * crystalAppearProgress * (0.35f + 0.1f * (float) Math.sin(System.currentTimeMillis() / 300.0 + crystalIdx * 0.5));

                    matrices.push();
                    matrices.translate(targetX - camPos.x + sin, targetY - camPos.y + heightOffset, targetZ - camPos.z + cos);
                    matrices.multiply(camera.getRotation());
                    Matrix4f m = matrices.peek().getPositionMatrix();
                    float hs = (float) ((crystalSize.getValue() * 6.5f * crystalAppearProgress) / 2.0f);
                    int color = ColorProvider.setAlpha(currentGlowColor, (int) (255 * bloomAlpha));

                    glowBuffer.vertex(m, -hs, -hs, 0).texture(0, 0).color(color);
                    glowBuffer.vertex(m, -hs, hs, 0).texture(0, 1).color(color);
                    glowBuffer.vertex(m, hs, hs, 0).texture(1, 1).color(color);
                    glowBuffer.vertex(m, hs, -hs, 0).texture(1, 0).color(color);
                    matrices.pop();
                }
                crystalIdx++;
            }
        }
        BufferRenderer.drawWithGlobalProgram(glowBuffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void drawBoxMode(MatrixStack matrices, Camera camera, float tickDelta) {
        Box box = lastTarget.getBoundingBox().offset(
                -lastTarget.getX(),
                -lastTarget.getY(),
                -lastTarget.getZ()
        );

        double renderX = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta) - camera.getPos().x;
        double renderY = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta) - camera.getPos().y;
        double renderZ = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta) - camera.getPos().z;

        float hurt = getAuraHurtFactor(lastTarget);
        int themeColor = mixWithHurt(ColorProvider.getThemeColor(), hurt);
        int secondThemeColor = mixWithHurt(ColorProvider.getThemeColorTwo(), hurt);
        int outlineColor = twoColorsTheme.getValue()
                ? getGradient(themeColor, secondThemeColor, (float) (Math.sin(System.currentTimeMillis() / 350.0) * 0.5f + 0.5f))
                : themeColor;

        float alphaProgress = (float) animation.getValue();
        int fillColor = ColorProvider.setAlpha(outlineColor, (int) (alphaProgress * 45f));
        int lineColor = ColorProvider.setAlpha(outlineColor, (int) (alphaProgress * 210f));

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(2.0f);

        if (fillBox.getValue()) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder fillBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            drawFilledBox(fillBuffer, matrices.peek().getPositionMatrix(), box, fillColor);
            BufferRenderer.drawWithGlobalProgram(fillBuffer.end());
        }

        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        drawOutlinedBox(lineBuffer, matrices, box, lineColor);
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private void drawQuad(BufferBuilder buffer, MatrixStack ms, Camera camera, float x, float y, float z, float scale, int color) {
        ms.push();
        ms.translate(x, y, z);
        ms.scale(scale, scale, scale);
        ms.multiply(camera.getRotation());
        Matrix4f m = ms.peek().getPositionMatrix();
        buffer.vertex(m, -1f, 1f, 0.0f).texture(0.0f, 1.0f).color(color);
        buffer.vertex(m, 1f, 1f, 0.0f).texture(1.0f, 1.0f).color(color);
        buffer.vertex(m, 1f, -1f, 0.0f).texture(1.0f, 0.0f).color(color);
        buffer.vertex(m, -1f, -1f, 0.0f).texture(0.0f, 0.0f).color(color);
        ms.pop();
    }

    private void drawFilledBox(BufferBuilder buffer, Matrix4f matrix, Box box, int color) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        buffer.vertex(matrix, minX, minY, minZ).color(color);
        buffer.vertex(matrix, maxX, minY, minZ).color(color);
        buffer.vertex(matrix, maxX, maxY, minZ).color(color);
        buffer.vertex(matrix, minX, maxY, minZ).color(color);

        buffer.vertex(matrix, minX, minY, maxZ).color(color);
        buffer.vertex(matrix, minX, maxY, maxZ).color(color);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(color);
        buffer.vertex(matrix, maxX, minY, maxZ).color(color);

        buffer.vertex(matrix, minX, minY, minZ).color(color);
        buffer.vertex(matrix, minX, maxY, minZ).color(color);
        buffer.vertex(matrix, minX, maxY, maxZ).color(color);
        buffer.vertex(matrix, minX, minY, maxZ).color(color);

        buffer.vertex(matrix, maxX, minY, minZ).color(color);
        buffer.vertex(matrix, maxX, minY, maxZ).color(color);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(color);
        buffer.vertex(matrix, maxX, maxY, minZ).color(color);

        buffer.vertex(matrix, minX, maxY, minZ).color(color);
        buffer.vertex(matrix, maxX, maxY, minZ).color(color);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(color);
        buffer.vertex(matrix, minX, maxY, maxZ).color(color);

        buffer.vertex(matrix, minX, minY, minZ).color(color);
        buffer.vertex(matrix, minX, minY, maxZ).color(color);
        buffer.vertex(matrix, maxX, minY, maxZ).color(color);
        buffer.vertex(matrix, maxX, minY, minZ).color(color);
    }

    private void drawOutlinedBox(BufferBuilder buffer, MatrixStack matrices, Box box, int color) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        addLine(buffer, matrices, minX, minY, minZ, maxX, minY, minZ, color);
        addLine(buffer, matrices, maxX, minY, minZ, maxX, minY, maxZ, color);
        addLine(buffer, matrices, maxX, minY, maxZ, minX, minY, maxZ, color);
        addLine(buffer, matrices, minX, minY, maxZ, minX, minY, minZ, color);

        addLine(buffer, matrices, minX, maxY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrices, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrices, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        addLine(buffer, matrices, minX, maxY, maxZ, minX, maxY, minZ, color);

        addLine(buffer, matrices, minX, minY, minZ, minX, maxY, minZ, color);
        addLine(buffer, matrices, maxX, minY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrices, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrices, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    private void addLine(BufferBuilder buffer, MatrixStack matrices, float startX, float startY, float startZ, float endX, float endY, float endZ, int color) {
        VertexUtil.vertexLine(matrices, buffer, startX, startY, startZ, endX, endY, endZ, new java.awt.Color(color, true));
    }

    private void drawSolidCrystalToBuffer(BufferBuilder b, MatrixStack ms, float size, int color) {
        Matrix4f m = ms.peek().getPositionMatrix();
        float w = size / 2f, h = size;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bCol = color & 0xFF, a = (color >> 24) & 0xFF;
        int darkColor = (((int) (r * 0.6f)) << 16) | (((int) (g * 0.6f)) << 8) | ((int) (bCol * 0.6f)) | (a << 24);

        b.vertex(m, 0, h, 0).color(color); b.vertex(m, -w, 0, -w).color(color); b.vertex(m, w, 0, -w).color(color);
        b.vertex(m, 0, h, 0).color(darkColor); b.vertex(m, w, 0, -w).color(darkColor); b.vertex(m, w, 0, w).color(darkColor);
        b.vertex(m, 0, h, 0).color(color); b.vertex(m, w, 0, w).color(color); b.vertex(m, -w, 0, w).color(color);
        b.vertex(m, 0, h, 0).color(darkColor); b.vertex(m, -w, 0, w).color(darkColor); b.vertex(m, -w, 0, -w).color(darkColor);
        b.vertex(m, 0, -h, 0).color(darkColor); b.vertex(m, w, 0, -w).color(darkColor); b.vertex(m, -w, 0, -w).color(darkColor);
        b.vertex(m, 0, -h, 0).color(color); b.vertex(m, w, 0, w).color(color); b.vertex(m, w, 0, -w).color(color);
        b.vertex(m, 0, -h, 0).color(darkColor); b.vertex(m, -w, 0, w).color(darkColor); b.vertex(m, w, 0, w).color(darkColor);
        b.vertex(m, 0, -h, 0).color(color); b.vertex(m, -w, 0, -w).color(color); b.vertex(m, -w, 0, w).color(color);
    }
}
