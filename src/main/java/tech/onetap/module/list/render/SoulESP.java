package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.render.providers.ColorProvider;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Soul ESP", moduleDesc = "Показывает душу игрока после смерти", moduleCategory = ModuleCategory.RENDER)
public class SoulESP extends Module {
    private static final float DURATION = 3.0f;
    private static final float HEIGHT = 3.5f;

    private final List<Ghost> ghosts = new ArrayList<>();

    public SoulESP() {
        super();
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;

        if (e.getPacket() instanceof EntityStatusS2CPacket packet) {
            byte status = packet.getStatus();
            if (status == 3 || status == 35) {
                Entity entity = packet.getEntity(mc.world);

                if (entity instanceof PlayerEntity player) {
                    if (player == mc.player) return;

                    ghosts.add(new Ghost(
                            player.getPos(),
                            player.getBodyYaw(),
                            player.isSneaking(),
                            player.age,
                            System.currentTimeMillis()
                    ));
                }
            }
        }
    }

    @Subscribe
    public void onRender(EventWorldRender e) {
        if (mc.player == null || mc.world == null || ghosts.isEmpty()) return;

        long now = System.currentTimeMillis();
        float dur = DURATION * 1000f;
        ghosts.removeIf(g -> (now - g.time) >= dur);

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        MatrixStack m = e.getMatrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        for (Ghost g : ghosts) {
            float t = (now - g.time) / dur;
            if (t >= 1f) continue;

            float alpha = (1f - t) * 0.6f;
            float rise = HEIGHT * ease(t);

            int colorFirst = ColorProvider.getThemeColor();
            int colorSecond = ColorProvider.getThemeColorTwo();
            int c = ColorProvider.interpolateColor(colorFirst, colorSecond, t);

            float r = ColorProvider.red(c) / 255f;
            float gr = ColorProvider.green(c) / 255f;
            float b = ColorProvider.blue(c) / 255f;

            m.push();
            m.translate(g.pos.x - cam.x, g.pos.y - cam.y + rise, g.pos.z - cam.z);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - g.yaw));
            m.scale(-1f, -1f, 1f);
            m.translate(0, -1.5, 0);

            if (g.sneak) {
                m.translate(0, 0.2, 0);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28f));
            }

            Matrix4f mat = m.peek().getPositionMatrix();
            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            float u = 1f / 16f;
            float swing = MathHelper.sin(g.phase * 0.6662f) * 0.6f;

            box(buf, mat, -4*u, 0, -2*u, 8*u, 12*u, 4*u, r, gr, b, alpha);
            box(buf, mat, -4*u, -8*u, -4*u, 8*u, 8*u, 8*u, r, gr, b, alpha);

            m.push();
            m.translate(-6*u, 2*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotation(-swing));
            m.translate(6*u, -2*u, 0);
            box(buf, m.peek().getPositionMatrix(), -8*u, -2*u, -2*u, 4*u, 12*u, 4*u, r, gr, b, alpha);
            m.pop();

            m.push();
            m.translate(6*u, 2*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotation(swing));
            m.translate(-6*u, -2*u, 0);
            box(buf, m.peek().getPositionMatrix(), 4*u, -2*u, -2*u, 4*u, 12*u, 4*u, r, gr, b, alpha);
            m.pop();

            m.push();
            m.translate(-2*u, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotation(swing));
            m.translate(2*u, -12*u, 0);
            box(buf, m.peek().getPositionMatrix(), -4*u, 12*u, -2*u, 4*u, 12*u, 4*u, r, gr, b, alpha);
            m.pop();

            m.push();
            m.translate(2*u, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotation(-swing));
            m.translate(-2*u, -12*u, 0);
            box(buf, m.peek().getPositionMatrix(), 0, 12*u, -2*u, 4*u, 12*u, 4*u, r, gr, b, alpha);
            m.pop();

            BufferRenderer.drawWithGlobalProgram(buf.end());
            m.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void box(BufferBuilder b, Matrix4f m, float x, float y, float z, float sx, float sy, float sz, float r, float g, float bl, float a) {
        float x2 = x + sx, y2 = y + sy, z2 = z + sz;
        b.vertex(m, x, y, z2).color(r, g, bl, a);
        b.vertex(m, x2, y, z2).color(r, g, bl, a);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a);
        b.vertex(m, x, y2, z2).color(r, g, bl, a);
        b.vertex(m, x2, y, z).color(r, g, bl, a);
        b.vertex(m, x, y, z).color(r, g, bl, a);
        b.vertex(m, x, y2, z).color(r, g, bl, a);
        b.vertex(m, x2, y2, z).color(r, g, bl, a);
        b.vertex(m, x, y, z).color(r, g, bl, a);
        b.vertex(m, x, y, z2).color(r, g, bl, a);
        b.vertex(m, x, y2, z2).color(r, g, bl, a);
        b.vertex(m, x, y2, z).color(r, g, bl, a);
        b.vertex(m, x2, y, z2).color(r, g, bl, a);
        b.vertex(m, x2, y, z).color(r, g, bl, a);
        b.vertex(m, x2, y2, z).color(r, g, bl, a);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a);
        b.vertex(m, x, y2, z2).color(r, g, bl, a);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a);
        b.vertex(m, x2, y2, z).color(r, g, bl, a);
        b.vertex(m, x, y2, z).color(r, g, bl, a);
        b.vertex(m, x, y, z).color(r, g, bl, a);
        b.vertex(m, x2, y, z).color(r, g, bl, a);
        b.vertex(m, x2, y, z2).color(r, g, bl, a);
        b.vertex(m, x, y, z2).color(r, g, bl, a);
    }

    private float ease(float t) {
        return 1f - (float) Math.pow(1f - MathHelper.clamp(t, 0f, 1f), 3);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        ghosts.clear();
    }

    private static class Ghost {
        Vec3d pos;
        float yaw;
        boolean sneak;
        float phase;
        long time;

        Ghost(Vec3d pos, float yaw, boolean sneak, float phase, long time) {
            this.pos = pos;
            this.yaw = yaw;
            this.sneak = sneak;
            this.phase = phase;
            this.time = time;
        }
    }
}