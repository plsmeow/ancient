package tech.onetap.module.list.render.hud.old;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.Comparator;
import java.util.List;

public final class OldPotions {
    private OldPotions() {}

    public static void render(Interface hud, DrawContext context) {
        hud.potionItems.sort(Comparator.comparing(pi -> pi.name));
        List<Interface.PotionItem> visible = hud.potionItems.stream().filter(pi -> pi.active).toList();
        if (visible.isEmpty() && !(hud.mc.currentScreen instanceof ChatScreen)) return;

        float posX = hud.potionsDrag.getX();
        float posY = hud.potionsDrag.getY();
        float fontSize = 7.5f;
        var font = Fonts.SFMEDIUM.get();

        DrawUtil.drawText(font, "[ Potions ]", posX, posY, ColorProvider.getThemeColor(), fontSize);
        posY += 10f;

        float maxWidth = font.getWidth("[ Potions ]", fontSize);

        for (Interface.PotionItem item : visible) {
            int totalSec = Math.max(0, item.durationTicks / 20);
            String time = String.format("%d:%02d", totalSec / 60, totalSec % 60);
            String text = item.name + " " + (item.amplifier + 1) + " -> " + time;

            DrawUtil.drawText(font, text, posX, posY, ColorProvider.rgba(235, 235, 235, 255), fontSize);

            float rowWidth = font.getWidth(text, fontSize);
            if (rowWidth > maxWidth) maxWidth = rowWidth;

            posY += 9f;
        }

        hud.potionsDrag.setWidth(maxWidth);
        hud.potionsDrag.setHeight(posY - hud.potionsDrag.getY());
    }
}
