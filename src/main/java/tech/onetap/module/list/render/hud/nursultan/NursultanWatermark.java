package tech.onetap.module.list.render.hud.nursultan;

import net.minecraft.client.gui.DrawContext;
import tech.onetap.Onetap;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.math.Counter;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.server.Server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class NursultanWatermark {
    private NursultanWatermark() {}

    public static void render(Interface hud, DrawContext context) {
        if (hud.mc.player == null) return;

        Counter.updateFPS();

        String userText = hud.mc.player.getName().getString();
        String fpsValue = Counter.getCurrentFPS() + " Fps";
        String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String coordsText = (int) hud.mc.player.getX() + " " + (int) hud.mc.player.getY() + " " + (int) hud.mc.player.getZ();
        String pingText = Server.getPing(hud.mc.player) + " Ping";
        String tpsText = String.format("%.1f Ticks", Onetap.getInstance().getTpsGetter().getTPS());
        double dX = hud.mc.player.getX() - hud.mc.player.prevX;
        double dZ = hud.mc.player.getZ() - hud.mc.player.prevZ;
        String speedText = String.format("%.1f Bps", Math.hypot(dX, dZ) * 20);

        float x = hud.watermarkDrag.getX();
        float y = hud.watermarkDrag.getY();
        float startX = x;
        float height = 15f;
        float gap = 3f;

        int sepColor = ColorProvider.rgba(255, 255, 255, 100);
        int themeColor = ColorProvider.getThemeColor();
        int themeColorTwo = ColorProvider.getThemeColorTwo();
        int whiteColor = -1;

        long time = System.currentTimeMillis();

        String alphaStr = "Ancient";
        float alphaW = Fonts.SFMEDIUM.get().getWidth(alphaStr, 7f);
        float firstBoxWidth = 17f + 10f + alphaW;

        hud.drawBackground(x, y, firstBoxWidth, height, 4, 255);

        float pulse = (float) (Math.sin(time / 200.0) * 0.3 + 0.7);
        int animatedThemeColor = ColorProvider.setAlpha(themeColor, (int)(255 * pulse));
        DrawUtil.drawText(Fonts.MOONWARD.get(), "\ue900", x + 4.2f, y + 3.1f, animatedThemeColor, 15f);
        DrawUtil.drawRound(x + 18f, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);

        float currentAlphaX = x + 22f;
        for (int i = 0; i < alphaStr.length(); i++) {
            String ch = String.valueOf(alphaStr.charAt(i));
            int charColor = colorLerp(themeColor, themeColorTwo, 8.0f, i * 0.35f);
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), ch, currentAlphaX, y + 3.5f, charColor, 7f);
            currentAlphaX += Fonts.SFMEDIUM.get().getWidth(ch, 7f);
        }

        float firstRowX = x + firstBoxWidth + gap;
        float userW = Fonts.SFMEDIUM.get().getWidth(userText, 7f);
        float fpsW = Fonts.SFMEDIUM.get().getWidth(fpsValue, 7f);
        float timeW = Fonts.SFMEDIUM.get().getWidth(timeText, 7f);
        float wCombined = 4 + 10 + userW + 5 + 1 + 5 + 10 + fpsW + 5 + 1 + 5 + 10 + timeW + 6;

        hud.drawBackground(firstRowX, y, wCombined, height, 4, 255);

        float currX = firstRowX + 4;
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0057", currX, y + 4.25f, themeColor, 8f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), userText, currX + 10, y + 3.5f, whiteColor, 7f);
        currX += 11 + userW + 5;
        DrawUtil.drawRound(currX, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        currX += 6;
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0058", currX, y + 4.25f, themeColor, 8f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), fpsValue, currX + 11, y + 3.5f, whiteColor, 7f);
        currX += 11 + fpsW + 5;
        DrawUtil.drawRound(currX, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        currX += 6;
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0056", currX, y + 4.25f, themeColor, 8f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), timeText, currX + 11, y + 3.5f, whiteColor, 7f);

        float row1Width = (firstRowX + wCombined) - startX;
        x = startX;
        y += height + gap;

        float pulse2 = (float) (Math.sin((time + 150) / 250.0) * 0.3 + 0.7);
        int animatedThemeColor2 = ColorProvider.setAlpha(themeColor, (int)(255 * pulse2));
        hud.drawBackground(x, y, 17, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0055", x + 4.5f, y + 4.25f, animatedThemeColor2, 8f);
        x += 17 + gap;

        float wCoords = 17 + Fonts.SFMEDIUM.get().getWidth(coordsText, 7f) + 4;
        hud.drawBackground(x, y, wCoords, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0046", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 13, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), coordsText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wCoords + gap;

        float wPing = 17 + Fonts.SFMEDIUM.get().getWidth(pingText, 7f) + 4;
        hud.drawBackground(x, y, wPing, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0051", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 13.5f, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), pingText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wPing + gap;

        float wTps = 17 + Fonts.SFMEDIUM.get().getWidth(tpsText, 7f) + 4;
        hud.drawBackground(x, y, wTps, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0054", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 13, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), tpsText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wTps + gap;

        float wSpeed = 20 + Fonts.SFMEDIUM.get().getWidth(speedText, 7f) + 4;
        hud.drawBackground(x, y, wSpeed, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0040", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 15, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), speedText, x + 20, y + 3.5f, whiteColor, 7f);

        float row2Width = (x + wSpeed) - startX;

        hud.watermarkDrag.setWidth(Math.max(row1Width, row2Width));
        hud.watermarkDrag.setHeight((height * 2) + gap);
    }

    private static int colorLerp(int start, int end, float speed, float offset) {
        long t = System.currentTimeMillis();
        double ph = t * (speed / 1000.0) + offset;
        float p = (float) (Math.sin(ph) * 0.5 + 0.5);

        int sr = (start >> 16) & 0xFF;
        int sg = (start >> 8) & 0xFF;
        int sb = start & 0xFF;
        int er = (end >> 16) & 0xFF;
        int eg = (end >> 8) & 0xFF;
        int eb = end & 0xFF;

        int r = (int) (sr * (1f - p) + er * p);
        int g = (int) (sg * (1f - p) + eg * p);
        int b = (int) (sb * (1f - p) + eb * p);

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
