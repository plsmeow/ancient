package tech.onetap.ui.mainmenu;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public class MenuButton {

    @Getter
    private final float x, y, width, height;
    private final String text;
    private final Runnable action;

    private final Animation hoverAnimation = new Animation(Easing.QUINTIC_OUT, 250);

    public MenuButton(float x, float y, float width, float height, String text, Runnable action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.action = action;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = HoverUtil.isHovered(mouseX, mouseY, x, y + 2, width, height);

        hoverAnimation.run(hovered ? 1f : 0f);
        float hover = hoverAnimation.getValue();

        DrawUtil.drawRound(x, y + 2, width, height, 4,
                ColorProvider.rgba(28, 28, 34, (int) (200 + 40 * hover)),
                ColorProvider.rgba(18, 18, 24, (int) (200 + 40 * hover)));

        if (hover > 0.01f) {
            DrawUtil.drawRound(x - 1f, y + 1f, width + 2f, height + 2f, 5,
                    ColorProvider.rgba(140, 140, 150, (int) (180 * hover)));
            DrawUtil.drawRound(x, y + 2, width, height, 4,
                    ColorProvider.rgba(0, 0, 0, (int) (160 * hover)));
        }

        int textColor = ColorProvider.interpolate(
                ColorProvider.rgb(180, 180, 180),
                ColorProvider.rgb(255, 255, 255),
                hover
        );
        float textSize = 8.5f;
        float textWidth = Fonts.SFBOLD.get().getWidth(text, textSize);
        DrawUtil.drawText(Fonts.SFBOLD.get(), text,
                x + width / 2f - textWidth / 2f,
                y + height / 2f - 3.5f,
                textColor, textSize);
    }

    public void click(int mouseX, int mouseY, int button) {
        if (HoverUtil.isHovered(mouseX, mouseY, x, y + 2, width, height)) {
            action.run();
        }
    }
}
