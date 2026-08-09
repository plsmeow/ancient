package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
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
import tech.onetap.module.list.combat.AutoFlyMace;
import tech.onetap.module.list.combat.BoatAura;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.list.combat.TpAura;
import tech.onetap.module.list.misc.ScoreboardHealth;
import tech.onetap.module.list.render.hud.celestial.KeyBinds;
import tech.onetap.module.list.render.hud.celestial.Potions;
import tech.onetap.module.list.render.hud.celestial.StaffList;
import tech.onetap.module.list.render.hud.celestial.TargetHud;
import tech.onetap.module.list.render.hud.celestial.Watermark;
import tech.onetap.module.list.render.hud.mini.MiniTargetHud;
import tech.onetap.module.list.render.hud.moonward.MoonwardTargetHud;
import tech.onetap.module.list.render.hud.nursultan.NursultanKeyBinds;
import tech.onetap.module.list.render.hud.nursultan.NursultanPotions;
import tech.onetap.module.list.render.hud.nursultan.NursultanStaffList;
import tech.onetap.module.list.render.hud.nursultan.NursultanTargetHud;
import tech.onetap.module.list.render.hud.nursultan.NursultanWatermark;
import tech.onetap.module.list.render.hud.old.OldKeyBinds;
import tech.onetap.module.list.render.hud.old.OldPotions;
import tech.onetap.module.list.render.hud.old.OldStaffList;
import tech.onetap.module.list.render.hud.old.OldTargetHud;
import tech.onetap.module.list.render.hud.old.OldWatermark;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.Setting;
import tech.onetap.module.settings.SliderSetting;
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
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.replace.ReplaceUtil;
import tech.onetap.util.server.Server;
import tech.onetap.util.staff.Staff;
import tech.onetap.util.staff.StaffManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ModuleInformation(moduleName = "Interface", moduleCategory = ModuleCategory.RENDER)
public class Interface extends Module {

    public static final Identifier TARGET_HUD_GLOW_TEXTURE = Identifier.of("mre", "images/glow.png");

