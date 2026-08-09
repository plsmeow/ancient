package tech.onetap.module.list.render.hud.celestial;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.List;

public final class KeyBinds {
    private KeyBinds() {}

    private static final Animation emptyAnim = new Animation(Easing.EXPO_OUT, 233);

    public static void renderCelestial(Interface hud, DrawContext context) {
        if (hud.mc.player == null) return;

        final boolean chatOpen = hud.mc.currentScreen instanceof ChatScreen;

        List<Interface.BindRow> activeRows = hud.collectBindRows();

        boolean showPlaceholder = chatOpen && activeRows.isEmpty();
        emptyAnim.run(showPlaceholder ? 1f : 0f);
        hud.alpha.run((activeRows.isEmpty() && !chatOpen) ? 0f : 1f);

        float globalAlpha = (float) hud.alpha.getValue();
        if (globalAlpha <= 0.05f) return;

        int aInt = MathHelper.clamp((int) (255f * globalAlpha), 0, 255);
        float emptyAnimVal = MathHelper.clamp((float) emptyAnim.getValue(), 0f, 1f);

        final String placeholderText = "No active binds";
        final float fontSize = 7.5f;
        final float headerH = 14f;
        final float rowH = 9.5f;

        float targetWidth = 70f;
        for (Interface.BindRow row : activeRows) {
            String keyText = "[" + row.key() + "]";
            float rowWidth = Fonts.SFBOLD.get().getWidth(row.label(), fontSize) + Fonts.SFBOLD.get().getWidth(keyText, fontSize) + 20f;
            targetWidth = Math.max(targetWidth, rowWidth);
        }
        if (emptyAnimVal > 0.001f) {
            targetWidth = Math.max(targetWidth, Fonts.SFBOLD.get().getWidth(placeholderText, fontSize) + 14f);
        }
        hud.widthAnim.run(targetWidth);
        float curW = Math.max(70f, (float) hud.widthAnim.getValue());

        float rowsHeight = 0f;
        for (Interface.BindRow row : activeRows) {
            rowsHeight += rowH * MathHelper.clamp(row.alpha(), 0f, 1f);
        }
        rowsHeight += rowH * emptyAnimVal;
        float totalH = headerH + rowsHeight + (rowsHeight > 0f ? 3f : 1f);

        float x = hud.keyBindsDrag.getX();
        float y = hud.keyBindsDrag.getY();

        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();
        int[] orbital = ColorProvider.getOrbitalRect(t1, t2, 300.0, aInt);
        int[] glow = ColorProvider.getOrbitalRect(t1, t2, 300.0, (int) (110 * globalAlpha));
        Matrix4f m2 = context.getMatrices().peek().getPositionMatrix();

        hud.drawCelestialGlow(m2, x, y, curW, totalH, 4f, globalAlpha);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, curW + 1f, totalH + 1f, 4f, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, curW, totalH, 4f, ColorProvider.rgba(14, 10, 6, aInt));

        Builder.rectangle()
                .size(new SizeState(curW + 0.5f, headerH))
                .radius(new QuadRadiusState(4, 0, 0, 4))
                .color(new QuadColorState(orbital[0], orbital[1], orbital[2], orbital[3]))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        float headerTextX = x + (curW - Fonts.SFBOLD.get().getWidth("Keybinds", 10f)) / 2f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), "Keybinds", headerTextX, y + 1f, ColorProvider.rgba(255, 255, 255, aInt), 10f);

        float curY = y + headerH + 1f;

        for (Interface.BindRow row : activeRows) {
            float rowAnim = MathHelper.clamp(row.alpha(), 0f, 1f);
            if (rowAnim <= 0.001f) continue;

            float itemHeight = 9 * rowAnim;
            int itemAlpha = MathHelper.clamp((int) (aInt * rowAnim), 0, 255);

            if (itemAlpha >= 4) {
                float textY = curY + (itemHeight / 2f) - (fontSize / 2f) - 1;
                String key = "[" + row.key() + "]";

                DrawUtil.drawText(Fonts.SFBOLD.get(), row.label(), x + 5f, textY, ColorProvider.rgba(233, 233, 233, itemAlpha), fontSize);

                float keyX = x + curW - Fonts.SFBOLD.get().getWidth(key, fontSize) - 5f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), key, keyX, textY, ColorProvider.rgba(200, 200, 200, itemAlpha), fontSize);
            }
            curY += itemHeight;
        }

        if (emptyAnimVal > 0.001f) {
            float itemHeight = rowH * emptyAnimVal;
            int itemAlpha = MathHelper.clamp((int) (aInt * emptyAnimVal), 0, 255);

            if (itemAlpha >= 4) {
                float textY = curY + (itemHeight / 2f) - (fontSize / 2f);
                float textX = x + (curW - Fonts.SFBOLD.get().getWidth(placeholderText, fontSize)) / 2f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, textX, textY, ColorProvider.rgba(255, 205, 70, itemAlpha), fontSize);
            }
            curY += itemHeight;
        }

        hud.keyBindsDrag.setWidth(curW);
        hud.keyBindsDrag.setHeight(totalH);
    }

    public static void renderOld(Interface hud, DrawContext context) {
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
