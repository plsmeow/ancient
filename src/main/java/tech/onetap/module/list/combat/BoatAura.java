package tech.onetap.module.list.combat;

import lombok.Getter;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.providers.ColorProvider;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "BoatAura", moduleDesc = "Перемещает лодку к цели перед ударом KillAura и возвращает назад", moduleCategory = ModuleCategory.COMBAT)
public class BoatAura extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "TP", "TP", "Steps");
    private final SliderSetting step = new SliderSetting("Шаг", 9.9, 0.5, 10.0, 0.1)
            .setVisible(() -> mode.is("Steps"));
    private final SliderSetting gap = new SliderSetting("Отступ", 0.15, 0.0, 1.0, 0.05);
    private final SliderSetting maxDistance = new SliderSetting("Макс дистанция", 200.0, 1.0, 200.0, 1.0);

    private Vec3d origin;
    private float originYaw;
    private float originPitch;
    private BoatEntity activeBoat;
    private boolean renderRegistered = false;

    @Getter
    private LivingEntity target;

    private final List<Vec3d> renderSteps = new ArrayList<>();
    private Box renderBoatBox;
    private Box boatBaseBox;

    public double getMaxDistance() {
        return maxDistance.getValue();
    }

    public Vec3d getRenderPosition(Entity entity) {
        if (mc.player == null || !(mc.player.getVehicle() instanceof BoatEntity boat)) return null;
        return getDestination(boat, entity, boat.getPos());
    }

    public boolean beforeAttack(Entity target) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;
        if (!(mc.player.getVehicle() instanceof BoatEntity boat)) return false;
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return false;

        Vec3d start = boat.getPos();
        Vec3d destination = getDestination(boat, target, start);
        if (destination == null) return false;

        origin = start;
        originYaw = boat.getYaw();
        originPitch = boat.getPitch();
        activeBoat = boat;
        this.target = living;

        renderSteps.clear();
        Box boatBox = boat.getBoundingBox();
        boatBaseBox = new Box(
                boatBox.minX - boat.getX(), boatBox.minY - boat.getY(), boatBox.minZ - boat.getZ(),
                boatBox.maxX - boat.getX(), boatBox.maxY - boat.getY(), boatBox.maxZ - boat.getZ()
        );
        renderBoatBox = boatBaseBox.offset(destination.x, destination.y, destination.z);

        move(boat, start, destination, boat.getYaw(), boat.getPitch());
        return true;
    }

    public void afterAttack() {
        if (origin == null || activeBoat == null || activeBoat.isRemoved() || mc.getNetworkHandler() == null) {
            resetMove();
            return;
        }

        move(activeBoat, activeBoat.getPos(), origin, originYaw, originPitch);
        resetMove();
    }

    private Vec3d getDestination(BoatEntity boat, Entity target, Vec3d start) {
        Box targetBox = target.getBoundingBox();
        Vec3d targetCenter = targetBox.getCenter();
        Vec3d delta = targetCenter.subtract(start);
        double distance = delta.length();
        if (distance > maxDistance.getValue() || distance <= 0.001) return null;

        Vec3d direction = delta.normalize();
        double targetRadius = Math.max(
                Math.max(targetBox.maxX - targetBox.minX, targetBox.maxZ - targetBox.minZ) * 0.5,
                (targetBox.maxY - targetBox.minY) * 0.5
        );
        Box boatBox = boat.getBoundingBox();
        double boatRadius = Math.max(
                Math.max(boatBox.maxX - boatBox.minX, boatBox.maxZ - boatBox.minZ) * 0.5,
                (boatBox.maxY - boatBox.minY) * 0.5
        );
        double clearance = targetRadius + boatRadius + gap.getValue();

        if (distance <= clearance) return null;
        return targetCenter.subtract(direction.multiply(clearance));
    }

    private void move(BoatEntity boat, Vec3d from, Vec3d to, float yaw, float pitch) {
        if (mode.is("TP")) {
            sendBoatPosition(boat, to, yaw, pitch);
            return;
        }

        Vec3d delta = to.subtract(from);
        double distance = delta.length();
        int packets = Math.max(1, (int) Math.ceil(distance / step.getValue()));

        for (int i = 1; i <= packets; i++) {
            Vec3d pos = from.lerp(to, i / (double) packets);
            sendBoatPosition(boat, pos, yaw, pitch);
            renderSteps.add(pos);
            from = pos;
        }
    }

    private void sendBoatPosition(BoatEntity boat, Vec3d pos, float yaw, float pitch) {
        boat.setPosition(pos.x, pos.y, pos.z);
        boat.setYaw(yaw);
        boat.setPitch(pitch);
        mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(pos, yaw, pitch, false));
    }

    private void resetMove() {
        origin = null;
        activeBoat = null;
        originYaw = 0.0f;
        originPitch = 0.0f;
    }

    private void onRender(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!isEnabled() || target == null) return;
        if (renderSteps.isEmpty() && renderBoatBox == null) return;

        Vec3d camPos = camera.getPos();
        int color = ColorProvider.getThemeColor();
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(2.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        if (!renderSteps.isEmpty() && boatBaseBox != null) {
            for (Vec3d s : renderSteps) {
                double sx = s.x - camPos.x;
                double sy = s.y - camPos.y;
                double sz = s.z - camPos.z;
                drawLineBox(buffer, matrix,
                        sx + boatBaseBox.minX, sy + boatBaseBox.minY, sz + boatBaseBox.minZ,
                        sx + boatBaseBox.maxX, sy + boatBaseBox.maxY, sz + boatBaseBox.maxZ,
                        r, g, b, 0.35f);
            }
        }

        if (renderBoatBox != null) {
            drawLineBox(buffer, matrix,
                    renderBoatBox.minX - camPos.x, renderBoatBox.minY - camPos.y, renderBoatBox.minZ - camPos.z,
                    renderBoatBox.maxX - camPos.x, renderBoatBox.maxY - camPos.y, renderBoatBox.maxZ - camPos.z,
                    r, g, b, 0.8f);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(1.0f);
    }

    private void drawLineBox(BufferBuilder buffer, Matrix4f matrix, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float r, float g, float b, float a) {
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (!renderRegistered) {
            WorldRenderEvents.LAST.register((context) -> onRender(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true)));
            renderRegistered = true;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetMove();
        renderSteps.clear();
        renderBoatBox = null;
        boatBaseBox = null;
        target = null;
    }
}
