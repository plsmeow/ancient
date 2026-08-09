package tech.onetap.module.list.render.hud.celestial;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import tech.onetap.module.list.misc.ScoreboardHealth;
import tech.onetap.module.list.render.Interface;
import tech.onetap.Onetap;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TargetHud {
    private TargetHud() {}

    public static void renderCelestial(Interface hud, DrawContext context) {
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

        float width = 100, height = 37, x = hud.targetHUDDrag.getX(), y = hud.targetHUDDrag.getY();
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();

        hud.drawCelestialGlow(m, x, y, width, height, 5, anim);
        int theme1 = ColorProvider.getThemeColor(), theme2 = ColorProvider.getThemeColorTwo();
        int[] glow = ColorProvider.getOrbitalRect(theme1, theme2, 300.0, (int)(110 * anim));

        DrawUtil.drawRound(x - 0.5F, y - 0.5F, width + 1F, height + 1F, 5, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, width, height, 5, ColorProvider.rgba(14, 10, 6, alphaInt));

        float headSize = 30, headX = x + 2.5f, headY = y + 3.75f;
        int headColor = ColorProvider.rgba(255, (int)(255 * (1 - livingEntity.hurtTime / 10f)), (int)(255 * (1 - livingEntity.hurtTime / 10f)), alphaInt);

        if (playerEntity != null) {
            try {
                int texId = hud.mc.getTextureManager().getTexture(playerEntity.getSkinTextures().texture()).getGlId();
                Builder.texture().size(new SizeState(headSize, headSize)).radius(new QuadRadiusState(3)).color(new QuadColorState(headColor)).texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, texId).smoothness(1f).build().render(context.getMatrices().peek().getPositionMatrix(), headX, headY);
            } catch (Exception ignored) {}
        } else {
            net.minecraft.item.Item spawnEgg = net.minecraft.item.SpawnEggItem.forEntity(livingEntity.getType());
            if (spawnEgg != null) {
                context.getMatrices().push();
                context.getMatrices().translate(headX + headSize / 2f, headY + headSize / 2f, 50.0);
                float animatedScale = (headSize / 16.0f) * anim;
                context.getMatrices().scale(animatedScale, animatedScale, 1.0f);
                context.getMatrices().translate(-8.0, -8.0, 0.0);
                context.drawItem(new net.minecraft.item.ItemStack(spawnEgg), 0, 0);
                context.getMatrices().pop();
            } else {
                DrawUtil.drawRound(headX, headY, headSize, headSize, 3, ColorProvider.rgba(40, 40, 40, alphaInt));
                DrawUtil.drawText(tech.onetap.util.render.msdf.Fonts.ICONS_NURIK.get(), "N", headX + 1.5f, headY + 8f, ColorProvider.rgba(255, 255, 255, alphaInt), 24f);
            }
        }

        float textX = headX + headSize + 2;
        String name = livingEntity.getName().getString();
        DrawUtil.drawText(tech.onetap.util.render.msdf.Fonts.SFBOLD.get(), name, textX, y + 5, ColorProvider.rgba(230, 230, 230, alphaInt), 9f, 0.3f, 0.7f, width);

        float currentHp = Math.max(0, livingEntity.getHealth());
        ScoreboardHealth sbh = Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class);
        if (sbh != null && sbh.isEnabled() && playerEntity != null) {
            float scoreboardHp = sbh.getRealHp(playerEntity);
            if (scoreboardHp != -1) {
                currentHp = scoreboardHp;
            }
        }

        float absHp = Math.max(0, livingEntity.getAbsorptionAmount());
        float maxHealth = Math.max(1f, livingEntity.getMaxHealth());
        float barX = textX - 1, barY = y + 23f, barHeight = 7.5f, barWidth = width - headSize - 8;

        hud.hpAnimation.run(barWidth * MathHelper.clamp(currentHp / maxHealth, 0, 1));
        float hpWNow = (float) hud.hpAnimation.getValue();
        float animatedHp = (barWidth > 0) ? (hpWNow / barWidth) * maxHealth : currentHp;

        String hpText = String.format(java.util.Locale.US, "HP: %.1f", animatedHp);
        float fontSize = 6.5f, hpY = y + 14f;
        DrawUtil.drawText(tech.onetap.util.render.msdf.Fonts.SFBOLD.get(), hpText, textX + 0.5F, hpY, ColorProvider.rgba(230, 230, 230, alphaInt), fontSize);

        if (absHp > 0.05f) {
            String absText = String.format(java.util.Locale.US, "%.1f AB", absHp);
            float hpW = tech.onetap.util.render.msdf.Fonts.SFBOLD.get().getWidth(hpText, fontSize), plusW = tech.onetap.util.render.msdf.Fonts.SFBOLD.get().getWidth("  + ", fontSize);
            DrawUtil.drawText(tech.onetap.util.render.msdf.Fonts.SFBOLD.get(), "  + ", textX + hpW, hpY, ColorProvider.rgba(160, 160, 160, alphaInt), fontSize);
            DrawUtil.drawText(tech.onetap.util.render.msdf.Fonts.SFBOLD.get(), absText, textX + hpW + plusW, hpY, ColorProvider.rgba(255, 205, 70, alphaInt), fontSize);
        }

        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1.5f, ColorProvider.rgba(45, 45, 45, alphaInt));
        if (hpWNow > 0.5f) {
            int[] colors = ColorProvider.getOrbitalRect(theme1, theme2, 300.0, alphaInt);
            float rR = (hpWNow >= barWidth - 1f || hpWNow <= barHeight) ? 1.5f : 0f;
            Builder.rectangle().size(new SizeState(hpWNow, barHeight)).radius(new QuadRadiusState(1.5f, 1.5f, rR, rR)).color(new QuadColorState(colors[0], colors[1], colors[2], colors[3])).build().render(context.getMatrices().peek().getPositionMatrix(), barX, barY);
        }

        float armorAlpha = (float) hud.armorAnim.getValue();
        if (armorAlpha > 0.05f) {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack stack : livingEntity.getArmorItems()) {
                items.add(stack);
            }
            items.add(livingEntity.getOffHandStack());
            items.add(livingEntity.getMainHandStack());
            Collections.reverse(items);

            float itemScale = 0.55f;
            float slotSize = 16 * itemScale;
            float padding = 1.5f;

            long activeItemsCount = items.stream().filter(stack -> !stack.isEmpty()).count();

            if (activeItemsCount > 0) {
                float totalArmorWidth = (activeItemsCount * slotSize) + ((activeItemsCount - 1) * padding);
                float itemX = x + (width - totalArmorWidth) / 2f;
                float itemY = y - slotSize - 3f;

                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 100);
                TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

                for (ItemStack stack : items) {
                    if (stack.isEmpty()) continue;

                    context.getMatrices().push();
                    context.getMatrices().translate(itemX, itemY, 0);
                    context.getMatrices().scale(armorAlpha * itemScale, armorAlpha * itemScale, 1f);

                    context.drawItem(stack, 0, 0);
                    context.drawStackOverlay(textRenderer, stack, 0, 0);

                    context.getMatrices().pop();
                    itemX += slotSize + padding;
                }
                context.getMatrices().pop();
            }
        }

        hud.targetHUDDrag.setWidth(width);
        hud.targetHUDDrag.setHeight(height);
    }
}
