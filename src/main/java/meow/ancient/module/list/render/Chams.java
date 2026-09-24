package meow.ancient.module.list.render;

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
import meow.ancient.mixin.IMinecraftClientAccessor;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.module.settings.ColorSetting;
import meow.ancient.module.settings.ModeSetting;
import meow.ancient.module.settings.SliderSetting;
import meow.ancient.util.render.chams.ChamsShaders;
import meow.ancient.util.render.providers.ColorProvider;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Chams", moduleDesc = "Мягкое свечение с эффектом fade наружу и внутрь контура", moduleCategory = ModuleCategory.RENDER)
public class Chams extends Module {
    private static final int FULL_BRIGHT = 15728880;

    private final ModeSetting mode = new ModeSetting("Режим", "Main", "Main", "Smooth");
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Свой", "Градиент");
    private final ColorSetting customColor = new ColorSetting("Свой цвет", 0xFF00AAFF);
    private final ColorSetting customColor1 = new ColorSetting("Первый цвет", 0xFF00AAFF);
    private final ColorSetting customColor2 = new ColorSetting("Второй цвет", 0xFFFF44AA);

    private final SliderSetting lineWidth = new SliderSetting("Толщина линии", 2.0, 0.1, 20.0, 0.1);
    private final SliderSetting fillOpacity = new SliderSetting("Прозрачность заливки", 0, 0, 100, 1);
    private final SliderSetting animSpeed = new SliderSetting("Скорость градиента", 1.5, 0.1, 5.0, 0.1);

    public final BooleanSetting hands = new BooleanSetting("Руки", true);
    private final BooleanSetting players = new BooleanSetting("Игроки", true);
    private final BooleanSetting mobs = new BooleanSetting("Мобы", true);
    private final BooleanSetting self = new BooleanSetting("Себя", false);

    private boolean registered;
    private final WorldRenderEvents.Last listener = context -> {
        if (isEnabled()) {
            renderWorld(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
        }
    };

    public Chams() {
        customColor.setVisible(() -> colorMode.is("Свой"));
        customColor1.setVisible(() -> colorMode.is("Градиент"));
        customColor2.setVisible(() -> colorMode.is("Градиент"));
        animSpeed.setVisible(() -> colorMode.is("Тема") || colorMode.is("Градиент"));
    }

    @Override
    public void onEnable() {
        if (!registered) {
            WorldRenderEvents.LAST.register(listener);
            registered = true;
        }
        super.onEnable();
    }

    public boolean isHandsEnabled() {
        return isEnabled() && hands.getValue();
    }

    public boolean shouldRenderNormalHands() {
        return fillOpacity.getIntValue() < 100;
    }

    public void renderHands(MatrixStack matrices, float tickDelta, Runnable handRenderer) {
        if (!isEnabled() || !hands.getValue()) {
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

        SimpleFramebuffer silhouette = shaders.handFramebuffer(width, height);
        silhouette.setClearColor(0f, 0f, 0f, 0f);
        silhouette.clear();
        silhouette.beginWrite(true);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        ((IMinecraftClientAccessor) (Object) mc).setFramebuffer(silhouette);
        try {
            handRenderer.run();
            immediate.draw();
        } finally {
            ((IMinecraftClientAccessor) (Object) mc).setFramebuffer(main);
            silhouette.endWrite();
        }

        main.beginWrite(true);
        drawPass(shaders, silhouette, width, height);
    }

    private void renderWorld(MatrixStack matrices, Camera camera, float tickDelta) {
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

        SimpleFramebuffer silhouette = shaders.worldFramebuffer(width, height);
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
                matrices.push();
                try {
                    double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cam.x;
                    double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cam.y;
                    double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cam.z;
                    dispatcher.render(entity, x, y, z, tickDelta, matrices, immediate, FULL_BRIGHT);
                } catch (Throwable ignored) {
                } finally {
                    matrices.pop();
                }
            }
            immediate.draw();
        } finally {
            ((IMinecraftClientAccessor) (Object) mc).setFramebuffer(main);
            dispatcher.setRenderShadows(true);
            silhouette.endWrite();
        }
    }

