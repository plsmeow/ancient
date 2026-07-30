package im.expensive.ui.mainmenu; //не трогай наху

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import im.expensive.Expensive;
import im.expensive.utils.client.ClientUtil;
import im.expensive.utils.client.IMinecraft;
import im.expensive.utils.client.Vec2i;
import im.expensive.utils.math.MathUtil;
import im.expensive.utils.render.ColorUtils;
import im.expensive.utils.render.DisplayUtils;
import im.expensive.utils.render.KawaseBlur;
import im.expensive.utils.render.Stencil;
import im.expensive.utils.render.font.Fonts;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.screen.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.WorldSelectionScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MainScreen extends Screen implements IMinecraft {

    public MainScreen() {
        super(ITextComponent.getTextComponentOrEmpty(""));
    }

    private final ResourceLocation backmenu = new ResourceLocation("expensive/images/mainmenu/background.png");
    //private final ResourceLocation logo_line = new ResourceLocation("expensive/images/mainmenu/icon_outline.png");
    //private final ResourceLocation menu = new ResourceLocation("expensive/images/mainmenu/w.png");
    //private final ResourceLocation menu2 = new ResourceLocation("expensive/images/mainmenu/w2.png");
    private final List<Button> buttons = new ArrayList<>();

    public static final ResourceLocation button = new ResourceLocation("expensive/images/button.png");

    @Override
    public void init(Minecraft minecraft, int width, int height) {
        super.init(minecraft, width, height);

        float widthButton = 250f / 2f;
        float x = 410f;
        float y = Math.round(ClientUtil.calc(height) / 2f - 28);

        buttons.clear();

        buttons.add(new Button(x, y, widthButton, 25, "Одиночная игра", () -> mc.displayGuiScreen(new WorldSelectionScreen(this))));
        y += 47f / 2f + 5;
        buttons.add(new Button(
                x, y, widthButton, 25, "Сервера", () -> mc.displayGuiScreen(new MultiplayerScreen(this))));
        y += 47f / 2f + 5;
        buttons.add(new Button(x, y, widthButton, 25, "Настройки", () -> mc.displayGuiScreen(new OptionsScreen(this, mc.gameSettings))));
        y += 47f / 2f + 5;
        buttons.add(new Button(x, y, widthButton, 25, "Выход", mc::shutdownMinecraftApplet));
        y += 47f / 2f + 5;
        //buttons.add(new Button(x, y, widthButton, 25, "Accounts",
          //     () -> mc.displayGuiScreen(new Alt(this))));

        // >>> действие кнопки Accounts
        //buttons.add(new Button(x, y, widthButton, 25, "Accounts",
         //       () -> mc.displayGuiScreen(new AltManager(this))));
        // <<< конец изменения

        //y += 47f / 2f + 5; //перенос кнопки
        //buttons.add(new Button(x, y, widthButton, 25, "Quit", mc::shutdownMinecraftApplet));
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        super.render(matrixStack, mouseX, mouseY, partialTicks);

        MainWindow mainWindow = mc.getMainWindow();
        int windowWidth = ClientUtil.calc(mainWindow.getScaledWidth());
        int windowHeight = ClientUtil.calc(mainWindow.getScaledHeight());

        DisplayUtils.drawImage(backmenu, 0, 0, width, height, -1);
        mc.gameRenderer.setupOverlayRendering(2);

        //DisplayUtils.drawImage(menu, 1, 1, 430, 580, ColorUtils.rgba(23, 45, 79, 255));
        //DisplayUtils.drawImage(menu2, 611, 1, 430, 350, ColorUtils.rgba(23, 45, 79, 255));
        //DisplayUtils.drawImage(logo_line, 1, 1, 430, 580, -1);

        Fonts.sfMedium.drawText(matrixStack, "ARCADE", 437, 210, ColorUtils.rgb(17, 89, 235), 16.5f);
        //Fonts.sfMedium.drawText(matrixStack, "Ver. 1.0", 500, 490, ColorUtils.rgba(90, 92, 94, 200), 8);

        KawaseBlur.blur.updateBlur(3, 4);
        drawButtons(matrixStack, mouseX, mouseY, partialTicks);

        Expensive.getInstance().getAltWidget().render(matrixStack, mouseX, mouseY);
        mc.gameRenderer.setupOverlayRendering();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Expensive.getInstance().getAltWidget().updateScroll((int) mouseX, (int) mouseY, (float) delta);
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

//    @Override
//    public boolean mouseClicked(double mouseX, double mouseY, int button) {
//        Vec2i fixed = ClientUtil.getMouse((int) mouseX, (int) mouseY);
//        buttons.forEach(b -> b.click(fixed.getX(), fixed.getY(), button));
//        Expensive.getInstance().getAltWidget().click(fixed.getX(), fixed.getY(), button);
//        return super.mouseClicked(mouseX, mouseY, button);
//    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Expensive.getInstance().getAltWidget().onChar(codePoint);
        return super.charTyped(codePoint, modifiers);
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Expensive.getInstance().getAltWidget().onKey(keyCode);
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Vec2i fixed = ClientUtil.getMouse((int) mouseX, (int) mouseY);
        buttons.forEach(b -> b.click(fixed.getX(), fixed.getY(), button));
        Expensive.getInstance().getAltWidget().click(fixed.getX(), fixed.getY(), button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawButtons(MatrixStack stack, int mX, int mY, float pt) {
        buttons.forEach(b -> b.render(stack, mX, mY, pt));
    }

    class Button {
        @Getter private final float x, y, width, height;
        private final String text;
        private final Runnable action;

        public Button(float x, float y, float width, float height, String text, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
            this.action = action;
        }

        public void render(MatrixStack stack, int mouseX, int mouseY, float pt) {
            boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y + 2, width, height);

            // Фон кнопки через стенил + картинка
            Stencil.initStencilToWrite();
            DisplayUtils.drawRoundedRect(x, y + 2, width, height, 5, -1);
            Stencil.readStencilBuffer(1);
            DisplayUtils.drawImage(button, x, y + 2, width, height, ColorUtils.rgb(255, 255, 255));
            Stencil.uninitStencilBuffer();

            // СЕРАЯ ОБВОДКА ПРИ НАВЕДЕНИИ
            if (hovered) {
                DisplayUtils.drawRoundedRect(x - 1f, y + 1f, width + 2f, height + 2f, 6, ColorUtils.rgba(112, 112, 112, 255));
                DisplayUtils.drawRoundedRect(x, y + 2, width, height, 5, ColorUtils.rgba(0, 0, 0, 200));
            }

            // Текст
            int textColor = hovered ? ColorUtils.rgb(255, 255, 255) : ColorUtils.rgb(180, 180, 180);
            Fonts.sfbold.drawCenteredText(stack, text, x + width / 2f, y + height / 2f - 4.5f, textColor, 10f);
        }

        public void click(int mouseX, int mouseY, int button) {
            if (MathUtil.isHovered(mouseX, mouseY, x, y + 2, width, height)) {
                action.run();
            }
        }
    }
}