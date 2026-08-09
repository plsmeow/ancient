package tech.onetap.module.list.render.hud.nursultan;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.Comparator;

public final class NursultanPotions {
    private NursultanPotions() {}

    private static final Animation xLine = new Animation(Easing.EXPO_OUT, 170);

    public static void render(Interface hud, DrawContext context) {
        if (hud.mc.player == null) return;

        float posX = hud.potionsDrag.getX();
        float posY = hud.potionsDrag.getY();

        float headerIconW = Fonts.ICONS_NURIK.get().getWidth("E", 8);
        float headerTextW = Fonts.SFMEDIUM.get().getWidth("Active Potions", 7.5f);
        float defaultWidth = headerIconW + headerTextW + 30;

        float height = 14.5f;

        hud.potionItems.sort(Comparator.comparing(pi -> pi.name));

        boolean isFound = false;

        for (Interface.PotionItem item : hud.potionItems) {
            item.animation.run(item.active ? 1 : 0);
            if (item.animation.getValue() > 0.001f) {
                isFound = true;
            }
            int seconds = item.durationTicks / 20;
            int minutes = seconds / 60;
            int sec = seconds % 60;
            String duration = String.format("%d:%02d", minutes, sec);

            float nameW = Fonts.SFREGULAR.get().getWidth(item.name, 6.5f);
            float ampW = (item.amplifier >= 1 ? Fonts.SFREGULAR.get().getWidth(" " + (item.amplifier + 1), 6.5f) : 0);
            float timeW = Fonts.SFREGULAR.get().getWidth(duration, 6.5f);

            float moduleWidth = nameW + ampW + timeW + 45;

            if (moduleWidth > defaultWidth) {
                defaultWidth = moduleWidth;
            }
        }

        if (!isFound && !(hud.mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) hud.alpha3.run(0);
        else hud.alpha3.run(1);

        float globalAlpha = (float) hud.alpha3.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        hud.widthAnim3.run(defaultWidth);

        float currentWidth = Math.max(20, (float) hud.widthAnim3.getValue());

        hud.drawBackground(posX, posY, currentWidth - 3, height, 3, headerAlpha);

        DrawUtil.drawRound(posX + 13.75f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(88, 88, 88, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "E", posX + 4, posY + 3.75f, ColorProvider.rgba(255, 255, 255, headerAlpha), 8);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Active Potions", posX + 18f, posY + 3.25f, ColorProvider.rgba(255, 255, 255, headerAlpha), 7.5f);

        posY += 14.5f;

        xLine.run(12);

        for (Interface.PotionItem item : hud.potionItems) {
            float animVal = (float) item.animation.getValue();
            if (animVal <= 0.001f) continue;

            float heightFactor = Math.min(1.0f, animVal);
            float itemHeight = 12 * heightFactor;
            height += itemHeight;

            float alphaFactor = Math.min(1.0f, Math.max(0.0f, animVal));
            int itemAlpha = (int) (255 * alphaFactor * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String moduleName = item.name;
            int seconds = item.durationTicks / 20;
            int minutes = seconds / 60;
            int sec = seconds % 60;
            String bind = String.format("%d:%02d", minutes, sec);

            float textYOffset = (itemHeight / 2f) - (3f);

            hud.drawBackground(posX, posY, currentWidth - 3, itemHeight, 3, itemAlpha);

            float separatorX = (float) (posX + currentWidth - 6.5f - xLine.getValue());

            DrawUtil.drawRound(separatorX, posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(88, 88, 88, itemAlpha));

            DrawUtil.drawText(Fonts.SFREGULAR.get(), moduleName, posX + 4, posY + textYOffset - 0.5f, ColorProvider.rgba(255, 255, 255, itemAlpha), 6.5f);

            if (item.amplifier >= 1) {
                DrawUtil.drawText(Fonts.SFREGULAR.get(), String.valueOf(item.amplifier + 1),
                        posX + 6 + Fonts.SFREGULAR.get().getWidth(moduleName, 6.75f),
                        posY + textYOffset - 0.5f,
                        ColorProvider.setAlpha(ColorProvider.rgba(211, 211, 211, 255), itemAlpha), 6.5f);
            }

            float timeWidth = Fonts.SFREGULAR.get().getWidth(bind, 6.75f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), bind, separatorX - timeWidth - 3f, posY + textYOffset - 0.5f, ColorProvider.rgba(255, 255, 255, itemAlpha), 6.5f);

            Sprite sprite = hud.mc.getStatusEffectSpriteManager().getSprite(item.effect);
            if (sprite != null) {
                RenderSystem.setShaderColor(1f, 1f, 1f, (itemAlpha / 255f));
                float iconSize = 9;
                float iconX = separatorX + 3.5f;
                float iconY = posY + (itemHeight - iconSize) / 2f;

                int color = (itemAlpha << 24) | 0xFFFFFF;

                context.drawSpriteStretched(
                        net.minecraft.client.render.RenderLayer::getGuiTextured,
                        sprite,
                        (int) iconX,
                        (int) iconY,
                        (int) iconSize,
                        (int) iconSize,
                        color
                );
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            posY += itemHeight;
        }

        hud.widthAnim3.run(defaultWidth);
        hud.potionsDrag.setWidth((float) hud.widthAnim3.getValue());
        hud.potionsDrag.setHeight(height);
    }
}
