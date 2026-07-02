package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.packet.NetworkUtils;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInformation(moduleName = "Blink", moduleDesc = "Задерживает пакеты движения", moduleCategory = ModuleCategory.MOVEMENT)
public class Blink extends Module {

    private final BooleanSetting dummy = new BooleanSetting("Фейк игрок", true);
    private final BooleanSetting ambush = new BooleanSetting("Засада", false);
    private final BooleanSetting renderBox = new BooleanSetting("Рендер бокса", true);
    private final BooleanSetting autoReset = new BooleanSetting("Авто сброс", false);
    private final SliderSetting resetAfter = new SliderSetting("Сброс после", 100.0, 1.0, 1000.0, 1.0)
            .setVisible(autoReset::getValue);
    private final ModeSetting resetAction = new ModeSetting("Действие", "Blink", "Blink", "Reset")
            .setVisible(autoReset::getValue);
    private final BooleanSetting autoDisable = new BooleanSetting("Авто выкл", true)
            .setVisible(autoReset::getValue);

    private final CopyOnWriteArrayList<Packet<?>> packets = new CopyOnWriteArrayList<>();
    private OtherClientPlayerEntity dummyPlayer = null;
    private Box box = null;
    private boolean renderListenerRegistered = false;

    private final WorldRenderEvents.Last renderListener = this::onRender3D;

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player == null || mc.world == null) return;

        box = mc.player.getBoundingBox();
        packets.clear();

        if (dummy.getValue()) {
            createDummyPlayer();
        }

        if (!renderListenerRegistered) {
            WorldRenderEvents.LAST.register(renderListener);
            renderListenerRegistered = true;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        flushPackets();
        removeDummyPlayer();
        packets.clear();
        box = null;
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;

        Packet<?> packet = e.getPacket();

        if (e.getType() == EventPacket.Type.RECEIVE) {
            if (packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {
                setEnabled(false);
                return;
            }
        }

        if (e.getType() == EventPacket.Type.SEND) {
            if (packet instanceof ClientStatusC2SPacket status) {
                if (status.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN) {
                    setEnabled(false);
                    return;
                }
            }

            if (ambush.getValue() && packet instanceof PlayerInteractEntityC2SPacket) {
                setEnabled(false);
                return;
            }

            packets.add(packet);
            e.setCancelled(true);
        }
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;

        if (autoReset.getValue() && packets.size() > resetAfter.getIntValue()) {
            if (resetAction.is("Reset")) {
                cancelPackets();
            } else if (resetAction.is("Blink")) {
                flushPackets();
                if (dummyPlayer != null) {
                    dummyPlayer.copyPositionAndRotation(mc.player);
                }
                box = mc.player.getBoundingBox();
            }

            if (autoDisable.getValue()) {
                setEnabled(false);
            }
        }
    }

    private void onRender3D(WorldRenderContext context) {
        if (!isEnabled() || mc.player == null || !renderBox.getValue() || box == null) return;

        Camera camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();

        double minX = box.minX - camX;
        double minY = box.minY - camY;
        double minZ = box.minZ - camZ;
        double maxX = box.maxX - camX;
        double maxY = box.maxY - camY;
        double maxZ = box.maxZ - camZ;

        // Рисуем линии бокса
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(255, 255, 255, 255);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(255, 255, 255, 255);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(255, 255, 255, 255);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void createDummyPlayer() {
        if (mc.player == null || mc.world == null) return;

        GameProfile profile = new GameProfile(UUID.randomUUID(), mc.player.getName().getString());
        dummyPlayer = new OtherClientPlayerEntity(mc.world, profile);
        dummyPlayer.copyFrom(mc.player);
        dummyPlayer.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        dummyPlayer.setHeadYaw(mc.player.getHeadYaw());
        mc.world.addEntity(dummyPlayer);
    }

    private void removeDummyPlayer() {
        if (dummyPlayer != null && mc.world != null) {
            mc.world.removeEntity(dummyPlayer.getId(), Entity.RemovalReason.DISCARDED);
            dummyPlayer = null;
        }
    }

    private void flushPackets() {
        for (Packet<?> packet : packets) {
            NetworkUtils.sendSilentPacket(packet);
        }
        packets.clear();
    }

    private void cancelPackets() {
        packets.clear();
    }
}
