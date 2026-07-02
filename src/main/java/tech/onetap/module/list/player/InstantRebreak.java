package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventAttackBlock; // Твой ивент из миксина
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "InstantRebreak", moduleCategory = ModuleCategory.PLAYER)
public class InstantRebreak extends Module {

    private final SliderSetting tickDelay = new SliderSetting("Задержка", ValueUnit.countable("тик", "тика", "тиков"), 0, 0, 20, 1);
    private final BooleanSetting rotate = new BooleanSetting("Ротация", true);
    private final BooleanSetting air = new BooleanSetting("Air", false);
    private final BooleanSetting render = new BooleanSetting("Рендер", true);
    private final ModeSetting shapeMode = new ModeSetting("Режим рендера", "Both", "Both", "Lines", "Sides");

    public BlockPos blockPos = null;
    private int ticks;
    private Direction direction;
    // Позиция «прогрета»: блок здесь уже был сломан обычным образом хотя бы раз,
    // после чего разрешён моментальный ре-брейк. Сбрасывается при смене цели.
    private boolean primed;

    private boolean renderListenerRegistered = false;
    private final WorldRenderEvents.Last renderListener = context -> {
        if (isEnabled() && render.getValue()) {
            renderBlockOverlay(context.matrixStack(), context.camera());
        }
    };

    public InstantRebreak() {
        // Конструктор пустой, ивенты подхватываются автоматически базой tech.onetap
    }

    @Override
    public void onEnable() {
        ticks = 0;
        blockPos = null;
        direction = Direction.UP;
        primed = false;

        if (!renderListenerRegistered) {
            WorldRenderEvents.LAST.register(renderListener);
            renderListenerRegistered = true;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        blockPos = null;
        super.onDisable();
    }

    @Subscribe
    private void onAttackBlock(final EventAttackBlock event) {
        // Получаем блок из твоего миксина
        if (event.getBlockPos() != null) {
            // Новая цель — нужно заново дождаться обычного слома перед моменталкой.
            if (!event.getBlockPos().equals(this.blockPos)) {
                this.primed = false;
            }
            this.blockPos = event.getBlockPos();
            this.direction = event.getDirection();
        }
    }

    @Subscribe
    private void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null || blockPos == null) return;

        // Фаза 1: «прогрев». Ждём, пока блок реально сломается обычным образом (станет воздухом),
        // не мешая ванильному майнингу. Это и есть авто-замена трюка «сначала макс. задержка, потом 0».
        if (!primed) {
            if (mc.world.getBlockState(blockPos).isAir()) {
                primed = true;
                ticks = 0;
            }
            return;
        }

        // Фаза 2: позиция прогрета — моментальный ре-брейк при появлении блока.
        if (ticks >= tickDelay.getValue()) {
            ticks = 0;

            if (shouldMine()) {
                if (rotate.getValue()) {
                    // 1. Точно как в KillAura: рассчитываем центр блока через RotationUtil.calculate
                    Vec3d blockCenter = Vec3d.ofCenter(blockPos);
                    Rotation rot = new Rotation(RotationUtil.calculate(blockCenter));

                    float yaw = rot.getYaw();
                    float pitch = rot.getPitch();

                    // 2. Применяем фирменный GCD Фикс из утилит твоего софта
                    float gcd = GCDFixer.getGCDValue();
                    if (gcd > 0.0f) {
                        yaw -= (yaw - mc.player.getYaw()) % gcd;
                        pitch -= (pitch - mc.player.getPitch()) % gcd;
                    }

                    // 3. Отправляем ротацию на сервер.
                    // Последний аргумент выставлен в false (clientLook) -> голова на экране НЕ дергается!
                    RotationComponent.update(new Rotation(yaw, pitch), 360, 360, 360, 360, 0, 1, false);
                }

                sendPacket();
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        } else {
            ticks++;
        }
    }

    public void sendPacket() {
        if (mc.getNetworkHandler() == null || blockPos == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                blockPos,
                direction == null ? Direction.UP : direction
        ));
    }

    public boolean shouldMine() {
        if (blockPos == null || mc.world == null) return false;

        if (air.getValue()) {
            return true;
        }

        return !mc.world.getBlockState(blockPos).isAir();
    }

    private void renderBlockOverlay(MatrixStack matrices, Camera camera) {
        if (blockPos == null || !shouldMine()) return;

        Vec3d camPos = camera.getPos();
        double renderX = blockPos.getX() - camPos.x;
        double renderY = blockPos.getY() - camPos.y;
        double renderZ = blockPos.getZ() - camPos.z;

        int color = ColorProvider.getThemeColor();
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        if (shapeMode.is("Both") || shapeMode.is("Sides")) {
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float a = 0.15f;

            buffer.vertex(matrix, 0, 0, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 0, 1).color(r, g, b, a);

            buffer.vertex(matrix, 0, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 0, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 0).color(r, g, b, a);

            buffer.vertex(matrix, 0, 0, 0).color(r, g, b, a);
            buffer.vertex(matrix, 0, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 0).color(r, g, b, a);

            buffer.vertex(matrix, 1, 0, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 1).color(r, g, b, a);

            buffer.vertex(matrix, 1, 0, 1).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 0, 1).color(r, g, b, a);

            buffer.vertex(matrix, 0, 0, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 0, 0, 0).color(r, g, b, a);

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        if (shapeMode.is("Both") || shapeMode.is("Lines")) {
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            float a = 1.0f;

            buffer.vertex(matrix, 0, 0, 0).color(r, g, b, a); buffer.vertex(matrix, 1, 0, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 0).color(r, g, b, a); buffer.vertex(matrix, 1, 0, 1).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 1).color(r, g, b, a); buffer.vertex(matrix, 0, 0, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 0, 1).color(r, g, b, a); buffer.vertex(matrix, 0, 0, 0).color(r, g, b, a);

            buffer.vertex(matrix, 0, 1, 0).color(r, g, b, a); buffer.vertex(matrix, 1, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 0).color(r, g, b, a); buffer.vertex(matrix, 1, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 1, 1, 1).color(r, g, b, a); buffer.vertex(matrix, 0, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 1, 1).color(r, g, b, a); buffer.vertex(matrix, 0, 1, 0).color(r, g, b, a);

            buffer.vertex(matrix, 0, 0, 0).color(r, g, b, a); buffer.vertex(matrix, 0, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 0).color(r, g, b, a); buffer.vertex(matrix, 1, 1, 0).color(r, g, b, a);
            buffer.vertex(matrix, 1, 0, 1).color(r, g, b, a); buffer.vertex(matrix, 1, 1, 1).color(r, g, b, a);
            buffer.vertex(matrix, 0, 0, 1).color(r, g, b, a); buffer.vertex(matrix, 0, 1, 1).color(r, g, b, a);

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }
}