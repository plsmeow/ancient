package meow.ancient.module.list.render.hud.old;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import meow.ancient.module.list.misc.ScoreboardHealth;
import meow.ancient.module.list.render.Interface;
import meow.ancient.Ancient;
import meow.ancient.util.render.msdf.Fonts;
import meow.ancient.util.render.providers.ColorProvider;
import meow.ancient.util.render.renderers.DrawUtil;

public final class OldTargetHud {
    private OldTargetHud() {}

    public static void render(Interface hud, DrawContext context) {
        LivingEntity target = hud.getTargetHudTarget();

        if (target == null) return;

        float x = hud.targetHUDDrag.getX();
        float y = hud.targetHUDDrag.getY();
        float fontSize = 7.5f;
        var font = Fonts.SFMEDIUM.get();

        String nameText = target.getName().getString();

        float currentHp = Math.max(0, target.getHealth());
        ScoreboardHealth sbh = Ancient.getInstance().getModuleStorage().get(ScoreboardHealth.class);
        if (sbh != null && sbh.isEnabled() && target instanceof AbstractClientPlayerEntity playerEntity) {
            float scoreboardHp = sbh.getRealHp(playerEntity);
            if (scoreboardHp != -1) {
                currentHp = scoreboardHp;
            }
        }

        float absHp = Math.max(0, target.getAbsorptionAmount());

        String targetInfoText;
        if (absHp > 0.05f) {
            targetInfoText = String.format(java.util.Locale.US, "%s -> %.1f + %.1f", nameText, currentHp, absHp);
        } else {
            targetInfoText = String.format(java.util.Locale.US, "%s -> %.1f", nameText, currentHp);
        }

        int textColor = ColorProvider.getThemeColor();

        DrawUtil.drawText(font, targetInfoText, x, y, textColor, fontSize);

        float width = font.getWidth(targetInfoText, fontSize);
        hud.targetHUDDrag.setWidth(width);
        hud.targetHUDDrag.setHeight(9f);
    }
}
