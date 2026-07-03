package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ArmorStandItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.providers.ColorProvider;

@ModuleInformation(moduleName = "Air Place", moduleDesc = "Ставит блок в точку прицела в воздухе", moduleCategory = ModuleCategory.PLAYER)
public class AirPlace extends Module {
    private final BooleanSetting render = new BooleanSetting("Рендер", true);
    private final BooleanSetting customRange = new BooleanSetting("Своя дистанция", false);
    private final SliderSetting range = new SliderSetting("Дистанция", 5.0, 0.0, 6.0, 0.1).setVisible(customRange::getValue);
    private final SliderSetting sideAlpha = new SliderSetting("Прозрачность сторон", 65.0, 0.0, 255.0, 1.0);

    private BlockHitResult hitResult;

    @Subscribe
    private void onTick(EventTick event) {
        hitResult = null;

        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.MISS) return;
        if (!placeable(mc.player.getMainHandStack()) && !placeable(mc.player.getOffHandStack())) return;

        double distance = customRange.getValue() ? range.getValue() : mc.player.getBlockInteractionRange();
        HitResult picked = mc.player.raycast(distance, 0.0f, false);
        if (!(picked instanceof BlockHitResult blockHitResult)) return;

        BlockPos pos = blockHitResult.getBlockPos();
        if (!mc.world.getBlockState(pos).isReplaceable()) return;

        hitResult = new BlockHitResult(Vec3d.ofCenter(pos), mc.player.getHorizontalFacing().getOpposite(), pos, false);
    }

    @Subscribe
    private void onKey(EventKeyInput event) {
        if (event.getKey() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || hitResult == null) return;

        Hand hand = placeable(mc.player.getMainHandStack()) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        if (!result.isAccepted()) return;

        mc.player.swingHand(hand);
    }

    @Subscribe
    private void onWorldRender(EventWorldRender event) {
        if (!render.getValue() || mc.player == null || mc.world == null || hitResult == null) return;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.MISS) return;
        if (!mc.world.getBlockState(hitResult.getBlockPos()).isReplaceable()) return;

        drawBox(event.getMatrixStack(), hitResult.getBlockPos());
    }

    private boolean placeable(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BlockItem || item instanceof SpawnEggItem || item instanceof FireworkRocketItem || item instanceof ArmorStandItem;
    }

    private void drawBox(MatrixStack matrices, BlockPos pos) {
        int lineColor = ColorProvider.getThemeColor();
        int sideColor = ColorProvider.rgba(ColorProvider.red(lineColor), ColorProvider.green(lineColor), ColorProvider.blue(lineColor), sideAlpha.getIntValue());
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        double minX = pos.getX() - camPos.x;
        double minY = pos.getY() - camPos.y;
        double minZ = pos.getZ() - camPos.z;
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        drawFilled(matrices, minX, minY, minZ, maxX, maxY, maxZ, sideColor);
        drawOutline(matrices, minX, minY, minZ, maxX, maxY, maxZ, lineColor);
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
