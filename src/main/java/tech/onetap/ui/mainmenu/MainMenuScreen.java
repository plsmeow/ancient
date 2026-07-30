package tech.onetap.ui.mainmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import tech.onetap.Onetap;
import tech.onetap.module.settings.impl.ThemeManager;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MainMenuScreen extends Screen implements IMinecraft {

    private final List<MenuButton> buttons = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();

    private final Animation fadeIn = new Animation(Easing.QUINTIC_OUT, 700);

    private long initTime;

    public MainMenuScreen() {
        super(Text.of(""));
    }

    @Override
    protected void init() {
        super.init();
        initTime = System.currentTimeMillis();
        fadeIn.setValue(0f);
        fadeIn.run(1f);

        buttons.clear();

        float widthButton = 160f;
        float heightButton = 24f;
        float spacing = 6f;
        float totalHeight = heightButton * 4 + spacing * 3;
        float x = (mc.getWindow().getScaledWidth() - widthButton) / 2f;
        float y = (mc.getWindow().getScaledHeight() - totalHeight) / 2f + 10;

        buttons.add(new MenuButton(x, y, widthButton, heightButton, "Одиночная игра",
                () -> mc.setScreen(new SelectWorldScreen(this))));
        y += heightButton + spacing;
        buttons.add(new MenuButton(x, y, widthButton, heightButton, "Сервера",
                () -> mc.setScreen(new MultiplayerScreen(this))));
        y += heightButton + spacing;
        buttons.add(new MenuButton(x, y, widthButton, heightButton, "Настройки",
                () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        y += heightButton + spacing;
        buttons.add(new MenuButton(x, y, widthButton, heightButton, "Выход",
                () -> mc.scheduleStop()));

        if (particles.isEmpty()) {
            for (int i = 0; i < 40; i++) {
                particles.add(new Particle(
                        ThreadLocalRandom.current().nextFloat() * mc.getWindow().getScaledWidth(),
                        ThreadLocalRandom.current().nextFloat() * mc.getWindow().getScaledHeight(),
                        ThreadLocalRandom.current().nextFloat() * 1.5f + 0.5f,
                        ThreadLocalRandom.current().nextFloat() * 0.4f + 0.1f
                ));
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        DrawUtil.drawRound(0, 0, width, height, 0, ColorProvider.rgba(5, 5, 10, 255));

        super.render(context, mouseX, mouseY, delta);

        float fade = fadeIn.getValue();
        int alpha = (int) (255 * fade);

        int themeColor = ThemeManager.getInstance().getCurrentTheme().getColorFirst();

        DrawUtil.drawRound(0, 0, width, height, 0,
                ColorProvider.rgba(8, 8, 14, (int) (255 * fade)),
                ColorProvider.rgba(5, 5, 10, (int) (255 * fade)));

        int glowColor = ColorProvider.setAlpha(themeColor, (int) (40 * fade));
        DrawUtil.drawRoundBlur(width / 2f - 200, height / 2f - 200, 400, 400, 0, glowColor, 60f);

        renderParticles(context, delta);

        float logoScale = 1.0f + (float) Math.sin((System.currentTimeMillis() - initTime) / 800.0) * 0.02f;
        renderLogo(context, width, height, fade, themeColor, logoScale);

        for (MenuButton button : buttons) {
            button.render(context, mouseX, mouseY, delta);
        }

        Onetap.getInstance().getAltWidget().render(context, mouseX, mouseY);

        renderBottomInfo(context, width, height, fade, themeColor);

        renderSidePanel(context, width, height, fade, themeColor);
    }

    private void renderParticles(DrawContext context, float delta) {
        for (Particle p : particles) {
            p.y -= p.speed;
            if (p.y < -10) {
                p.y = mc.getWindow().getScaledHeight() + 10;
                p.x = ThreadLocalRandom.current().nextFloat() * mc.getWindow().getScaledWidth();
            }
            int color = ColorProvider.rgba(255, 255, 255, (int) (p.alpha * 255));
            DrawUtil.drawRound(p.x, p.y, p.size, p.size, 1, color);
        }
    }

    private void renderLogo(DrawContext context, int width, int height, float fade, int themeColor, float scale) {
        String logo = "ANCIENT";
        float logoSize = 64f;
        float logoWidth = Fonts.SFBOLD.get().getWidth(logo, logoSize);
        float logoX = (width - logoWidth) / 2f;
        float logoY = height / 2f - 180;

        context.getMatrices().push();
        context.getMatrices().translate(logoX + logoWidth / 2f, logoY + logoSize / 2f, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-(logoX + logoWidth / 2f), -(logoY + logoSize / 2f), 0);

        DrawUtil.drawText(Fonts.SFBOLD.get(), logo, logoX, logoY,
                ColorProvider.setAlpha(themeColor, (int) (255 * fade)), logoSize);

        context.getMatrices().pop();

        String subtitle = "Minecraft 1.21.4 — Free Client";
        float subSize = 12f;
        float subWidth = Fonts.SFREGULAR.get().getWidth(subtitle, subSize);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), subtitle, (width - subWidth) / 2f, logoY + logoSize + 8,
                ColorProvider.rgba(180, 180, 200, (int) (200 * fade)), subSize);
    }

    private void renderBottomInfo(DrawContext context, int width, int height, float fade, int themeColor) {
        String versionText = "v1.0.0";
        float versionSize = 8f;
        DrawUtil.drawText(Fonts.SFREGULAR.get(), versionText, 8, height - 14,
                ColorProvider.rgba(140, 140, 160, (int) (180 * fade)), versionSize);

        String userText = "User: " + mc.getSession().getUsername();
        float userSize = 8f;
        float userWidth = Fonts.SFREGULAR.get().getWidth(userText, userSize);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), userText, width - userWidth - 8, height - 14,
                ColorProvider.rgba(140, 140, 160, (int) (180 * fade)), userSize);
    }

    private void renderSidePanel(DrawContext context, int width, int height, float fade, int themeColor) {
        float panelW = 220;
        float panelH = 140;
        float panelX = 20;
        float panelY = height - panelH - 30;

        DrawUtil.drawRound(panelX, panelY, panelW, panelH, 8,
                ColorProvider.rgba(15, 15, 22, (int) (180 * fade)));
        DrawUtil.drawRound(panelX, panelY, panelW, 0.5f, 0,
                ColorProvider.setAlpha(themeColor, (int) (120 * fade)));

        String title = "ANCIENT CLIENT";
        float titleSize = 10f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), title, panelX + 12, panelY + 12,
                ColorProvider.setAlpha(themeColor, (int) (255 * fade)), titleSize);

        String line1 = "• Cheat for Minecraft 1.21.4";
        String line2 = "• Free & open source";
        String line3 = "• " + (mc.getCurrentServerEntry() != null ? "Server: " + mc.getCurrentServerEntry().address : "No server");
        float lineSize = 8f;
        int lineColor = ColorProvider.rgba(200, 200, 220, (int) (220 * fade));
        DrawUtil.drawText(Fonts.SFREGULAR.get(), line1, panelX + 12, panelY + 32, lineColor, lineSize);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), line2, panelX + 12, panelY + 48, lineColor, lineSize);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), line3, panelX + 12, panelY + 64,
                ColorProvider.rgba(160, 160, 180, (int) (200 * fade)), lineSize);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons.forEach(b -> b.click((int) mouseX, (int) mouseY, button));
        Onetap.getInstance().getAltWidget().click((int) mouseX, (int) mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Onetap.getInstance().getAltWidget().updateScroll((int) mouseX, (int) mouseY, (float) verticalAmount);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        Onetap.getInstance().getAltWidget().onChar(chr);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Onetap.getInstance().getAltWidget().onKey(keyCode);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private static class Particle {
        float x, y, speed, alpha, size;

        Particle(float x, float y, float speed, float alpha) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.alpha = alpha;
            this.size = 1.0f;
        }
    }
}