    public final ModeSetting hudStyle = new ModeSetting("Стиль HUD", "Celestial", "Nursultan", "Celestial", "Old");
    public final ModeListSetting elements = new ModeListSetting("Элементы",
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
    public final ModeSetting targetStyle = new ModeSetting("Стиль таргета", "Nursultan", "Nursultan", "Moonward", "Celestial", "Mini", "Old");
    public final SliderSetting backgroundIntensity =
            new SliderSetting("Интенсивность фона", 0.15f, 0.05f, 1.0f, 0.01f);
    public final SliderSetting lowHpAlertThreshold =
            new SliderSetting("Порог ХП оповещения", 8f, 1f, 20f, 0.5f);

    private static final SoundEvent SPEK_SOUND = SoundEvent.of(Identifier.of("mre", "spek"));

    public final Draggable watermarkDrag = DragManager.installDrag(this, "Watermark", 4, 4);
    public final Draggable keyBindsDrag = DragManager.installDrag(this, "HotKeys", 100, 50);
    public final Draggable staffListDrag = DragManager.installDrag(this, "StaffList", 200, 50);
    public final Draggable potionsDrag = DragManager.installDrag(this, "Potions", 300, 50);
    public final Draggable targetHUDDrag = DragManager.installDrag(this, "TargetHUD", 130, 130);
    private final Draggable totemCounterDrag = DragManager.installDrag(this, "TotemCounter", 200, 200);

    private final Map<String, Long> spekSuspects = new ConcurrentHashMap<>();

    public final Animation animation = new Animation(Easing.EXPO_OUT, 300);
    public final Animation armorAnim = new Animation(Easing.EXPO_OUT, 300);
    public final Animation hpAnimation = new Animation(Easing.EXPO_OUT, 300);
    public final Animation absorptionAnimation = new Animation(Easing.EXPO_OUT, 300);

    public final Animation widthAnim = new Animation(Easing.EXPO_OUT, 200);
    public final Animation alpha = new Animation(Easing.EXPO_OUT, 200);

    public final Animation widthAnim2 = new Animation(Easing.EXPO_OUT, 200);
    public final Animation alpha2 = new Animation(Easing.EXPO_OUT, 200);

    public final Animation widthAnim3 = new Animation(Easing.EXPO_OUT, 200);
    public final Animation alpha3 = new Animation(Easing.EXPO_OUT, 200);

    private final Animation lowHpAlertAnimation = new Animation(Easing.EXPO_OUT, 300);

    public Entity lastTarget;
    public float lastHpRaw = -1f;
    public final List<Staff> staffPlayers = new ArrayList<>();
    public final List<PotionItem> potionItems = new CopyOnWriteArrayList<>();

    private final Pattern namePattern = Pattern.compile("^\\w{3,16}$");
    private final Pattern prefixMatches = Pattern.compile(".*(ꔷ|ꔳ|ꔩ|ꔥ|ꔡ|ꔗ|ꔓ|\\bmod\\b|\\badm\\b|\\bhelp\\b|\\bwne\\b|модер|мод|хелп|помощ|помо|админ|адм|владел|владе|отриц|отри|\\btaf\\b|\\bcurat\\b|куратор|курато|\\bdev\\b|разраб|раз|\\bsupp\\b|\\bꜱupp\\b|саппорт|сапп|\\bder\\b|\\byt\\b|\\[yt\\]|ютуб|стажер|сотрудник).*");

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

    public int getThemeTint(int alpha) {
        int themeColor = ColorProvider.getThemeColor();
        return ColorProvider.setAlpha(themeColor, (int) (100 * (alpha / 255f) * backgroundIntensity.getFloatValue()));
    }

    public void drawCelestialGlow(Matrix4f matrix, float x, float y, float w, float h, float radius, float anim) {
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

    public void drawEntity(float x, float y, float scale, float yawAngle, float pitchAngle, LivingEntity entity) {
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

    public java.awt.Color getHealthBarColor(float currentHp, float maxHp) {
        float ratio = MathHelper.clamp(currentHp / maxHp, 0.0f, 1.0f);
        java.awt.Color colorAtMax = new java.awt.Color(44, 246, 53);
        java.awt.Color colorAt56 = new java.awt.Color(160, 228, 69);
        java.awt.Color colorAt38 = new java.awt.Color(222, 191, 79);
        java.awt.Color colorAt32 = new java.awt.Color(233, 150, 87);
        java.awt.Color colorAt11 = new java.awt.Color(255, 125, 98);

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

    public LivingEntity getTargetHudTarget() {
        KillAura killAura = tech.onetap.util.base.Instance.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null && killAura.getTarget().isAlive()) {
            return killAura.getTarget();
        }

        AutoFlyMace autoFlyMaceHud = tech.onetap.util.base.Instance.get(AutoFlyMace.class);
        if (autoFlyMaceHud != null && autoFlyMaceHud.isEnabled() && autoFlyMaceHud.getTarget() != null && autoFlyMaceHud.getTarget().isAlive()) {
            return autoFlyMaceHud.getTarget();
        }

        BoatAura boatAura = tech.onetap.util.base.Instance.get(BoatAura.class);
        if (boatAura != null && boatAura.isEnabled() && boatAura.getTarget() != null && boatAura.getTarget().isAlive()) {
            return boatAura.getTarget();
        }

        TpAura tpAura = tech.onetap.util.base.Instance.get(TpAura.class);
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

    public List<BindRow> collectBindRows() {
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

    public record BindRow(String label, String key, float alpha) {}

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
        public Text prefix;
        public String name;
        public boolean isSpec;
        public Status status;
        public boolean isOnServer;
        public Animation animation;
        public long mills;

        public Staff(Text prefix, String name, boolean isSpec, Status status) {
            this.prefix = prefix;
            this.name = name;
            this.isSpec = isSpec;
            this.status = status;
            animation = new Animation(Easing.EXPO_OUT, 233);
            mills = System.currentTimeMillis();
        }
    }

    public static class PotionItem {
        public String name;
        public int amplifier;
        public int durationTicks;
        public boolean active;
        public net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect;
        public Animation animation = new Animation(Easing.EXPO_OUT, 233);

        public PotionItem(String name, int amplifier, int durationTicks,
                          net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect) {
            this.name = name;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.effect = effect;
            this.active = true;
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
            if (hudStyle.is("Celestial")) {
                Watermark.renderCelestial(this, e.getDrawContext());
            } else if (hudStyle.is("Old")) {
                OldWatermark.render(this, e.getDrawContext());
            } else {
                NursultanWatermark.render(this, e.getDrawContext());
            }
        }
        if (elements.isEnabled("Координаты")) {
            renderCoordsInfo(e.getDrawContext());
        }
        if (elements.isEnabled("Активный таргет")) {
            if (targetStyle.is("Moonward")) {
                MoonwardTargetHud.render(this, e.getDrawContext());
            } else if (targetStyle.is("Celestial")) {
                TargetHud.renderCelestial(this, e.getDrawContext());
            } else if (targetStyle.is("Mini")) {
                MiniTargetHud.render(this, e.getDrawContext());
            } else if (targetStyle.is("Old")) {
                OldTargetHud.render(this, e.getDrawContext());
            } else {
                NursultanTargetHud.render(this, e.getDrawContext());
            }
        }
        if (elements.isEnabled("Привязанные модули")) {
            if (hudStyle.is("Celestial")) {
                KeyBinds.renderCelestial(this, e.getDrawContext());
            } else if (hudStyle.is("Old")) {
                OldKeyBinds.render(this, e.getDrawContext());
            } else {
                NursultanKeyBinds.render(this, e.getDrawContext());
            }
        }
        if (elements.isEnabled("Активные модераторы")) {
            if (hudStyle.is("Celestial")) {
                StaffList.renderCelestial(this, e.getDrawContext());
            } else if (hudStyle.is("Old")) {
                OldStaffList.render(this, e.getDrawContext());
            } else {
                NursultanStaffList.render(this, e.getDrawContext());
            }
        }
        if (elements.isEnabled("Бафы")) {
            if (hudStyle.is("Celestial")) {
                Potions.renderCelestial(this, e.getDrawContext());
            } else if (hudStyle.is("Old")) {
                OldPotions.render(this, e.getDrawContext());
            } else {
                NursultanPotions.render(this, e.getDrawContext());
            }
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

            KillAura ka = tech.onetap.util.base.Instance.get(KillAura.class);
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

    private void renderLowHealthAlert(DrawContext context) {
        if (mc.player == null) return;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float threshold = lowHpAlertThreshold.getFloatValue();
        boolean shouldShow = hp <= threshold && !mc.player.isDead();

        lowHpAlertAnimation.run(shouldShow ? 1 : 0);
        float anim = (float) lowHpAlertAnimation.getValue();
        if (anim <= 0.01f) return;

        int alphaInt = (int) (255 * anim);

        String text = String.format(Locale.US, "Критическое здоровье: %.1f HP", hp);
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
        context.drawItem(new net.minecraft.item.ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING), 0, 0);
        context.getMatrices().pop();

        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, posX + 15, posY + 4.5f, -1, 9);

        totemCounterDrag.setWidth(width);
        totemCounterDrag.setHeight(height);
    }

    private void renderCoordsInfo(DrawContext context) {}

    private void renderSpeed(DrawContext context) {
        if (mc.player == null) return;

        double deltaX = mc.player.getX() - mc.player.prevX;
        double deltaY = mc.player.getY() - mc.player.prevY;
        double deltaZ = mc.player.getZ() - mc.player.prevZ;
        double speedBps = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 20;

        String text = String.format(Locale.US, "%.2f", speedBps);
        float textWidth = Fonts.SFREGULAR.get().getWidth(text, 9);

        float x = mc.getWindow().getScaledWidth() / 2f - (textWidth / 2f);
        float y = mc.getWindow().getScaledHeight() / 2f + 12f;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, x, y, -1, 9);
    }

    private void renderMACEDMG(DrawContext context) {
        if (mc.player == null) return;

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

        float fallDistance = mc.player.fallDistance;
        double totalDamage = 0.0;
        boolean canSmash = fallDistance >= 1.5f;

        if (canSmash) {
            double heightDamage = 0.0;
            float blocksFallen = fallDistance;

            if (blocksFallen > 3.0f) {
                heightDamage += 3.0f * 4.0;
                blocksFallen -= 3.0f;

                if (blocksFallen > 5.0f) {
                    heightDamage += 5.0f * 2.0;
                    blocksFallen -= 5.0f;
                    heightDamage += blocksFallen * 1.0;
                } else {
                    heightDamage += blocksFallen * 2.0;
                }
            } else {
                heightDamage += blocksFallen * 4.0;
            }

            totalDamage = (totalDamage + heightDamage) * 1.5;
        }

        int textColor;
        if (!canSmash) {
            textColor = -1;
        } else if (totalDamage < 10.0) {
            textColor = 0xFF55FF55;
        } else if (totalDamage < 50.0) {
            textColor = 0xFFFFFF55;
        } else if (totalDamage < 100.0) {
            textColor = 0xFFFFAA00;
        } else {
            textColor = 0xFFFF5555;
        }

        String text = String.format(Locale.US, "%.1f DMG", totalDamage);
        float textWidth = Fonts.SFREGULAR.get().getWidth(text, 9);

        float x = mc.getWindow().getScaledWidth() / 2f - (textWidth / 2f);
        float y = mc.getWindow().getScaledHeight() / 2f + 32f;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, x, y, textColor, 9);
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
                                    MutableText newText = Text.empty();
                                    int currentLength = 0;
                                    MutableText baseCopy = prefix.copy();
                                    baseCopy.getSiblings().clear();
                                    String mainContent = baseCopy.getString();

                                    if (!mainContent.isEmpty() && currentLength < endIndex) {
                                        int takeLength = Math.min(mainContent.length(), endIndex - currentLength);
                                        newText.append(Text.literal(mainContent.substring(0, takeLength)).setStyle(prefix.getStyle()));
                                        currentLength += takeLength;
                                    }

                                    for (Text sibling : prefix.getSiblings()) {
                                        if (currentLength >= endIndex) break;
                                        MutableText siblingCopy = sibling.copy();
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

    public void updatePotions() {
        Map<String, StatusEffectInstance> currentEffects = mc.player.getStatusEffects().stream()
                .collect(Collectors.toMap(
                        e -> Text.translatable(e.getTranslationKey()).getString() + ":" + e.getAmplifier(),
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
                    Text.translatable(effect.getTranslationKey()).getString(),
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
            Interface interfaceModule = tech.onetap.util.base.Instance.get(Interface.class);
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
                    if (n.enabled) {
                        iconColor = ColorProvider.rgba(55, 222, 55, alphaInt);
                    } else {
                        iconColor = ColorProvider.rgba(222, 55, 55, alphaInt);
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

                Interface interfaceModule = tech.onetap.util.base.Instance.get(Interface.class);
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
