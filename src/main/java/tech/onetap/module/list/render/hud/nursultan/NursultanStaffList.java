package tech.onetap.module.list.render.hud.nursultan;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.render.renderers.impl.BuiltTexture;
import tech.onetap.util.render.msdf.Fonts;

public final class NursultanStaffList {
    private NursultanStaffList() {}

    public static void render(Interface hud, DrawContext context) {
        float posX = hud.staffListDrag.getX();
        float posY = hud.staffListDrag.getY();

        float defaultWidth = 64;
        float height = 14.5f;

        boolean isFound = false;
        if (!hud.staffPlayers.isEmpty()) {
            hud.alpha2.run(1);
            isFound = true;
        }

        if (!isFound && !(hud.mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) hud.alpha2.run(0);
        if (hud.mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) hud.alpha2.run(1);

        float globalAlpha = (float) hud.alpha2.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        hud.drawBackground(posX, posY, (float) hud.widthAnim2.getValue(), 14.5f, 3, headerAlpha);

        DrawUtil.drawRound(posX + 15.25f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(88, 88, 88, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "O", posX + 4.25f, posY + 4.5f, ColorProvider.setAlpha(-1, headerAlpha), 8.5f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Staff Online", posX + 19.5f, posY + 3.25f, ColorProvider.rgba(255, 255, 255, headerAlpha), 7.5f);

        posY += 14.5f;
        float bindWidth = 0;

        float headOffset = 12f;

        for (Interface.Staff staff : hud.staffPlayers) {
            staff.animation.run(staff.isOnServer ? 1 : 0);
            float localBindWidth = headOffset + Fonts.SFREGULAR.get().getWidth(staff.prefix, 6.75f) + Fonts.SFREGULAR.get().getWidth(staff.status.string, 6.75f);
            if (localBindWidth > bindWidth) {
                bindWidth = localBindWidth;
            }
        }

        for (Interface.Staff staff : hud.staffPlayers) {
            float animVal = (float) staff.animation.getValue();
            if (animVal <= 0.001f) continue;

            float heightFactor = Math.min(1.0f, animVal);
            float itemHeight = 11 * heightFactor;
            height += itemHeight;

            float alphaFactor = Math.min(1.0f, Math.max(0.0f, animVal));
            int itemAlpha = (int) (255 * alphaFactor * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String name = staff.name;
            Text prefix = staff.prefix;

            float elementsWidth = headOffset + Fonts.SFREGULAR.get().getWidth(prefix, 6.75f) + 15;
            float textYOffset = (itemHeight / 2f) - (3f);

            hud.drawBackground(posX, posY, (float) hud.widthAnim2.getValue(), itemHeight, 3, itemAlpha);

            DrawUtil.drawRound((float) (posX + hud.widthAnim2.getValue() - 11.25f), posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(125, 125, 125, itemAlpha));

            float headSize = 8f;
            float headX = posX + 3f;
            float headY = posY + textYOffset - 1f;

            Identifier skinTexture;
            PlayerListEntry playerEntry = hud.mc.getNetworkHandler().getPlayerListEntry(name);
            if (playerEntry != null) {
                skinTexture = playerEntry.getSkinTextures().texture();
            } else {
                skinTexture = DefaultSkinHelper.getTexture();
            }

            int textureId = hud.mc.getTextureManager().getTexture(skinTexture).getGlId();

            BuiltTexture headBuilt = Builder.texture()
                    .size(new SizeState(headSize, headSize))
                    .radius(new QuadRadiusState(2))
                    .color(new QuadColorState(ColorProvider.setAlpha(-1, itemAlpha)))
                    .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, textureId)
                    .smoothness(1f)
                    .build();

            headBuilt.render(context.getMatrices().peek().getPositionMatrix(), headX, headY);

            DrawUtil.drawText(Fonts.SFMEDIUM.get(), prefix, posX + 2f + headOffset, posY + textYOffset - 0.5f, 6.5f, itemAlpha);

            DrawUtil.drawRound((float) (posX + hud.widthAnim2.getValue() - 8), posY + textYOffset + 1f, 5, 5, 2,
                    staff.status == Interface.Status.NONE ? ColorProvider.rgba(32, 255, 32, itemAlpha) : ColorProvider.rgba(255, 32, 32, itemAlpha));

            if (elementsWidth > defaultWidth) {
                defaultWidth = elementsWidth;
            }

            posY += itemHeight;
        }

        hud.widthAnim2.run(defaultWidth);
        hud.staffListDrag.setWidth((float) hud.widthAnim2.getValue());
        hud.staffListDrag.setHeight(height);
    }
}
