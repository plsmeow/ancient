package tech.onetap.util.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;

/**
 * GUI scale push/pop ported from DeltaClient (aethereal.render.ScaleUtil).
 * The Delta UI renders at a fixed GUI scale of 2 regardless of the user setting.
 * Requires the Window.setScaleFactor access-widener entry.
 */
public final class ScaleUtil {
    private ScaleUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void pushScale2(DrawContext context) {
        Window window = MinecraftClient.getInstance().getWindow();
        double previous = window.getScaleFactor();
        double target = window.calculateScaleFactor(2, MinecraftClient.getInstance().forcesUnicodeFont());
        window.setScaleFactor(target);
        context.getMatrices().push();
        context.getMatrices().scale((float) (target / previous), (float) (target / previous), 1.0f);
    }

    public static void popScale(DrawContext context) {
        context.getMatrices().pop();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getWindow().setScaleFactor(mc.getWindow().calculateScaleFactor(
                mc.options.getGuiScale().getValue().intValue(), mc.forcesUnicodeFont()));
    }
}
