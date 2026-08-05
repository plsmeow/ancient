package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.block.AStarPathFinder;
import tech.onetap.util.render.providers.ColorProvider;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "TpAura", moduleDesc = "Телепортирует игрока к цели перед ударом KillAura и возвращает обратно", moduleCategory = ModuleCategory.COMBAT)
public class TpAura extends Module {
    private final ModeSetting tpMode = new ModeSetting("TP Mode", "Simple", "Simple", "Bypass", "Vault");
    private final ModeSetting simpleMode = new ModeSetting("Режим", "TP", "TP", "Steps")
            .setVisible(() -> tpMode.is("Simple"));
    private final SliderSetting maxDistance = new SliderSetting("Макс дистанция", 200.0, 1.0, 200.0, 1.0);
    private final ModeSetting bypassStepsMode = new ModeSetting("Режим Bypass", "TP", "TP", "Steps")
            .setVisible(() -> tpMode.is("Bypass"));
    private final SliderSetting stepSize = new SliderSetting("Шаг", 9.9, 0.5, 10.0, 0.1)
            .setVisible(() -> (tpMode.is("Simple") && simpleMode.is("Steps"))
                    || (tpMode.is("Bypass") && bypassStepsMode.is("Steps")));
    private final BooleanSetting pathfinder = new BooleanSetting("Pathfinder", false)
            .setVisible(() -> tpMode.is("Bypass") && bypassStepsMode.is("Steps"));
    private final SliderSetting packets = new SliderSetting("Packets", 10, 1, 50, 1)
            .setVisible(() -> tpMode.is("Bypass"));
    private final BooleanSetting bypassOnGround = new BooleanSetting("Bypass OnGround", true)
            .setVisible(() -> tpMode.is("Bypass"));
    private final SliderSetting vaultDelay = new SliderSetting("Задержка", 0, 0, 2000, 50)
            .setVisible(() -> tpMode.is("Vault"));

    private Vec3d origin;
    private float originYaw;
    private float originPitch;
    private boolean renderRegistered = false;

    private boolean pendingVaultReturn = false;
    private long vaultReturnAt = 0L;
    private Vec3d vaultReturnFrom;
    private Vec3d vaultReturnTo;

    @Getter
    private LivingEntity target;

    private final List<Vec3d> renderSteps = new ArrayList<>();
    private Vec3d renderDestination;
    private Box renderHitbox;

    public double getMaxDistance() {
        return maxDistance.getValue();
    }

    public boolean isPendingVaultReturn() {
        return pendingVaultReturn;
    }

    public Vec3d getRenderPosition(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return null;
        if (mc.player == null || mc.player.hasVehicle()) return null;
        if (mc.player.distanceTo(living) > maxDistance.getValue()) return null;
        return living.getPos();
    }

    public boolean beforeAttack(Entity entity) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (mc.player.hasVehicle()) return false;
        if (mc.player.distanceTo(living) > maxDistance.getValue()) return false;

        origin = mc.player.getPos();
        originYaw = mc.player.getYaw();
        originPitch = mc.player.getPitch();
        target = living;

        Vec3d destination = living.getPos();

        renderSteps.clear();
        renderDestination = destination;
        float halfWidth = living.getWidth() / 2f;
        renderHitbox = new Box(
                destination.x - halfWidth, destination.y, destination.z - halfWidth,
                destination.x + halfWidth, destination.y + living.getHeight(), destination.z + halfWidth
        );

        if (tpMode.is("Bypass")) {
            teleportBypass(destination, true);
        } else if (tpMode.is("Vault")) {
            teleportVault(origin, destination);
        } else {
            teleportSimple(origin, destination, mc.player.getYaw(), mc.player.getPitch(), true);
        }

