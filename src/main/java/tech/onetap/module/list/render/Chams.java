package tech.onetap.module.list.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import tech.onetap.mixin.IMinecraftClientAccessor;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.chams.ChamsShaders;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Chams", moduleDesc = "Просвечивает сущности сквозь стены", moduleCategory = ModuleCategory.RENDER)
public class Chams extends Module {
    private static final int FULL_BRIGHT = 15728880;

    private final ModeSetting mode = new ModeSetting("Режим", "Solid", "Solid", "Outline", "Gradient", "Rainbow", "Bloom");
    private final ModeSetting shape = new ModeSetting("Отображение", "Обе", "Обводка", "Заливка", "Обе");
    private final BooleanSetting players = new BooleanSetting("Игроки", true);
    private final BooleanSetting mobs = new BooleanSetting("Мобы", true);
    private final BooleanSetting self = new BooleanSetting("Себя", false);
    private final SliderSetting range = new SliderSetting("Дальность", 64, 4, 256, 1);
    private final SliderSetting width = new SliderSetting("Толщина обводки", 2, 0, 10, 1);
    private final SliderSetting glow = new SliderSetting("Свечение", 1.0, 0.0, 5.0, 0.05);
    private final SliderSetting opacity = new SliderSetting("Прозрачность заливки", 100, 0, 100, 1);
    private final BooleanSetting fastLines = new BooleanSetting("FastLines", true);
    private final ColorSetting fillColor = new ColorSetting("Заливка", 0x6600AAFF);
    private final ColorSetting outlineColor = new ColorSetting("Обводка", 0xFF00AAFF);
    private final ColorSetting fillColor2 = new ColorSetting("Заливка 2", 0x66FF44AA);
    private final ColorSetting outlineColor2 = new ColorSetting("Обводка 2", 0xFFFF44AA);
    private final SliderSetting animSpeed = new SliderSetting("Скорость анимации", 1.0, 0.0, 5.0, 0.05);
    private final SliderSetting glowQuality = new SliderSetting("Качество Bloom", 4, 1, 8, 1);

    private boolean registered;
    private final WorldRenderEvents.Last listener = context -> {
        if (isEnabled()) {
            render(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
        }
    };

    public Chams() {
        fillColor2.setVisible(() -> mode.is("Gradient"));
        outlineColor2.setVisible(() -> mode.is("Gradient"));
        animSpeed.setVisible(() -> mode.is("Gradient") || mode.is("Rainbow"));
        glowQuality.setVisible(() -> mode.is("Bloom"));
    }

    @Override
    public void onEnable() {
        if (!registered) {
            WorldRenderEvents.LAST.register(listener);
            registered = true;
        }
        super.onEnable();
    }

    private void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (matrices == null || camera == null || mc.world == null || mc.player == null) {
            return;
        }

        List<Entity> targets = collectTargets(camera);
        if (targets.isEmpty()) {
            return;
        }

        ChamsShaders shaders = ChamsShaders.getInstance();
        if (!shaders.isReady()) {
            return;
        }

        Framebuffer main = mc.getFramebuffer();
        int width = main.textureWidth;
        int height = main.textureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }

        SimpleFramebuffer silhouette = shaders.framebuffer(width, height);
        renderSilhouettes(main, silhouette, matrices, camera, tickDelta, targets);

