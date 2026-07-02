package tech.onetap.module.list.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.render.providers.ColorProvider;

import java.awt.Color;
import java.util.*;

@ModuleInformation(moduleName = "Trails", moduleDesc = "Оставляет след за сущностями", moduleCategory = ModuleCategory.RENDER)
public class Trails extends Module {

    private final SliderSetting duration = new SliderSetting("Длительность", 1000.0f, 500.0f, 2000.0f, 100.0f);
    private final SliderSetting lineWidth = new SliderSetting("Толщина", 2.0f, 0.5f, 5.0f, 0.1f);
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 100.0f, 0.0f, 255.0f, 5.0f);
    private final BooleanSetting firstPerson = new BooleanSetting("От первого лица", false);

    private final BooleanSetting targetSelf = new BooleanSetting("Себе", true);
    private final BooleanSetting targetFriends = new BooleanSetting("На друзьях", true);
    private final BooleanSetting targetPlayers = new BooleanSetting("На игроках", true);
    private final BooleanSetting targetMobs = new BooleanSetting("На мобах", false);

    private final Map<Integer, List<Point>> entityPoints = new HashMap<>();
    private final Map<Integer, Vec3d> lastPositions = new HashMap<>();
    private final Map<Integer, Long> lastPointTimes = new HashMap<>();
    private final Map<Integer, EntityType> entityTypes = new HashMap<>();

    private enum EntityType {
        SELF, FRIEND, PLAYER, MOB
    }

    public Trails() {
        super();
        WorldRenderEvents.LAST.register(this::onRender);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        clearData();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        clearData();
    }

    private void clearData() {
        entityPoints.clear();
        lastPositions.clear();
        lastPointTimes.clear();
        entityTypes.clear();
    }

    private void onRender(WorldRenderContext context) {
        if (!this.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) {
            if (!entityPoints.isEmpty()) clearData();
            return;
        }

        float tickDelta = context.tickCounter().getTickDelta(true);
        boolean selfVisible = firstPerson.getValue() || !mc.options.getPerspective().isFirstPerson();
        long currentTime = System.currentTimeMillis();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || !entity.isAlive() || !(entity instanceof LivingEntity)) {
                continue;
            }

            boolean isTarget = false;
            EntityType entityType = null;

            if (entity == mc.player) {
                isTarget = targetSelf.getValue() && selfVisible;
                entityType = EntityType.SELF;
            } else if (entity instanceof PlayerEntity player) {
                boolean isFriend = FriendRepository.isFriend(player.getNameForScoreboard());

                if (isFriend) {
                    isTarget = targetFriends.getValue();
                    entityType = EntityType.FRIEND;
                } else {
                    isTarget = targetPlayers.getValue();
                    entityType = EntityType.PLAYER;
                }
            } else if (entity instanceof MobEntity) {
                isTarget = targetMobs.getValue();
                entityType = EntityType.MOB;
            }

            if (!isTarget) {
                removeEntityData(entity.getId());
                continue;
            }

            entityTypes.put(entity.getId(), entityType);

            Vec3d currentPos = entity.getLerpedPos(tickDelta);
            long lastTime = lastPointTimes.getOrDefault(entity.getId(), 0L);
            boolean shouldAddPoint = currentTime - lastTime >= 15;

            if (shouldAddPoint) {
                Vec3d lastPos = lastPositions.get(entity.getId());
                if (lastPos != null) {
                    double distanceMoved = lastPos.distanceTo(currentPos);
                    shouldAddPoint = distanceMoved > 0.01 || !entityPoints.containsKey(entity.getId()) || entityPoints.get(entity.getId()).isEmpty();
                }

                if (shouldAddPoint) {
                    List<Point> points = entityPoints.computeIfAbsent(entity.getId(), k -> new ArrayList<>());
                    points.add(0, new Point(currentPos, currentTime));
                    lastPointTimes.put(entity.getId(), currentTime);
                    lastPositions.put(entity.getId(), currentPos);
                }
            }
        }

        Iterator<Map.Entry<Integer, List<Point>>> iterator = entityPoints.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, List<Point>> entry = iterator.next();
            Entity entity = mc.world.getEntityById(entry.getKey());

            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                removeEntityData(entry.getKey());
                continue;
            }

            List<Point> points = entry.getValue();
            points.removeIf(point -> currentTime - point.timestamp > (float) duration.getValue());

            if (points.isEmpty()) {
                iterator.remove();
                removeEntityData(entry.getKey());
            }
        }

        for (Map.Entry<Integer, List<Point>> entry : entityPoints.entrySet()) {
            List<Point> points = entry.getValue();
            if (points.size() < 2) continue;

            Entity entity = mc.world.getEntityById(entry.getKey());
            if (entity != null && entity.isAlive()) {
                EntityType entityType = entityTypes.get(entry.getKey());
                renderTrail(points, context, currentTime, entity.getHeight(), entityType);
            }
        }
    }

    private void renderTrail(List<Point> points, WorldRenderContext context, long currentTime, float entityHeight, EntityType entityType) {
        MatrixStack matrixStack = context.matrixStack();
        Vec3d camPos = context.camera().getPos();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();

        RenderSystem.enableDepthTest();

        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();

        matrixStack.push();
        matrixStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        int index = 0;
        for (Point point : points) {
            float lifeRatio = Math.min(1.0f, (float) ((currentTime - point.timestamp) / duration.getValue()));
            float alpha = (float) ((opacity.getValue() / 255.0f) * (1.0f - easeOutQuad(lifeRatio)));

            int color = getColorForEntity(entityType, index);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            buffer.vertex(matrix, (float) point.pos.x, (float) point.pos.y, (float) point.pos.z).color(r, g, b, alpha);
            buffer.vertex(matrix, (float) point.pos.x, (float) (point.pos.y + entityHeight - 0.1D), (float) point.pos.z).color(r, g, b, alpha);
            index++;
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth((float) lineWidth.getValue());

        BufferBuilder bufferLineBottom = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        index = 0;
        for (Point point : points) {
            float lifeRatio = Math.min(1.0f, (float) ((currentTime - point.timestamp) / duration.getValue()));
            float alpha = (float) ((opacity.getValue() / 255.0f) * (1.0f - easeOutQuad(lifeRatio)));

            int color = getColorForEntity(entityType, index);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            bufferLineBottom.vertex(matrix, (float) point.pos.x, (float) point.pos.y, (float) point.pos.z).color(r, g, b, alpha);
            index++;
        }
        BufferRenderer.drawWithGlobalProgram(bufferLineBottom.end());

        BufferBuilder bufferLineTop = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        index = 0;
        for (Point point : points) {
            float lifeRatio = Math.min(1.0f, (float) ((currentTime - point.timestamp) / duration.getValue()));
            float alpha = (float) ((opacity.getValue() / 255.0f) * (1.0f - easeOutQuad(lifeRatio)));

            int color = getColorForEntity(entityType, index);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            bufferLineTop.vertex(matrix, (float) point.pos.x, (float) (point.pos.y + entityHeight - 0.1D), (float) point.pos.z).color(r, g, b, alpha);
            index++;
        }
        BufferRenderer.drawWithGlobalProgram(bufferLineTop.end());

        matrixStack.pop();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private int getColorForEntity(EntityType entityType, int index) {
        int themeColor = ColorProvider.getThemeColor();

        if (entityType == null) return themeColor;

        switch (entityType) {
            case PLAYER:
                return new Color(255, 85, 85).getRGB(); // Враги - красные
            case MOB:
                return new Color(255, 170, 0).getRGB(); // Мобы - оранжевые
            case FRIEND:
            case SELF:
            default:
                return themeColor; // Друзья и ты - цвет темы (Theme color)
        }
    }

    private float easeOutQuad(float x) {
        return 1 - (1 - x) * (1 - x);
    }

    private void removeEntityData(int entityId) {
        lastPositions.remove(entityId);
        lastPointTimes.remove(entityId);
        entityTypes.remove(entityId);
    }

    public static class Point {
        public final Vec3d pos;
        public final long timestamp;

        public Point(Vec3d pos, long timestamp) {
            this.pos = pos;
            this.timestamp = timestamp;
        }
    }
}