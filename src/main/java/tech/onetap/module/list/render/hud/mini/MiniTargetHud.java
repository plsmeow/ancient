package tech.onetap.module.list.render.hud.mini;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import tech.onetap.module.list.misc.ScoreboardHealth;
import tech.onetap.module.list.render.Interface;
import tech.onetap.Onetap;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public final class MiniTargetHud {
    private MiniTargetHud() {}

    public static void render(Interface hud, DrawContext context) {
        LivingEntity target = hud.getTargetHudTarget();

        if (target != null) {
            hud.lastTarget = target;
            hud.animation.run(1);
        } else {
            hud.animation.run(0);
        }

        if (hud.animation.getValue() <= 0.05f || hud.lastTarget == null || !(hud.lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) hud.lastTarget;
        float anim = (float) hud.animation.getValue();
        int alphaInt = (int) (255 * anim);

        float width = 90f;
        float height = 28f;
        float x = hud.targetHUDDrag.getX();
        float y = hud.targetHUDDrag.getY();

        hud.drawBackground(x, y, width, height, 4f, alphaInt);

        float textX = x + 5f;
        String name = livingEntity.getName().getString();
        DrawUtil.drawText(Fonts.SFBOLD.get(), name, textX, y + 4.5f, ColorProvider.rgba(230, 230, 230, alphaInt), 8f);

        float currentHp = Math.max(0, livingEntity.getHealth());
        ScoreboardHealth sbh = Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class);
        if (sbh != null && sbh.isEnabled() && hud.lastTarget instanceof AbstractClientPlayerEntity playerEntity) {
            float scoreboardHp = sbh.getRealHp(playerEntity);
            if (scoreboardHp != -1) {
                currentHp = scoreboardHp;
            }
        }

        float absHp = Math.max(0, livingEntity.getAbsorptionAmount());
        float maxHealth = Math.max(1f, livingEntity.getMaxHealth());

        float barX = textX;
        float barY = y + 19.5f;
        float barWidth = width - 10f;
        float barHeight = 3.5f;

        hud.hpAnimation.run(barWidth * MathHelper.clamp(currentHp / maxHealth, 0, 1));
        float hpWNow = (float) hud.hpAnimation.getValue();
        float animatedHp = (barWidth > 0) ? (hpWNow / barWidth) * maxHealth : currentHp;

        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1f, ColorProvider.rgba(40, 40, 40, alphaInt));

        int hpLeft, hpRight;
        if (hud.elements.isEnabled("Таргет худ от темы")) {
            hpRight = ColorProvider.setAlpha(ColorProvider.getThemeColor(), alphaInt);
            hpLeft = ColorProvider.setAlpha(ColorProvider.getThemeColorTwo(), alphaInt);
        } else {
            java.awt.Color hpCol = hud.getHealthBarColor(currentHp, maxHealth);
            hpLeft = ColorProvider.rgba((int)(hpCol.getRed() * 0.5), (int)(hpCol.getGreen() * 0.5), (int)(hpCol.getBlue() * 0.5), alphaInt);
            hpRight = ColorProvider.rgba(hpCol.getRed(), hpCol.getGreen(), hpCol.getBlue(), alphaInt);
        }

        if (hpWNow > 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWNow, barHeight, 1f, hpLeft, hpLeft, hpRight, hpRight);
        }

        if (absHp > 0.05f) {
            float absPercent = MathHelper.clamp(absHp / maxHealth, 0f, 1f);
            hud.absorptionAnimation.run(barWidth * absPercent);
            float abWNow = (float) hud.absorptionAnimation.getValue();

            if (abWNow > 0.5f) {
                int absLeftColor = ColorProvider.rgba(160, 140, 0, alphaInt);
                int absRightColor = ColorProvider.rgba(255, 215, 0, alphaInt);
                DrawUtil.drawRound(barX, barY, abWNow, barHeight, 1f, absLeftColor, absLeftColor, absRightColor, absRightColor);
            }
        }

        String hpText = String.format(java.util.Locale.US, "%.1f", animatedHp);
        float hpTextWidth = Fonts.SFBOLD.get().getWidth(hpText, 6.5f);

        float hpTextX = barX + hpWNow - (hpTextWidth / 2f);
        hpTextX = MathHelper.clamp(hpTextX, barX, barX + barWidth - hpTextWidth);
        float hpTextY = barY - 7.5f;

        float absTextX = barX;
        float absTextY = barY - 7.5f;

        if (absHp > 0.05f) {
            String absText = String.format(java.util.Locale.US, "%.1f AB", absHp);
            float absTextWidth = Fonts.SFBOLD.get().getWidth(absText, 6.5f);

            float minAllowedHpX = absTextX + absTextWidth + 3f;
            if (hpTextX < minAllowedHpX) {
                hpTextX = minAllowedHpX;
            }

            DrawUtil.drawText(Fonts.SFBOLD.get(), absText, absTextX, absTextY, ColorProvider.rgba(255, 215, 0, alphaInt), 6.5f);
        }

        DrawUtil.drawText(Fonts.SFBOLD.get(), hpText, hpTextX, hpTextY, ColorProvider.rgba(225, 225, 225, alphaInt), 6.5f);

        hud.targetHUDDrag.setWidth(width);
        hud.targetHUDDrag.setHeight(height);
    }
}
