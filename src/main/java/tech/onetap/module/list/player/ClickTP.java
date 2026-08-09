package tech.onetap.module.list.player;

import meteordevelopment.orbit.EventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventAttackBlock;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.commands.defaults.ClipBypass;
import tech.onetap.util.render.providers.ColorProvider;

@ModuleInformation(moduleName = "Click TP", moduleDesc = "Телепорт по ЛКМ в точку прицела", moduleCategory = ModuleCategory.PLAYER)
public class ClickTP extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Simple", "Simple", "Vault");
    private final SliderSetting distance = new SliderSetting("Дистанция", 100.0, 10.0, 500.0, 5.0);
    private final SliderSetting airDistance = new SliderSetting("Дистанция в воздух", 10.0, 2.0, 100.0, 0.5);

    private BlockHitResult target;

    @EventHandler
    private void onTick(EventTick event) {
        target = null;

        if (mc.player == null || mc.world == null) return;

        HitResult hit = mc.player.raycast(distance.getValue(), 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hit;
        if (mc.world.getBlockState(blockHit.getBlockPos()).isAir()) return;

        target = blockHit;
    }

    @EventHandler
    private void onKey(EventKeyInput event) {
        if (event.getKey() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) return;
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        Vec3d pos = target != null ? getTeleportPos() : getAirTeleportPos();
        if (mode.is("Vault")) {
            ClipBypass.teleport(pos.x, pos.y, pos.z, "vault");
        } else {
            mc.player.setPosition(pos.x, pos.y, pos.z);
        }
    }

    @EventHandler
    private void onAttackBlock(EventAttackBlock event) {
        event.cancelEvent();
    }

    @EventHandler
    private void onAttack(EventAttack event) {
        event.cancelEvent();
    }

    @EventHandler
    public void onWorldRender(EventWorldRender event) {
        if (mc.world == null || mc.player == null || target == null) return;

        BlockPos pos = target.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        int lineColor = ColorProvider.getThemeColor();
        int sideColor = ColorProvider.rgba(ColorProvider.red(lineColor), ColorProvider.green(lineColor), ColorProvider.blue(lineColor), 60);

        MatrixStack matrices = event.getMatrixStack();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        matrices.push();

        Box box = shape.getBoundingBox();
        double minX = pos.getX() + box.minX - camPos.x;
        double minY = pos.getY() + box.minY - camPos.y;
        double minZ = pos.getZ() + box.minZ - camPos.z;
        double maxX = pos.getX() + box.maxX - camPos.x;
        double maxY = pos.getY() + box.maxY - camPos.y;
        double maxZ = pos.getZ() + box.maxZ - camPos.z;

        drawFilled(matrices, minX, minY, minZ, maxX, maxY, maxZ, sideColor);
        drawOutline(matrices, minX, minY, minZ, maxX, maxY, maxZ, lineColor);

        matrices.pop();
    }

    private Vec3d getTeleportPos() {
        BlockPos pos = target.getBlockPos();
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        double top = shape.isEmpty() ? 1.0 : shape.getBoundingBox().maxY;
        return new Vec3d(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
    }

    private Vec3d getAirTeleportPos() {
        Vec3d end = mc.player.getCameraPosVec(1.0f).add(mc.player.getRotationVec(1.0f).multiply(airDistance.getValue()));
        return end.subtract(0.0, mc.player.getStandingEyeHeight(), 0.0);
    }

    private void drawOutline(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(2.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = ColorProvider.red(color) / 255f;
        float g = ColorProvider.green(color) / 255f;
        float b = ColorProvider.blue(color) / 255f;

        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private void drawFilled(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float r = ColorProvider.red(color) / 255f;
        float g = ColorProvider.green(color) / 255f;
        float b = ColorProvider.blue(color) / 255f;
        float a = ColorProvider.alpha(color) / 255f;

        quad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        quad(buffer, matrix, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        quad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        quad(buffer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        quad(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        quad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, 1.0f);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, 1.0f);
    }

    private void quad(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x3, (float) y3, (float) z3).color(r, g, b, a);
        buffer.vertex(matrix, (float) x4, (float) y4, (float) z4).color(r, g, b, a);
    }
}
