package tech.onetap.module.list.render.hud.celestial;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.math.Counter;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Watermark {
    private Watermark() {}

    public static void renderCelestial(Interface hud, DrawContext context) {
        if (hud.mc.player == null) return;

        Counter.updateFPS();

        String userText = hud.mc.player.getName().getString();
        String uid = "UID: 1337";
        String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));

        String fullText = "Ancient | " + userText + " | " + uid + " | " + timeText;
        float fontSize = 7.5f;

        float x = hud.watermarkDrag.getX();
        float y = hud.watermarkDrag.getY();
        float height = 14f;
        float width = Fonts.SFBOLD.get().getWidth(fullText, fontSize) + 12f;

        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();
        int[] glow = ColorProvider.getOrbitalRect(t1, t2, 300.0, 110);
        int[] orbital = ColorProvider.getOrbitalRect(t1, t2, 300.0, 255);
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();

        hud.drawCelestialGlow(m, x, y, width, height, 4f, 1.0f);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, width + 1f, height + 1f, 4f, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, width, height, 4f, ColorProvider.rgba(14, 10, 6, 255));

        float textY = y + (height - fontSize) / 2f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), fullText, x + 3f, textY, ColorProvider.rgba(255, 255, 255, 255), fontSize);

        hud.watermarkDrag.setWidth(width);
        hud.watermarkDrag.setHeight(height);
    }

    public static void renderOld(Interface hud, DrawContext context) {
        if (hud.mc.player == null) return;

        float posX = hud.watermarkDrag.getX();
        float posY = hud.watermarkDrag.getY();
        float fontSize = 7.5f;
        var font = Fonts.SFBOLD.get();

        int fps = hud.mc.getCurrentFps();
        int ping = 0;
        if (hud.mc.getNetworkHandler() != null && hud.mc.getNetworkHandler().getPlayerListEntry(hud.mc.player.getUuid()) != null) {
            ping = hud.mc.getNetworkHandler().getPlayerListEntry(hud.mc.player.getUuid()).getLatency();
        }

        String name = hud.mc.player.getName().getString();
        String coords = String.format("%d %d %d", (int) hud.mc.player.getX(), (int) hud.mc.player.getY(), (int) hud.mc.player.getZ());
        String text = String.format("Ancient | %s | %d fps | %d ms | %s", name, fps, ping, coords);

        DrawUtil.drawText(font, text, posX, posY, ColorProvider.getThemeColor(), fontSize);

        float width = font.getWidth(text, fontSize);
        hud.watermarkDrag.setWidth(width);
        hud.watermarkDrag.setHeight(fontSize + 2f);
    }
}
