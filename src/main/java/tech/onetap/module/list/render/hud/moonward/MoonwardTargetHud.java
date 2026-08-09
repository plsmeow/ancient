package tech.onetap.module.list.render.hud.moonward;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.EquipmentSlot;
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
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.render.stencil.StencilUtil;

import java.util.ArrayList;
import java.util.List;

public final class MoonwardTargetHud {
    private MoonwardTargetHud() {}

    private static final List<DamageParticle> damageParticles = new ArrayList<>();
    private static float trailHealthPercent = 1f;
    private static float lastHealthPercent = 1f;
    private static float lastAbsorptionPercent = 0f;

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

        float animAlpha = (float) hud.animation.getValue();
        if (animAlpha <= 0.05f || hud.lastTarget == null || !(hud.lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) hud.lastTarget;
        float x = hud.targetHUDDrag.getX();
        float y = hud.targetHUDDrag.getY();
        float width = 105f;
        float height = 36.5f;
        float panelRadius = 6f;

        hud.drawBackground(x, y, width, height, panelRadius, (int) (255 * animAlpha));

        float headSize = 28f;
        float headX = x + width - headSize - 4f;
        float headY = y + (height - headSize) / 2f;
        float headRadius = headSize / 2f;

        context.draw();
        StencilUtil.push();
        DrawUtil.drawRound(headX, headY, headSize, headSize, headRadius, -1);
        StencilUtil.read(1);

        float currentAnimScale = (float) hud.armorAnim.getValue();
        float entityScale = (headSize / 1.3f) * currentAnimScale;

        if (entityScale > 0.1f) {
            float entityX = headX + headSize / 2f;
            float entityY = headY + headSize + 15f * currentAnimScale;
            float elytra = livingEntity.isGliding() ? -10f : 0f;
            if (livingEntity.isGliding()) entityY -= 20f * currentAnimScale;
            hud.drawEntity(entityX - elytra, entityY + elytra, entityScale, -33.0F, 0.0F, livingEntity);
        }

        context.draw();
        StencilUtil.pop();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.client.render.DiffuseLighting.disableGuiDepthLighting();

        Builder.border()
                .size(new SizeState(headSize + 1.5f, headSize + 1.5f))
                .radius(new QuadRadiusState(headRadius))
                .color(new QuadColorState(ColorProvider.rgba(60, 60, 60, (int) (255 * animAlpha))))
                .thickness(1f)
                .smoothness(1f, 0.5f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), headX - 0.75f, headY - 0.75f);

        float textX = x + 6f;
        float textY = y + 7f;

        String rawName = livingEntity.getName().getString();
        String name = transliterate(rawName);

        int textColor = ColorProvider.rgba(222, 222, 222, (int) (255 * animAlpha));
        float rightTextLimit = headX - 3f;

