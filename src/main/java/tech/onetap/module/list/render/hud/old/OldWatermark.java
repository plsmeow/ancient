package tech.onetap.module.list.render.hud.old;

import net.minecraft.client.gui.DrawContext;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public final class OldWatermark {
    private OldWatermark() {}

    public static void render(Interface hud, DrawContext context) {
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
