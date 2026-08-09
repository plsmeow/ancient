package tech.onetap.module.list.render.hud.nursultan;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.List;

public final class NursultanKeyBinds {
    private NursultanKeyBinds() {}

    private static final Animation xLine = new Animation(Easing.EXPO_OUT, 170);

    public static void render(Interface hud, DrawContext context) {
        if (hud.mc.player == null) return;

        float posX = hud.keyBindsDrag.getX();
        float posY = hud.keyBindsDrag.getY();

        float defaultWidth = 55;
        float height = 14.5f;

        List<Interface.BindRow> allRows = hud.collectBindRows();

        boolean isFound = !allRows.isEmpty();
        if (isFound) hud.alpha.run(1);
        else if (!(hud.mc.currentScreen instanceof ChatScreen)) hud.alpha.run(0);
        if (hud.mc.currentScreen instanceof ChatScreen) hud.alpha.run(1);

        float globalAlpha = (float) hud.alpha.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        hud.drawBackground(posX, posY, (float) hud.widthAnim.getValue(), height, 3, headerAlpha);

        DrawUtil.drawRound(posX + 15.25f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(125, 125, 125, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "C", posX + 4f, posY + 4f, ColorProvider.rgba(255, 255, 255, headerAlpha), 8);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Hotkeys", posX + 19.5f, posY + 3.25f, ColorProvider.rgba(255, 255, 255, headerAlpha), 7.5f);

        posY += 14.5f;
        float bindWidth = 0;

        for (Interface.BindRow row : allRows) {
            float localBindWidth = Fonts.SFREGULAR.get().getWidth(row.key(), 6.75f);
            if (localBindWidth > bindWidth) {
                bindWidth = localBindWidth;
            }
        }

        xLine.run(bindWidth);

        for (Interface.BindRow row : allRows) {
            float rowAlpha = MathHelper.clamp(row.alpha(), 0f, 1f);
            if (rowAlpha <= 0.001f) continue;

            float itemHeight = 12 * Math.min(1.0f, rowAlpha);
            height += itemHeight;

            int itemAlpha = (int) (255 * rowAlpha * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String bind = row.key();
            String label = row.label();
            float elementsWidth = Fonts.SFREGULAR.get().getWidth(label, 6.75f) + Fonts.SFREGULAR.get().getWidth(bind, 6.75f) + 30;

            float textYOffset = (itemHeight / 2f) - 4f;

            hud.drawBackground(posX, posY, (float) hud.widthAnim.getValue(), itemHeight, 3, itemAlpha);

            float separatorX = (float) (posX + hud.widthAnim.getValue() - 6.5f - xLine.getValue());
            DrawUtil.drawRound(separatorX, posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(125, 125, 125, itemAlpha));

            DrawUtil.drawText(Fonts.SFREGULAR.get(), label, posX + 4.25f, posY + textYOffset, ColorProvider.rgba(255, 255, 255, itemAlpha), 6.5f);

            float bindX = (float) (posX + hud.widthAnim.getValue() - 2.5f - xLine.getValue() - Fonts.SFREGULAR.get().getWidth(bind, 6.75f) / 2 + xLine.getValue() / 2 - 0.25f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), bind, bindX, posY + textYOffset, ColorProvider.rgba(255, 255, 255, itemAlpha), 6.5f);

            if (elementsWidth > defaultWidth) {
                defaultWidth = elementsWidth;
            }

            posY += itemHeight;
        }

        hud.widthAnim.run(defaultWidth);
        hud.keyBindsDrag.setWidth((float) hud.widthAnim.getValue());
        hud.keyBindsDrag.setHeight(height);
    }
}
