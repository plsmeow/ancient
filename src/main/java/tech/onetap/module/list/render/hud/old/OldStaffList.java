package tech.onetap.module.list.render.hud.old;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.List;

public final class OldStaffList {
    private OldStaffList() {}

    public static void render(Interface hud, DrawContext context) {
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

            MutableText fullRowText = Text.empty().append(staff.prefix);
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
