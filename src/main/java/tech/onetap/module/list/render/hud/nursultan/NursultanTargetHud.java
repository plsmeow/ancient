package tech.onetap.module.list.render.hud.nursultan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import tech.onetap.module.list.misc.ScoreboardHealth;
import tech.onetap.module.list.render.Interface;
import tech.onetap.Onetap;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NursultanTargetHud {
    private NursultanTargetHud() {}

    private static final Animation outdatedHpAnimation = new Animation(Easing.EXPO_OUT, 600);
    private static final List<HeadParticle> headParticles = new ArrayList<>();
    private static float lastHpPercent = -1f;

    public static void render(Interface hud, DrawContext context) {
        LivingEntity target = hud.getTargetHudTarget();
        if (target != null) {
            hud.lastTarget = target;
            hud.animation.run(1);
            hud.armorAnim.run(1);
        } else {
            hud.animation.run(0);
            hud.armorAnim.run(0);
        }

        if (hud.animation.getValue() <= 0.05f || hud.lastTarget == null || !(hud.lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) hud.lastTarget;
        AbstractClientPlayerEntity playerEntity = hud.lastTarget instanceof AbstractClientPlayerEntity ? (AbstractClientPlayerEntity) hud.lastTarget : null;

        float anim = (float) hud.animation.getValue();
        int alphaInt = (int) (255 * anim);

        float width = 100;
        float height = 36;
        float x = hud.targetHUDDrag.getX();
        float y = hud.targetHUDDrag.getY();

        hud.drawBackground(x, y, width, height, 7, alphaInt);

        float headSize = 30;
        float headX = x + 3;
        float headY = y + (height - headSize) / 2f;
        float currentHpRaw = livingEntity.getHealth();

        if (hud.lastHpRaw == -1f || hud.lastTarget != livingEntity) {
            hud.lastHpRaw = currentHpRaw;
            headParticles.clear();
        }

        if (currentHpRaw < hud.lastHpRaw) {
            int count = MathHelper.clamp((int)((hud.lastHpRaw - currentHpRaw) * 4), 5, 15);
            for (int i = 0; i < count; i++) {
                headParticles.add(new HeadParticle(headX + headSize / 2f, headY + headSize / 2f, ColorProvider.getThemeColor()));
            }
            hud.lastHpRaw = currentHpRaw;
        } else if (currentHpRaw > hud.lastHpRaw) {
            hud.lastHpRaw = currentHpRaw;
        }

        headParticles.removeIf(p -> p.getAlpha() <= 0);
        for (HeadParticle p : headParticles) {
            p.update();
            int pAlpha = (int) (p.getAlpha() * alphaInt);
            if (pAlpha > 5) {
                DrawUtil.drawRound(p.x - p.size / 2f, p.y - p.size / 2f, p.size, p.size, p.size / 2f, ColorProvider.setAlpha(p.color, pAlpha));
            }
        }

        float hurtPercent = livingEntity.hurtTime / 10f;
        int headColor = ColorProvider.rgba(255, (int)(255 * (1 - hurtPercent)), (int)(255 * (1 - hurtPercent)), alphaInt);

        if (playerEntity != null) {
            try {
                net.minecraft.util.Identifier skin = playerEntity.getSkinTextures().texture();
                int texId = hud.mc.getTextureManager().getTexture(skin).getGlId();

                var headTexture = Builder.texture()
                        .size(new SizeState(headSize, headSize))
                        .radius(new QuadRadiusState(5))
                        .color(new QuadColorState(headColor))
                        .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, texId)
                        .smoothness(1f)
                        .build();

                headTexture.render(context.getMatrices().peek().getPositionMatrix(), headX, headY);
            } catch (Exception ignored) {}
        } else {
            DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "N", headX + 1, headY + 8, headColor, 26f);
        }

        float textX = x + 35;

        String name = livingEntity.getName().getString();

        Scissor.push();
        Scissor.setFromComponentCoordinates(textX, y, width - 42, height);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), name, textX + 1, y + 5, ColorProvider.rgba(255, 255, 255, alphaInt), 8f);
        Scissor.unset();
        Scissor.pop();

        float currentHp = livingEntity.getHealth();
        ScoreboardHealth sbh = Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class);
        if (sbh != null && sbh.isEnabled() && playerEntity != null) {
            float scoreboardHp = sbh.getRealHp(playerEntity);
            if (scoreboardHp != -1) {
                currentHp = scoreboardHp;
            }
        }
        if (Float.isNaN(currentHp) || currentHp < 0) currentHp = 0;

        String hpText = String.format(java.util.Locale.US, "HP: %.1f", currentHp);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), hpText, textX + 1, y + 15.5f, ColorProvider.rgba(255, 255, 255, alphaInt), 6.75f);

        float absorption = livingEntity.getAbsorptionAmount();
        if (absorption > 0) {
            String absText = String.format(java.util.Locale.US, "(+ %.1f)", absorption);
            float offset = Fonts.SFMEDIUM.get().getWidth(hpText, 6.5f) + 3;
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), absText, textX + offset + 3, y + 15.5f, ColorProvider.rgba(255, 215, 0, alphaInt), 6.5f);
        }

        float barX = textX;
        float barY = y + 25;
        float barWidth = width - 42;
        float barHeight = 7;

        float maxHealth = livingEntity.getMaxHealth();
        float hpPercent = MathHelper.clamp(currentHp / maxHealth, 0, 1);

        hud.hpAnimation.run(barWidth * hpPercent);

        if (hpPercent < lastHpPercent) {
            outdatedHpAnimation.run(barWidth * hpPercent);
        } else {
            outdatedHpAnimation.setValue(hud.hpAnimation.getValue());
        }

        lastHpPercent = hpPercent;

        int hpLeftFull, hpRightFull, hpLeftGhost, hpRightGhost;

        if (hud.elements.isEnabled("Таргет худ от темы")) {
            int c1 = ColorProvider.getThemeColor();
            int c2 = ColorProvider.getThemeColorTwo();

            hpRightFull = ColorProvider.setAlpha(c1, alphaInt);
            hpLeftFull = ColorProvider.setAlpha(c2, alphaInt);

            hpLeftGhost = ColorProvider.setAlpha(c1, (int) (110 * anim));
            hpRightGhost = ColorProvider.setAlpha(c2, (int) (110 * anim));
        } else {
            java.awt.Color baseColor = hud.getHealthBarColor(currentHp, maxHealth);
            int br = baseColor.getRed();
            int bg = baseColor.getGreen();
            int bb = baseColor.getBlue();

            hpLeftFull = ColorProvider.rgba(MathHelper.clamp((int)(br * 0.35f), 0, 255), MathHelper.clamp((int)(bg * 0.35f), 0, 255), MathHelper.clamp((int)(bb * 0.35f), 0, 255), alphaInt);
            hpRightFull = ColorProvider.rgba(br, bg, bb, alphaInt);

            hpLeftGhost = ColorProvider.rgba(br, bg, bb, (int) (110 * anim));
            hpRightGhost = ColorProvider.rgba(MathHelper.clamp((int)(br * 0.35f), 0, 255), MathHelper.clamp((int)(bg * 0.35f), 0, 255), MathHelper.clamp((int)(bb * 0.35f), 0, 255), (int) (110 * anim));
        }

        int backColor;
        if (hud.elements.isEnabled("Таргет худ от темы")) {
            backColor = ColorProvider.rgba(20, 20, 20, (int)(120 * anim));
        } else {
            java.awt.Color baseColor = hud.getHealthBarColor(currentHp, maxHealth);
            backColor = ColorProvider.rgba((int)(baseColor.getRed() * 0.45f), (int)(baseColor.getGreen() * 0.45f), (int)(baseColor.getBlue() * 0.45f), (int)(120 * anim));
        }

        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1.5f, backColor);

        float hpWOld = (float) outdatedHpAnimation.getValue();
        if (hpWOld > 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWOld, barHeight, 1.5f, hpLeftGhost, hpLeftGhost, hpRightGhost, hpRightGhost);
        }

        float hpWNow = (float) hud.hpAnimation.getValue();
        if (hpWNow > 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWNow, barHeight, 1.5f, hpLeftFull, hpLeftFull, hpRightFull, hpRightFull);
        }

        float absPercent = MathHelper.clamp(livingEntity.getAbsorptionAmount() / maxHealth, 0, 1);
        hud.absorptionAnimation.run(barWidth * absPercent);
        float abWNow = (float) hud.absorptionAnimation.getValue();

        if (abWNow > 0.5f) {
            int absLeftColor = ColorProvider.rgba(140, 120, 0, (int)(200 * anim));
            int absRightColor = ColorProvider.rgba(255, 215, 0, (int)(255 * anim));
            DrawUtil.drawRound(barX - 0.25f, barY, abWNow, barHeight, 1.5f,
                    absLeftColor, absLeftColor, absRightColor, absRightColor);
        }

        float armorAlpha = (float) hud.armorAnim.getValue();
        if (armorAlpha > 0.05f) {
            List<ItemStack> items = new ArrayList<>();
            items.add(livingEntity.getMainHandStack());
            for (ItemStack stack : livingEntity.getArmorItems()) items.add(stack);
            items.add(livingEntity.getOffHandStack());
            Collections.reverse(items);

            float itemScale = 0.65f;
            float slotSize = 16 * itemScale;
            float itemX = x + width - (slotSize * 6) - 5;
            float itemY = y - slotSize - 2;

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 100);
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            for (ItemStack stack : items) {
                if (stack.isEmpty()) {
                    itemX += slotSize;
                    continue;
                }
                context.getMatrices().push();
                context.getMatrices().translate(itemX, itemY, 0);
                context.getMatrices().scale(armorAlpha * itemScale, armorAlpha * itemScale, 1f);
                context.drawItem(stack, 0, 0);
                context.drawStackOverlay(textRenderer, stack, 0, 0);
                context.getMatrices().pop();
                itemX += slotSize;
            }
            context.getMatrices().pop();
        }

        hud.targetHUDDrag.setWidth(width);
        hud.targetHUDDrag.setHeight(height);
    }

    private static class HeadParticle {
        float x, y, vx, vy, size;
        long spawnTime;
        int color;

        HeadParticle(float startX, float startY, int color) {
            this.x = startX;
            this.y = startY;
            double angle = Math.random() * Math.PI * 2;
            double speed = Math.random() * 0.4 + 0.1;
            this.vx = (float) (Math.cos(angle) * speed);
            this.vy = (float) (Math.sin(angle) * speed);
            this.size = (float) (Math.random() * 8 + 2);
            this.spawnTime = System.currentTimeMillis();
            this.color = color;
        }

        void update() {
            x += vx;
            y += vy;
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - spawnTime;
            if (elapsed >= 2000) return 0;
            return 1f - ((float) elapsed / 2000f);
        }
    }
}
