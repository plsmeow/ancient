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

public final class StaffList {
    private StaffList() {}

    private static final Animation emptyAnim = new Animation(Easing.EXPO_OUT, 233);

    public static void renderCelestial(Interface hud, DrawContext context) {
        final boolean chatOpen = hud.mc.currentScreen instanceof ChatScreen;

        for (Interface.Staff staff : hud.staffPlayers) {
            staff.animation.run(staff.isOnServer ? 1 : 0);
        }

        List<Interface.Staff> activeStaff = hud.staffPlayers.stream()
                .filter(s -> s.animation.getValue() > 0.01f)
                .toList();

        boolean showPlaceholder = chatOpen && activeStaff.isEmpty();
        emptyAnim.run(showPlaceholder ? 1f : 0f);
        hud.alpha2.run((activeStaff.isEmpty() && !chatOpen) ? 0f : 1f);

        float globalAlpha = (float) hud.alpha2.getValue();
        if (globalAlpha <= 0.05f) return;

        int aInt = MathHelper.clamp((int) (255f * globalAlpha), 0, 255);
        float emptyAnimVal = MathHelper.clamp((float) emptyAnim.getValue(), 0f, 1f);

        final String headerText = "Staff Online";
        final String placeholderText = "No active staff";

        final float fontSize = 7.5f;
        final float headerH = 14f;
        final float rowH = 9.5f;
        final float padL = 5f;
        final float padR = 5f;

        float targetWidth = 70f;

        for (Interface.Staff staff : activeStaff) {
            float prefixW = Fonts.SFBOLD.get().getWidth(staff.prefix, fontSize);
            float nameW = Fonts.SFBOLD.get().getWidth(" " + staff.name, fontSize);

            float rowWidth = padL + prefixW + nameW + 14f + padR;
            targetWidth = Math.max(targetWidth, rowWidth);
        }

        if (emptyAnimVal > 0.001f) {
            targetWidth = Math.max(targetWidth, Fonts.SFBOLD.get().getWidth(placeholderText, fontSize) + 14f);
        }

        hud.widthAnim2.run(targetWidth);
        float curW = Math.max(70f, (float) hud.widthAnim2.getValue());

        float rowsHeight = (float) activeStaff.stream()
                .mapToDouble(s -> rowH * MathHelper.clamp((float) s.animation.getValue(), 0f, 1f))
                .sum();
        rowsHeight += rowH * emptyAnimVal;

        float totalH = headerH + rowsHeight + (rowsHeight > 0f ? 3f : 1f);

        float x = hud.staffListDrag.getX();
        float y = hud.staffListDrag.getY();

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

        for (Interface.Staff staff : activeStaff) {
            float rowAnim = MathHelper.clamp((float) staff.animation.getValue(), 0f, 1f);
            if (rowAnim <= 0.001f) continue;

            float itemH = rowH * rowAnim;
            int itemA = MathHelper.clamp((int) (aInt * rowAnim), 0, 255);

            if (itemA >= 4) {
                float textY = curY + (itemH / 2f) - (fontSize / 2f) - 1f;

                int nameColor;
                if (staff.status == Interface.Status.VANISHED || staff.isSpec) nameColor = ColorProvider.rgba(255, 50, 50, itemA);
                else if (hud.mc.world != null && hud.mc.world.getPlayers().stream()
                        .anyMatch(p -> p.getName().getString().equals(staff.name))) nameColor = ColorProvider.rgba(255, 215, 0, itemA);
                else nameColor = ColorProvider.rgba(220, 220, 220, itemA);

                DrawUtil.drawText(Fonts.SFBOLD.get(), staff.prefix, x + padL, textY, fontSize, itemA);

                int statusColor;
                if (staff.status == Interface.Status.VANISHED || staff.isSpec) statusColor = ColorProvider.rgba(255, 50, 50, itemA);
                else if (hud.mc.world != null && hud.mc.world.getPlayers().stream()
                        .anyMatch(p -> p.getName().getString().equals(staff.name))) statusColor = ColorProvider.rgba(255, 215, 0, itemA);
                else statusColor = ColorProvider.rgba(50, 255, 50, itemA);

                float r = 2.5f;
                float cx = x + curW - padR - (r * 2f);
                float cy = curY + (itemH / 2f) - r;

                DrawUtil.drawRound(cx, cy, r * 2f, r * 2f, r, statusColor);
            }

            curY += itemH;
        }

        if (emptyAnimVal > 0.001f) {
            float itemH = rowH * emptyAnimVal;
            int itemA = MathHelper.clamp((int) (aInt * emptyAnimVal), 0, 255);

            if (itemA >= 4) {
                float textY = curY + (itemH / 2f) - (fontSize / 2f) - 1;
                float textX = x + (curW - Fonts.SFBOLD.get().getWidth(placeholderText, fontSize)) / 2f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, textX, textY,
                        ColorProvider.rgba(255, 205, 70, itemA), fontSize);
            }

            curY += itemH;
        }

        hud.staffListDrag.setWidth(curW);
        hud.staffListDrag.setHeight(totalH);
    }

    public static void renderOld(Interface hud, DrawContext context) {
        final boolean chatOpen = hud.mc.currentScreen instanceof ChatScreen;

        List<Interface.Staff> activeStaff = hud.staffPlayers.stream()
                .filter(s -> s.isOnServer)
                .toList();

        boolean hasStaff = !activeStaff.isEmpty();
        boolean showPlaceholder = chatOpen && !hasStaff;

        final String headerText = "[ Staff ]";
        final String placeholderText = "No active staff";
        final float fontSize = 7.5f;

        float posX = hud.staffListDrag.getX();
        float posY = hud.staffListDrag.getY();

        DrawUtil.drawText(Fonts.SFBOLD.get(), headerText, posX, posY, ColorProvider.getThemeColor(), fontSize);
        float startY = posY;
        posY += 10f;

        float maxWidth = Fonts.SFBOLD.get().getWidth(headerText, fontSize);

        for (Interface.Staff staff : activeStaff) {
            int nameColor;
            if (staff.status == Interface.Status.VANISHED || staff.isSpec) {
                nameColor = 0xFF5050;
            } else if (hud.mc.world != null && hud.mc.world.getPlayers().stream()
                    .anyMatch(p -> p.getName().getString().equals(staff.name))) {
                nameColor = 0xFFD700;
            } else {
                nameColor = 0xDCDCDC;
            }

            var fullRowText = net.minecraft.text.Text.empty().append(staff.prefix);
            fullRowText.setStyle(fullRowText.getStyle().withColor(nameColor));

            context.getMatrices().push();
            context.getMatrices().translate(posX, posY, 0);
            float scale = fontSize / 9.0f;
            context.getMatrices().scale(scale, scale, 1f);

            context.drawText(hud.mc.textRenderer, fullRowText, 0, 0, -1, true);

            context.getMatrices().pop();

            float rowWidth = hud.mc.textRenderer.getWidth(fullRowText) * scale;
            if (rowWidth > maxWidth) maxWidth = rowWidth;

            posY += 9f;
        }

        if (showPlaceholder) {
            DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, posX, posY, ColorProvider.rgba(255, 205, 70, 255), fontSize);
            float rowWidth = Fonts.SFBOLD.get().getWidth(placeholderText, fontSize);
            if (rowWidth > maxWidth) maxWidth = rowWidth;
            posY += 9f;
        }

        hud.staffListDrag.setWidth(maxWidth);
        hud.staffListDrag.setHeight(posY - startY);
    }
}
