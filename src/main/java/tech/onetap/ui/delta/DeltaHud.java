package tech.onetap.ui.delta;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import tech.onetap.module.Module;
import tech.onetap.module.list.render.Interface;
import tech.onetap.util.KeyUtil;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaAnimation;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.font.DeltaFonts;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Delta-styled HUD elements ported from DeltaClient's widget suite
 * (WatermarkWidget, HotkeysWidget, PotionWidget, TargetWidget, NotificationWidget):
 * blur+glow panels radius 5 (glow radius 8), 12.5px headers with icon/separator/title
 * (gt_regular 7), 11.5px rows, global 0.3s sine fade. Positions reuse the existing
 * onetap draggables so dragging persists through DragManager.
 */
public final class DeltaHud {
    private static final float HEADER_HEIGHT = 12.5f;
    private static final float ROW_HEIGHT = 11.5f;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Map<Module, Animation> MODULE_ANIMS = new LinkedHashMap<>();
    private static final Map<String, DeltaAnimation> ROW_ANIMS = new LinkedHashMap<>();
    private static final DeltaAnimation HP_ANIM = new DeltaAnimation();

    private DeltaHud() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static final float ICON_SIZE = 6.5f;
    private static final float LOGO_SIZE = 8.0f;
    private static final float START_PADDING = 5.0f;
    private static final float ICON_TEXT_GAP = 3.0f;
    private static final float LOGO_GAP = 4.0f;

    private static float smoothedFps = 0;

    /**
     * Watermark ported from DeltaClient WatermarkWidget: joined (или split) верхняя
     * строка с иконками — ник, FPS, пинг, время — и опциональная вторая строка —
     * координаты, TPS, скорость (BPS). Секции переключаются через ПКМ-попап.
     */
    public static void renderWatermark(Interface hud, DrawContext context) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.GT_REGULAR.get();
        var icons = DeltaFonts.ICONS.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean split = hud.elements.isEnabled("Разделять элементы ватермарки");

        smoothedFps = smoothedFps + ((mc.getCurrentFps() - smoothedFps) * 0.1f);

        List<String[]> topSections = buildTopSections(hud, mc);
        List<String[]> bottomSections = buildBottomSections(hud, mc);

        float sectionGap = split ? 2.0f : 5.0f;
        float topWidth = sectionsWidth(icons, fonts, topSections, true, sectionGap);
        float bottomWidth = sectionsWidth(icons, fonts, bottomSections, false, sectionGap);

        float x = (mc.getWindow().getScaledWidth() - topWidth) / 2.0f;
        float y = hud.watermarkDrag.getY();
        float bottomX = x + ((topWidth - bottomWidth) / 2.0f);

        int primaryColor = DeltaThemeInfo.PRIMARY.resolve();

        renderSectionRow(matrices, draw, fonts, icons, hud, x, y, topSections, true, primaryColor, sectionGap, 1.0f);
        if (!bottomSections.isEmpty()) {
            renderSectionRow(matrices, draw, fonts, icons, hud, bottomX, y + HEADER_HEIGHT + 3.0f,
                    bottomSections, false, primaryColor, sectionGap, 1.0f);
        }