    private void drawPass(ChamsShaders shaders, SimpleFramebuffer silhouette, int width, int height) {
        if (mode.is("Main")) {
            drawSydneyPass(shaders, silhouette, width, height);
            return;
        }

        int blurProg = shaders.getBlurHProgram();
        int bloomProg = shaders.getBloomProgram();
        if (blurProg <= 0 || bloomProg <= 0) {
            return;
        }

        SimpleFramebuffer blurFbo = shaders.blurFramebuffer(width, height);
        float lineW = lineWidth.getFloatValue();

        // Pass 1: Static horizontal Gaussian blur from silhouette into blurFbo
        blurFbo.setClearColor(0f, 0f, 0f, 0f);
        blurFbo.clear();
        blurFbo.beginWrite(true);

        GlStateManager._glUseProgram(blurProg);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(silhouette.getColorAttachment());

        shaders.set1i(blurProg, "u_Texture", 0);
        shaders.set2f(blurProg, "u_Size", width, height);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        GL30.glBindVertexArray(shaders.getQuadVao());
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

        blurFbo.endWrite();

        // Pass 2: Vertical Gaussian blur + bidirectional fade glow into main
        mc.getFramebuffer().beginWrite(true);

        int[] colors = getColors();
        boolean useGrad = isGradientActive();
        float time = (System.currentTimeMillis() % 100000L) / 1000f * animSpeed.getFloatValue();

        GlStateManager._glUseProgram(bloomProg);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(silhouette.getColorAttachment());

        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(blurFbo.getColorAttachment());

        shaders.set1i(bloomProg, "u_Texture", 0);
        shaders.set1i(bloomProg, "u_BlurTexture", 1);
        shaders.set1f(bloomProg, "u_LineWidth", lineW);
        shaders.set1f(bloomProg, "u_FillOpacity", (float) (fillOpacity.getIntValue() / 100.0));
        shaders.set1i(bloomProg, "u_UseGradient", useGrad ? 1 : 0);
        shaders.set1f(bloomProg, "u_Time", time);
        shaders.set2f(bloomProg, "u_Size", width, height);

        setColor(shaders, bloomProg, "u_Color1", colors[0]);
        setColor(shaders, bloomProg, "u_Color2", colors[1]);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL30.glBindVertexArray(0);

        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(0);

        GlStateManager._glUseProgram(0);
        RenderSystem.clearShader();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void drawSydneyPass(ChamsShaders shaders, SimpleFramebuffer silhouette, int width, int height) {
        int sydneyProg = shaders.getSydneyProgram();
        if (sydneyProg <= 0) {
            return;
        }

        mc.getFramebuffer().beginWrite(true);

        int[] colors = getColors();
        boolean useGrad = isGradientActive();
        float time = (System.currentTimeMillis() % 100000L) / 1000f * animSpeed.getFloatValue();

        GlStateManager._glUseProgram(sydneyProg);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(silhouette.getColorAttachment());

        shaders.set1i(sydneyProg, "u_Texture", 0);
        shaders.set1f(sydneyProg, "u_LineWidth", lineWidth.getFloatValue());
        shaders.set1f(sydneyProg, "u_FillOpacity", (float) (fillOpacity.getIntValue() / 100.0));
        shaders.set1i(sydneyProg, "u_UseGradient", useGrad ? 1 : 0);
        shaders.set1f(sydneyProg, "u_Time", time);
        shaders.set2f(sydneyProg, "u_Size", width, height);

        setColor(shaders, sydneyProg, "u_Color1", colors[0]);
        setColor(shaders, sydneyProg, "u_Color2", colors[1]);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        GL30.glBindVertexArray(shaders.getQuadVao());
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL30.glBindVertexArray(0);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(0);

        GlStateManager._glUseProgram(0);
        RenderSystem.clearShader();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private int[] getColors() {
        if (colorMode.is("Тема")) {
            int c1 = ColorProvider.getThemeColor();
            int c2 = ColorProvider.getThemeColorTwo();
            return new int[]{c1, c2};
        } else if (colorMode.is("Градиент")) {
            return new int[]{customColor1.getValue(), customColor2.getValue()};
        } else {
            int c = customColor.getValue();
            return new int[]{c, c};
        }
    }

    private boolean isGradientActive() {
        return colorMode.is("Градиент") || colorMode.is("Тема");
    }

    private List<Entity> collectTargets(Camera camera) {
        List<Entity> list = new ArrayList<>();
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
            list.add(entity);
        }
        return list;
    }

    private void setColor(ChamsShaders shaders, int program, String name, int argb) {
        float r = (argb >> 16 & 0xFF) / 255f;
        float g = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float a = (argb >>> 24 & 0xFF) / 255f;
        if (a <= 0f) a = 1f;
        shaders.set4f(program, name, r, g, b, a);
    }
}
