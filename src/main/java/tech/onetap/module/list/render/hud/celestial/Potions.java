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
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Potions {
    private Potions() {}

    private static final Animation emptyAnim = new Animation(Easing.EXPO_OUT, 233);

    public static void renderCelestial(Interface hud, DrawContext context) {
        if (hud.mc.player == null) return;

        final boolean chatOpen = hud.mc.currentScreen instanceof ChatScreen;

        hud.potionItems.sort(Comparator.comparing(pi -> pi.name));

        List<Interface.PotionItem> visible = new ArrayList<>();
        for (Interface.PotionItem item : hud.potionItems) {
            item.animation.run(item.active ? 1f : 0f);
            if (item.animation.getValue() > 0.01f) visible.add(item);
        }

        boolean showPlaceholder = chatOpen && visible.isEmpty();
        emptyAnim.run(showPlaceholder ? 1f : 0f);
        hud.alpha3.run((visible.isEmpty() && !chatOpen) ? 0f : 1f);

        float globalAlpha = (float) hud.alpha3.getValue();
        if (globalAlpha <= 0.05f) return;

        int aInt = MathHelper.clamp((int) (255f * globalAlpha), 0, 255);
        float emptyAnimVal = MathHelper.clamp((float) emptyAnim.getValue(), 0f, 1f);

        final String headerText = "Potions";
        final String placeholderText = "No active effects";

        final float fontSize = 7.5f;
        final float headerH = 14f;
        final float rowH = 9.5f;
        final float padL = 5f;
        final float padR = 5f;

        float targetWidth = 70f;

        for (Interface.PotionItem item : visible) {
            int totalSec = Math.max(0, item.durationTicks / 20);
            int minutes = totalSec / 60;
            int sec = totalSec % 60;
            String time = String.format("%d:%02d", minutes, sec);

            int lvl = item.amplifier + 1;
            String lvlText = "   " + lvl;

            float nameW = Fonts.SFBOLD.get().getWidth(item.name, fontSize);
            float lvlW = Fonts.SFBOLD.get().getWidth(lvlText, fontSize);
            float timeW = Fonts.SFBOLD.get().getWidth(time, fontSize);

            float rowW = padL + nameW + lvlW + 10f + timeW + padR;
            targetWidth = Math.max(targetWidth, rowW);
        }

        if (emptyAnimVal > 0.001f) {
            targetWidth = Math.max(targetWidth, Fonts.SFBOLD.get().getWidth(placeholderText, fontSize) + 14f);
        }

        hud.widthAnim3.run(targetWidth);
        float curW = Math.max(70f, (float) hud.widthAnim3.getValue());

        float rowsHeight = 0f;
        for (Interface.PotionItem item : visible) {
            rowsHeight += rowH * MathHelper.clamp((float) item.animation.getValue(), 0f, 1f);
        }
        rowsHeight += rowH * emptyAnimVal;

        float totalH = headerH + rowsHeight + (rowsHeight > 0f ? 3f : 1f);

        float x = hud.potionsDrag.getX();
        float y = hud.potionsDrag.getY();

        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();
        int[] orbital = ColorProvider.getOrbitalRect(t1, t2, 300.0, aInt);
        int[] glow = ColorProvider.getOrbitalRect(t1, t2, 300.0, (int) (110 * globalAlpha));
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();

        hud.drawCelestialGlow(m, x, y, curW, totalH, 4f, globalAlpha);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, curW + 1f, totalH + 1f, 4f, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, curW, totalH, 4f, ColorProvider.rgba(14, 10, 6, aInt));

        Builder.rectangle()
                .size(new SizeState(curW + 0.5f, headerH))
                .radius(new QuadRadiusState(4, 0, 0, 4))
                .color(new QuadColorState(orbital[0], orbital[1], orbital[2], orbital[3]))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        float headerTextX = x + (curW - Fonts.SFBOLD.get().getWidth(headerText, 10f)) / 2f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), headerText, headerTextX, y + 1f,
                ColorProvider.rgba(255, 255, 255, aInt), 10f);

        float curY = y + headerH + 1f;

        for (Interface.PotionItem item : visible) {
            float rowAnim = MathHelper.clamp((float) item.animation.getValue(), 0f, 1f);
            if (rowAnim <= 0.001f) continue;

            float itemH = rowH * rowAnim;
            int itemA = MathHelper.clamp((int) (aInt * rowAnim), 0, 255);

            if (itemA >= 4) {
                int totalSec = Math.max(0, item.durationTicks / 20);
                int minutes = totalSec / 60;
                int sec = totalSec % 60;
                String time = String.format("%d:%02d", minutes, sec);

                int lvl = item.amplifier + 1;
                String lvlText = "   " + lvl;

                float timeW = Fonts.SFBOLD.get().getWidth(time, fontSize);
                float timeX = x + curW - timeW - padR;

                float leftX = x + padL;
                float textY = curY + (itemH / 2f) - (fontSize / 2f) - 1f;

                float clipW = Math.max(0f, (timeX - 6f) - leftX);
                Scissor.push();
                Scissor.setFromComponentCoordinates(leftX, curY, clipW, itemH);

                DrawUtil.drawText(Fonts.SFBOLD.get(), item.name, leftX, textY,
                        ColorProvider.rgba(233, 233, 233, itemA), fontSize);

                float nameW = Fonts.SFBOLD.get().getWidth(item.name, fontSize);

                int lvlColor = (lvl >= 2)
                        ? ColorProvider.rgba(192, 100, 106, itemA)
                        : ColorProvider.rgba(200, 200, 200, itemA);
                if (lvl > 1) {
                    DrawUtil.drawText(Fonts.SFBOLD.get(), lvlText, leftX + nameW, textY, lvlColor, fontSize);
                }

                Scissor.unset();
                Scissor.pop();

                DrawUtil.drawText(Fonts.SFBOLD.get(), time, timeX, textY,
                        ColorProvider.rgba(200, 200, 200, itemA), fontSize);
            }

            curY += itemH;
        }

        if (emptyAnimVal > 0.001f) {
            float itemH = rowH * emptyAnimVal;
            int itemA = MathHelper.clamp((int) (aInt * emptyAnimVal), 0, 255);

            if (itemA >= 4) {
                float textY = curY + (itemH / 2f) - (fontSize / 2f);
                float textX = x + (curW - Fonts.SFBOLD.get().getWidth(placeholderText, fontSize)) / 2f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, textX, textY,
                        ColorProvider.rgba(255, 205, 70, itemA), fontSize);
            }

            curY += itemH;
        }

        hud.potionsDrag.setWidth(curW);
        hud.potionsDrag.setHeight(totalH);
    }

    public static void renderOld(Interface hud, DrawContext context) {
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