        Scissor.push();
        Scissor.setFromComponentCoordinates(textX, y, rightTextLimit - textX, height);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), name, textX, textY - 2f, textColor, 8.25f);
        Scissor.unset();
        Scissor.pop();

        float currentHp = Math.max(0f, livingEntity.getHealth());
        ScoreboardHealth sbh = Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class);
        if (sbh != null && sbh.isEnabled() && livingEntity instanceof AbstractClientPlayerEntity playerEntity) {
            float scoreboardHp = sbh.getRealHp(playerEntity);
            if (scoreboardHp != -1) {
                currentHp = scoreboardHp;
            }
        }

        float absorptionHP = Math.max(0f, livingEntity.getAbsorptionAmount());
        float maxHealth = Math.max(1f, livingEntity.getMaxHealth());

        String hpText = String.format(java.util.Locale.US, "%.1f", currentHp);
        String absorpText = String.format(java.util.Locale.US, "%.1f", absorptionHP);

        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "HP: " + hpText, textX, textY + 10f, textColor, 7.5f);

        if (absorptionHP > 0f) {
            int absColor = ColorProvider.rgba(222, 222, 0, (int) (255 * animAlpha));
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), "(+" + absorpText + ")", textX + 35f, textY + 10f, absColor, 7.5f);
        }

        float myTotalHp = hud.mc.player.getHealth() + hud.mc.player.getAbsorptionAmount();
        float targetTotalHp = currentHp + absorptionHP;
        float damage = 1.0f;
        ItemStack weapon = hud.mc.player.getMainHandStack();

        if (weapon != null && !weapon.isEmpty()) {
            String itemName = net.minecraft.registry.Registries.ITEM.getId(weapon.getItem()).getPath();
            if (itemName.contains("netherite_sword")) damage += 7.0f;
            else if (itemName.contains("diamond_sword")) damage += 6.0f;
            else if (itemName.contains("iron_sword")) damage += 5.0f;
            else if (itemName.contains("stone_sword")) damage += 4.0f;
            else if (itemName.contains("golden_sword") || itemName.contains("wooden_sword")) damage += 3.0f;
            else if (itemName.contains("netherite_axe")) damage += 9.0f;
            else if (itemName.contains("diamond_axe") || itemName.contains("iron_axe") || itemName.contains("stone_axe")) damage += 8.0f;
            else if (itemName.contains("golden_axe") || itemName.contains("wooden_axe")) damage += 6.0f;
            if (weapon.hasGlint()) damage += 3.0f;
        }

        if (hud.mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.STRENGTH)) {
            damage += 3.0f * (hud.mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.STRENGTH).getAmplifier() + 1);
        }
        if (hud.mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS)) {
            damage -= 4.0f * (hud.mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS).getAmplifier() + 1);
        }

        float potentialDamage = damage * 1.5f;
        float targetArmor = livingEntity.getArmor();
        float targetToughness = (float) livingEntity.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ARMOR_TOUGHNESS);
        float f = 2.0F + targetToughness / 4.0F;
        float g = MathHelper.clamp(targetArmor - potentialDamage / f, targetArmor * 0.2F, 20.0F);
        potentialDamage = potentialDamage * (1.0F - g / 25.0F);

        int epf = 0;
        for (ItemStack armorPiece : livingEntity.getArmorItems()) {
            if (!armorPiece.isEmpty() && armorPiece.hasGlint()) epf += 4;
        }
        epf = Math.min(20, epf);
        if (epf > 0) potentialDamage = potentialDamage * (1.0F - (epf * 0.04F));

        String topText;
        int topColor;
        if (targetTotalHp <= potentialDamage - 1 && targetTotalHp > 0) {
            topText = "ONETAP";
            topColor = ColorProvider.rgba(255, 75, 75, (int) (255 * animAlpha));
        } else {
            topText = myTotalHp >= targetTotalHp ? "WINNING" : "LOSING";
            topColor = ColorProvider.rgba(255, 255, 255, (int) (255 * animAlpha));
        }

        float topTextWidth = Fonts.SFMEDIUM.get().getWidth(topText, 7.0f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), topText, x + (width / 2f) - (topTextWidth / 2f), y - 20f, topColor, 8.0f);

        float barX = textX - 1f;
        float barY = y + 27f;
        float barWidth = width - headSize - 12f;
        float barHeight = 5f;

        if (hud.lastHpRaw == -1f || hud.lastTarget != livingEntity) {
            hud.lastHpRaw = currentHp;
            damageParticles.clear();
        }

        if (currentHp < hud.lastHpRaw) {
            int count = MathHelper.clamp((int)((hud.lastHpRaw - currentHp) * 4), 10, 25);
            java.awt.Color pColor = hud.getHealthBarColor(currentHp, maxHealth);
            float lostHpWidth = barWidth * MathHelper.clamp((hud.lastHpRaw - currentHp) / maxHealth, 0f, 1f);
            float currentHpWidth = barWidth * MathHelper.clamp(currentHp / maxHealth, 0f, 1f);

            for (int i = 0; i < count; i++) {
                float spawnX = barX + currentHpWidth + (float)(Math.random() * lostHpWidth);
                float spawnY = barY + barHeight / 2f;
                damageParticles.add(new DamageParticle(spawnX, spawnY, pColor.getRGB()));
            }
            hud.lastHpRaw = currentHp;
        } else if (currentHp > hud.lastHpRaw) {
            hud.lastHpRaw = currentHp;
        }

        damageParticles.removeIf(p -> p.getAlpha() <= 0);
        if (!damageParticles.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, Interface.TARGET_HUD_GLOW_TEXTURE);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

            for (DamageParticle p : damageParticles) {
                p.update();
                float pAlpha = p.getAlpha() * animAlpha;
                int c = ColorProvider.setAlpha(p.color, (int) (pAlpha * 255));
                float half = p.getSize() / 2f;

                buffer.vertex(matrix, p.x - half, p.y - half, 0).texture(0, 0).color(c);
                buffer.vertex(matrix, p.x - half, p.y + half, 0).texture(0, 1).color(c);
                buffer.vertex(matrix, p.x + half, p.y + half, 0).texture(1, 1).color(c);
                buffer.vertex(matrix, p.x + half, p.y - half, 0).texture(1, 0).color(c);
            }

            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
        }

        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1.5f, ColorProvider.rgba(60, 60, 60, (int) (255 * animAlpha)));

        float hpPercent = MathHelper.clamp(currentHp / maxHealth, 0f, 1f);
        float absorptionPercent = MathHelper.clamp(absorptionHP / maxHealth, 0f, 1f);

        lastHealthPercent += (hpPercent - lastHealthPercent) * 0.25f;
        lastAbsorptionPercent += (absorptionPercent - lastAbsorptionPercent) * 0.15f;
        trailHealthPercent += (lastHealthPercent - trailHealthPercent) * 0.008f;

        float hpWidth = barWidth * lastHealthPercent;
        float trailWidth = barWidth * trailHealthPercent;
        float absWidth = barWidth * lastAbsorptionPercent;

        int hpLeft, hpRight;
        if (hud.elements.isEnabled("Таргет худ от темы")) {
            hpRight = ColorProvider.setAlpha(ColorProvider.getThemeColor(), (int) (255 * animAlpha));
            hpLeft = ColorProvider.setAlpha(ColorProvider.getThemeColorTwo(), (int) (255 * animAlpha));
        } else {
            java.awt.Color hpCol = hud.getHealthBarColor(currentHp, maxHealth);
            hpLeft = ColorProvider.rgba((int)(hpCol.getRed()*0.5), (int)(hpCol.getGreen()*0.5), (int)(hpCol.getBlue()*0.5), (int) (255 * animAlpha));
            hpRight = ColorProvider.rgba(hpCol.getRed(), hpCol.getGreen(), hpCol.getBlue(), (int) (255 * animAlpha));
        }

        if (trailWidth > hpWidth) {
            DrawUtil.drawRound(barX, barY, trailWidth, barHeight, 1.5f, ColorProvider.setAlpha(ColorProvider.getThemeColor(), (int) (135 * animAlpha)));
        }
        if (hpWidth > 0) {
            DrawUtil.drawRound(barX, barY, hpWidth, barHeight, 1.5f, hpLeft, hpLeft, hpRight, hpRight);
        }
        if (absWidth > 0) {
            int absBase = ColorProvider.rgba(255, 222, 0, (int) (255 * animAlpha));
            int absLeft = ColorProvider.rgba(180, 155, 0, (int) (255 * animAlpha));
            DrawUtil.drawRound(barX, barY, absWidth, barHeight, 1.5f, absLeft, absLeft, absBase, absBase);
        }

        float armorAlpha = (float) hud.armorAnim.getValue();
        if (armorAlpha > 0.05f) {
            List<ItemStack> items = new ArrayList<>();
            items.add(livingEntity.getEquippedStack(EquipmentSlot.HEAD));
            items.add(livingEntity.getEquippedStack(EquipmentSlot.CHEST));
            items.add(livingEntity.getEquippedStack(EquipmentSlot.LEGS));
            items.add(livingEntity.getEquippedStack(EquipmentSlot.FEET));
            items.add(livingEntity.getMainHandStack());
            items.add(livingEntity.getOffHandStack());
            items.removeIf(ItemStack::isEmpty);

            if (!items.isEmpty()) {
                float itemScale = 0.7f;
                float slotSize = 14f * itemScale;
                float padding = 2f;
                float totalArmorWidth = (items.size() * slotSize) + ((items.size() - 1) * padding);
                float itemX = x + (width - totalArmorWidth) / 2f - 18f;
                float itemY = y - slotSize - 2f;

                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 100);
                for (ItemStack stack : items) {
                    context.getMatrices().push();
                    context.getMatrices().translate(itemX, itemY, 0);
                    context.getMatrices().scale(armorAlpha * itemScale, armorAlpha * itemScale, 1f);
                    context.drawItem(stack, 0, 0);
                    context.drawStackOverlay(hud.mc.textRenderer, stack, 0, 0);
                    context.getMatrices().pop();
                    itemX += slotSize + padding;
                }
                context.getMatrices().pop();
            }
        }

        hud.targetHUDDrag.setWidth(width);
        hud.targetHUDDrag.setHeight(height);
    }

    private static String transliterate(String text) {
        if (text == null) return "";
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            String replacement = switch (c) {
                case 'а', 'А' -> c == 'А' ? "A" : "a";
                case 'б', 'Б' -> c == 'Б' ? "B" : "b";
                case 'в', 'В' -> c == 'В' ? "V" : "v";
                case 'г', 'Г' -> c == 'Г' ? "G" : "g";
                case 'д', 'Д' -> c == 'Д' ? "D" : "d";
                case 'е', 'Е' -> c == 'Е' ? "E" : "e";
                case 'ё', 'Ё' -> c == 'Ё' ? "Yo" : "yo";
                case 'ж', 'Ж' -> c == 'Ж' ? "Zh" : "zh";
                case 'з', 'З' -> c == 'З' ? "Z" : "z";
                case 'и', 'И' -> c == 'И' ? "I" : "i";
                case 'й', 'Й' -> c == 'Й' ? "Y" : "y";
                case 'к', 'К' -> c == 'К' ? "K" : "k";
                case 'л', 'Л' -> c == 'Л' ? "L" : "l";
                case 'м', 'М' -> c == 'М' ? "M" : "m";
                case 'н', 'Н' -> c == 'Н' ? "N" : "n";
                case 'о', 'О' -> c == 'О' ? "O" : "o";
                case 'п', 'П' -> c == 'П' ? "P" : "p";
                case 'р', 'Р' -> c == 'Р' ? "R" : "r";
                case 'с', 'С' -> c == 'С' ? "S" : "s";
                case 'т', 'Т' -> c == 'Т' ? "T" : "t";
                case 'у', 'У' -> c == 'У' ? "U" : "u";
                case 'ф', 'Ф' -> c == 'Ф' ? "F" : "f";
                case 'х', 'Х' -> c == 'Х' ? "Kh" : "kh";
                case 'ц', 'Ц' -> c == 'Ц' ? "Ts" : "ts";
                case 'ч', 'Ч' -> c == 'Ч' ? "Ch" : "ch";
                case 'ш', 'Ш' -> c == 'Ш' ? "Sh" : "sh";
                case 'щ', 'Щ' -> c == 'Щ' ? "Shch" : "shch";
                case 'ъ', 'Ъ' -> "";
                case 'ы', 'Ы' -> c == 'Ы' ? "Y" : "y";
                case 'ь', 'Ь' -> "";
                case 'э', 'Э' -> c == 'Э' ? "E" : "e";
                case 'ю', 'Ю' -> c == 'Ю' ? "Yu" : "yu";
                case 'я', 'Я' -> c == 'Я' ? "Ya" : "ya";
                default -> String.valueOf(c);
            };
            result.append(replacement);
        }
        return result.toString();
    }

    private static class DamageParticle {
        float x, y, vx, vy, baseSize;
        long spawnTime, maxLife;
        int color;

        DamageParticle(float x, float y, int color) {
            this.x = x;
            this.y = y;
            double angle = Math.random() * Math.PI * 2;
            double speed = Math.random() * 2.0 + 0.5;
            this.vx = (float) (Math.cos(angle) * speed);
            this.vy = (float) (Math.sin(angle) * speed);
            this.baseSize = (float) (Math.random() * 7 + 6);
            this.spawnTime = System.currentTimeMillis();
            this.maxLife = (long) (Math.random() * 700 + 800);
            this.color = color;
        }

        void update() {
            x += vx;
            y += vy;
            vx *= 0.85f;
            vy *= 0.85f;
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - spawnTime;
            if (elapsed >= maxLife) return 0;
            return 1f - ((float) elapsed / maxLife);
        }

        float getSize() {
            return baseSize * getAlpha();
        }
    }
}
