package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.render.providers.ColorProvider;

@ModuleInformation(moduleName = "Block Overlay", moduleDesc = "Кастомная подсветка блока", moduleCategory = ModuleCategory.RENDER)
public class BlockOverlay extends Module {

    @Subscribe
    public void onWorldRender(EventWorldRender event) {
        if (mc.world == null || mc.player == null) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hitResult = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);

        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        int color = ColorProvider.getThemeColor();

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

        drawFilled(matrices, minX, minY, minZ, maxX, maxY, maxZ, color);
        drawOutline(matrices, minX, minY, minZ, maxX, maxY, maxZ, color);

        matrices.pop();
    }

    private void drawOutline(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(2.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, 1f);

        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, 1f);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, 1f);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, 1f);

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
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 130 / 500f;

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private int getRainbowColor() {
        float hue = (System.currentTimeMillis() % 3000) / 3000f;
        return java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
    }
}
