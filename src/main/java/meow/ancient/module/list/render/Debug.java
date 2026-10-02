package meow.ancient.module.list.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import meow.ancient.Ancient;
import meow.ancient.event.list.EventTick;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.module.settings.SliderSetting;
import meow.ancient.util.player.combat.BacktrackUtil;
import meow.ancient.util.player.combat.EntityExtrapolation;
import meow.ancient.util.render.providers.ColorProvider;
import meow.ancient.util.text.ValueUnit;
import meteordevelopment.orbit.EventHandler;

@ModuleInformation(moduleName = "Debug", moduleDesc = "Визуализация экстраполяции и бэктрека на всех энтити", moduleCategory = ModuleCategory.RENDER)
public class Debug extends Module {

    public final BooleanSetting extrapolation = new BooleanSetting("Extrapolation", true);
    public final BooleanSetting extrapolationAuto = new BooleanSetting("Экстраполяция по пингу", true)
            .setVisible(extrapolation::getValue);
    public final SliderSetting extrapolationTicks = new SliderSetting("Тики экстраполяции",
            ValueUnit.countable("тик", "тика", "тиков"), 3, 1, 10, 0.5f)
            .setVisible(() -> extrapolation.getValue() && !extrapolationAuto.getValue());

    public final BooleanSetting backtrack = new BooleanSetting("BackTrack", true);
    public final SliderSetting backtrackTicks = new SliderSetting("Тики бэктрека",
            ValueUnit.countable("тик", "тика", "тиков"), 5, 1, 20, 1)
            .setVisible(backtrack::getValue);

    private final EntityExtrapolation debugExtrapolator = new EntityExtrapolation();
    private final BacktrackUtil debugBacktrack = new BacktrackUtil();

    private boolean listenerRegistered = false;

    private final WorldRenderEvents.Last renderListener = context -> {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        renderDebug(context.matrixStack(), context.camera());
    };

    @Override
    public void onEnable() {
        if (!listenerRegistered) {
            WorldRenderEvents.LAST.register(renderListener);
            listenerRegistered = true;
        }
        debugExtrapolator.clear();
        debugBacktrack.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        debugExtrapolator.clear();
        debugBacktrack.clear();
        super.onDisable();
    }

    @EventHandler
    private void onTick(EventTick e) {
        if (mc.world == null || mc.player == null) return;

        int btTicks = backtrackTicks.getIntValue();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity instanceof ClientPlayerEntity || entity instanceof ArmorStandEntity || !entity.isAlive()) continue;

            if (extrapolation.getValue()) {
                debugExtrapolator.update(entity);
            }
            if (backtrack.getValue()) {
                debugBacktrack.update(entity, btTicks);
            }
        }
    }

    private void renderDebug(MatrixStack matrices, Camera camera) {
        Vec3d camPos = camera.getPos();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity instanceof ClientPlayerEntity || entity instanceof ArmorStandEntity || !entity.isAlive()) continue;

            // Рендер BackTrack бокса (зеленоватый / бирюзовый)
            if (backtrack.getValue()) {
                KillAura aura = Ancient.getInstance().getModuleStorage().get(KillAura.class);
                double reach = aura != null ? aura.distance.getValue() + 2.0 : 6.0;
                double penalty = aura != null ? aura.backtrackAgePenalty.getValue() : 0.15;
                Box btBox = debugBacktrack.getBestBox(entity, backtrackTicks.getIntValue(), reach, penalty);
                if (btBox != null) {
                    drawBox(matrices, camPos, btBox, 0.2f, 0.95f, 0.35f, 0.85f);
                }
            }

            // Рендер Extrapolation бокса (цвет темы / синий)
            if (extrapolation.getValue()) {
                boolean auto = extrapolationAuto.getValue();
                float ticks = auto ? 0f : (float) extrapolationTicks.getValue();
                Box exBox = debugExtrapolator.getExtrapolatedBox(entity, ticks, auto);
                if (exBox != null) {
                    int theme = ColorProvider.getThemeColor();
                    float r = ((theme >> 16) & 0xFF) / 255f;
                    float g = ((theme >> 8) & 0xFF) / 255f;
                    float b = (theme & 0xFF) / 255f;
                    drawBox(matrices, camPos, exBox, r, g, b, 0.9f);
                }
            }
        }
    }

    private void drawBox(MatrixStack matrices, Vec3d camPos, Box box, float r, float g, float b, float a) {
        double minX = box.minX - camPos.x;
        double minY = box.minY - camPos.y;
        double minZ = box.minZ - camPos.z;
        double maxX = box.maxX - camPos.x;
        double maxY = box.maxY - camPos.y;
        double maxZ = box.maxZ - camPos.z;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(1.5f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Нижнее основание
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Верхнее основание
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Вертикальные ребра
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }
}
