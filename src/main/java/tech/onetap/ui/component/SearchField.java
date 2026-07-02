package tech.onetap.ui.component;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public class SearchField {
    private float x, y, width, height;
    public String text = "";
    private boolean isFocused;
    private final String placeholder;

    public SearchField(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        if (HoverUtil.isHovered(mouseX, mouseY, x, y, width, height)) {
            CursorManager.requestIBeam();
        }

        // Avalora Style: Блюр, тонкая обводка и фон
        DrawUtil.drawRoundBlur(x, y, width, height, 4, ColorProvider.rgba(9, 0, 17, 200), 10);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, width + 1, height + 1, 4.5f, ColorProvider.rgba(80, 80, 85, 100)); // Обводка
        DrawUtil.drawRound(x, y, width, height, 4, ColorProvider.rgba(20, 20, 25, 210)); // Фон

        String textToDraw = text.isEmpty() && !isFocused ? placeholder : text;
        String cursor = isFocused && System.currentTimeMillis() % 1000 > 500 ? "_" : "";

        DrawUtil.drawText(Fonts.SFREGULAR.get(), textToDraw + cursor, x + 6, y + (height / 2f) - 3f,
                text.isEmpty() && !isFocused ? ColorProvider.rgba(150, 150, 150, 255) : ColorProvider.rgba(255, 255, 255, 255), 7.5f);
    }

    public void charTyped(char codePoint, int modifiers) {
        if (isFocused && text.length() < 15) {
            text += codePoint;
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isFocused = false;
            }
        }
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        isFocused = HoverUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }
}