        hud.watermarkDrag.setWidth(Math.max(topWidth, bottomWidth));
        hud.watermarkDrag.setHeight(bottomSections.isEmpty() ? HEADER_HEIGHT : HEADER_HEIGHT + 3.0f + HEADER_HEIGHT);
        hud.watermarkDrag.onDraw();
    }

    private static List<String[]> buildTopSections(Interface hud, MinecraftClient mc) {
        List<String[]> sections = new ArrayList<>();
        if (hud.elements.isEnabled("Ник в ватермарке") && mc.player != null) {
            sections.add(new String[]{"L", mc.player.getName().getString()});
        }
        if (hud.elements.isEnabled("FPS в ватермарке")) {
            sections.add(new String[]{"q", ((int) smoothedFps) + " FPS"});
        }
        if (hud.elements.isEnabled("Пинг в ватермарке") && mc.player != null && mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
            sections.add(new String[]{"P",
                    mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() + " ms"});
        }
        if (hud.elements.isEnabled("Время в ватермарке")) {
            sections.add(new String[]{"T", LocalTime.now().format(TIME_FORMAT)});
        }
        return sections;
    }

    private static List<String[]> buildBottomSections(Interface hud, MinecraftClient mc) {
        List<String[]> sections = new ArrayList<>();
        if (mc.player == null) return sections;
        if (hud.elements.isEnabled("Координаты в ватермарке")) {
            sections.add(new String[]{"b", "x " + ((int) mc.player.getX())
                    + " y " + ((int) mc.player.getY()) + " z " + ((int) mc.player.getZ())});
        }
        if (hud.elements.isEnabled("TPS в ватермарке")) {
            float tps = tech.onetap.Onetap.getInstance().getTpsGetter().getAverageTPS();
            sections.add(new String[]{"g", String.format("%.1f TPS", tps)});
        }
        if (hud.elements.isEnabled("Скорость в ватермарке")) {
            double dx = mc.player.getX() - mc.player.prevX;
            double dz = mc.player.getZ() - mc.player.prevZ;
            double bps = Math.hypot(dx, dz) * 20;
            sections.add(new String[]{"e", String.format("%.2f BPS", bps)});
        }
        return sections;
    }

    /**
     * Ширина строки секций (joined либо split-режим), попиксельно как в WatermarkWidget.
     */
    private static float sectionsWidth(tech.onetap.util.render.font.Font icons,
                                       tech.onetap.util.render.font.Font fonts,
                                       List<String[]> sections, boolean logo, float sectionGap) {
        if (sections.isEmpty()) {
            return 0.0f;
        }
        float width = START_PADDING;
        if (logo) {
            width += icons.getWidth("a", LOGO_SIZE) + LOGO_GAP + 1.0f + sectionGap;
        }
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                width += 1.0f + sectionGap;
            }
            width += icons.getWidth(sections.get(i)[0], ICON_SIZE) + ICON_TEXT_GAP
                    + fonts.getWidth(sections.get(i)[1], 7.0f) + sectionGap;
        }
        return width;
    }

    /**
     * Рисует строку секций: иконка (PRIMARY) + текст (TEXT) + разделители между ними.
     */
    private static void renderSectionRow(MatrixStack matrices, tech.onetap.util.render.Draw2D draw,
                                         tech.onetap.util.render.font.Font fonts,
                                         tech.onetap.util.render.font.Font icons, Interface hud,
                                         float x, float y, List<String[]> sections, boolean logo,
                                         int primaryColor, float sectionGap, float anim) {
        drawPanel(matrices, draw, x, y, sectionsWidth(icons, fonts, sections, logo, sectionGap), HEADER_HEIGHT, true, anim);
        float cursor = x + START_PADDING;
        float textY = (y + ((HEADER_HEIGHT - fonts.getHeight(7.0f)) / 2.0f)) - 0.5f;
        if (logo) {
            icons.drawText(matrices, "a", cursor,
                    y + ((HEADER_HEIGHT - icons.getHeight(LOGO_SIZE)) / 2.0f), LOGO_SIZE, primaryColor);
            float cursor2 = cursor + icons.getWidth("a", LOGO_SIZE) + LOGO_GAP;
            renderSeparator(matrices, draw, cursor2, y, HEADER_HEIGHT, anim);
            cursor = cursor2 + 1.0f + sectionGap;
        }
        for (int i = 0; i < sections.size(); i++) {
            String[] section = sections.get(i);
            if (i > 0) {
                renderSeparator(matrices, draw, cursor, y, HEADER_HEIGHT, anim);
                cursor += 1.0f + sectionGap;
            }
            icons.drawText(matrices, section[0], cursor,
                    y + ((HEADER_HEIGHT - icons.getHeight(ICON_SIZE)) / 2.0f), ICON_SIZE, primaryColor);
            float cursor3 = cursor + icons.getWidth(section[0], ICON_SIZE) + ICON_TEXT_GAP;
            fonts.drawText(matrices, section[1], cursor3, textY, 7.0f, DeltaThemeInfo.TEXT.resolve());
            cursor = cursor3 + fonts.getWidth(section[1], 7.0f) + sectionGap;
        }
    }

    /**
     * Hotkeys: bound-module list, ported from HotkeysWidget.
     */
    public static void renderHotkeys(Interface hud, DrawContext context, float delta) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.GT_REGULAR.get();
        var icons = DeltaFonts.ICONS.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Module> bound = new ArrayList<>();
        for (Module module : tech.onetap.Onetap.getInstance().getModuleStorage().getModules()) {
            if (module.getKey() != -1) {
                bound.add(module);
            }
        }
        var headerFont = DeltaFonts.GT_REGULAR.get();
        float width = 14.5f + headerFont.getWidth("Hot-keys", 7.0f) + 5.0f + 2.0f;
        for (Module module : bound) {
            Animation anim = moduleAnim(module);
            width = Math.max(width, 19.0f + fonts.getWidth(module.getName(), 6.5f) + 8.0f
                    + fonts.getWidth(KeyUtil.getKeyName(module.getKey()), 6.5f) + 4.0f
                    + icons.getWidth("Q", 6.5f) + 5.0f + 2.0f);
        }

        float x = hud.keyBindsDrag.getX();
        float y = hud.keyBindsDrag.getY();
        float anim = 1.0f;

        renderHeader(matrices, draw, fonts, icons, "Q", "Hot-keys", x, y, width, anim);

        float contentY = y + HEADER_HEIGHT + 1.0f;
        for (Module module : bound) {
            Animation moduleAnim = moduleAnim(module);
            float moduleAnimValue = moduleAnim.getValue();
            if (moduleAnimValue <= 0.0f) continue;
            float offsetX = -8.0f * (1.0f - moduleAnimValue);
            float offsetY = -(1.0f - moduleAnimValue);
            float drawY = contentY + offsetY;
            drawPanel(matrices, draw, x + offsetX, drawY, width, ROW_HEIGHT, false, moduleAnimValue);
            renderSeparator(matrices, draw, x + offsetX + 15.0f, drawY, ROW_HEIGHT, moduleAnimValue);
            icons.drawText(matrices, categoryIcon(module), x + offsetX + 5.0f,
                    (drawY + ((ROW_HEIGHT - icons.getHeight(6.5f)) / 2.0f)) - 0.15f, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), moduleAnimValue));
            fonts.drawText(matrices, module.getName(), x + offsetX + 19.0f,
                    (drawY + ((ROW_HEIGHT - fonts.getHeight(6.5f)) / 2.0f)) - 0.5f, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), moduleAnimValue));
            String bindText = KeyUtil.getKeyName(module.getKey());
            float bindWidth = fonts.getWidth(bindText, 6.5f);
            float rightIconX = ((x + offsetX + width) - 5.0f - icons.getWidth("C", 6.5f)) - 1.0f;
            fonts.drawText(matrices, bindText, (rightIconX - 4.0f) - bindWidth,
                    (drawY + ((ROW_HEIGHT - fonts.getHeight(6.5f)) / 2.0f)) - 0.5f, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), 0.55f * moduleAnimValue));
            icons.drawText(matrices, "C", rightIconX,
                    (drawY + ((ROW_HEIGHT - icons.getHeight(6.5f)) / 2.0f)) - 0.15f, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), moduleAnimValue));
            contentY += 13.5f * moduleAnimValue;
        }
        hud.keyBindsDrag.setWidth(width);
        hud.keyBindsDrag.setHeight(bound.isEmpty() ? HEADER_HEIGHT : (contentY - y) - 2.0f);
        hud.keyBindsDrag.onDraw();
    }

    /**
     * Potions: effect list with sprite, name, duration, ported from PotionWidget.
     */
    public static void renderPotions(Interface hud, DrawContext context) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.GT_REGULAR.get();
        var icons = DeltaFonts.ICONS.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        var effects = new ArrayList<net.minecraft.entity.effect.StatusEffectInstance>(
                mc.player.getStatusEffects());
        effects.removeIf(effect -> effect.getEffectType().value() == net.minecraft.entity.effect.StatusEffects.NIGHT_VISION);

        float width = 14.5f + fonts.getWidth("Potion-list", 7.0f) + 5.0f + 2.0f;
        for (var effect : effects) {
            String name = effectName(effect);
            String duration = formatDuration(effect.getDuration());
            width = Math.max(width, 19.0f + fonts.getWidth(name, 6.5f) + 8.0f
                    + fonts.getWidth(duration, 6.5f) + 5.0f + 2.0f);
        }

        float x = hud.potionsDrag.getX();
        float y = hud.potionsDrag.getY();
        float anim = effects.isEmpty() ? 0.0f : 1.0f;
        if (anim <= 0.0f) return;

        renderHeader(matrices, draw, fonts, icons, "E", "Potion-list", x, y, width, anim);

        float contentY = y + HEADER_HEIGHT + 1.0f;
        for (var effect : effects) {
            String rowKey = effect.getEffectType().value().getName().getString() + effect.getAmplifier();
            DeltaAnimation rowAnim = rowAnim(rowKey);
            rowAnim.update(0.0f, 1.0f, 0.3f, DeltaEasing.SINE_IN, 0.0f);
            rowAnim.tick(true);
            float rowAnimValue = rowAnim.getAnimationValue();
            float offsetX = -8.0f * (1.0f - rowAnimValue);
            float drawY = contentY - (1.0f - rowAnimValue);
            drawPanel(matrices, draw, x + offsetX, drawY, width, ROW_HEIGHT, false, rowAnimValue);
            renderSeparator(matrices, draw, x + offsetX + 15.0f, drawY, ROW_HEIGHT, rowAnimValue);
            var sprite = mc.getStatusEffectSpriteManager().getSprite(effect.getEffectType());
            context.getMatrices().push();
            context.getMatrices().translate(x + offsetX + 5.0f, drawY + 2.0f, 0.0f);
            context.getMatrices().scale(0.4f, 0.4f, 1.0f);
            context.drawSpriteStretched(net.minecraft.client.render.RenderLayer::getGuiTextured,
                    sprite, 0, 0, 18, 18, ColorUtil.applyAlphaToColor(0xFFFFFF, rowAnimValue));
            context.getMatrices().pop();
            String name = effectName(effect);
            boolean harmful = effect.getEffectType().value().getCategory() == net.minecraft.entity.effect.StatusEffectCategory.HARMFUL;
            int nameColor = harmful ? ColorUtil.convertToARGB(255, 125, 125, 255) : DeltaThemeInfo.TEXT.resolve();
            fonts.drawText(matrices, name, x + offsetX + 19.0f,
                    (drawY + ((ROW_HEIGHT - fonts.getHeight(6.5f)) / 2.0f)) - 0.5f, 6.5f,
                    ColorUtil.applyAlphaToColor(nameColor, rowAnimValue));
            String duration = formatDuration(effect.getDuration());
            float durationWidth = fonts.getWidth(duration, 6.5f);
            fonts.drawText(matrices, duration, (x + offsetX + width - 5.0f) - durationWidth,
                    (drawY + ((ROW_HEIGHT - fonts.getHeight(6.5f)) / 2.0f)) - 0.5f, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), 0.55f * rowAnimValue));
            contentY += 13.5f * rowAnimValue;
        }
        hud.potionsDrag.setWidth(width);
        hud.potionsDrag.setHeight(effects.isEmpty() ? HEADER_HEIGHT : (contentY - y) - 2.0f);
        hud.potionsDrag.onDraw();
    }


    /**
     * Staff list: online staff with head/name/status rows, ported from StaffWidget.
     */
    public static void renderStaffList(Interface hud, DrawContext context) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.GT_REGULAR.get();
        var icons = DeltaFonts.ICONS.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Interface.Staff> staff = new ArrayList<>();
        for (Interface.Staff entry : hud.staffPlayers) {
            if (entry.isOnServer) {
                staff.add(entry);
            }
        }

        float width = 14.5f + fonts.getWidth("Staff-list", 7.0f) + 5.0f + 2.0f;
        for (Interface.Staff entry : staff) {
            String status = entry.status == Interface.Status.VANISHED ? "SPEC" : "Online";
            width = Math.max(width, 19.0f + fonts.getWidth(entry.name, 6.5f) + 8.0f
                    + fonts.getWidth(status, 6.5f) + 5.0f + 2.0f);
        }

        boolean staffFound = !hud.staffPlayers.isEmpty();
        if (staffFound) {
            hud.alpha2.run(1);
        }
        if (!staffFound && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
            hud.alpha2.run(0);
        }
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
            hud.alpha2.run(1);
        }

        float x = hud.staffListDrag.getX();
        float y = hud.staffListDrag.getY();
        float anim = (float) hud.alpha2.getValue();
        if (anim <= 0.05f) return;

        renderHeader(matrices, draw, fonts, icons, "i", "Staff-list", x, y, width, anim);

        float contentY = y + HEADER_HEIGHT + 1.0f;
        for (Interface.Staff entry : staff) {
            entry.animation.run(entry.isOnServer ? 1 : 0);
            float rowAnimValue = (float) entry.animation.getValue();
            if (rowAnimValue <= 0.001f) continue;
            float offsetX = -8.0f * (1.0f - rowAnimValue);
            float drawY = contentY - (1.0f - rowAnimValue);
            float rowAlpha = rowAnimValue * anim;
            drawPanel(matrices, draw, x + offsetX, drawY, width, ROW_HEIGHT, false, rowAlpha);
            renderSeparator(matrices, draw, x + offsetX + 15.0f, drawY, ROW_HEIGHT, rowAlpha);

            float headSize = 7.5f;
            PlayerListEntry playerEntry = mc.getNetworkHandler() != null
                    ? mc.getNetworkHandler().getPlayerListEntry(entry.name) : null;
            if (playerEntry != null) {
                draw.drawSkinHead(matrices, playerEntry.getSkinTextures().texture(),
                        x + offsetX + 5.0f, drawY + 2.0f, headSize, headSize, 2.0f, rowAlpha);
            } else {
                icons.drawText(matrices, "y", x + offsetX + 5.0f,
                        (drawY + ((ROW_HEIGHT - icons.getHeight(8.0f)) / 2.0f)), 8.0f,
                        ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), rowAlpha));
            }
            fonts.drawText(matrices, entry.name, x + offsetX + 19.0f,
                    (drawY + ((ROW_HEIGHT - fonts.getHeight(6.5f)) / 2.0f)) - 0.5f, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), rowAlpha));
            boolean near = entry.status != Interface.Status.VANISHED && playerEntry != null;
            String status = entry.status == Interface.Status.VANISHED ? "SPEC" : "Online";
            int statusColor = near ? ColorUtil.convertToARGB(107, 207, 107, 255)
                    : ColorUtil.convertToARGB(232, 168, 64, 255);
            float statusWidth = fonts.getWidth(status, 6.5f);
            fonts.drawText(matrices, status, (x + offsetX + width - 5.0f) - statusWidth - 1.0f,
                    (drawY + ((ROW_HEIGHT - fonts.getHeight(6.5f)) / 2.0f)) - 0.5f, 6.5f,
                    ColorUtil.applyAlphaToColor(statusColor, rowAlpha));
            contentY += 13.5f * rowAnimValue;
        }
        hud.staffListDrag.setWidth(width);
        hud.staffListDrag.setHeight((contentY - y) - 2.0f);
        hud.staffListDrag.onDraw();
    }

    /**
     * Target HUD: 100x24 panel with skin head, name, HP bar and rolling HP digits,
     * ported from TargetWidget.
     */
    public static void renderTarget(Interface hud, DrawContext context) {
        LivingEntity target = hud.getTargetHudTarget();
        if (target != null) {
            hud.lastTarget = target;
            hud.animation.run(1);
        } else {
            hud.animation.run(0);
        }

        if (hud.animation.getValue() <= 0.05f || hud.lastTarget == null || !(hud.lastTarget instanceof LivingEntity)) return;
        target = (LivingEntity) hud.lastTarget;
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.GT_REGULAR.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        float anim = hud.animation.getValue();

        float x = hud.targetHUDDrag.getX();
        float y = hud.targetHUDDrag.getY();
        float width = 100.0f;
        float height = 24.0f;

        drawPanel(matrices, draw, x, y, width, height, true, anim);

        float headSize = 24.0f / 1.35f;
        float headY = y + ((height - headSize) / 2.0f);
        if (target instanceof PlayerEntity player && mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) != null) {
            var skin = mc.getNetworkHandler().getPlayerListEntry(player.getUuid()).getSkinTextures().texture();
            draw.drawSkinHead(matrices, skin, x + 5.0f, headY, headSize, headSize, 2.0f, anim);
        } else {
            var icons = DeltaFonts.ICONS.get();
            icons.drawText(matrices, "B", x + 6.5f + ((headSize - 24.0f) / 2.0f),
                    headY + ((headSize - 24.0f) / 2.0f), 24.0f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), anim));
        }
        float textX = x + 5.0f + headSize + 5.0f;
        String name = target.getName().getString();
        if (name.length() > 12) {
            name = name.substring(0, 12);
        }
        fonts.drawText(matrices, name, textX,
                (headY + ((fonts.getHeight(7.5f) - fonts.getHeight(7.5f)) / 2.0f)) - 0.5f, 7.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), anim));

        float maxHealth = target.getMaxHealth();
        float health = target.getHealth() + target.getAbsorptionAmount();
        float targetHP = DeltaMath.clamp(DeltaMath.clamp(health, 0.0f, maxHealth) / maxHealth, 0.0f, 1.0f);
        HP_ANIM.update(0.0f, 1.0f, 0.5f, DeltaEasing.SINE_IN, 0.0f);
        HP_ANIM.smooth(targetHP, 0.5f);
        float lineHP = HP_ANIM.getCurrentValue();
        draw.drawRounded(matrices, textX, headY + 12.5f, 54.0f, 3.0f, 0.5f,
                ColorUtil.applyAlphaToColor(ColorUtil.darken(DeltaThemeInfo.PRIMARY.resolve(), 0.3f), anim));
        draw.drawRounded(matrices, textX, headY + 12.5f, 54.0f * lineHP, 3.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), anim));

        String hpValue = String.valueOf((int) health);
        fonts.drawText(matrices, hpValue, (x + width - 5.0f) - fonts.getWidth(hpValue, 7.0f),
                headY + 0.5f, 7.0f, ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), anim));
        hud.targetHUDDrag.setWidth(width);
        hud.targetHUDDrag.setHeight(height);
        hud.targetHUDDrag.onDraw();
    }

    /**
     * Shared panel background: blur+glow rounded rect radius 5 (glow radius 8).
     */
    private static void drawPanel(MatrixStack matrices, tech.onetap.util.render.Draw2D draw, float x, float y, float width, float height,
                                  boolean glow, float anim) {
        int background = ColorUtil.applyAlphaToColor(
                ColorUtil.lerpColor(DeltaThemeInfo.BACKGROUND_HUD.resolve(), DeltaThemeInfo.PRIMARY.resolve(),
                        DeltaThemeInfo.PRIMARY.alphaFloat() / 6.0f),
                DeltaThemeInfo.BACKGROUND_HUD.alphaFloat() * anim);
        if (glow) {
            draw.drawGlowBlur(matrices, x, y, width, height, 5.0f, background, anim, background, 8.0f);
        } else {
            draw.drawShadowBlur(matrices, x, y, width, height, 5.0f, background, anim);
        }
    }

    private static void renderHeader(MatrixStack matrices, tech.onetap.util.render.Draw2D draw, tech.onetap.util.render.font.Font fonts, tech.onetap.util.render.font.Font icons, String icon,
                                     String title, float x, float y, float width, float anim) {
        drawPanel(matrices, draw, x, y, width, HEADER_HEIGHT, true, anim);
        float iconSize = 8.0f;
        icons.drawText(matrices, icon, x + 3.0f,
                y + ((HEADER_HEIGHT - icons.getHeight(iconSize)) / 2.0f), iconSize,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), anim));
        renderSeparator(matrices, draw, x + 13.5f, y, HEADER_HEIGHT, anim);
        fonts.drawText(matrices, title, x + 17.5f,
                (y + ((HEADER_HEIGHT - fonts.getHeight(7.0f)) / 2.0f)) - 0.5f, 7.0f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), anim));
    }

    private static void renderSeparator(MatrixStack matrices, tech.onetap.util.render.Draw2D draw, float x, float y, float height, float anim) {
        draw.drawRounded(matrices, x, y + ((height - height / 2.0f) / 2.0f), 0.75f, height / 2.0f, 0.0f,
                ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(200, 200, 200, 255), 0.5f * anim));
    }


    private static Animation moduleAnim(Module module) {
        Animation anim = MODULE_ANIMS.get(module);
        if (anim == null) {
            anim = new Animation(Easing.QUINTIC_OUT, 300);
            MODULE_ANIMS.put(module, anim);
        }
        anim.run(module.isEnabled());
        return anim;
    }

    private static DeltaAnimation rowAnim(String key) {
        return ROW_ANIMS.computeIfAbsent(key, k -> new DeltaAnimation());
    }

    private static String categoryIcon(Module module) {
        return switch (module.getCategory()) {
            case COMBAT -> "V";
            case MOVEMENT -> "I";
            case RENDER -> "t";
            case PLAYER -> "L";
            case MISC -> "D";
        };
    }

    private static String effectName(net.minecraft.entity.effect.StatusEffectInstance effect) {
        String name = effect.getEffectType().value().getName().getString().toLowerCase().replace('_', ' ');
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return effect.getAmplifier() > 0 ? name + " " + (effect.getAmplifier() + 1) : name;
    }

    private static String formatDuration(int ticks) {
        if (ticks > 1000000) return "∞";
        int seconds = ticks / 20;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