        return true;
    }

    public void afterAttack() {
        if (origin == null || mc.getNetworkHandler() == null) {
            resetState(false);
            return;
        }

        if (tpMode.is("Bypass")) {
            teleportBypass(origin, false);
        } else if (tpMode.is("Vault")) {
            if (vaultDelay.getIntValue() > 0) {
                // Откладываем возврат на заданную задержку (мс)
                vaultReturnFrom = renderDestination;
                vaultReturnTo = origin;
                vaultReturnAt = System.currentTimeMillis() + vaultDelay.getIntValue();
                pendingVaultReturn = true;
                resetState(true);
                return;
            }
            teleportVault(renderDestination, origin);
        } else {
            teleportSimple(mc.player.getPos(), origin, originYaw, originPitch, false);
        }

        resetState(true);
    }

    @Subscribe
    private void onTick(EventTick event) {
        if (!pendingVaultReturn) return;
        if (mc.player == null || mc.getNetworkHandler() == null) {
            cancelVaultReturn();
            return;
        }
        if (System.currentTimeMillis() >= vaultReturnAt) {
            teleportVault(vaultReturnFrom, vaultReturnTo);
            cancelVaultReturn();
        }
    }

    private void cancelVaultReturn() {
        pendingVaultReturn = false;
        vaultReturnFrom = null;
        vaultReturnTo = null;
    }

    private void teleportSimple(Vec3d from, Vec3d destination, float yaw, float pitch, boolean toTarget) {
        if (simpleMode.is("Steps")) {
            double distance = from.distanceTo(destination);
            int packetCount = Math.max(1, (int) Math.ceil(distance / stepSize.getValue()));
            for (int i = 1; i <= packetCount; i++) {
                Vec3d pos = from.lerp(destination, i / (double) packetCount);
                sendPlayerPosition(pos, yaw, pitch);
                if (toTarget) renderSteps.add(pos);
            }
        } else {
            sendPlayerPosition(destination, yaw, pitch);
            if (toTarget) renderSteps.add(destination);
        }
    }

    private void teleportBypass(Vec3d pos, boolean toTarget) {
        if (bypassStepsMode.is("Steps")) {
            Vec3d from = mc.player.getPos();
            if (pathfinder.getValue()) {
                List<Vec3d> path = AStarPathFinder.findPath(from, pos);
                if (!path.isEmpty()) {
                    // Схлопываем путь в максимально длинные прямые отрезки:
                    // ломаем путь только там, где прямая невозможна.
                    // "Шаг" — лимит на один телепорт внутри прямого отрезка.
                    List<Vec3d> waypoints = AStarPathFinder.smoothPath(from, path, pos);
                    Vec3d current = from;
                    for (Vec3d node : waypoints) {
                        sendBypassStepsAlong(current, node, toTarget);
                        current = node;
                    }
                    return;
                }
                // Путь не найден: напрямую идём только если прямая свободна,
                // сквозь стены не телепортируемся
                if (AStarPathFinder.hasLineOfSight(from, pos)) {
                    sendBypassStepsAlong(from, pos, toTarget);
                }
                return;
            }
            sendBypassStepsAlong(from, pos, toTarget);
        } else {
            sendBypassPackets(pos);
            if (toTarget) renderSteps.add(pos);
        }
    }

    private void sendBypassStepsAlong(Vec3d from, Vec3d to, boolean toTarget) {
        double distance = from.distanceTo(to);
        if (distance < 1.0E-4) return;
        int stepCount = Math.max(1, (int) Math.ceil(distance / stepSize.getValue()));
        for (int i = 1; i <= stepCount; i++) {
            Vec3d step = from.lerp(to, i / (double) stepCount);
            sendBypassPackets(step);
            if (toTarget) renderSteps.add(step);
        }
    }

    private void sendBypassPackets(Vec3d pos) {
        mc.player.setPosition(pos.x, pos.y, pos.z);
        for (int i = 0; i < packets.getIntValue(); i++) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    pos.x, pos.y, pos.z,
                    bypassOnGround.getValue(),
                    mc.player.horizontalCollision
            ));
        }
    }

    private void teleportVault(Vec3d from, Vec3d destination) {
        Vec3d upPos = from.add(0, 129.0, 0);
        Vec3d aboveTarget = new Vec3d(destination.x, upPos.y, destination.z);
        Vec3d downPos = new Vec3d(destination.x, destination.y, destination.z);
        Vec3d finalPos = downPos.add(0, 0.01, 0);

        for (int i = 0; i < 13; i++) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
        }

        sendVaultMove(upPos);
        sendVaultMove(aboveTarget);
        sendVaultMove(downPos);
        sendVaultMove(finalPos);

        // Физически перемещаем клиентскую позицию на финальную точку
        mc.player.setPosition(finalPos.x, finalPos.y, finalPos.z);
    }

    private void sendVaultMove(Vec3d pos) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                pos.x, pos.y, pos.z, false, mc.player.horizontalCollision
        ));
    }

    private void sendPlayerPosition(Vec3d pos, float yaw, float pitch) {
        mc.player.setPosition(pos.x, pos.y, pos.z);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                pos.x, pos.y, pos.z, yaw, pitch, false, mc.player.horizontalCollision
        ));
    }

    private void resetState(boolean keepTarget) {
        origin = null;
        originYaw = 0.0f;
        originPitch = 0.0f;
        if (!keepTarget) {
            target = null;
            renderSteps.clear();
            renderDestination = null;
            renderHitbox = null;
        }
    }

    private void onRender(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!isEnabled() || target == null) return;
        if (renderHitbox == null && renderDestination == null) return;

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

        if (!renderSteps.isEmpty() && mc.player != null) {
            float hw = mc.player.getWidth() / 2f;
            float h = mc.player.getHeight();
            for (Vec3d step : renderSteps) {
                double sx = step.x - camPos.x;
                double sy = step.y - camPos.y;
                double sz = step.z - camPos.z;
                drawLineBox(buffer, matrix, sx - hw, sy, sz - hw, sx + hw, sy + h, sz + hw, r, g, b, 0.35f);
            }
        }

        if (renderHitbox != null) {
            drawLineBox(buffer, matrix,
                    renderHitbox.minX - camPos.x, renderHitbox.minY - camPos.y, renderHitbox.minZ - camPos.z,
                    renderHitbox.maxX - camPos.x, renderHitbox.maxY - camPos.y, renderHitbox.maxZ - camPos.z,
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
        cancelVaultReturn();
        resetState(false);
    }
}
