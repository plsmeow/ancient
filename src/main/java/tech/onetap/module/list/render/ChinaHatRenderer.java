package tech.onetap.module.list.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import tech.onetap.util.render.providers.ColorProvider;

public class ChinaHatRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final int SEGMENTS = 60;
    private static final float PI2 = (float) (Math.PI * 2);

    public ChinaHatRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        ChinaHat module = ChinaHat.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (module == null || !module.isEnabled() || mc.player == null || mc.world == null) return;

        boolean isSelf = (state.id == mc.player.getId());
        boolean isFriend = false;

        net.minecraft.entity.Entity entity = mc.world.getEntityById(state.id);
        if (entity != null) {
            String name = entity.getName().getString();
        }

        if (!isSelf && !isFriend) return;
        if (isSelf && mc.options.getPerspective().isFirstPerson()) return;

        matrixStack.push();

        this.getContextModel().head.rotate(matrixStack);

        float yOffset = -0.489f;
        if (entity instanceof net.minecraft.entity.LivingEntity living) {
            if (!living.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
                yOffset -= 0.0625f;
            }
        }

        matrixStack.translate(0.0f, yOffset, 0.0f);

        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();

        float width = 0.62f;
        float coneHeight = 0.3f;
        int centerColor = getThemeColor(200);
        int edgeColor = getThemeColor(80);
        RenderSystem.depthMask(false);
        BufferBuilder bufferInside = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < SEGMENTS; i++) {
            float angle1 = i * PI2 / SEGMENTS;
            float angle2 = (i + 1) * PI2 / SEGMENTS;

            float x1 = -MathHelper.sin(angle1) * width;
            float z1 = MathHelper.cos(angle1) * width;
            float x2 = -MathHelper.sin(angle2) * width;
            float z2 = MathHelper.cos(angle2) * width;

            bufferInside.vertex(matrix, 0, -coneHeight, 0).color(centerColor);
            bufferInside.vertex(matrix, x2, 0, z2).color(edgeColor);
            bufferInside.vertex(matrix, x1, 0, z1).color(edgeColor);
        }
        BufferRenderer.drawWithGlobalProgram(bufferInside.end());

        RenderSystem.depthMask(true);
        BufferBuilder bufferOutside = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < SEGMENTS; i++) {
            float angle1 = i * PI2 / SEGMENTS;
            float angle2 = (i + 1) * PI2 / SEGMENTS;

            float x1 = -MathHelper.sin(angle1) * width;
            float z1 = MathHelper.cos(angle1) * width;
            float x2 = -MathHelper.sin(angle2) * width;
            float z2 = MathHelper.cos(angle2) * width;

            bufferOutside.vertex(matrix, 0, -coneHeight, 0).color(centerColor);
            bufferOutside.vertex(matrix, x1, 0, z1).color(edgeColor);
            bufferOutside.vertex(matrix, x2, 0, z2).color(edgeColor);
        }
        BufferRenderer.drawWithGlobalProgram(bufferOutside.end());

        BufferBuilder outlineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        RenderSystem.lineWidth(2.0f);
        int outlineColor = getThemeColor(255);

        for (int i = 0; i < SEGMENTS; i++) {
            float angle1 = i * PI2 / SEGMENTS;
            float angle2 = (i + 1) * PI2 / SEGMENTS;

            float x1 = -MathHelper.sin(angle1) * width;
            float z1 = MathHelper.cos(angle1) * width;
            float x2 = -MathHelper.sin(angle2) * width;
            float z2 = MathHelper.cos(angle2) * width;

            outlineBuffer.vertex(matrix, x1, 0, z1).color(outlineColor);
            outlineBuffer.vertex(matrix, x2, 0, z2).color(outlineColor);
        }
        BufferRenderer.drawWithGlobalProgram(outlineBuffer.end());

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrixStack.pop();
    }

    private int getThemeColor(int alpha) {
        int themeColor = ColorProvider.getThemeColor();
        int r = (themeColor >> 16) & 0xFF;
        int g = (themeColor >> 8) & 0xFF;
        int b = themeColor & 0xFF;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
}