        main.beginWrite(true);
        drawPass(shaders, silhouette, width, height);
    }

    private void renderSilhouettes(Framebuffer main, SimpleFramebuffer silhouette, MatrixStack matrices, Camera camera, float tickDelta, List<Entity> targets) {
        silhouette.setClearColor(0f, 0f, 0f, 0f);
        silhouette.clear();
        silhouette.beginWrite(true);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadows(false);
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        Vec3d cam = camera.getPos();

        ((IMinecraftClientAccessor) (Object) mc).setFramebuffer(silhouette);
        try {
            for (Entity entity : targets) {
                double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cam.x;
                double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cam.y;
                double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cam.z;
                dispatcher.render(entity, x, y, z, tickDelta, matrices, immediate, FULL_BRIGHT);
            }
            immediate.draw();
        } finally {
            ((IMinecraftClientAccessor) (Object) mc).setFramebuffer(main);
            dispatcher.setRenderShadows(true);
        }

        silhouette.endWrite();
    }

    private void drawPass(ChamsShaders shaders, SimpleFramebuffer silhouette, int width, int height) {
        int program = shaders.program(mode.getValue());
        if (program <= 0) {
            return;
        }

        int shapeMode = shapeMode();
        int outlineWidth = this.width.getIntValue();
        float glowMultiplier = glow.getFloatValue();
        float fillAlpha = (float) (opacity.getIntValue() / 100.0);
        boolean rainbow = mode.is("Rainbow");
        float time = (System.currentTimeMillis() % 100000L) / 1000f * animSpeed.getFloatValue();
        float rainbowOffset = (System.currentTimeMillis() % 5000L) / 5000f * Math.max(animSpeed.getFloatValue(), 0.01f);

        GlStateManager._glUseProgram(program);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(silhouette.getColorAttachment());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 1);
        GlStateManager._bindTexture(shaders.getWhiteTexture());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        shaders.set1i(program, "u_Texture", 0);
        shaders.set1i(program, "u_Overlay", 1);
        shaders.set1f(program, "u_OverlayAlpha", 1f);
        shaders.set1i(program, "u_Image", 0);
        shaders.set1i(program, "u_Dots", 0);
        shaders.set1i(program, "u_DotsRadius", 1);
        shaders.set1f(program, "u_DotsAlpha", 1f);
        shaders.set1i(program, "u_FastLines", fastLines.getValue() ? 1 : 0);
        shaders.set2f(program, "u_Size", width, height);
        shaders.set1i(program, "u_Width", outlineWidth);
        shaders.set1i(program, "u_Radius", outlineWidth);
        shaders.set1i(program, "u_ShapeMode", shapeMode);
        shaders.set1f(program, "u_GlowMultiplier", glowMultiplier);
        shaders.set1i(program, "u_GlowQuality", glowQuality.getIntValue());
        shaders.set1f(program, "u_Time", time);
        shaders.set1f(program, "u_Step", 1f);

        setColor(shaders, program, "u_FillColor", fillColor.getValue(), fillAlpha);
        setColor(shaders, program, "u_OutlineColor", outlineColor.getValue(), 1f);
        setColor(shaders, program, "u_FillColor2", fillColor2.getValue(), fillAlpha);
        setColor(shaders, program, "u_OutlineColor2", outlineColor2.getValue(), 1f);
        setColor(shaders, program, "u_Fill", fillColor.getValue(), fillAlpha);
        setColor(shaders, program, "u_Outline", outlineColor.getValue(), 1f);
        shaders.set1f(program, "u_Fill_Offset", rainbow ? rainbowOffset : 0f);
        shaders.set1f(program, "u_Fill_Strength", 1f);
        shaders.set1f(program, "u_Outline_Offset", rainbow ? rainbowOffset : 0f);
        shaders.set1f(program, "u_Outline_Strength", 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        GL30.glBindVertexArray(shaders.getQuadVao());
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL30.glBindVertexArray(0);

        GlStateManager._glUseProgram(0);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private List<Entity> collectTargets(Camera camera) {
        List<Entity> list = new ArrayList<>();
        double maxDistance = range.getValue();
        Vec3d cam = camera.getPos();
        boolean firstPerson = mc.options.getPerspective() == Perspective.FIRST_PERSON;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity) || !entity.isAlive()) {
                continue;
            }
            if (entity == mc.player) {
                if (!self.getValue() || firstPerson) {
                    continue;
                }
            } else if (entity instanceof PlayerEntity) {
                if (!players.getValue()) {
                    continue;
                }
            } else if (!mobs.getValue()) {
                continue;
            }
            if (cam.distanceTo(entity.getPos()) > maxDistance) {
                continue;
            }
            list.add(entity);
        }
        return list;
    }

    private int shapeMode() {
        if (shape.is("Обводка")) {
            return 0;
        }
        if (shape.is("Заливка")) {
            return 1;
        }
        return 2;
    }

    private void setColor(ChamsShaders shaders, int program, String name, int argb, float alphaScale) {
        float r = (argb >> 16 & 0xFF) / 255f;
        float g = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float a = (argb >>> 24 & 0xFF) / 255f * alphaScale;
        shaders.set4f(program, name, r, g, b, a);
    }
}
