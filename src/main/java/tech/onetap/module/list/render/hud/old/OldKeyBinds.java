package tech.onetap.module.list.render.hud.old;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.List;

public final class OldKeyBinds {
    private OldKeyBinds() {}

    public static void render(Interface hud, DrawContext context) {
        List<Interface.BindRow> activeRows = hud.collectBindRows();
        if (activeRows.isEmpty() && !(hud.mc.currentScreen instanceof ChatScreen)) return;

        float posX = hud.keyBindsDrag.getX();
        float posY = hud.keyBindsDrag.getY();
        float fontSize = 7.5f;
        var font = Fonts.SFMEDIUM.get();

        DrawUtil.drawText(font, "[ Keybinds ]", posX, posY, ColorProvider.getThemeColor(), fontSize);
        posY += 10f;

        float maxWidth = font.getWidth("[ Keybinds ]", fontSize);

        for (Interface.BindRow row : activeRows) {
            String text = row.label() + " -> " + row.key();
            DrawUtil.drawText(font, text, posX, posY, ColorProvider.rgba(255, 255, 255, 255), fontSize);

            float rowWidth = font.getWidth(text, fontSize);
            if (rowWidth > maxWidth) maxWidth = rowWidth;

            posY += 9f;
        }

        if (activeRows.isEmpty() && hud.mc.currentScreen instanceof ChatScreen) {
            DrawUtil.drawText(font, "No active binds", posX, posY, ColorProvider.rgba(150, 150, 150, 255), fontSize);
            posY += 9f;
        }

        hud.keyBindsDrag.setWidth(maxWidth);
        hud.keyBindsDrag.setHeight(posY - hud.keyBindsDrag.getY());
    }
}
