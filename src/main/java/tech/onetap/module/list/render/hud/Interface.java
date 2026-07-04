package tech.onetap.module.list.render.hud;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.scoreboard.*;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventHUD;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.list.combat.AutoFlyMace;
import tech.onetap.module.list.combat.BoatAura;
import tech.onetap.module.list.combat.TpAura;
import tech.onetap.module.list.misc.ScoreboardHealth;

import tech.onetap.module.settings.*;
import tech.onetap.module.settings.impl.Theme;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import tech.onetap.util.base.Instance;
import tech.onetap.util.draggable.DragManager;
import tech.onetap.util.draggable.Draggable;
import tech.onetap.util.keyboard.KeyStorage;
import tech.onetap.util.math.Counter;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.msdf.MsdfFont;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.replace.ReplaceUtil;
import tech.onetap.util.server.Server;
import tech.onetap.util.staff.StaffManager;
import tech.onetap.util.math.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.scoreboard.Scoreboard;

@ModuleInformation(moduleName = "Interface", moduleCategory = ModuleCategory.RENDER)
public class Interface extends Module {

    private static final Identifier TARGET_HUD_GLOW_TEXTURE = Identifier.of("mre", "images/glow.png");

    public final ModeSetting hudStyle = new ModeSetting("Стиль HUD", "Celestial", "Nursultan", "Celestial", "Old");

    private final ModeListSetting elements = new ModeListSetting("Элементы",
            new BooleanSetting("Ватермарка", true),
            new BooleanSetting("Координаты", false),
            new BooleanSetting("Активный таргет", true),
            new BooleanSetting("Таргет худ от темы", false),
            new BooleanSetting("Привязанные модули", true),
            new BooleanSetting("Активные модераторы", true),
            new BooleanSetting("Бафы", true),
            new BooleanSetting("Скорость", true),
            new BooleanSetting("Счетчик тотемов", true),
            new BooleanSetting("Нотификации", true),
            new BooleanSetting("СпекТрекер", true),
            new BooleanSetting("Блюр фона", true),
            new BooleanSetting("Задний фон от темы", false),
            new BooleanSetting("Урон Булавы", false)
    );
    private final ModeSetting targetStyle = new ModeSetting("Стиль таргета", "Nursultan", "Nursultan", "Moonward", "Celestial", "Mini", "Old");
    private final SliderSetting backgroundIntensity =
            new SliderSetting("Интенсивность фона", 0.15f, 0.05f, 1.0f, 0.01f);
    private final SliderSetting lowHpAlertThreshold =
            new SliderSetting("Порог ХП оповещения", 8f, 1f, 20f, 0.5f);

    private static final SoundEvent SPEK_SOUND = SoundEvent.of(Identifier.of("mre", "spek"));

    private final Draggable watermarkDrag = DragManager.installDrag(this, "Watermark", 4, 4);
    private final Draggable keyBindsDrag = DragManager.installDrag(this, "HotKeys", 100, 50);
    private final Draggable staffListDrag = DragManager.installDrag(this, "StaffList", 200, 50);
    private final Draggable potionsDrag = DragManager.installDrag(this, "Potions", 300, 50);
    private final Draggable targetHUDDrag = DragManager.installDrag(this, "TargetHUD", 130, 130);
    private final Draggable totemCounterDrag = DragManager.installDrag(this, "TotemCounter", 200, 200);

    private final Map<String, Long> spekSuspects = new ConcurrentHashMap<>();
    public void drawBackground(float x, float y, float w, float h, float radius, int alpha) {
        if (elements.isEnabled("Блюр фона")) {
            int color = ColorProvider.rgba(15, 15, 15, (int) (alpha * backgroundIntensity.getFloatValue()));

            DrawUtil.drawRoundBlur(x, y, w, h, radius, ColorProvider.rgba(200, 200, 200, alpha), 12);
            DrawUtil.drawRound(x, y, w, h, radius, color);

        } else {
            int color = ColorProvider.rgba(15, 15, 15, (int) (alpha * backgroundIntensity.getFloatValue()));
            DrawUtil.drawRound(x, y, w, h, radius, color);
        }

        if (elements.isEnabled("Задний фон от темы")) {
            DrawUtil.drawRound(x, y, w, h, radius, getThemeTint(alpha));
        }
    }

    @Subscribe
    public void onEventHUD(EventHUD e) {
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;

        if (elements.isEnabled("Нотификации")) {
            NotificationManager.render(e.getDrawContext());
        }

        renderLowHealthAlert(e.getDrawContext());

        if (elements.isEnabled("Счетчик тотемов")) {
            renderTotemCounter(e.getDrawContext());
        }
        if (elements.isEnabled("Ватермарка")) {
            renderWatermark(e.getDrawContext());
        }
        if (elements.isEnabled("Координаты")) {
            renderCoordsInfo(e.getDrawContext());
        }
        if (elements.isEnabled("Активный таргет")) {
            if (targetStyle.is("Moonward")) {
                renderTargetHUDMoonward(e.getDrawContext());
            } else if (targetStyle.is("Celestial")) {
                renderTargetHUDCelestial(e.getDrawContext());
            } else if (targetStyle.is("Mini")) {
                renderTargetHUDMini(e.getDrawContext());
            } else if (targetStyle.is("Old")) {
                renderTargetHUDOld(e.getDrawContext()); // Наш новый метод
            } else {
                renderTargetHUDClassic(e.getDrawContext());
            }
        }
        if (elements.isEnabled("Привязанные модули")) {
            renderKeyBinds(e.getDrawContext());
        }
        if (elements.isEnabled("Активные модераторы")) {
            renderStaffList(e.getDrawContext());
        }
        if (elements.isEnabled("Бафы")) {
            renderPotions(e.getDrawContext());
        }
        if (elements.isEnabled("Скорость")) {
            renderSpeed(e.getDrawContext());
        }
        if (elements.isEnabled("Урон Булавы")) {
            renderMACEDMG(e.getDrawContext());
        }
    }

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        if (elements.isEnabled("СпекТрекер")) {
            long now = System.currentTimeMillis();

            spekSuspects.entrySet().removeIf(entry -> now - entry.getValue() > 30000);

            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (mc.player.distanceTo(p) < 50) {
                    spekSuspects.put(p.getName().getString(), now);
                }
            }

            KillAura ka = Instance.get(KillAura.class);
            if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
                spekSuspects.put(ka.getTarget().getName().getString(), now);
            }
        }

        if (elements.isEnabled("Активные модераторы")) {
            update();
        }
        if (elements.isEnabled("Бафы")) {
            updatePotions();
        }
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (!elements.isEnabled("СпекТрекер") || mc.player == null) return;

        if (e.getPacket() instanceof GameMessageS2CPacket packet) {
            String rawContent = packet.content().getString();
            String msgLower = rawContent.toLowerCase();

            boolean isTrigger = msgLower.contains("спек") ||
                    msgLower.contains("spec") ||
                    msgLower.contains("spek") ||
                    msgLower.contains("report") ||
                    msgLower.contains("фаст");

            if (isTrigger) {
                for (String suspect : spekSuspects.keySet()) {
                    if (rawContent.contains(suspect)) {
                        NotificationManager.postWarning("Report Detect: " + suspect);
                        playSpekSound();
                        break;
                    }
                }
            }
        }
    }

    private void playSpekSound() {
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SPEK_SOUND, 1.0f));
        }
    }


    private final Animation animation = new Animation(Easing.EXPO_OUT, 300);
    private final Animation armorAnim = new Animation(Easing.EXPO_OUT, 300);
    private final Animation hpAnimation = new Animation(Easing.EXPO_OUT, 300);
    private final Animation outdatedHpAnimation = new Animation(Easing.EXPO_OUT, 600);
    private final Animation absorptionAnimation = new Animation(Easing.EXPO_OUT, 300);

    private float lastHealthVal = 0;
    private long lastTime = System.currentTimeMillis();

    private Entity lastTarget;
    private float lastHpPercent = -1f;
    private final Animation widthAnim = new Animation(Easing.EXPO_OUT, 200);
    private final Animation xLine = new Animation(Easing.EXPO_OUT, 170);
    private final Animation alpha = new Animation(Easing.EXPO_OUT, 200);

    public int getThemeTint(int alpha) {
        int themeColor = ColorProvider.getThemeColor();
        return ColorProvider.setAlpha(themeColor, (int) (100 * (alpha / 255f) * backgroundIntensity.getFloatValue()));
    }

    private final Animation lowHpAlertAnimation = new Animation(Easing.EXPO_OUT, 300);

    private void renderLowHealthAlert(DrawContext context) {
        if (mc.player == null) return;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float threshold = lowHpAlertThreshold.getFloatValue();
        boolean shouldShow = hp <= threshold && !mc.player.isDead();

        lowHpAlertAnimation.run(shouldShow ? 1 : 0);
        float anim = (float) lowHpAlertAnimation.getValue();
        if (anim <= 0.01f) return;

        int alphaInt = (int) (255 * anim);

        String text = String.format(java.util.Locale.US, "Критическое здоровье: %.1f HP", hp);
        String iconCode = "G";

        float textWidth = Fonts.SFMEDIUM.get().getWidth(text, 7f);
        float iconWidth = Fonts.ICONS_NURIK.get().getWidth(iconCode, 9f);
        float width = iconWidth + textWidth + 22f;
        float height = 14.5f;

        float screenWidth = mc.getWindow().getScaledWidth();
        float x = (screenWidth - width) / 2f;
        float y = 100f;

        float danger = MathHelper.clamp((threshold - hp) / Math.max(1f, threshold), 0f, 1f);
        float beat = 0.5f + 0.5f * (float) Math.abs(Math.sin(System.currentTimeMillis() / (150f - 50f * danger)));
        int iconColor = ColorProvider.rgba(255, (int)(50 * (1 - beat)), (int)(50 * (1 - beat)), alphaInt);

        context.getMatrices().push();
        context.getMatrices().translate(x + width / 2f, y + height / 2f, 0);
        context.getMatrices().scale(anim, anim, 1f);
        context.getMatrices().translate(-(x + width / 2f), -(y + height / 2f), 0);

        drawBackground(x, y, width, height, 4, alphaInt);

        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), iconCode, x + 5, y + 4, iconColor, 9f);
        DrawUtil.drawRound(x + 18f, y + 2.5f, 0.5f, height - 5f, 0, ColorProvider.rgba(255, 255, 255, (int) (120 * anim)));
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), text, x + 23f, y + 3f, ColorProvider.rgba(255, 255, 255, alphaInt), 7f);

        context.getMatrices().pop();
    }
    private final Animation keybindsEmptyAnim = new Animation(Easing.EXPO_OUT, 233);

    /** Строка списка Keybinds: подпись, текст клавиши и прозрачность (для анимации модулей). */
    private record BindRow(String label, String key, float alpha) {}

    /** Собирает биндов модулей + биндов настроек в формате "Модуль Настройка: значение". */
    private List<BindRow> collectBindRows() {
        boolean instant = hudStyle.is("Old");
        List<BindRow> rows = new ArrayList<>();
        for (Module m : Onetap.getInstance().getModuleStorage().getModules()) {
            boolean visible = instant ? m.isEnabled() : MathHelper.clamp((float) m.getAnimation().getValue(), 0f, 1f) > 0.001f;
            if (m.getKey() != -1 && visible) {
                rows.add(new BindRow(m.getName(), KeyStorage.getKey(m.getKey()), 1f));
            }
            for (Setting s : m.getSettings()) {
                if (s.isBound()) {
                    boolean show = s instanceof BooleanSetting || s.isBindActive();
                    if (show) {
                        rows.add(new BindRow(m.getName() + " " + s.getName() + ": " + s.getBindDisplayValue(),
                                KeyStorage.getKey(s.getKey()), 1f));
                    }
                }
            }
        }
        return rows;
    }

    private void renderKeyBindsOld(DrawContext context) {
        List<BindRow> activeRows = collectBindRows();
        if (activeRows.isEmpty() && !(mc.currentScreen instanceof ChatScreen)) return;

        float posX = keyBindsDrag.getX();
        float posY = keyBindsDrag.getY();
        float fontSize = 7.5f;
        MsdfFont font = Fonts.SFMEDIUM.get();

        // Заголовок без фонов
        DrawUtil.drawText(font, "[ Keybinds ]", posX, posY, ColorProvider.getThemeColor(), fontSize);
        posY += 10f;

        float maxWidth = font.getWidth("[ Keybinds ]", fontSize);

        for (BindRow row : activeRows) {
            String text = row.label() + " -> " + row.key();
            DrawUtil.drawText(font, text, posX, posY, ColorProvider.rgba(255, 255, 255, 255), fontSize);

            float rowWidth = font.getWidth(text, fontSize);
            if (rowWidth > maxWidth) maxWidth = rowWidth;

            posY += 9f;
        }

        if (activeRows.isEmpty() && mc.currentScreen instanceof ChatScreen) {
            DrawUtil.drawText(font, "No active binds", posX, posY, ColorProvider.rgba(150, 150, 150, 255), fontSize);
            posY += 9f;
        }

        keyBindsDrag.setWidth(maxWidth);
        keyBindsDrag.setHeight(posY - keyBindsDrag.getY());
    }

    private void renderKeyBindsCelestial(DrawContext context) {
        if (mc.player == null) return;

        final boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        List<BindRow> activeRows = collectBindRows();

        boolean showPlaceholder = chatOpen && activeRows.isEmpty();
        keybindsEmptyAnim.run(showPlaceholder ? 1f : 0f);
        alpha.run((activeRows.isEmpty() && !chatOpen) ? 0f : 1f);

        float globalAlpha = (float) alpha.getValue();
        if (globalAlpha <= 0.05f) return;

        int aInt = MathHelper.clamp((int) (255f * globalAlpha), 0, 255);
        float emptyAnimVal = MathHelper.clamp((float) keybindsEmptyAnim.getValue(), 0f, 1f);

        final String placeholderText = "No active binds";
        final float fontSize = 7.5f;
        final float headerH = 14f;
        final float rowH = 9.5f;

        float targetWidth = 70f;
        for (BindRow row : activeRows) {
            String keyText = "[" + row.key() + "]";
            float rowWidth = Fonts.SFBOLD.get().getWidth(row.label(), fontSize) + Fonts.SFBOLD.get().getWidth(keyText, fontSize) + 20f;
            targetWidth = Math.max(targetWidth, rowWidth);
        }
        if (emptyAnimVal > 0.001f) {
            targetWidth = Math.max(targetWidth, Fonts.SFBOLD.get().getWidth(placeholderText, fontSize) + 14f);
        }
        widthAnim.run(targetWidth);
        float curW = Math.max(70f, (float) widthAnim.getValue());

        float rowsHeight = 0f;
        for (BindRow row : activeRows) {
            rowsHeight += rowH * MathHelper.clamp(row.alpha(), 0f, 1f);
        }
        rowsHeight += rowH * emptyAnimVal;
        float totalH = headerH + rowsHeight + (rowsHeight > 0f ? 3f : 1f);

        float x = keyBindsDrag.getX();
        float y = keyBindsDrag.getY();

        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();
        int[] orbital = ColorProvider.getOrbitalRect(t1, t2, 300.0, aInt);
        int[] glow = ColorProvider.getOrbitalRect(t1, t2, 300.0, (int) (110 * globalAlpha));
        Matrix4f m2 = context.getMatrices().peek().getPositionMatrix();

        drawCelestialGlow(m2,x, y, curW, totalH, 4f, globalAlpha);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, curW + 1f, totalH + 1f, 4f, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, curW, totalH, 4f, ColorProvider.rgba(14, 10, 6, aInt));

        Builder.rectangle()
                .size(new SizeState(curW + 0.5f, headerH))
                .radius(new QuadRadiusState(4, 0, 0, 4))
                .color(new QuadColorState(orbital[0], orbital[1], orbital[2], orbital[3]))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        float headerTextX = x + (curW - Fonts.SFBOLD.get().getWidth("Keybinds", 10f)) / 2f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), "Keybinds", headerTextX, y + 1f, ColorProvider.rgba(255, 255, 255, aInt), 10f);

        float curY = y + headerH + 1f;

        for (BindRow row : activeRows) {
            float rowAnim = MathHelper.clamp(row.alpha(), 0f, 1f);
            if (rowAnim <= 0.001f) continue;

            float itemHeight = 9 * rowAnim;
            int itemAlpha = MathHelper.clamp((int) (aInt * rowAnim), 0, 255);

            if (itemAlpha >= 4) {
                float textY = curY + (itemHeight / 2f) - (fontSize / 2f) - 1;
                String key = "[" + row.key() + "]";

                DrawUtil.drawText(Fonts.SFBOLD.get(), row.label(), x + 5f, textY, ColorProvider.rgba(233, 233, 233, itemAlpha), fontSize);

                float keyX = x + curW - Fonts.SFBOLD.get().getWidth(key, fontSize) - 5f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), key, keyX, textY, ColorProvider.rgba(200, 200, 200, itemAlpha), fontSize);
            }
            curY += itemHeight;
        }

        if (emptyAnimVal > 0.001f) {
            float itemHeight = rowH * emptyAnimVal;
            int itemAlpha = MathHelper.clamp((int) (aInt * emptyAnimVal), 0, 255);

            if (itemAlpha >= 4) {
                float textY = curY + (itemHeight / 2f) - (fontSize / 2f);
                float textX = x + (curW - Fonts.SFBOLD.get().getWidth(placeholderText, fontSize)) / 2f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, textX, textY, ColorProvider.rgba(255, 205, 70, itemAlpha), fontSize);
            }
            curY += itemHeight;
        }

        keyBindsDrag.setWidth(curW);
        keyBindsDrag.setHeight(totalH);
    }
    private void renderKeyBinds(DrawContext context) {
        if (mc.player == null) return;

        if (hudStyle.is("Celestial")) {
            renderKeyBindsCelestial(context);
        } else if (hudStyle.is("Old")) {
            renderKeyBindsOld(context);
        }

        else {
            renderKeyBindsClassic(context);
        }
    }

    private void renderKeyBindsClassic(DrawContext context) {
        if (mc.player == null) return;

        float posX = keyBindsDrag.getX();
        float posY = keyBindsDrag.getY();

        float defaultWidth = 55;
        float height = 14.5f;

        List<BindRow> allRows = collectBindRows();

        boolean isFound = !allRows.isEmpty();
        if (isFound) alpha.run(1);
        else if (!(mc.currentScreen instanceof ChatScreen)) alpha.run(0);
        if (mc.currentScreen instanceof ChatScreen) alpha.run(1);

        float globalAlpha = (float) alpha.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        drawBackground(posX, posY, (float) widthAnim.getValue(), height, 3, headerAlpha);

        DrawUtil.drawRound(posX + 15.25f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(125,125,125, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "C", posX + 4f, posY + 4f, ColorProvider.rgba(255,255,255, headerAlpha), 8);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Hotkeys", posX + 19.5f, posY + 3.25f, ColorProvider.rgba(255,255,255, headerAlpha), 7.5f);

        posY += 14.5f;
        float bindWidth = 0;

        for (BindRow row : allRows) {
            float localBindWidth = Fonts.SFREGULAR.get().getWidth(row.key(), 6.75f);
            if (localBindWidth > bindWidth) {
                bindWidth = localBindWidth;
            }
        }

        xLine.run(bindWidth);

        for (BindRow row : allRows) {
            float rowAlpha = MathHelper.clamp(row.alpha(), 0f, 1f);
            if (rowAlpha <= 0.001f) continue;

            float itemHeight = 12 * Math.min(1.0f, rowAlpha);
            height += itemHeight;

            int itemAlpha = (int) (255 * rowAlpha * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String bind = row.key();
            String label = row.label();
            float elementsWidth = Fonts.SFREGULAR.get().getWidth(label, 6.75f) + Fonts.SFREGULAR.get().getWidth(bind, 6.75f) + 30;

            float textYOffset = (itemHeight / 2f) - 4f;

            drawBackground(posX, posY, (float) widthAnim.getValue(), itemHeight, 3, itemAlpha);

            float separatorX = (float) (posX + widthAnim.getValue() - 6.5f - xLine.getValue());
            DrawUtil.drawRound(separatorX, posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(125,125,125, itemAlpha));

            DrawUtil.drawText(Fonts.SFREGULAR.get(), label, posX + 4.25f, posY + textYOffset, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            float bindX = (float) (posX + widthAnim.getValue() - 2.5f - xLine.getValue() - Fonts.SFREGULAR.get().getWidth(bind, 6.75f) / 2 + xLine.getValue() / 2 - 0.25f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), bind, bindX, posY + textYOffset, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            if (elementsWidth > defaultWidth) {
                defaultWidth = elementsWidth;
            }

            posY += itemHeight;
        }

        widthAnim.run(defaultWidth);
        keyBindsDrag.setWidth((float) widthAnim.getValue());
        keyBindsDrag.setHeight(height);
    }


    private final List<Staff> staffPlayers = new ArrayList<>();
    private final Pattern namePattern = Pattern.compile("^\\w{3,16}$");
    private final Pattern prefixMatches = Pattern.compile(".*(ꔷ|ꔳ|ꔩ|ꔥ|ꔡ|ꔗ|ꔓ|\\bmod\\b|\\badm\\b|\\bhelp\\b|\\bwne\\b|модер|мод|хелп|помощ|помо|админ|адм|владел|владе|отриц|отри|\\btaf\\b|\\bcurat\\b|куратор|курато|\\bdev\\b|разраб|раз|\\bsupp\\b|\\bꜱupp\\b|саппорт|сапп|\\bder\\b|\\byt\\b|\\[yt\\]|ютуб|стажер|сотрудник).*");
    private void renderTotemCounter(DrawContext context) {
        if (mc.player == null) return;

        float posX = totemCounterDrag.getX();
        float posY = totemCounterDrag.getY();

        int totemCount = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == net.minecraft.item.Items.TOTEM_OF_UNDYING) {
                totemCount += mc.player.getInventory().getStack(i).getCount();
            }
        }

        String text = totemCount + "x";
        float width = Fonts.SFREGULAR.get().getWidth(text, 9) + 18;
        float height = 14;

        context.getMatrices().push();
        context.getMatrices().translate(posX + 2, posY + 2, 0);
        context.getMatrices().scale(0.79f, 0.79f, 0.79f);
        context.drawItem(new ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING), 0, 0);
        context.getMatrices().pop();

        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, posX + 15, posY + 4.5f, -1, 9);

        totemCounterDrag.setWidth(width);
        totemCounterDrag.setHeight(height);
    }

    private final Animation widthAnim2 = new Animation(Easing.EXPO_OUT, 200);
    private final Animation alpha2 = new Animation(Easing.EXPO_OUT, 200);
    private final Animation staffListEmptyAnim = new Animation(Easing.EXPO_OUT, 233);

    private void renderStaffList(DrawContext context) {
        if (mc.player == null) return;

        if (hudStyle.is("Celestial")) {
            renderStaffListCelestial(context);
        } else if (hudStyle.is("Old")) {
            renderStaffListOld(context);
        } else {
            renderStaffListClassic(context);
        }
    }

    private void renderStaffListOld(DrawContext context) {
        final boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        List<Staff> activeStaff = staffPlayers.stream()
                .filter(s -> s.isOnServer)
                .toList();

        boolean hasStaff = !activeStaff.isEmpty();
        boolean showPlaceholder = chatOpen && !hasStaff;

        final String headerText = "[ Staff ]";
        final String placeholderText = "No active staff";
        final float fontSize = 7.5f;

        float posX = staffListDrag.getX();
        float posY = staffListDrag.getY();

        DrawUtil.drawText(Fonts.SFBOLD.get(), headerText, posX, posY, ColorProvider.getThemeColor(), fontSize);
        float startY = posY;
        posY += 10f;

        float maxWidth = Fonts.SFBOLD.get().getWidth(headerText, fontSize);

        for (Staff staff : activeStaff) {
            int nameColor;
            if (staff.status == Status.VANISHED || staff.isSpec) {
                nameColor = 0xFF5050;
            } else if (mc.world != null && mc.world.getPlayers().stream()
                    .anyMatch(p -> p.getName().getString().equals(staff.name))) {
                nameColor = 0xFFD700;
            } else {
                nameColor = 0xDCDCDC;
            }

            MutableText fullRowText = Text.empty().append(staff.prefix);
            fullRowText.setStyle(fullRowText.getStyle().withColor(nameColor));

            context.getMatrices().push();
            context.getMatrices().translate(posX, posY, 0);
            float scale = fontSize / 9.0f;
            context.getMatrices().scale(scale, scale, 1f);

            context.drawText(mc.textRenderer, fullRowText, 0, 0, -1, true);

            context.getMatrices().pop();

            float rowWidth = mc.textRenderer.getWidth(fullRowText) * scale;
            if (rowWidth > maxWidth) maxWidth = rowWidth;

            posY += 9f;
        }

        if (showPlaceholder) {
            DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, posX, posY, ColorProvider.rgba(255, 205, 70, 255), fontSize);
            float rowWidth = Fonts.SFBOLD.get().getWidth(placeholderText, fontSize);
            if (rowWidth > maxWidth) maxWidth = rowWidth;
            posY += 9f;
        }

        staffListDrag.setWidth(maxWidth);
        staffListDrag.setHeight(posY - startY);
    }

    private void renderStaffListCelestial(DrawContext context) {
        final boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        for (Staff staff : staffPlayers) {
            staff.animation.run(staff.isOnServer ? 1 : 0);
        }

        List<Staff> activeStaff = staffPlayers.stream()
                .filter(s -> s.animation.getValue() > 0.01f)
                .toList();

        boolean showPlaceholder = chatOpen && activeStaff.isEmpty();
        staffListEmptyAnim.run(showPlaceholder ? 1f : 0f);
        alpha2.run((activeStaff.isEmpty() && !chatOpen) ? 0f : 1f);

        float globalAlpha = (float) alpha2.getValue();
        if (globalAlpha <= 0.05f) return;

        int aInt = MathHelper.clamp((int) (255f * globalAlpha), 0, 255);
        float emptyAnimVal = MathHelper.clamp((float) staffListEmptyAnim.getValue(), 0f, 1f);

        final String headerText = "Staff Online";
        final String placeholderText = "No active staff";

        // === BASE как в Keybinds ===
        final float fontSize = 7.5f;
        final float headerH = 14f;
        final float rowH = 9.5f;
        final float padL = 5f;
        final float padR = 5f;

        float targetWidth = 70f; // как у Keybinds

        for (Staff staff : activeStaff) {
            float prefixW = Fonts.SFBOLD.get().getWidth(staff.prefix, fontSize);
            float nameW = Fonts.SFBOLD.get().getWidth(" " + staff.name, fontSize);

            // слева паддинг, справа место под кружок статуса
            float rowWidth = padL + prefixW + nameW + 14f + padR;
            targetWidth = Math.max(targetWidth, rowWidth);
        }

        if (emptyAnimVal > 0.001f) {
            targetWidth = Math.max(targetWidth, Fonts.SFBOLD.get().getWidth(placeholderText, fontSize) + 14f);
        }

        widthAnim2.run(targetWidth);
        float curW = Math.max(70f, (float) widthAnim2.getValue());

        float rowsHeight = (float) activeStaff.stream()
                .mapToDouble(s -> rowH * MathHelper.clamp((float) s.animation.getValue(), 0f, 1f))
                .sum();
        rowsHeight += rowH * emptyAnimVal;

        float totalH = headerH + rowsHeight + (rowsHeight > 0f ? 3f : 1f);

        float x = staffListDrag.getX();
        float y = staffListDrag.getY();

        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();
        int[] orbital = ColorProvider.getOrbitalRect(t1, t2, 300.0, aInt);
        int[] glow = ColorProvider.getOrbitalRect(t1, t2, 300.0, (int) (110 * globalAlpha));
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();

        drawCelestialGlow(m, x, y, curW, totalH, 4f, globalAlpha);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, curW + 1f, totalH + 1f, 4f, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, curW, totalH, 4f, ColorProvider.rgba(14, 10, 6, aInt));

        Builder.rectangle()
                .size(new SizeState(curW + 0.5f, headerH))
                .radius(new QuadRadiusState(4, 0, 0, 4))
                .color(new QuadColorState(orbital[0], orbital[1], orbital[2], orbital[3]))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        float headerTextX = x + (curW - Fonts.SFBOLD.get().getWidth(headerText, 10f)) / 2f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), headerText, headerTextX, y + 1f,
                ColorProvider.rgba(255, 255, 255, aInt), 10f);

        float curY = y + headerH + 1f;

        for (Staff staff : activeStaff) {
            float rowAnim = MathHelper.clamp((float) staff.animation.getValue(), 0f, 1f);
            if (rowAnim <= 0.001f) continue;

            float itemH = rowH * rowAnim;
            int itemA = MathHelper.clamp((int) (aInt * rowAnim), 0, 255);

            if (itemA >= 4) {
                float textY = curY + (itemH / 2f) - (fontSize / 2f) - 1f;

                int nameColor;
                if (staff.status == Status.VANISHED || staff.isSpec) nameColor = ColorProvider.rgba(255, 50, 50, itemA);
                else if (mc.world != null && mc.world.getPlayers().stream()
                        .anyMatch(p -> p.getName().getString().equals(staff.name))) nameColor = ColorProvider.rgba(255, 215, 0, itemA);
                else nameColor = ColorProvider.rgba(220, 220, 220, itemA);

                DrawUtil.drawText(Fonts.SFBOLD.get(), staff.prefix, x + padL, textY, fontSize, itemA);

                int statusColor;
                if (staff.status == Status.VANISHED || staff.isSpec) statusColor = ColorProvider.rgba(255, 50, 50, itemA);
                else if (mc.world != null && mc.world.getPlayers().stream()
                        .anyMatch(p -> p.getName().getString().equals(staff.name))) statusColor = ColorProvider.rgba(255, 215, 0, itemA);
                else statusColor = ColorProvider.rgba(50, 255, 50, itemA);

                float r = 2.5f;
                float cx = x + curW - padR - (r * 2f);
                float cy = curY + (itemH / 2f) - r;

                DrawUtil.drawRound(cx, cy, r * 2f, r * 2f, r, statusColor);
            }

            curY += itemH;
        }

        if (emptyAnimVal > 0.001f) {
            float itemH = rowH * emptyAnimVal;
            int itemA = MathHelper.clamp((int) (aInt * emptyAnimVal), 0, 255);

            if (itemA >= 4) {
                float textY = curY + (itemH / 2f) - (fontSize / 2f) - 1;
                float textX = x + (curW - Fonts.SFBOLD.get().getWidth(placeholderText, fontSize)) / 2f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, textX, textY,
                        ColorProvider.rgba(255, 205, 70, itemA), fontSize);
            }

            curY += itemH;
        }

        staffListDrag.setWidth(curW);
        staffListDrag.setHeight(totalH);
    }

    private void renderStaffListClassic(DrawContext context) {
        float posX = staffListDrag.getX();
        float posY = staffListDrag.getY();

        float defaultWidth = 64;
        float height = 14.5f;

        boolean isFound = false;
        if (!staffPlayers.isEmpty()) {
            alpha2.run(1);
            isFound = true;
        }

        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha2.run(0);
        if (mc.currentScreen instanceof ChatScreen) alpha2.run(1);

        float globalAlpha = (float) alpha2.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        drawBackground(posX, posY, (float) widthAnim2.getValue(), 14.5f, 3, headerAlpha);

        DrawUtil.drawRound(posX + 15.25f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(88,88,88, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "O", posX + 4.25f, posY + 4.5f, ColorProvider.setAlpha(-1, headerAlpha), 8.5f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Staff Online", posX + 19.5f, posY + 3.25f, ColorProvider.rgba(255,255,255, headerAlpha), 7.5f);

        posY += 14.5f;
        float bindWidth = 0;

        float headOffset = 12f;

        for (Staff staff : staffPlayers) {
            staff.animation.run(staff.isOnServer ? 1 : 0);
            float localBindWidth = headOffset + Fonts.SFREGULAR.get().getWidth(staff.prefix, 6.75f) + Fonts.SFREGULAR.get().getWidth(staff.status.string, 6.75f);
            if (localBindWidth > bindWidth) {
                bindWidth = localBindWidth;
            }
        }

        for (Staff staff : staffPlayers) {
            float animVal = (float) staff.animation.getValue();
            if (animVal <= 0.001f) continue;

            float heightFactor = Math.min(1.0f, animVal);
            float itemHeight = 11 * heightFactor;
            height += itemHeight;

            float alphaFactor = Math.min(1.0f, Math.max(0.0f, animVal));
            int itemAlpha = (int) (255 * alphaFactor * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String name = staff.name;
            Text prefix = staff.prefix;

            float elementsWidth = headOffset + Fonts.SFREGULAR.get().getWidth(prefix, 6.75f) + 15;
            float textYOffset = (itemHeight / 2f) - (3f);

            drawBackground(posX, posY, (float) widthAnim2.getValue(), itemHeight, 3, itemAlpha);

            DrawUtil.drawRound((float) (posX + widthAnim2.getValue() - 11.25f), posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(125,125,125, itemAlpha));

            float headSize = 8f;
            float headX = posX + 3f;
            float headY = posY + textYOffset - 1f;

            net.minecraft.util.Identifier skinTexture;
            PlayerListEntry playerEntry = mc.getNetworkHandler().getPlayerListEntry(name);
            if (playerEntry != null) {
                skinTexture = playerEntry.getSkinTextures().texture();
            } else {
                skinTexture = DefaultSkinHelper.getTexture();
            }

            int textureId = mc.getTextureManager().getTexture(skinTexture).getGlId();

            tech.onetap.util.render.renderers.impl.BuiltTexture headBuilt = Builder.texture()
                    .size(new SizeState(headSize, headSize))
                    .radius(new QuadRadiusState(2))
                    .color(new QuadColorState(ColorProvider.setAlpha(-1, itemAlpha)))
                    .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, textureId)
                    .smoothness(1f)
                    .build();

            headBuilt.render(context.getMatrices().peek().getPositionMatrix(), headX, headY);

            DrawUtil.drawText(Fonts.SFMEDIUM.get(), prefix, posX + 2f + headOffset, posY + textYOffset - 0.5f, 6.5f, itemAlpha);

            DrawUtil.drawRound((float) (posX + widthAnim2.getValue() - 8), posY + textYOffset + 1f, 5, 5, 2, staff.status == Status.NONE ? ColorProvider.rgba(32,255,32, itemAlpha) : ColorProvider.rgba(255,32,32, itemAlpha));

            if (elementsWidth > defaultWidth) {
                defaultWidth = elementsWidth;
            }

            posY += itemHeight;
        }

        widthAnim2.run(defaultWidth);
        staffListDrag.setWidth((float) widthAnim2.getValue());
        staffListDrag.setHeight(height);
    }
    private final Animation widthAnim3 = new Animation(Easing.EXPO_OUT, 200);
    private final Animation xLine2 = new Animation(Easing.EXPO_OUT, 170);
    private final Animation alpha3 = new Animation(Easing.EXPO_OUT, 200);
    private final Animation potionsEmptyAnim = new Animation(Easing.EXPO_OUT, 233);
    private void renderPotions(DrawContext context) {
        if (mc.player == null) return;

        if (hudStyle.is("Celestial")) {
            renderPotionsCelestial(context);
        } else if (hudStyle.is("Old")) {
            renderPotionsOld(context); // Наш новый режим
        } else {
            renderPotionsClassic(context);
        }
    }

    private void renderPotionsOld(DrawContext context) {
        potionItems.sort(Comparator.comparing(pi -> pi.name));
        List<PotionItem> visible = potionItems.stream().filter(pi -> pi.active).toList();
        if (visible.isEmpty() && !(mc.currentScreen instanceof ChatScreen)) return;

        float posX = potionsDrag.getX();
        float posY = potionsDrag.getY();
        float fontSize = 7.5f;
        MsdfFont font = Fonts.SFMEDIUM.get();

        DrawUtil.drawText(font, "[ Potions ]", posX, posY, ColorProvider.getThemeColor(), fontSize);
        posY += 10f;

        float maxWidth = font.getWidth("[ Potions ]", fontSize);

        for (PotionItem item : visible) {
            int totalSec = Math.max(0, item.durationTicks / 20);
            String time = String.format("%d:%02d", totalSec / 60, totalSec % 60);
            String text = item.name + " " + (item.amplifier + 1) + " -> " + time;

            DrawUtil.drawText(font, text, posX, posY, ColorProvider.rgba(235, 235, 235, 255), fontSize);

            float rowWidth = font.getWidth(text, fontSize);
            if (rowWidth > maxWidth) maxWidth = rowWidth;

            posY += 9f;
        }

        potionsDrag.setWidth(maxWidth);
        potionsDrag.setHeight(posY - potionsDrag.getY());
    }

    private void renderPotionsCelestial(DrawContext context) {
        if (mc.player == null) return;

        final boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        potionItems.sort(Comparator.comparing(pi -> pi.name));

        List<PotionItem> visible = new ArrayList<>();
        for (PotionItem item : potionItems) {
            item.animation.run(item.active ? 1f : 0f);
            if (item.animation.getValue() > 0.01f) visible.add(item);
        }

        boolean showPlaceholder = chatOpen && visible.isEmpty();
        potionsEmptyAnim.run(showPlaceholder ? 1f : 0f);
        alpha3.run((visible.isEmpty() && !chatOpen) ? 0f : 1f);

        float globalAlpha = (float) alpha3.getValue();
        if (globalAlpha <= 0.05f) return;

        int aInt = MathHelper.clamp((int) (255f * globalAlpha), 0, 255);
        float emptyAnimVal = MathHelper.clamp((float) potionsEmptyAnim.getValue(), 0f, 1f);

        final String headerText = "Potions";
        final String placeholderText = "No active effects";

        // === BASE как в Keybinds ===
        final float fontSize = 7.5f;
        final float headerH = 14f;
        final float rowH = 9.5f;
        final float padL = 5f;
        final float padR = 5f;

        float targetWidth = 70f; // как у Keybinds

        for (PotionItem item : visible) {
            int totalSec = Math.max(0, item.durationTicks / 20);
            int minutes = totalSec / 60;
            int sec = totalSec % 60;
            String time = String.format("%d:%02d", minutes, sec);

            int lvl = item.amplifier + 1;
            String lvlText = "   " + lvl;

            float nameW = Fonts.SFBOLD.get().getWidth(item.name, fontSize);
            float lvlW = Fonts.SFBOLD.get().getWidth(lvlText, fontSize);
            float timeW = Fonts.SFBOLD.get().getWidth(time, fontSize);

            float rowW = padL + nameW + lvlW + 10f + timeW + padR;
            targetWidth = Math.max(targetWidth, rowW);
        }

        if (emptyAnimVal > 0.001f) {
            targetWidth = Math.max(targetWidth, Fonts.SFBOLD.get().getWidth(placeholderText, fontSize) + 14f);
        }

        widthAnim3.run(targetWidth);
        float curW = Math.max(70f, (float) widthAnim3.getValue());

        float rowsHeight = 0f;
        for (PotionItem item : visible) {
            rowsHeight += rowH * MathHelper.clamp((float) item.animation.getValue(), 0f, 1f);
        }
        rowsHeight += rowH * emptyAnimVal;

        float totalH = headerH + rowsHeight + (rowsHeight > 0f ? 3f : 1f);

        float x = potionsDrag.getX();
        float y = potionsDrag.getY();

        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();
        int[] orbital = ColorProvider.getOrbitalRect(t1, t2, 300.0, aInt);
        int[] glow = ColorProvider.getOrbitalRect(t1, t2, 300.0, (int) (110 * globalAlpha));
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();

        drawCelestialGlow(m, x, y, curW, totalH, 4f, globalAlpha);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, curW + 1f, totalH + 1f, 4f, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, curW, totalH, 4f, ColorProvider.rgba(14, 10, 6, aInt));

        Builder.rectangle()
                .size(new SizeState(curW + 0.5f, headerH))
                .radius(new QuadRadiusState(4, 0, 0, 4))
                .color(new QuadColorState(orbital[0], orbital[1], orbital[2], orbital[3]))
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x, y);

        float headerTextX = x + (curW - Fonts.SFBOLD.get().getWidth(headerText, 10f)) / 2f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), headerText, headerTextX, y + 1f,
                ColorProvider.rgba(255, 255, 255, aInt), 10f);

        float curY = y + headerH + 1f;

        for (PotionItem item : visible) {
            float rowAnim = MathHelper.clamp((float) item.animation.getValue(), 0f, 1f);
            if (rowAnim <= 0.001f) continue;

            float itemH = rowH * rowAnim;
            int itemA = MathHelper.clamp((int) (aInt * rowAnim), 0, 255);

            if (itemA >= 4) {
                int totalSec = Math.max(0, item.durationTicks / 20);
                int minutes = totalSec / 60;
                int sec = totalSec % 60;
                String time = String.format("%d:%02d", minutes, sec);

                int lvl = item.amplifier + 1;
                String lvlText = "   " + lvl;

                float timeW = Fonts.SFBOLD.get().getWidth(time, fontSize);
                float timeX = x + curW - timeW - padR;

                float leftX = x + padL;
                float textY = curY + (itemH / 2f) - (fontSize / 2f) - 1f; // как в Keybinds

                float clipW = Math.max(0f, (timeX - 6f) - leftX);
                Scissor.push();
                Scissor.setFromComponentCoordinates(leftX, curY, clipW, itemH);

                DrawUtil.drawText(Fonts.SFBOLD.get(), item.name, leftX, textY,
                        ColorProvider.rgba(233, 233, 233, itemA), fontSize);

                float nameW = Fonts.SFBOLD.get().getWidth(item.name, fontSize);

                int lvlColor = (lvl >= 2)
                        ? ColorProvider.rgba(192, 100, 106, itemA)
                        : ColorProvider.rgba(200, 200, 200, itemA);
                if(lvl > 1){
                    DrawUtil.drawText(Fonts.SFBOLD.get(), lvlText, leftX + nameW, textY, lvlColor, fontSize);

                }

                Scissor.unset();
                Scissor.pop();

                DrawUtil.drawText(Fonts.SFBOLD.get(), time, timeX, textY,
                        ColorProvider.rgba(200, 200, 200, itemA), fontSize);
            }

            curY += itemH;
        }

        if (emptyAnimVal > 0.001f) {
            float itemH = rowH * emptyAnimVal;
            int itemA = MathHelper.clamp((int) (aInt * emptyAnimVal), 0, 255);

            if (itemA >= 4) {
                float textY = curY + (itemH / 2f) - (fontSize / 2f);
                float textX = x + (curW - Fonts.SFBOLD.get().getWidth(placeholderText, fontSize)) / 2f;
                DrawUtil.drawText(Fonts.SFBOLD.get(), placeholderText, textX, textY,
                        ColorProvider.rgba(255, 205, 70, itemA), fontSize);
            }

            curY += itemH;
        }

        potionsDrag.setWidth(curW);
        potionsDrag.setHeight(totalH);
    }
    private void renderPotionsClassic(DrawContext context) {
        if (mc.player == null) return;

        float posX = potionsDrag.getX();
        float posY = potionsDrag.getY();

        float headerIconW = Fonts.ICONS_NURIK.get().getWidth("E", 8);
        float headerTextW = Fonts.SFMEDIUM.get().getWidth("Active Potions", 7.5f);
        float defaultWidth = headerIconW + headerTextW + 30;

        float height = 14.5f;

        potionItems.sort(java.util.Comparator.comparing(pi -> pi.name));

        boolean isFound = false;

        for (PotionItem item : potionItems) {
            item.animation.run(item.active ? 1 : 0);
            if (item.animation.getValue() > 0.001f) {
                isFound = true;
            }
            int seconds = item.durationTicks / 20;
            int minutes = seconds / 60;
            int sec = seconds % 60;
            String duration = String.format("%d:%02d", minutes, sec);

            float nameW = Fonts.SFREGULAR.get().getWidth(item.name, 6.5f);
            float ampW = (item.amplifier >= 1 ? Fonts.SFREGULAR.get().getWidth(" " + (item.amplifier + 1), 6.5f) : 0);
            float timeW = Fonts.SFREGULAR.get().getWidth(duration, 6.5f);

            float moduleWidth = nameW + ampW + timeW + 45;

            if (moduleWidth > defaultWidth) {
                defaultWidth = moduleWidth;
            }
        }

        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha3.run(0);
        else alpha3.run(1);

        float globalAlpha = (float) alpha3.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        widthAnim3.run(defaultWidth);

        float currentWidth = Math.max(20, (float) widthAnim3.getValue());

        drawBackground(posX, posY, currentWidth - 3, height, 3, headerAlpha);

        DrawUtil.drawRound(posX + 13.75f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(88,88,88, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "E", posX + 4, posY + 3.75f, ColorProvider.rgba(255,255,255, headerAlpha), 8);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Active Potions", posX + 18f, posY + 3.25f, ColorProvider.rgba(255,255,255, headerAlpha), 7.5f);

        posY += 14.5f;

        xLine2.run(12);

        for (PotionItem item : potionItems) {
            float animVal = (float) item.animation.getValue();
            if (animVal <= 0.001f) continue;

            float heightFactor = Math.min(1.0f, animVal);
            float itemHeight = 12 * heightFactor;
            height += itemHeight;

            float alphaFactor = Math.min(1.0f, Math.max(0.0f, animVal));
            int itemAlpha = (int) (255 * alphaFactor * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String moduleName = item.name;
            int seconds = item.durationTicks / 20;
            int minutes = seconds / 60;
            int sec = seconds % 60;
            String bind = String.format("%d:%02d", minutes, sec);

            float textYOffset = (itemHeight / 2f) - (3f);

            drawBackground(posX, posY, currentWidth - 3, itemHeight, 3, itemAlpha);

            float separatorX = (float) (posX + currentWidth - 6.5f - xLine2.getValue());

            DrawUtil.drawRound(separatorX, posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(88,88,88, itemAlpha));

            DrawUtil.drawText(Fonts.SFREGULAR.get(), moduleName, posX + 4, posY + textYOffset - 0.5f, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            if (item.amplifier >= 1) {
                DrawUtil.drawText(Fonts.SFREGULAR.get(), String.valueOf(item.amplifier + 1), posX + 6 + Fonts.SFREGULAR.get().getWidth(moduleName, 6.75f), posY + textYOffset - 0.5f, ColorProvider.setAlpha(ColorProvider.rgba(211,211,211,255), itemAlpha), 6.5f);
            }

            float timeWidth = Fonts.SFREGULAR.get().getWidth(bind, 6.75f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), bind, separatorX - timeWidth - 3f, posY + textYOffset - 0.5f, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            net.minecraft.client.texture.Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(item.effect);
            if (sprite != null) {
                RenderSystem.setShaderColor(1f, 1f, 1f, (itemAlpha / 255f));
                float iconSize = 9;
                float iconX = separatorX + 3.5f;
                float iconY = posY + (itemHeight - iconSize) / 2f;

                int color = (itemAlpha << 24) | 0xFFFFFF;

                context.drawSpriteStretched(
                        net.minecraft.client.render.RenderLayer::getGuiTextured,
                        sprite,
                        (int) iconX,
                        (int) iconY,
                        (int) iconSize,
                        (int) iconSize,
                        color
                );
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            posY += itemHeight;
        }

        widthAnim3.run(defaultWidth);
        potionsDrag.setWidth((float) widthAnim3.getValue());
        potionsDrag.setHeight(height);
    }
    public void update() {
        StaffManager.checkScoreboardTeams();

        for (Staff staff : staffPlayers) {
            staff.isOnServer = false;
        }

        for (PlayerListEntry playerListEntry : mc.getNetworkHandler().getPlayerList()) {
            String name = playerListEntry.getProfile().getName().replaceAll("[\\[\\]]", "");
            PlayerListEntry info = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(name);
            boolean vanish = info == null;
            boolean isGM3 = info != null && info.getGameMode() == GameMode.SPECTATOR;
            boolean vanishByPacket = StaffManager.isVanishDetectedRecently(name);

            boolean matchesPrefix = prefixMatches.matcher(playerListEntry.getDisplayName() != null ? playerListEntry.getDisplayName().getString().toLowerCase(Locale.ROOT) : "").matches();
            boolean isValidName = namePattern.matcher(name).matches();
            boolean notSelf = !name.equals(MinecraftClient.getInstance().player.getName().getString());

            if ((isValidName && notSelf && matchesPrefix) || (isValidName && notSelf && vanish) || (isValidName && notSelf && vanishByPacket) || StaffManager.isStaff(name)) {
                if (StaffManager.isStaff(name)) {
                    String[] names = new String[]{"auction", "exp_smith", "shop_balls", "shop_grief", "free", "shop_kits", "siege", "rwplus", "bossfight", "guide", "shop_smith", "shop_spawners", "colliseum", "battlepass", "buyer", "huckster", "buff_brewer", "killer", "shop_mage"};
                    boolean contains = false;
                    if (MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address != null && (MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.rwdonat.pw") || MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.cakeworld.pw"))) {
                        for (int i = 0; i < Arrays.stream(names).count(); i++) {
                            if (name.contains(names[i])) {
                                contains = true;
                                break;
                            }
                        }
                    }
                    if (contains) continue;
                }
                Optional<Staff> existingStaff = staffPlayers.stream().filter(s -> s.name.equals(name)).findFirst();

                Status status = (vanish || vanishByPacket) ? Status.VANISHED : (isGM3 ? Status.VANISHED : Status.NONE);

                if (existingStaff.isPresent()) {
                    Staff s = existingStaff.get();
                    s.isOnServer = true;
                    s.status = status;
                } else {
                    String[] names = new String[]{"auction", "exp_smith", "shop_balls", "shop_grief", "free", "shop_kits", "siege", "rwplus", "bossfight", "guide", "shop_smith", "shop_spawners", "colliseum", "battlepass", "buyer", "huckster", "buff_brewer", "killer", "shop_mage"};
                    boolean contains = false;
                    if (MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address != null && (MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.rwdonat.pw") || MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.cakeworld.pw"))) {
                        for (int i = 0; i < Arrays.stream(names).count(); i++) {
                            if (name.contains(names[i])) {
                                contains = true;
                            }
                        }
                    }
                    if (!contains) {
                        Text originalPrefix = playerListEntry.getDisplayName();
                        Text prefix = originalPrefix;
                        if (prefix != null) {
                            prefix = ReplaceUtil.replaceSymbols(prefix);
                            String fullString = prefix.getString();
                            int nickIndex = fullString.indexOf(name);
                            if (nickIndex != -1) {
                                int endIndex = nickIndex + name.length();
                                if (endIndex < fullString.length()) {
                                    net.minecraft.text.MutableText newText = Text.empty();
                                    int currentLength = 0;
                                    net.minecraft.text.MutableText baseCopy = prefix.copy();
                                    baseCopy.getSiblings().clear();
                                    String mainContent = baseCopy.getString();

                                    if (!mainContent.isEmpty() && currentLength < endIndex) {
                                        int takeLength = Math.min(mainContent.length(), endIndex - currentLength);
                                        newText.append(Text.literal(mainContent.substring(0, takeLength)).setStyle(prefix.getStyle()));
                                        currentLength += takeLength;
                                    }

                                    for (Text sibling : prefix.getSiblings()) {
                                        if (currentLength >= endIndex) break;
                                        net.minecraft.text.MutableText siblingCopy = sibling.copy();
                                        siblingCopy.getSiblings().clear();
                                        String siblingContent = siblingCopy.getString();

                                        int takeLength = Math.min(siblingContent.length(), endIndex - currentLength);
                                        if (takeLength > 0) {
                                            newText.append(Text.literal(siblingContent.substring(0, takeLength)).setStyle(sibling.getStyle()));
                                            currentLength += takeLength;
                                        }
                                    }

                                    prefix = newText;
                                }
                            }
                        }
                        Staff staff = new Staff(prefix == null ? Text.of(playerListEntry.getProfile().getName()) : prefix, name, vanish || isGM3 || vanishByPacket, status);
                        staff.isOnServer = true;
                        staffPlayers.add(staff);
                    }
                }
            }
        }

        staffPlayers.removeIf(staff -> !staff.isOnServer && staff.animation.getValue() == 0);
    }

    public enum Status {
        NONE("", -1),
        VANISHED("SPEC", ColorProvider.rgba(229, 0, 63, 255));

        public final String string;
        public final int color;

        Status(String string, int color) {
            this.string = string;
            this.color = color;
        }
    }

    public static class Staff {
        Text prefix;
        public String name;
        boolean isSpec;
        Status status;
        boolean isOnServer;
        Animation animation;
        long mills;

        public Staff(Text prefix, String name, boolean isSpec, Status status) {
            this.prefix = prefix;
            this.name = name;
            this.isSpec = isSpec;
            this.status = status;
            animation = new Animation(Easing.EXPO_OUT, 233);
            mills = System.currentTimeMillis();
        }
    }

    public int getPing(PlayerEntity entity) {
        PlayerListEntry list = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return list != null ? list.getLatency() : 0;
    }

    private void renderWatermark(DrawContext context) {
        if (mc.player == null) return;

        if (hudStyle.is("Celestial")) {
            renderWatermarkCelestial(context);
        } else if (hudStyle.is("Old")) {
            renderWatermarkOld(context);
        } else {
            renderWatermarkNursultan(context);
        }
    }

    private void renderWatermarkOld(DrawContext context) {
        float posX = watermarkDrag.getX();
        float posY = watermarkDrag.getY();
        float fontSize = 7.5f;
        MsdfFont font = Fonts.SFBOLD.get();

        int fps = mc.getCurrentFps();
        int ping = 0;
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
            ping = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency();
        }

        String name = mc.player.getName().getString();
        String coords = String.format("%d %d %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        String text = String.format("Ancient | %s | %d fps | %d ms | %s", name, fps, ping, coords);

        DrawUtil.drawText(font, text, posX, posY, ColorProvider.getThemeColor(), fontSize);

        float width = font.getWidth(text, fontSize);
        watermarkDrag.setWidth(width);
        watermarkDrag.setHeight(fontSize + 2f);
    }

    private void renderWatermarkCelestial(DrawContext context) {
        Counter.updateFPS();

        String userText = "plsmeow";
        String uid = "UID: 1337";
        String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));

        String fullText = "Ancient | " + userText + " | " + uid + " | " + timeText;
        float fontSize = 7.5f;

        float x = watermarkDrag.getX();
        float y = watermarkDrag.getY();
        float height = 14f;
        float width = Fonts.SFBOLD.get().getWidth(fullText, fontSize) + 12f;

        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();
        int[] glow = ColorProvider.getOrbitalRect(t1, t2, 300.0, 110);
        int[] orbital = ColorProvider.getOrbitalRect(t1, t2, 300.0, 255);
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();

        drawCelestialGlow(m,x, y, width, height, 4f, 1.0f);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, width + 1f, height + 1f, 4f, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, width, height, 4f, ColorProvider.rgba(14, 10, 6, 255));



        float textY = y + (height - fontSize) / 2f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), fullText, x + 3f, textY, ColorProvider.rgba(255, 255, 255, 255), fontSize);

        watermarkDrag.setWidth(width);
        watermarkDrag.setHeight(height);
    }

    private void renderWatermarkNursultan(DrawContext context) {
        Counter.updateFPS();

        String userText = "plsmeow";
        String fpsValue = Counter.getCurrentFPS() + " Fps";
        String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String coordsText = (int) mc.player.getX() + " " + (int) mc.player.getY() + " " + (int) mc.player.getZ();
        String pingText = Server.getPing(mc.player) + " Ping";
        String tpsText = String.format("%.1f Ticks", Onetap.getInstance().getTpsGetter().getTPS());
        double dX = mc.player.getX() - mc.player.prevX;
        double dZ = mc.player.getZ() - mc.player.prevZ;
        String speedText = String.format("%.1f Bps", Math.hypot(dX, dZ) * 20);

        float x = watermarkDrag.getX();
        float y = watermarkDrag.getY();
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

        drawBackground(x, y, firstBoxWidth, height, 4, 255);

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

        drawBackground(firstRowX, y, wCombined, height, 4, 255);

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
        drawBackground(x, y, 17, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0055", x + 4.5f, y + 4.25f, animatedThemeColor2, 8f);
        x += 17 + gap;

        float wCoords = 17 + Fonts.SFMEDIUM.get().getWidth(coordsText, 7f) + 4;
        drawBackground(x, y, wCoords, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0046", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 13, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), coordsText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wCoords + gap;

        float wPing = 17 + Fonts.SFMEDIUM.get().getWidth(pingText, 7f) + 4;
        drawBackground(x, y, wPing, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0051", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 13.5f, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), pingText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wPing + gap;

        float wTps = 17 + Fonts.SFMEDIUM.get().getWidth(tpsText, 7f) + 4;
        drawBackground(x, y, wTps, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0054", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 13, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), tpsText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wTps + gap;

        float wSpeed = 20 + Fonts.SFMEDIUM.get().getWidth(speedText, 7f) + 4;
        drawBackground(x, y, wSpeed, height, 4, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0040", x + 4, y + 4.25f, themeColor, 8f);
        DrawUtil.drawRound(x + 15, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), speedText, x + 20, y + 3.5f, whiteColor, 7f);

        float row2Width = (x + wSpeed) - startX;

        watermarkDrag.setWidth(Math.max(row1Width, row2Width));
        watermarkDrag.setHeight((height * 2) + gap);
    }
    private int colorLerp(int start, int end, float speed, float offset) {
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

    private void renderCoordsInfo(DrawContext context) {}

    private void renderSpeed(DrawContext context) {
        if (mc.player == null) return;

        double deltaX = mc.player.getX() - mc.player.prevX;
        double deltaY = mc.player.getY() - mc.player.prevY;
        double deltaZ = mc.player.getZ() - mc.player.prevZ;
        double speedBps = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 20;

        String text = String.format(java.util.Locale.US, "%.2f", speedBps);
        float textWidth = Fonts.SFREGULAR.get().getWidth(text, 9);

        float x = mc.getWindow().getScaledWidth() / 2f - (textWidth / 2f);
        float y = mc.getWindow().getScaledHeight() / 2f + 12f;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, x, y, -1, 9);
    }
    private MsdfFont resolveFont(String mode) {
        return switch (mode) {
            case "SemiBold" -> Fonts.SFSEMIBOLD.get();
            case "Medium" -> Fonts.SFMEDIUM.get();
            case "Regular" -> Fonts.SFREGULAR.get();
            case "Rubik" -> Fonts.RUBIK.get();
            default -> Fonts.SFBOLD.get();
        };
    }

    private void renderMACEDMG(DrawContext context) {
        if (mc.player == null) return;

        // 1. Проверяем наличие булавы в инвентаре игрока
        boolean hasMace = false;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && stack.isOf(net.minecraft.item.Items.MACE)) {
                hasMace = true;
                break;
            }
        }
        if (!hasMace) {
            net.minecraft.item.ItemStack offHandStack = mc.player.getOffHandStack();
            if (offHandStack != null && offHandStack.isOf(net.minecraft.item.Items.MACE)) {
                hasMace = true;
            }
        }

        if (!hasMace) return;

        // 2. Получаем текущую дистанцию падения
        float fallDistance = mc.player.fallDistance;

        // Базовый урон булавы при обычном ударе на земле (6 HP)
        double totalDamage = 0.0;
        boolean canSmash = fallDistance >= 1.5f;

        // Удар в падении (Smash attack) активируется при падении с 1.5 блоков и более
        if (canSmash) {
            double heightDamage = 0.0;
            float blocksFallen = fallDistance;

            // Расчет чистого урона по стадиям высоты:
            // Первые 3 блока (+4 HP за каждый блок)
            if (blocksFallen > 3.0f) {
                heightDamage += 3.0f * 4.0;
                blocksFallen -= 3.0f;

                // Следующие 5 блоков (+2 HP за каждый блок)
                if (blocksFallen > 5.0f) {
                    heightDamage += 5.0f * 2.0;
                    blocksFallen -= 5.0f;

                    // Все последующие блоки падения (+1 HP за каждый блок)
                    heightDamage += blocksFallen * 1.0;
                } else {
                    heightDamage += blocksFallen * 2.0;
                }
            } else {
                heightDamage += blocksFallen * 4.0;
            }

            // Суммируем базу и урон от высоты, затем умножаем на 1.5 (критический удар)
            totalDamage = (totalDamage + heightDamage) * 1.5;
        }

        // 3. Определение цвета текста в зависимости от условий
        int textColor;
        if (!canSmash) {
            textColor = -1; // Белый цвет (игрок на земле или падает меньше 1.5 блоков)
        } else if (totalDamage < 10.0) {
            textColor = 0xFF55FF55; // Светло-зеленый
        } else if (totalDamage < 50.0) {
            textColor = 0xFFFFFF55; // Желтый
        } else if (totalDamage < 100.0) {
            textColor = 0xFFFFAA00; // Оранжевый
        } else {
            textColor = 0xFFFF5555; // Светло-красный (100 HP и больше)
        }

        // 4. Форматирование текста (выводим чистый урон, например: "18.0 DMG")
        String text = String.format(java.util.Locale.US, "%.1f DMG", totalDamage);
        float textWidth = Fonts.SFREGULAR.get().getWidth(text, 9);

        float x = mc.getWindow().getScaledWidth() / 2f - (textWidth / 2f);
        float y = mc.getWindow().getScaledHeight() / 2f + 32f;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, x, y, textColor, 9);
    }


    private void renderTargetHUDCelestial(DrawContext context) {
        LivingEntity target = getTargetHudTarget();

        if (target != null) {
            lastTarget = target;
            animation.run(1);
            armorAnim.run(1); // Анимация брони
        } else {
            animation.run(0);
            armorAnim.run(0);
        }

        if (animation.getValue() <= 0.05f || lastTarget == null || !(lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) lastTarget;
        AbstractClientPlayerEntity playerEntity = lastTarget instanceof AbstractClientPlayerEntity ? (AbstractClientPlayerEntity) lastTarget : null;

        float anim = (float) animation.getValue();
        int alphaInt = (int) (255 * anim);

        // ОРИГИНАЛЬНЫЕ РАЗМЕРЫ: Высота 37, как в исходном коде
        float width = 100, height = 37, x = targetHUDDrag.getX(), y = targetHUDDrag.getY();
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();

        drawCelestialGlow(m, x, y, width, height, 5, anim);
        int theme1 = ColorProvider.getThemeColor(), theme2 = ColorProvider.getThemeColorTwo();
        int[] glow = ColorProvider.getOrbitalRect(theme1, theme2, 300.0, (int)(110 * anim));

        DrawUtil.drawRound(x - 0.5F, y - 0.5F, width + 1F, height + 1F, 5, glow[0], glow[1], glow[2], glow[3]);
        DrawUtil.drawRound(x, y, width, height, 5, ColorProvider.rgba(14, 10, 6, alphaInt));

        float headSize = 30, headX = x + 2.5f, headY = y + 3.75f;
        int headColor = ColorProvider.rgba(255, (int)(255 * (1 - livingEntity.hurtTime / 10f)), (int)(255 * (1 - livingEntity.hurtTime / 10f)), alphaInt);

        if (playerEntity != null) {
            try {
                int texId = mc.getTextureManager().getTexture(playerEntity.getSkinTextures().texture()).getGlId();
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
                DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "N", headX + 1.5f, headY + 8f, ColorProvider.rgba(255, 255, 255, alphaInt), 24f);
            }
        }

        float textX = headX + headSize + 2;
        String name = livingEntity.getName().getString();
        DrawUtil.drawText(Fonts.SFBOLD.get(), name, textX, y + 5, ColorProvider.rgba(230, 230, 230, alphaInt), 9f, 0.3f, 0.7f, width);

        // Замените старое получение здоровья на это:
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

        hpAnimation.run(barWidth * MathHelper.clamp(currentHp / maxHealth, 0, 1));
        float hpWNow = (float) hpAnimation.getValue();
        float animatedHp = (barWidth > 0) ? (hpWNow / barWidth) * maxHealth : currentHp;

        String hpText = String.format(java.util.Locale.US, "HP: %.1f", animatedHp);
        float fontSize = 6.5f, hpY = y + 14f;
        DrawUtil.drawText(Fonts.SFBOLD.get(), hpText, textX + 0.5F, hpY, ColorProvider.rgba(230, 230, 230, alphaInt), fontSize);

        if (absHp > 0.05f) {
            String absText = String.format(java.util.Locale.US, "%.1f AB", absHp);
            float hpW = Fonts.SFBOLD.get().getWidth(hpText, fontSize), plusW = Fonts.SFBOLD.get().getWidth("  + ", fontSize);
            DrawUtil.drawText(Fonts.SFBOLD.get(), "  + ", textX + hpW, hpY, ColorProvider.rgba(160, 160, 160, alphaInt), fontSize);
            DrawUtil.drawText(Fonts.SFBOLD.get(), absText, textX + hpW + plusW, hpY, ColorProvider.rgba(255, 205, 70, alphaInt), fontSize);
        }

        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1.5f, ColorProvider.rgba(45, 45, 45, alphaInt));
        if (hpWNow > 0.5f) {
            int[] colors = ColorProvider.getOrbitalRect(theme1, theme2, 300.0, alphaInt);
            float rR = (hpWNow >= barWidth - 1f || hpWNow <= barHeight) ? 1.5f : 0f;
            Builder.rectangle().size(new SizeState(hpWNow, barHeight)).radius(new QuadRadiusState(1.5f, 1.5f, rR, rR)).color(new QuadColorState(colors[0], colors[1], colors[2], colors[3])).build().render(context.getMatrices().peek().getPositionMatrix(), barX, barY);
        }

        // РЕНДЕР БРОНИ НАД ХУДОМ (NURSULTAN STYLE)
        float armorAlpha = (float) armorAnim.getValue();
        if (armorAlpha > 0.05f) {
            List<ItemStack> items = new ArrayList<>();
            // Стандартный порядок Nursultan: Armor -> Offhand -> Mainhand
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

                // Рендерим ровно НАД худом (y - высота_слота - отступ)
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

        targetHUDDrag.setWidth(width);
        targetHUDDrag.setHeight(height);
    }

    private void renderTargetHUDOld(DrawContext context) {
        LivingEntity target = getTargetHudTarget();

        if (target == null) return;

        // Позиционирование и единые шрифты/размеры (как в Keybinds/StaffList)
        float x = targetHUDDrag.getX();
        float y = targetHUDDrag.getY();
        float fontSize = 7.5f; 
        MsdfFont font = Fonts.SFMEDIUM.get();

        // Получаем имя цели
        String nameText = target.getName().getString();

        // Расчет корректного здоровья (с поддержкой плагинов/скорборда)
        float currentHp = Math.max(0, target.getHealth());
        ScoreboardHealth sbh = Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class);
        if (sbh != null && sbh.isEnabled() && target instanceof AbstractClientPlayerEntity playerEntity) {
            float scoreboardHp = sbh.getRealHp(playerEntity);
            if (scoreboardHp != -1) {
                currentHp = scoreboardHp;
            }
        }

        // Получаем поглощение (абсорбцию)
        float absHp = Math.max(0, target.getAbsorptionAmount());

        // Форматирование вывода: nick -> hp или nick -> hp + ab
        String targetInfoText;
        if (absHp > 0.05f) {
            targetInfoText = String.format(java.util.Locale.US, "%s -> %.1f + %.1f", nameText, currentHp, absHp);
        } else {
            targetInfoText = String.format(java.util.Locale.US, "%s -> %.1f", nameText, currentHp);
        }

        // Цвет текста строго от текущей темы
        int textColor = ColorProvider.getThemeColor();

        // Рендеринг текста
        DrawUtil.drawText(font, targetInfoText, x, y, textColor, fontSize);

        // Динамическое обновление границ хитбокса под размер текста
        float width = font.getWidth(targetInfoText, fontSize);
        targetHUDDrag.setWidth(width);
        targetHUDDrag.setHeight(9f); // Стандартная высота строки элементов
    }

    private void renderTargetHUDMini(DrawContext context) {
        LivingEntity target = getTargetHudTarget();

        if (target != null) {
            lastTarget = target;
            animation.run(1);
        } else {
            animation.run(0);
        }

        if (animation.getValue() <= 0.05f || lastTarget == null || !(lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) lastTarget;
        float anim = (float) animation.getValue();
        int alphaInt = (int) (255 * anim);

        // === ГЕОМЕТРИЯ ХУДА ===
        // Без головы худ стал намного компактнее (Nursultan Mini)
        float width = 90f;
        float height = 28f;
        float x = targetHUDDrag.getX();
        float y = targetHUDDrag.getY();

        // Фон в стиле Nursultan
        drawBackground(x, y, width, height, 4f, alphaInt);

        // === ИМЯ ИГРОКА ===
        float textX = x + 5f;
        String name = livingEntity.getName().getString();
        DrawUtil.drawText(Fonts.SFBOLD.get(), name, textX, y + 4.5f, ColorProvider.rgba(230, 230, 230, alphaInt), 8f);

        // === ДАННЫЕ ЗДОРОВЬЯ ===
        // === ДАННЫЕ ЗДОРОВЬЯ ===
        float currentHp = Math.max(0, livingEntity.getHealth());
        ScoreboardHealth sbh = Onetap.getInstance().getModuleStorage().get(ScoreboardHealth.class);
        if (sbh != null && sbh.isEnabled() && lastTarget instanceof AbstractClientPlayerEntity playerEntity) {
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
        float barHeight = 3.5f; // Фиксированная толщина 2.5

        // Анимация полосы здоровья
        hpAnimation.run(barWidth * MathHelper.clamp(currentHp / maxHealth, 0, 1));
        float hpWNow = (float) hpAnimation.getValue();
        float animatedHp = (barWidth > 0) ? (hpWNow / barWidth) * maxHealth : currentHp;

        // Отрисовка подложки (задника) полосы
        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1f, ColorProvider.rgba(40, 40, 40, alphaInt));

        // Выбор цвета в зависимости от темы
        int hpLeft, hpRight;
        if (elements.isEnabled("Таргет худ от темы")) {
            hpRight = ColorProvider.setAlpha(ColorProvider.getThemeColor(), alphaInt);
            hpLeft = ColorProvider.setAlpha(ColorProvider.getThemeColorTwo(), alphaInt);
        } else {
            java.awt.Color hpCol = getHealthBarColor(currentHp, maxHealth);
            hpLeft = ColorProvider.rgba((int)(hpCol.getRed() * 0.5), (int)(hpCol.getGreen() * 0.5), (int)(hpCol.getBlue() * 0.5), alphaInt);
            hpRight = ColorProvider.rgba(hpCol.getRed(), hpCol.getGreen(), hpCol.getBlue(), alphaInt);
        }

        // Отрисовка основной полосы здоровья
        if (hpWNow > 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWNow, barHeight, 1f, hpLeft, hpLeft, hpRight, hpRight);
        }

        // Отрисовка полосы AB поверх левого края простой полосы здоровья
        if (absHp > 0.05f) {
            float absPercent = MathHelper.clamp(absHp / maxHealth, 0f, 1f);
            absorptionAnimation.run(barWidth * absPercent);
            float abWNow = (float) absorptionAnimation.getValue();

            if (abWNow > 0.5f) {
                int absLeftColor = ColorProvider.rgba(160, 140, 0, alphaInt);
                int absRightColor = ColorProvider.rgba(255, 215, 0, alphaInt);
                // Рисуется ровно поверх левого края основной полосы
                DrawUtil.drawRound(barX, barY, abWNow, barHeight, 1f, absLeftColor, absLeftColor, absRightColor, absRightColor);
            }
        }

        // === ТЕКСТОВЫЕ ПОКАЗАТЕЛИ (ХП над концом полосы, AB зафиксировано слева над ХП) ===
        String hpText = String.format(java.util.Locale.US, "%.1f", animatedHp);
        float hpTextWidth = Fonts.SFBOLD.get().getWidth(hpText, 6.5f);

        // Текущее ХП динамически следует над концом заполненной полосы
        float hpTextX = barX + hpWNow - (hpTextWidth / 2f);
        hpTextX = MathHelper.clamp(hpTextX, barX, barX + barWidth - hpTextWidth); // зажим в границах
        float hpTextY = barY - 7.5f;

        // Полоса/Текст AB находится СЛЕВА над полосой здоровья зафиксировано
        float absTextX = barX;
        float absTextY = barY - 7.5f;

        if (absHp > 0.05f) {
            String absText = String.format(java.util.Locale.US, "%.1f AB", absHp);
            float absTextWidth = Fonts.SFBOLD.get().getWidth(absText, 6.5f);

            // Защита от наслоения текста ХП на зафиксированный текст AB при низком здоровье:
            // Если позиция текста ХП пытается уйти левее конца текста AB, сдвигаем текст ХП чуть "сзади" (правее)
            float minAllowedHpX = absTextX + absTextWidth + 3f; // 3 пикселя зазора
            if (hpTextX < minAllowedHpX) {
                hpTextX = minAllowedHpX;
            }

            // Отрисовка зафиксированного текста AB
            DrawUtil.drawText(Fonts.SFBOLD.get(), absText, absTextX, absTextY, ColorProvider.rgba(255, 215, 0, alphaInt), 6.5f);
        }

        // Отрисовка динамического текста ХП
        DrawUtil.drawText(Fonts.SFBOLD.get(), hpText, hpTextX, hpTextY, ColorProvider.rgba(225, 225, 225, alphaInt), 6.5f);

        // Обновление размеров драг-зоны
        targetHUDDrag.setWidth(width);
        targetHUDDrag.setHeight(height);
    }

    private void renderTargetHUDClassic(DrawContext context) {
        LivingEntity target = getTargetHudTarget();
        if (target != null) {
            lastTarget = target;
            animation.run(1);
            armorAnim.run(1);
        } else {
            animation.run(0);
            armorAnim.run(0);
        }

        if (animation.getValue() <= 0.05f || lastTarget == null || !(lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) lastTarget;
        AbstractClientPlayerEntity playerEntity = lastTarget instanceof AbstractClientPlayerEntity ? (AbstractClientPlayerEntity) lastTarget : null;

        float anim = (float) animation.getValue();
        int alphaInt = (int) (255 * anim);

        float width = 100;
        float height = 36;
        float x = targetHUDDrag.getX();
        float y = targetHUDDrag.getY();

        drawBackground(x, y, width, height, 7, alphaInt);

        float headSize = 30;
        float headX = x + 3;
        float headY = y + (height - headSize) / 2f;
        float currentHpRaw = livingEntity.getHealth();

        if (lastHpRaw == -1f || lastTarget != livingEntity) {
            lastHpRaw = currentHpRaw;
            headParticles.clear();
        }

        if (currentHpRaw < lastHpRaw) {
            int count = MathHelper.clamp((int)((lastHpRaw - currentHpRaw) * 4), 5, 15);
            for (int i = 0; i < count; i++) {
                headParticles.add(new HeadParticle(headX + headSize / 2f, headY + headSize / 2f, ColorProvider.getThemeColor()));
            }
            lastHpRaw = currentHpRaw;
        } else if (currentHpRaw > lastHpRaw) {
            lastHpRaw = currentHpRaw;
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
                int texId = mc.getTextureManager().getTexture(skin).getGlId();

                tech.onetap.util.render.renderers.impl.BuiltTexture headTexture = Builder.texture()
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
            String absText = String.format(java.util.Locale.US,"(+ %.1f)", absorption);
            float offset = Fonts.SFMEDIUM.get().getWidth(hpText, 6.5f) + 3;
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), absText, textX + offset + 3, y + 15.5f, ColorProvider.rgba(255, 215, 0, alphaInt), 6.5f);
        }

        float barX = textX;
        float barY = y + 25;
        float barWidth = width - 42;
        float barHeight = 7;

        float maxHealth = livingEntity.getMaxHealth();
        float hpPercent = MathHelper.clamp(currentHp / maxHealth, 0, 1);

        hpAnimation.run(barWidth * hpPercent);

        if (hpPercent < lastHpPercent) {
            outdatedHpAnimation.run(barWidth * hpPercent);
        } else {
            outdatedHpAnimation.setValue(hpAnimation.getValue());
        }

        lastHpPercent = hpPercent;

        int hpLeftFull, hpRightFull, hpLeftGhost, hpRightGhost;

        if (elements.isEnabled("Таргет худ от темы")) {
            int c1 = ColorProvider.getThemeColor();
            int c2 = ColorProvider.getThemeColorTwo();

            hpRightFull = ColorProvider.setAlpha(c1, alphaInt);
            hpLeftFull = ColorProvider.setAlpha(c2, alphaInt);

            hpLeftGhost = ColorProvider.setAlpha(c1, (int) (110 * anim));
            hpRightGhost = ColorProvider.setAlpha(c2, (int) (110 * anim));
        } else {
            java.awt.Color baseColor = getHealthBarColor(currentHp, maxHealth);
            int br = baseColor.getRed();
            int bg = baseColor.getGreen();
            int bb = baseColor.getBlue();

            hpLeftFull = ColorProvider.rgba(MathHelper.clamp((int)(br * 0.35f), 0, 255), MathHelper.clamp((int)(bg * 0.35f), 0, 255), MathHelper.clamp((int)(bb * 0.35f), 0, 255), alphaInt);
            hpRightFull = ColorProvider.rgba(br, bg, bb, alphaInt);

            hpLeftGhost = ColorProvider.rgba(br, bg, bb, (int) (110 * anim));
            hpRightGhost = ColorProvider.rgba(MathHelper.clamp((int)(br * 0.35f), 0, 255), MathHelper.clamp((int)(bg * 0.35f), 0, 255), MathHelper.clamp((int)(bb * 0.35f), 0, 255), (int) (110 * anim));
        }

        int backColor;
        if (elements.isEnabled("Таргет худ от темы")) {
            backColor = ColorProvider.rgba(20, 20, 20, (int)(120 * anim));
        } else {
            java.awt.Color baseColor = getHealthBarColor(currentHp, maxHealth);
            backColor = ColorProvider.rgba((int)(baseColor.getRed() * 0.45f), (int)(baseColor.getGreen() * 0.45f), (int)(baseColor.getBlue() * 0.45f), (int)(120 * anim));
        }

        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1.5f, backColor);

        float hpWOld = (float) outdatedHpAnimation.getValue();
        if (hpWOld > 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWOld, barHeight, 1.5f, hpLeftGhost, hpLeftGhost, hpRightGhost, hpRightGhost);
        }

        float hpWNow = (float) hpAnimation.getValue();
        if (hpWNow > 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWNow, barHeight, 1.5f, hpLeftFull, hpLeftFull, hpRightFull, hpRightFull);
        }

        float absPercent = MathHelper.clamp(livingEntity.getAbsorptionAmount() / maxHealth, 0, 1);
        absorptionAnimation.run(barWidth * absPercent);
        float abWNow = (float) absorptionAnimation.getValue();

        if (abWNow > 0.5f) {
            int absLeftColor = ColorProvider.rgba(140, 120, 0, (int)(200 * anim));
            int absRightColor = ColorProvider.rgba(255, 215, 0, (int)(255 * anim));
            DrawUtil.drawRound(barX - 0.25f, barY, abWNow, barHeight, 1.5f,
                    absLeftColor, absLeftColor, absRightColor, absRightColor);
        }

        float armorAlpha = (float) armorAnim.getValue();
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

        targetHUDDrag.setWidth(width);
        targetHUDDrag.setHeight(height);
    }


    private float trailHealthPercent = 1f;
    private float lastHealthPercent = 1f;
    private float lastAbsorptionPercent = 0f;
    private float lastHpRaw = -1f;
    private final List<DamageParticle> damageParticles = new ArrayList<>();

    private LivingEntity getTargetHudTarget() {
        KillAura killAura = Instance.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null && killAura.getTarget().isAlive()) {
            return killAura.getTarget();
        }

        AutoFlyMace autoFlyMaceHud = Instance.get(AutoFlyMace.class);
        if (autoFlyMaceHud != null && autoFlyMaceHud.isEnabled() && autoFlyMaceHud.getTarget() != null && autoFlyMaceHud.getTarget().isAlive()) {
            return autoFlyMaceHud.getTarget();
        }

        BoatAura boatAura = Instance.get(BoatAura.class);
        if (boatAura != null && boatAura.isEnabled() && boatAura.getTarget() != null && boatAura.getTarget().isAlive()) {
            return boatAura.getTarget();
        }

        TpAura tpAura = Instance.get(TpAura.class);
        if (tpAura != null && tpAura.isEnabled() && tpAura.getTarget() != null && tpAura.getTarget().isAlive()) {
            return tpAura.getTarget();
        }

        if (mc.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }

        if (mc.currentScreen instanceof ChatScreen) {
            return mc.player;
        }

        return null;
    }

    private void renderTargetHUDMoonward(DrawContext context) {
        LivingEntity target = getTargetHudTarget();

        if (target != null) {
            lastTarget = target;
            animation.run(1);
            armorAnim.run(1);
        } else {
            animation.run(0);
            armorAnim.run(0);
        }

        float animAlpha = (float) animation.getValue();
        if (animAlpha <= 0.05f || lastTarget == null || !(lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) lastTarget;
        float x = targetHUDDrag.getX();
        float y = targetHUDDrag.getY();
        float width = 105f;
        float height = 36.5f;
        float panelRadius = 6f;

        drawBackground(x, y, width, height, panelRadius, (int) (255 * animAlpha));

        float headSize = 28f;
        float headX = x + width - headSize - 4f;
        float headY = y + (height - headSize) / 2f;
        float headRadius = headSize / 2f;

        context.draw();
        tech.onetap.util.render.stencil.StencilUtil.push();
        DrawUtil.drawRound(headX, headY, headSize, headSize, headRadius, -1);
        tech.onetap.util.render.stencil.StencilUtil.read(1);

        float currentAnimScale = (float) armorAnim.getValue();
        float entityScale = (headSize / 1.3f) * currentAnimScale;

        if (entityScale > 0.1f) {
            float entityX = headX + headSize / 2f;
            float entityY = headY + headSize + 15f * currentAnimScale;
            float elytra = livingEntity.isGliding() ? -10f : 0f;
            if (livingEntity.isGliding()) entityY -= 20f * currentAnimScale;
            drawEntity(entityX - elytra, entityY + elytra, entityScale, -33.0F, 0.0F, livingEntity);
        }

        context.draw();
        tech.onetap.util.render.stencil.StencilUtil.pop();

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

        float myTotalHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float targetTotalHp = currentHp + absorptionHP;
        float damage = 1.0f;
        ItemStack weapon = mc.player.getMainHandStack();

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

        if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.STRENGTH)) {
            damage += 3.0f * (mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.STRENGTH).getAmplifier() + 1);
        }
        if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS)) {
            damage -= 4.0f * (mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS).getAmplifier() + 1);
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

        if (lastHpRaw == -1f || lastTarget != livingEntity) {
            lastHpRaw = currentHp;
            damageParticles.clear();
        }

        if (currentHp < lastHpRaw) {
            int count = MathHelper.clamp((int)((lastHpRaw - currentHp) * 4), 10, 25);
            java.awt.Color pColor = getHealthBarColor(currentHp, maxHealth);
            float lostHpWidth = barWidth * MathHelper.clamp((lastHpRaw - currentHp) / maxHealth, 0f, 1f);
            float currentHpWidth = barWidth * MathHelper.clamp(currentHp / maxHealth, 0f, 1f);

            for (int i = 0; i < count; i++) {
                float spawnX = barX + currentHpWidth + (float)(Math.random() * lostHpWidth);
                float spawnY = barY + barHeight / 2f;
                damageParticles.add(new DamageParticle(spawnX, spawnY, pColor.getRGB()));
            }
            lastHpRaw = currentHp;
        } else if (currentHp > lastHpRaw) {
            lastHpRaw = currentHp;
        }

        damageParticles.removeIf(p -> p.getAlpha() <= 0);
        if (!damageParticles.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, TARGET_HUD_GLOW_TEXTURE);

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
        if (elements.isEnabled("Таргет худ от темы")) {
            hpRight = ColorProvider.setAlpha(ColorProvider.getThemeColor(), (int) (255 * animAlpha));
            hpLeft = ColorProvider.setAlpha(ColorProvider.getThemeColorTwo(), (int) (255 * animAlpha));
        } else {
            java.awt.Color hpCol = getHealthBarColor(currentHp, maxHealth);
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

        float armorAlpha = (float) armorAnim.getValue();
        if (armorAlpha > 0.05f) {
            List<ItemStack> items = new ArrayList<>();
            items.add(livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
            items.add(livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
            items.add(livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
            items.add(livingEntity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
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
                    context.drawStackOverlay(mc.textRenderer, stack, 0, 0);
                    context.getMatrices().pop();
                    itemX += slotSize + padding;
                }
                context.getMatrices().pop();
            }
        }

        targetHUDDrag.setWidth(width);
        targetHUDDrag.setHeight(height);
    }

    private String transliterate(String text) {
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

    private final List<HeadParticle> headParticles = new ArrayList<>();

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
    private void drawCelestialGlow(Matrix4f matrix, float x, float y, float w, float h, float radius, float anim) {
        int t1 = ColorProvider.getThemeColor();
        int t2 = ColorProvider.getThemeColorTwo();

        float glow = 7.0f;
        int a = (int) (110 * anim);

        int[] c = ColorProvider.getOrbitalRect(t1, t2, 300.0, a);

        Builder.glow()
                .size(new SizeState(w + glow * 2f - 6, h + glow * 2f - 6))
                .radius(new QuadRadiusState(radius))
                .color(new QuadColorState(c[0], c[1], c[2], c[3]))
                .glowRadius(glow)
                .softness(0f)
                .intensity(2.0f)
                .additive(true)
                .build()
                .render(matrix, x - glow + 3, y - glow + 3, 0);
    }
    public void drawEntity(float x, float y, float scale, float yawAngle, float pitchAngle, net.minecraft.entity.LivingEntity entity) {
        MatrixStack matrices = new MatrixStack();
        matrices.push();
        matrices.translate(x, y, 50.0);
        matrices.scale(-scale, scale, scale);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yawAngle));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitchAngle));

        float bodyYaw = entity.bodyYaw;
        float prevBodyYaw = entity.prevBodyYaw;
        float headYaw = entity.headYaw;
        float prevHeadYaw = entity.prevHeadYaw;
        float yaw = entity.getYaw();
        float prevYaw = entity.prevYaw;
        float pitch = entity.getPitch();
        float prevPitch = entity.prevPitch;

        entity.bodyYaw = 0;
        entity.prevBodyYaw = 0;
        entity.headYaw = 0;
        entity.prevHeadYaw = 0;
        entity.setYaw(0);
        entity.prevYaw = 0;
        entity.setPitch(0);
        entity.prevPitch = 0;

        net.minecraft.client.render.DiffuseLighting.disableGuiDepthLighting();
        net.minecraft.client.render.VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);
        mc.getEntityRenderDispatcher().render(entity, 0.0, 0.0, 0.0, tickDelta, matrices, immediate, 0x00F000F0);

        immediate.draw();
        net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();

        entity.bodyYaw = bodyYaw;
        entity.prevBodyYaw = prevBodyYaw;
        entity.headYaw = headYaw;
        entity.prevHeadYaw = prevHeadYaw;
        entity.setYaw(yaw);
        entity.prevYaw = prevYaw;
        entity.setPitch(pitch);
        entity.prevPitch = prevPitch;

        matrices.pop();
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

    private java.awt.Color getHealthBarColor(float currentHp, float maxHp) {
        float ratio = MathHelper.clamp(currentHp / maxHp, 0.0f, 1.0f);
        java.awt.Color colorAtMax = new java.awt.Color(44, 246, 53);
        java.awt.Color colorAt56  = new java.awt.Color(160, 228, 69);
        java.awt.Color colorAt38  = new java.awt.Color(222, 191, 79);
        java.awt.Color colorAt32  = new java.awt.Color(233, 150, 87);
        java.awt.Color colorAt11  = new java.awt.Color(255, 125, 98);

        if (ratio >= 0.56f) {
            float t = MathHelper.clamp((1.0f - ratio) / (1.0f - 0.56f), 0.0f, 1.0f);
            return lerpColor(colorAtMax, colorAt56, t);
        } else if (ratio >= 0.38f) {
            float t = MathHelper.clamp((0.56f - ratio) / (0.56f - 0.38f), 0.0f, 1.0f);
            return lerpColor(colorAt56, colorAt38, t);
        } else if (ratio >= 0.32f) {
            float t = MathHelper.clamp((0.38f - ratio) / (0.38f - 0.32f), 0.0f, 1.0f);
            return lerpColor(colorAt38, colorAt32, t);
        } else if (ratio >= 0.11f) {
            float t = MathHelper.clamp((0.32f - ratio) / (0.32f - 0.11f), 0.0f, 1.0f);
            return lerpColor(colorAt32, colorAt11, t);
        } else {
            return colorAt11;
        }
    }

    private java.awt.Color lerpColor(java.awt.Color a, java.awt.Color b, float t) {
        return new java.awt.Color(
                (int) (a.getRed() + t * (b.getRed() - a.getRed())),
                (int) (a.getGreen() + t * (b.getGreen() - a.getGreen())),
                (int) (a.getBlue() + t * (b.getBlue() - a.getBlue()))
        );
    }


    private static class PotionItem {
        String name;
        int amplifier;
        int durationTicks;
        boolean active;
        net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect;
        Animation animation = new Animation(Easing.EXPO_OUT, 233);

        PotionItem(String name, int amplifier, int durationTicks, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect) {
            this.name = name;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.effect = effect;
            this.active = true;
        }
    }

    private final java.util.List<PotionItem> potionItems = new CopyOnWriteArrayList<>();

    private void updatePotions() {
        java.util.Map<String, StatusEffectInstance> currentEffects = mc.player.getStatusEffects().stream()
                .collect(Collectors.toMap(
                        e -> net.minecraft.text.Text.translatable(e.getTranslationKey()).getString() + ":" + e.getAmplifier(),
                        e -> e,
                        (e1, e2) -> e1
                ));

        potionItems.forEach(item -> {
            String key = item.name + ":" + item.amplifier;
            StatusEffectInstance effect = currentEffects.get(key);

            if (effect != null) {
                item.durationTicks = effect.getDuration();
                if (!item.active) {
                    item.animation.setValue(1.0f);
                }
                item.active = true;
                currentEffects.remove(key);
            } else {
                item.active = false;
            }
        });

        currentEffects.forEach((key, effect) -> {
            potionItems.add(new PotionItem(
                    net.minecraft.text.Text.translatable(effect.getTranslationKey()).getString(),
                    effect.getAmplifier(),
                    effect.getDuration(),
                    effect.getEffectType()
            ));
        });

        potionItems.removeIf(item -> !item.active && item.animation.getValue() == 0);
    }


    public static class NotificationManager {
        private static final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList<>();

        public static void post(String name, boolean enabled) {
            notifications.add(0, new Notification(name, enabled));
        }

        public static void postWarning(String text) {
            notifications.add(0, new Notification(text, true, true));
        }

        public static void render(DrawContext context) {
            Interface interfaceModule = Instance.get(Interface.class);
            if (interfaceModule != null && interfaceModule.hudStyle.is("Old")) {
                renderOld(context);
            } else {
                renderDefault(context);
            }
        }

        private static void renderOld(DrawContext context) {
            float centerX = MinecraftClient.getInstance().getWindow().getScaledWidth() / 2f;
            float startY = (MinecraftClient.getInstance().getWindow().getScaledHeight() / 2f) + 20f;
            float offset = 0;
            long now = System.currentTimeMillis();

            for (Notification n : notifications) {
                long elapsed = now - n.time;
                if (elapsed > n.duration) {
                    notifications.remove(n);
                    continue;
                }

                String fullText;
                int textColor;

                if (n.isWarning) {
                    fullText = n.customText;
                    textColor = ColorProvider.rgba(255, 50, 50, 255);
                } else {
                    String stateText = n.enabled ? "включен!" : "выключен!";
                    fullText = "Модуль " + n.name + " " + stateText;
                    textColor = n.enabled ? ColorProvider.rgba(55, 222, 55, 255) : ColorProvider.rgba(222, 55, 55, 255);
                }

                float textWidth = Fonts.SFMEDIUM.get().getWidth(fullText, 7.5f);
                float x = centerX - (textWidth / 2f);
                float y = startY + offset;

                DrawUtil.drawText(Fonts.SFMEDIUM.get(), fullText, x, y, textColor, 7.5f);
                offset += 10f;
            }
        }

        private static void renderDefault(DrawContext context) {
            float centerX = MinecraftClient.getInstance().getWindow().getScaledWidth() / 2f;
            float startY = (MinecraftClient.getInstance().getWindow().getScaledHeight() / 2f) + 20f;
            float offset = 0;

            for (Notification n : notifications) {
                if (System.currentTimeMillis() - n.time > n.duration && n.anim.getValue() <= 0.01) {
                    notifications.remove(n);
                    continue;
                }

                boolean expiring = System.currentTimeMillis() - n.time > n.duration;
                n.anim.run(expiring ? 0 : 1);

                double animValue = n.anim.getValue();
                if (animValue <= 0.01) continue;

                float clampedAlpha = (float) Math.max(0.0, Math.min(1.0, animValue));
                int alphaInt = (int) (255 * clampedAlpha);

                float height = 14.5f;
                String fullText;
                String iconCode;
                int iconColor;

                if (n.isWarning) {
                    fullText = n.customText;
                    iconCode = "G";
                    iconColor = ColorProvider.rgba(255, 50, 50, alphaInt);
                } else {
                    String stateText = n.enabled ? "включен!" : "выключен!";
                    fullText = "Модуль " + n.name + " " + stateText;
                    iconCode = n.enabled ? "J" : "K";
                    if(n.enabled){
                        iconColor = ColorProvider.rgba(55,222,55, alphaInt);
                    }
                    else{
                        iconColor = ColorProvider.rgba(222,55,55, alphaInt);
                    }
                }

                float textWidth = Fonts.SFMEDIUM.get().getWidth(fullText, 7f);
                float iconWidth = Fonts.ICONS_NURIK.get().getWidth(iconCode, 9f);
                float width = iconWidth + textWidth + 22f;

                float x = centerX - (width / 2f);
                float y = startY + offset;

                context.getMatrices().push();
                context.getMatrices().translate(centerX, y + height / 2f, 0);
                context.getMatrices().scale((float) animValue, (float) animValue, 1f);
                context.getMatrices().translate(-centerX, -(y + height / 2f), 0);

                Interface interfaceModule = Instance.get(Interface.class);
                interfaceModule.drawBackground(x, y, width, height, 4, alphaInt);

                DrawUtil.drawText(Fonts.ICONS_NURIK.get(), iconCode, x + 5, y + 4, iconColor, 9f);
                DrawUtil.drawRound(x + 18f, y + 2.5f, 0.5f, height - 5f, 0, ColorProvider.rgba(255, 255, 255, (int) (120 * clampedAlpha)));
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), fullText, x + 23f, y + 3f, ColorProvider.rgba(255, 255, 255, alphaInt), 7f);

                context.getMatrices().pop();
                offset += (height + 3) * clampedAlpha;
            }
        }

        private static class Notification {
            String name;
            boolean enabled;
            long time;
            long duration = 1000;
            Animation anim = new Animation(Easing.BACK_OUT, 300);

            boolean isWarning = false;
            String customText;

            public Notification(String name, boolean enabled) {
                this.name = name;
                this.enabled = enabled;
                this.time = System.currentTimeMillis();
            }

            public Notification(String customText, boolean enabled, boolean isWarning) {
                this.customText = customText;
                this.enabled = enabled;
                this.isWarning = isWarning;
                this.time = System.currentTimeMillis();
                this.duration = 2000;
            }
        }
    }
}