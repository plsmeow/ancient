package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import java.security.SecureRandom;
import lombok.Getter;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.Onetap;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.event.list.EventChangeSprint;
import tech.onetap.event.list.EventHUD;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventTickEnd;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.player.FreeCamera;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.base.Instance;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.target.TargetRepository;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.player.simulate.SimulatedPlayer;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.msdf.MsdfFont;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;
import tech.onetap.util.text.ValueUnit;
import tech.onetap.util.neuro.rotation.ActiveModel;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.neuro.rotation.AIRotationRecorder;
import tech.onetap.util.neuro.rotation.NeuroModelMeta;
import tech.onetap.util.neuro.rotation.NeuroRotationController;
import tech.onetap.util.neuro.rotation.RotationDumpRecorder;
import tech.onetap.util.neuro.rotation.TrainingLauncher;
import tech.onetap.module.list.combat.rotations.*;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInformation(moduleName = "KillAura", moduleCategory = ModuleCategory.COMBAT)
public class KillAura extends Module {

    public final ModeSetting rotation = new ModeSetting(
            "Ротация",
            "Vanilla",
            "Vanilla",
            "Snap",
            "Sloth",
            "Sloth2",
            "Sloth3",
            "Wellmine old",
            "NoRot",
            "LonyGrief",
            "Vulcan",
            "Funtime",
            "SpookyTime",
            "Universal",
            "GrimFun",
            "Grim 1.20.4",
            "Neuro",
            "AresMine"
    );
    public final ModeSetting sortBy = new ModeSetting("Сортировка", "FOV", "FOV", "Дистанция", "Здоровье");
    private final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Монстры", true),
            new BooleanSetting("Животные", true)
    );
    public final ModeSetting moveFix = new ModeSetting("MoveFix", "Сфокусированная", "Свободный", "Сфокусированная", "None");
    public final SliderSetting snapHoldTicks = new SliderSetting("Snap tick", ValueUnit.countable("тик", "тика", "тиков"), 2, 1, 10, 1)
            .setVisible(() -> rotation.is("Snap"));

    public final SliderSetting distance = new SliderSetting("Дистанция", ValueUnit.countable("блок", "блока", "блоков"), 3, 2, 6, 0.1f);
    public final BooleanSetting elytraTarget = new BooleanSetting("ElytraTarget", true);
    public final BooleanSetting smoothElytraRotation = new BooleanSetting("Плавная ротация (Elytra)", false)
            .setVisible(() -> elytraTarget.getValue());
    public final SliderSetting elytraDistance = new SliderSetting("Дистанция (Элитры)", 300, 3, 500, 10)
            .setVisible(() -> elytraTarget.getValue());
    private final SliderSetting preRotation = new SliderSetting("Пре дистанция", ValueUnit.countable("блок", "блока", "блоков"), 1.5f, 0, 3, 0.1f);
    private final BooleanSetting stopWhileEating = new BooleanSetting("Не бить при еде", false);
    public final BooleanSetting breakSwing = new BooleanSetting("Ломать swing", false);
    public final BooleanSetting breakShield = new BooleanSetting("Ломать щит", true);
    public final BooleanSetting forceBreakShield = new BooleanSetting("Ломать щит без задержки", true)
            .setVisible(breakShield::getValue);
    public final BooleanSetting unblockShield = new BooleanSetting("Отжимать щит", false);
    private final SliderSetting unblockShieldDelay = new SliderSetting("Отжатие: тиков до удара", ValueUnit.countable("тик", "тика", "тиков"), 2, 1, 6, 1)
            .setVisible(unblockShield::getValue);
    private final List<net.minecraft.item.Item> AXES = List.of(
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
            Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE
    );
    public final BooleanSetting raycastCheck = new BooleanSetting("Проверка на наведение", true);
    public final BooleanSetting smartAim = new BooleanSetting("Умное наведение", true);
    public final BooleanSetting predictate = new BooleanSetting("Предикт на элитрах", true)
            .setVisible(() -> elytraTarget.getValue());
    public final SliderSetting predictValue = new SliderSetting("Предикт значение", 3, 1, 5, 0.1f)
            .setVisible(() -> elytraTarget.getValue() && predictate.getValue());

    public final BooleanSetting hitAfterOvertake = new BooleanSetting("Бить токо после перегона", true)
            .setVisible(() -> elytraTarget.getValue());

    public final BooleanSetting onlySpace = new BooleanSetting("Только с пробелом", true);
    public final BooleanSetting clientLook = new BooleanSetting("Клиент лук", true);
    public final BooleanSetting showPredictPoint = new BooleanSetting("Показать предикт точку", true)
            .setVisible(() -> elytraTarget.getValue());
    public final BooleanSetting elytraTurnaround = new BooleanSetting("Разворот на элитрах", true)
            .setVisible(() -> elytraTarget.getValue());

    public final BooleanSetting freeze = new BooleanSetting("Freeze", false)
            .setVisible(() -> elytraTarget.getValue());

    public final SliderSetting neuroYawMultiplier = new SliderSetting("Yaw множитель", 1.0, 0.5, 2.0, 0.05)
            .setVisible(() -> rotation.is("Neuro"));
    public final SliderSetting neuroPitchMultiplier = new SliderSetting("Pitch множитель", 1.0, 0.5, 2.0, 0.05)
            .setVisible(() -> rotation.is("Neuro"));
    public final BooleanSetting neuroDebug = new BooleanSetting("Neuro отладка", false)
            .setVisible(() -> rotation.is("Neuro"));

    // Экземпляры ротаций (каждая хранит своё внутреннее состояние)
    private final VanillaRotation vanillaRotation = new VanillaRotation();
    private final SnapRotation snapRotation = new SnapRotation();
    private final Sloth2Rotation sloth2Rotation = new Sloth2Rotation();
    private final Sloth3Rotation sloth3Rotation = new Sloth3Rotation();
    private final SlothRotation slothRotation = new SlothRotation();
    private final WellmineRotation wellmineRotation = new WellmineRotation();
    private final NoRotRotation noRotRotation = new NoRotRotation();
    private final LonyGriefRotation lonyGriefRotation = new LonyGriefRotation();
    private final VulcanRotation vulcanRotation = new VulcanRotation();
    private final FuntimeRotation funtimeRotation = new FuntimeRotation();
    private final SpookyTimeRotation spookyTimeRotation = new SpookyTimeRotation();
    private final UniversalRotation universalRotation = new UniversalRotation();
    private final GrimFunRotation grimFunRotation = new GrimFunRotation();
    private final Grim1204Rotation grim1204Rotation = new Grim1204Rotation();
    private final NeuroRotation neuroRotation = new NeuroRotation();
    private final AresMineRotation aresMineRotation = new AresMineRotation();

    private boolean interpolationRotationInitialized;
    private LivingEntity interpolationRotationTarget;
    private float interpolatedYaw;
    private float interpolatedPitch;

    private long lastJerkTime = 0;

    private float targetOvershootYaw = 0;
    private float targetOvershootPitch = 0;
    private float jerkSpeedMultiplier = 1.0f;

    public boolean isTurnaroundActive = false;
    public static boolean isSlowdownActive = false;
    private static StopWatch stopWatch = new StopWatch();
    @Getter
    private LivingEntity target;
    public static LivingEntity lastTarget;
    public int ticksToAttack;

    private int razvorotikTicks;

    public boolean back;
    public float speedAcceleration;
    public float obhod;
    public static long lastPhysicalMoveTime;

    private boolean freezeActive = false;
    private double freezeLockX, freezeLockZ;
    private int freezeTicks = 0;
    private boolean freezeExpired = false;
    private static final int FREEZE_MAX_TICKS = 40;

    // Поля для логики Snap (используются также в canAttack/onUpdate)
    public boolean snapActive = false;
    public int snapTimer = 0;

    // Отжимать щит: 0 = idle, 1 = щит отжат, ждём удар
    private int shieldPhase = 0;
    private int shieldTicks = 0;

    public boolean isSnapActive() {
        return snapActive;
    }

    public tech.onetap.util.rotation.MoveFixMode getMoveFixMode() {
        if (moveFix.is("Свободный")) return tech.onetap.util.rotation.MoveFixMode.FREE;
        if (moveFix.is("None")) return tech.onetap.util.rotation.MoveFixMode.NONE;
        return tech.onetap.util.rotation.MoveFixMode.CORRECT;
    }

    private final StopWatch turnaroundTimer = new StopWatch();

    public float preddict;
    public float lastYaw;
    public float lastPitch;
    private float velocityYaw = 0.0F;

    private boolean renderListenerRegistered = false;
    private final WorldRenderEvents.Last renderListener = context -> {
        if (isEnabled() && elytraTarget.getValue() && showPredictPoint.getValue()) {
            renderPredictPoint(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
        }
        if (isEnabled() && rotation.is("Neuro") && neuroDebug.getValue()) {
            renderNeuroDebug(context.matrixStack(), context.camera());
        }
    };

    /**
     * Отладка Neuro: точка прицеливания, куда реально целится модель.
     */
    private void renderNeuroDebug(MatrixStack matrices, Camera camera) {
        Vec3d aimPoint = neuroRotation.getDebugAimPoint();
        if (aimPoint == null) return;

        Vec3d camPos = camera.getPos();
        double renderX = aimPoint.x - camPos.x;
        double renderY = aimPoint.y - camPos.y;
        double renderZ = aimPoint.z - camPos.z;

        // Зелёный при уверенной модели, красный при откате в fallback
        float confidence = neuroRotation.getDebugConfidence();
        boolean fallback = neuroRotation.isFallbackActive();
        float r = fallback ? 1.0f : (1.0f - confidence);
        float g = fallback ? 0.0f : confidence;
        float b = 0.2f;

        float size = 0.12f;

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        drawLineBox(buffer, matrix, -size, -size, -size, size, size, size, r, g, b, 1f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    /**
     * Текстовая debug-панель Neuro: модель, inference, бюджет, aim point, запись.
     */
    @EventHandler
    private void onHud(EventHUD e) {
        if (!isEnabled() || !rotation.is("Neuro") || !neuroDebug.getValue()) return;
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;

        int white = ColorProvider.rgba(235, 235, 235, 255);
        int gray = ColorProvider.rgba(170, 170, 170, 255);
        int red = ColorProvider.rgba(255, 90, 90, 255);

        java.util.List<String[]> lines = new java.util.ArrayList<>(); // [текст, цвет]

        ActiveModel model = AIRotationManager.getActive();
        if (model == null) {
            lines.add(new String[]{"Модель: не загружена (fallback NoRot)", "red"});
        } else {
            NeuroModelMeta meta = model.getMeta();
            lines.add(new String[]{"Модель: " + model.getName()
                    + String.format(" (%s, seq %d, сэмплов %d)", meta.getArch(), meta.getSeqLen(), meta.getTrainSamples()), "white"});
            lines.add(new String[]{String.format("loss %.4f | yaw MAE %.2f° | pitch MAE %.2f° | источник %s",
                    meta.getValLoss(), meta.getYawMae(), meta.getPitchMae(), meta.getSource()), "gray"});
        }

        lines.add(new String[]{String.format("inference %d мкс | conf %.2f | история %s",
                neuroRotation.getDebugInferenceNanos() / 1000,
                neuroRotation.getDebugConfidence(),
                neuroRotation.isHistoryWarm() ? "тёплая" : "прогрев"), "white"});

        NeuroRotationController ctrl = neuroRotation.getController();
        lines.add(new String[]{String.format("предсказание %+.2f° / %+.2f° | бюджет %+.2f° / %+.2f°",
                neuroRotation.getDebugPredYaw(), neuroRotation.getDebugPredPitch(),
                ctrl.getBudgetYaw(), ctrl.getBudgetPitch()), "white"});
        lines.add(new String[]{String.format("остаток %+.2f° / %+.2f° | geo ошибка %.2f° / %.2f°",
                ctrl.getRemainingYaw(), ctrl.getRemainingPitch(),
                neuroRotation.getDebugGeoYaw(), neuroRotation.getDebugGeoPitch()), "gray"});

        if (target != null) {
            lines.add(new String[]{String.format("цель %s (%.1f м) | cooldown %.2f",
                    target.getName().getString(),
                    mc.player.distanceTo(target),
                    mc.player.getAttackCooldownProgress(0.5f)), "white"});
        } else {
            lines.add(new String[]{"цель —", "gray"});
        }

        Vec3d aim = neuroRotation.getDebugAimPoint();
        if (aim != null) {
            lines.add(new String[]{String.format("aim point %.2f %.2f %.2f", aim.x, aim.y, aim.z), "gray"});
        }

        if (neuroRotation.isFallbackActive()) {
            lines.add(new String[]{String.format("fallback: %s (%d тиков подряд)",
                    neuroRotation.getFallbackReason(), neuroRotation.getFallbackTicks()), "red"});
        }

        StringBuilder status = new StringBuilder();
        if (AIRotationRecorder.isRecording()) {
            status.append("запись: ").append(AIRotationRecorder.getSampleCount()).append("  ");
        }
        if (RotationDumpRecorder.isRecording()) {
            status.append("дамп ").append(RotationDumpRecorder.getNamesLine())
                    .append(": ").append(RotationDumpRecorder.getTotalSamples()).append("  ");
        }
        if (TrainingLauncher.isRunning()) {
            status.append("обучение идёт");
        }
        if (status.length() > 0) {
            lines.add(new String[]{status.toString().trim(), "gray"});
        }

        // Рисуем панель
        MsdfFont font = Fonts.SFMEDIUM.get();
        float fontSize = 7f;
        float x = 4f;
        float y = 110f;

        float maxWidth = font.getWidth("[ Neuro ]", fontSize);
        for (String[] line : lines) {
            maxWidth = Math.max(maxWidth, font.getWidth(line[0], fontSize));
        }
        float panelHeight = 10f + lines.size() * 9f;

        DrawUtil.drawRound(x - 3f, y - 3f, maxWidth + 8f, panelHeight + 4f, 3f,
                ColorProvider.rgba(15, 15, 15, 140));

        DrawUtil.drawText(font, "[ Neuro ]", x, y, ColorProvider.getThemeColor(), fontSize);
        y += 10f;
        for (String[] line : lines) {
            int color = switch (line[1]) {
                case "red" -> red;
                case "gray" -> gray;
                default -> white;
            };
            DrawUtil.drawText(font, line[0], x, y, color, fontSize);
            y += 9f;
        }
    }

    @EventHandler
    private void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null) return;
        if (target == null && !rotation.is("Universal")) return;

        Onetap.getInstance().getModuleStorage().setRandomness(1);

        // Идёт запись датасета (учитель всегда человек) —
        // ротация не должна мешать демонстратору.
        if (AIRotationRecorder.isRecording()) {
            return;
        }

        boolean forceElytraRot = elytraTarget.getValue() && target != null && target.isGliding();
        if (forceElytraRot && !smoothElytraRotation.getValue()) {
            Vec3d center = target.getBoundingBox().getCenter();
            var rot = new Rotation(RotationUtil.calculate(center));
            RotationComponent.update(rot, 360, 360, 360, 360, 0, 1, clientLook.getValue(), getMoveFixMode(), "KillAura");
            lastYaw = rot.getYaw();
            lastPitch = rot.getPitch();
        } else if (forceElytraRot && smoothElytraRotation.getValue()) {
            slothRotation.update(this, target);
        } else {
            switch (rotation.getValue()) {
                case "Vanilla" -> vanillaRotation.update(this, target);
                case "Snap" -> snapRotation.update(this, target);
                case "Sloth2" -> sloth2Rotation.update(this, target);
                case "Sloth3" -> sloth3Rotation.update(this, target);
                case "Sloth" -> slothRotation.update(this, target);
                case "Wellmine old" -> wellmineRotation.update(this, target);
                case "NoRot" -> noRotRotation.update(this, target);
                case "LonyGrief" -> lonyGriefRotation.update(this, target);
                case "Vulcan" -> vulcanRotation.update(this, target);
                case "Funtime" -> funtimeRotation.update(this, target);
                case "SpookyTime" -> spookyTimeRotation.update(this, target);
                case "Universal" -> universalRotation.update(this, target);
                case "Grim 1.20.4" -> {
                    grim1204Rotation.update(this, target);
                    lastYaw = grim1204Rotation.getRotationYaw();
                    lastPitch = grim1204Rotation.getRotationPitch();
                }
                case "GrimFun" -> grimFunRotation.update(this, target);
                case "Neuro" -> neuroRotation.update(this, target);
                case "AresMine" -> aresMineRotation.update(this, target);
            }
        }
    }

    @EventHandler
    private void onChangeSprint(EventChangeSprint e) {
        if (canStopSprinting()) e.setSprinting(false);
    }

    @EventHandler
    private void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (ticksToAttack > 0) ticksToAttack--;
        if (razvorotikTicks > 0) razvorotikTicks--;

        updateTarget();

        if (target != null) {
            lastTarget = target;
            isSlowdownActive = false;

            MaceKill maceKill = Onetap.getInstance().getModuleStorage().get(MaceKill.class);
            maceKill.updateFunskyState(target);

            if (canStopSprinting()) {
                mc.player.setSprinting(false);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }

            boolean attackReady = handleShieldUnblock(canAttack());
            if (attackReady) {
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));

                AutoMace autoMace = Onetap.getInstance().getModuleStorage().get(AutoMace.class);
                autoMace.prepareAttack();

                // Funsky Auto: простые удары бьём текущим предметом, не триггеря AutoMace
                boolean funskySimpleHit = maceKill.isFunskyActive() && !maceKill.isFunskyWaitingSmash();

                int previousSlot = swapToAxe();
                if (previousSlot == -1 && !funskySimpleHit) {
                    previousSlot = autoMace.swapToMace();
                }

                BoatAura boatAura = Instance.get(BoatAura.class);
                TpAura tpAura = Instance.get(TpAura.class);
                boolean boatAuraMoved = boatAura != null && boatAura.beforeAttack(target);
                boolean tpAuraMoved = !boatAuraMoved && tpAura != null && tpAura.beforeAttack(target);

                Criticals crits = Onetap.getInstance().getModuleStorage().get(Criticals.class);

                // Блокируем onAttack-обработчик MaceKill на любом ударе KillAura:
                // его крит добавляется только явно ниже (иначе простые удары Funsky
                // всегда шли бы с критом MaceKill через EventAttack)
                maceKill.killAuraTriggered = true;

                // Funsky Auto: на простых ударах используем криты из Criticals, смэш — MaceKill
                if (maceKill.isEnabled() && !funskySimpleHit) {
                    crits.killAuraTriggered = true;
                    maceKill.doCrit();
                } else if (crits.isEnabled()) {
                    crits.killAuraTriggered = true;
                    crits.doCrit();
                }

                if (rotation.is("Grim 1.20.4")) {
                    grim1204Rotation.sendRotationPacket(this);
                }

                mc.interactionManager.attackEntity(mc.player, target);

                crits.killAuraTriggered = false;
                maceKill.killAuraTriggered = false;

                mc.player.swingHand(breakSwing.getValue() ? Hand.OFF_HAND : Hand.MAIN_HAND);

                if (rotation.is("Grim 1.20.4")) {
                    grim1204Rotation.sendResetPacket(this);
                }

                if (boatAuraMoved) {
                    boatAura.afterAttack();
                } else if (tpAuraMoved) {
                    tpAura.afterAttack();
                }

                if (previousSlot != -1) {
                    swapBack(previousSlot);
                }
                autoMace.scheduleAutoMaceElytraBack();

                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));

                if (maceKill.isEnabled() && maceKill.isFunskyActive() && maceKill.isFunskyWaitingSmash()) {
                    // Funsky Auto: смэш автомейса выполнен — цикл заново (простые удары)
                    maceKill.onFunskySmash();
                }

                if (maceKill.isEnabled() && maceKill.isCustomDelayEnabled()) {
                    // MaceKill: после удара ждём кастомную задержку, а не 10 тиков/кулдаун предмета
                    maceKill.resetAttackDelay();
                } else if (!autoMace.isForceAutoMaceReady(target) && !isForceBreakShieldReady()) {
                    ticksToAttack = 10;
                }

                if (rotation.is("Sloth2")) {
                    sloth2Rotation.onAttack();
                }

                if (rotation.is("AresMine")) {
                    aresMineRotation.onAttack();
                }

                if (rotation.is("Snap")) {
                    snapActive = false;
                    snapTimer = 0;
                }
            }
        } else {
            Onetap.getInstance().getModuleStorage().get(MaceKill.class).updateFunskyState(null);
            speedAcceleration = 0;
            razvorotikTicks = 0;
            snapActive = false;
            snapTimer = 0;
            shieldPhase = 0;
            slothRotation.reset(this);
            sloth2Rotation.reset(this);
            if (!rotation.is("Universal")) {
                universalRotation.reset(this);
            }
            grimFunRotation.reset(this);
            grim1204Rotation.reset(this);
            neuroRotation.reset(this);
            aresMineRotation.reset(this);
        }

        updateFreeze();
    }

    private boolean isAtOvertakePoint() {
        if (mc.player == null || target == null) return false;
        if (!elytraTarget.getValue() || !target.isGliding() || !mc.player.isGliding()) return false;
        Vec3d predict = PredictUtils.getPredicted(target, predictValue.getValue());
        double distToPredict = mc.player.getEyePos().distanceTo(predict);
        float threshold = hitAfterOvertake.getValue() ? 2.7f : 4f;
        return distToPredict <= threshold;
    }

    private void updateFreeze() {
        if (mc.player == null) return;
        boolean atPoint = isAtOvertakePoint();
        if (!atPoint) freezeExpired = false;
        if (freeze.getValue() && atPoint && !freezeExpired) {
            if (!freezeActive) {
                freezeActive = true;
                freezeTicks = 0;
                freezeLockX = mc.player.getX();
                freezeLockZ = mc.player.getZ();
            }
            if (++freezeTicks > FREEZE_MAX_TICKS) {
                freezeExpired = true;
                stopFreeze();
                return;
            }
            mc.player.setVelocity(0, 0, 0);
            mc.player.setNoGravity(true);
        } else {
            stopFreeze();
        }
    }

    private void stopFreeze() {
        if (!freezeActive) return;
        freezeActive = false;
        freezeTicks = 0;
        if (mc.player != null) mc.player.setNoGravity(false);
    }

    @EventHandler
    private void onFreezeTickEnd(EventTickEnd e) {
        if (mc.player == null || !freezeActive) return;
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        mc.player.setPosition(freezeLockX, mc.player.getY(), freezeLockZ);
    }

    @EventHandler
    private void onFreezePacket(EventPacket e) {
        if (mc.player == null || !freezeActive) return;
        if (e.getPacket() instanceof PlayerMoveC2SPacket) e.cancelEvent();
    }

    private boolean isValidEntity(Entity entity) {
        if (!entity.isAlive()) return false;
        PlayerEntity player = Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer != null ? Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer : mc.player;
        if (entity == Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer) return false;
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity instanceof PlayerEntity p) {
            if (p.getArmor() != 0 && !targets.isEnabled("Игроки")) return false;
            if (p.getArmor() == 0 && !targets.isEnabled("Голые")) return false;
            if (Onetap.getInstance().getModuleStorage().get(AntiBot.class).isBot(p)) return false;
            if (!FriendRepository.shouldAttack(p)) return false;
        } else if (entity instanceof HostileEntity || entity instanceof AmbientEntity) {
            if (!targets.isEnabled("Монстры")) return false;
        } else if (entity instanceof PassiveEntity || entity instanceof FishEntity) {
            if (!targets.isEnabled("Животные")) return false;
        } else {
            return false;
        }
        if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(entity)) > getTargetSearchDistance(player))
            return false;
        return true;
    }

    public boolean canAttack() {
        if (target == null) return false;

        // Ждём возврата TpAura из Vault-режима, чтобы не бить, пока игрок не на исходной точке
        TpAura tpAura = Instance.get(TpAura.class);
        if (tpAura != null && tpAura.isPendingVaultReturn()) return false;

        boolean eating = mc.player.isUsingItem() && !isSelfBlockingShield();

        if (eating && stopWhileEating.getValue()) return false;

        PlayerEntity player = Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer != null ?
                Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer : mc.player;

        if (!isInAttackDistance(player, target)) return false;

        isTurnaroundActive = false;
        if (elytraTarget.getValue() && target.isGliding() && mc.player.isGliding()) {
            Vec3d predict = PredictUtils.getPredicted(target, predictValue.getValue());
            double distToPredict = player.getEyePos().distanceTo(predict);

            preddict = hitAfterOvertake.getValue() ? 2.7f : 4f;

            if (distToPredict <= preddict && elytraTurnaround.getValue()) {
                isTurnaroundActive = true;
            }

            if (distToPredict > preddict) return false;

            if (isTurnaroundActive) {
                float targetYaw = new Rotation(RotationUtil.calculate(target.getBoundingBox().getCenter())).getYaw();
                float yawDiff = Math.abs(MathHelper.wrapDegrees(lastYaw - targetYaw));
                if (yawDiff > 5f) {
                    return false;
                }
            }
        } else if (!canReachWithPositionAura(target)) {
            if (!RaytraceUtil.rayTrace(player.getRotationVector(), distance.getValue(), target.getBoundingBox()) && raycastCheck.getValue())
                return false;

            if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(target)) > (distance.getValue() - 0.2f))
                return false;
        }

        if (isForceBreakShieldReady()) {
            return Onetap.getInstance().getIdealHitUtils().canCritical();
        }

        if (rotation.is("Snap")) {
            if (!snapActive || snapTimer < snapHoldTicks.getValue()) return false;
        }

        MaceKill maceKill = Onetap.getInstance().getModuleStorage().get(MaceKill.class);
        if (maceKill != null && maceKill.isEnabled() && maceKill.isFunskyActive() && maceKill.isFunskyWaitingSmash()) {
            // Funsky Auto: ждём конца i-frames цели (hurtTime == 0) и сразу бьём автомейсом
            if (target.hurtTime > 0) return false;
            // Funsky Auto (режимы 4.5s/5s): смэш не чаще заданного интервала
            if (!maceKill.isFunskySmashReady()) return false;
        } else if (maceKill != null && maceKill.isEnabled() && maceKill.isFunskyActive() && maceKill.isFunskyTimerMode()) {
            // Funsky Auto (режимы 4.5s/5s): простые удары не бьём — ждём смэш по таймеру
            return false;
        } else if (maceKill != null && maceKill.isEnabled() && maceKill.isCustomDelayEnabled()) {
            // MaceKill: бьём по кастомной задержке в тиках, игнорируя кулдаун предмета
            if (maceKill.getAttackTicks() > 0) return false;
        } else if (!Onetap.getInstance().getModuleStorage().get(AutoMace.class).isForceAutoMaceReady(target)) {
            if (mc.player.getAttackCooldownProgress(0.5f) < 0.98f) return false;
            if (ticksToAttack > 0) return false;
        }

        return Onetap.getInstance().getIdealHitUtils().canCritical();
    }

    public boolean isElytraPredictActive() {
        return predictate.getValue() && mc.player != null && mc.player.isGliding();
    }

    private boolean isInAttackDistance(PlayerEntity player, LivingEntity entity) {
        if (canReachWithPositionAura(entity)) return true;

        Vec3d nearestPoint = BestPoint.getNearestPoint(entity);
        if (nearestPoint == null) return false;

        double attackDistance = (elytraTarget.getValue() && player.isGliding()) ? elytraDistance.getValue() : distance.getValue();
        return player.getEyePos().distanceTo(nearestPoint) <= attackDistance;
    }

    private double getTargetSearchDistance(PlayerEntity player) {
        double searchDistance = (elytraTarget.getValue() && player.isGliding()) ? elytraDistance.getValue() : distance.getValue() + preRotation.getValue();

        BoatAura boatAura = Instance.get(BoatAura.class);
        if (boatAura != null && boatAura.isEnabled() && mc.player != null && mc.player.hasVehicle()) {
            searchDistance = Math.max(searchDistance, boatAura.getMaxDistance());
        }

        TpAura tpAura = Instance.get(TpAura.class);
        if (tpAura != null && tpAura.isEnabled() && mc.player != null && !mc.player.hasVehicle()) {
            searchDistance = Math.max(searchDistance, tpAura.getMaxDistance());
        }

        // HvH Target: если в Speed (Vanilla) включён HvH Target — ищем цель на любой дистанции (100 блоков)
        tech.onetap.module.list.movement.Speed speed = Onetap.getInstance().getModuleStorage().get(tech.onetap.module.list.movement.Speed.class);
        if (speed != null && speed.isEnabled() && speed.isHvhTargetEnabled()) {
            searchDistance = Math.max(searchDistance, 100.0);
        }

        return searchDistance;
    }

    private boolean canReachWithPositionAura(LivingEntity entity) {
        BoatAura boatAura = Instance.get(BoatAura.class);
        if (boatAura != null && boatAura.isEnabled() && boatAura.getRenderPosition(entity) != null) {
            return true;
        }

        TpAura tpAura = Instance.get(TpAura.class);
        return tpAura != null && tpAura.isEnabled() && tpAura.getRenderPosition(entity) != null;
    }

    private boolean isTargetBlocking() {
        return target != null && target.isUsingItem() && target.getActiveItem().isOf(Items.SHIELD);
    }

    private boolean isForceBreakShieldReady() {
        int axeSlot = -1;
        for (net.minecraft.item.Item axe : AXES) {
            int slot = InventoryUtil.searchItemHotbar(axe);
            if (slot != -1) {
                axeSlot = slot;
                break;
            }
        }

        return breakShield.getValue()
                && forceBreakShield.getValue()
                && isTargetBlocking()
                && axeSlot != -1;
    }

    private int swapToAxe() {
        if (!breakShield.getValue() || target == null || !isTargetBlocking()) return -1;

        boolean hasAxe = AXES.stream().anyMatch(axe -> InventoryUtil.searchItemHotbar(axe) != -1);
        if (!hasAxe) return -1;

        int axeSlot = -1;
        for (net.minecraft.item.Item axe : AXES) {
            int slot = InventoryUtil.searchItemHotbar(axe);
            if (slot != -1) {
                axeSlot = slot;
                break;
            }
        }

        if (axeSlot == -1 || mc.player.getInventory().selectedSlot == axeSlot) return -1;

        int previousSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = axeSlot;
        mc.interactionManager.syncSelectedSlot();
        return previousSlot;
    }

    private void swapBack(int previousSlot) {
        if (previousSlot == -1) return;

        mc.player.getInventory().selectedSlot = previousSlot;
        mc.interactionManager.syncSelectedSlot();
    }

    private boolean isSelfBlockingShield() {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        var active = mc.player.getActiveItem();
        return active.isOf(Items.SHIELD) || active.getUseAction() == UseAction.BLOCK;
    }

    public boolean isShieldSuppressed() {
        return isEnabled() && unblockShield.getValue() && shieldPhase == 1;
    }

    private boolean handleShieldUnblock(boolean attackReady) {
        if (!unblockShield.getValue()) {
            shieldPhase = 0;
            return attackReady;
        }

        boolean manualShield = isSelfBlockingShield() && mc.options.useKey.isPressed();
        int lead = Math.max(1, unblockShieldDelay.getIntValue());

        if (shieldPhase == 0) {
            // Опускаем щит заранее (до готовности удара), тайминг самого удара не сдвигается
            if (manualShield && (attackReady || isHitImminent(lead))) {
                mc.interactionManager.stopUsingItem(mc.player);
                shieldPhase = 1;
                shieldTicks = 0;
                return false;
            }
            return attackReady;
        }

        shieldTicks++;

        // Игрок отпустил ПКМ — перестаём держать щит опущенным
        if (!mc.options.useKey.isPressed()) {
            shieldPhase = 0;
            return attackReady;
        }

        // Щит опущен минимум тик назад — бьём в естественный тайминг без задержки
        if (attackReady && shieldTicks >= 1 && !mc.player.isUsingItem()) {
            return true;
        }

        // Удар уже не близко — возвращаем щит обратно
        if (!attackReady && !isHitImminent(lead)) {
            shieldPhase = 0;
        }
        return false;
    }

    public boolean isHitImminent(int lead) {
        if (target == null || mc.player == null) return false;
        if (!isInAttackDistance(mc.player, target)) return false;
        if (ticksToAttack > lead) return false;

        float perTick = mc.player.getAttackCooldownProgressPerTick();
        if (perTick > 0f) {
            float remaining = (1.0f - mc.player.getAttackCooldownProgress(0.5f)) / perTick;
            if (remaining > lead) return false;
        }
        return true;
    }

    public boolean canStopSprinting() {
        if (target == null) return false;
        if (!Onetap.getInstance().getIdealHitUtils().cooldownIsReached(true)) return false;
        if (ticksToAttack > 1) return false;
        if (SimulatedPlayer.simulateLocalPlayer(1).fallDistance == 0) return false;
        return true;
    }

    private void updateTarget() {
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        LivingEntity bestTargetList = null;
        double bestTargetListScore = Double.NEGATIVE_INFINITY;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F);

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living) {
                if (!isValidEntity(entity)) continue;

                double score;
                switch (sortBy.getValue()) {
                    case "Дистанция" -> {
                        score = -eyePos.distanceTo(BestPoint.getNearestPoint(entity));
                    }
                    case "Здоровье" -> {
                        score = -living.getHealth();
                    }
                    default -> {
                        Vec3d targetVec = BestPoint.getNearestPoint(entity).subtract(eyePos).normalize();
                        score = lookVec.dotProduct(targetVec);
                    }
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = living;
                }

                if (entity instanceof PlayerEntity p && TargetRepository.isTarget(p.getNameForScoreboard())) {
                    if (score > bestTargetListScore) {
                        bestTargetListScore = score;
                        bestTargetList = living;
                    }
                }
            }
        }

        if (bestTargetList != null) {
            if (target == null || !isValidEntity(target)
                    || !(target instanceof PlayerEntity cur) || !TargetRepository.isTarget(cur.getNameForScoreboard())) {
                this.target = bestTargetList;
            }
        } else if (target == null || !isValidEntity(target)) {
            this.target = best;
        }
    }

    public Vec3d resolveMultipoint(LivingEntity target, Vec3d point, double range) {
        if (!smartAim.getValue() || target == null) {
            return point;
        }

        return BestPoint.getNearestVisiblePoint(target, point, range);
    }

    private float applyGCD(float deltaRotation) {
        float sensitivity = (float) (mc.options.getMouseSensitivity().getValue() * 0.6f + 0.2f);
        float multiplier = sensitivity * sensitivity * sensitivity * 8.0f * 0.15f;
        return (Math.round(deltaRotation / multiplier) * multiplier);
    }

    private void renderPredictPoint(MatrixStack matrices, Camera camera, float tickDelta) {
        if (target == null || !target.isGliding() || mc.player == null || !mc.player.isGliding()) return;

        Vec3d predictPos = PredictUtils.getPredictedRender(target, predictValue.getValue(), tickDelta);
        Vec3d camPos = camera.getPos();

        double renderX = predictPos.x - camPos.x;
        double renderY = predictPos.y - camPos.y;
        double renderZ = predictPos.z - camPos.z;

        float size = 0.35f;
        int color = ColorProvider.getThemeColor();

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1;

        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);

        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, size, size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private void renderPositionAuraPoint(MatrixStack matrices, Camera camera) {
        if (target == null) return;

        Vec3d position = null;
        BoatAura boatAura = Instance.get(BoatAura.class);
        if (boatAura != null && boatAura.isEnabled()) {
            position = boatAura.getRenderPosition(target);
        }

        if (position == null) {
            TpAura tpAura = Instance.get(TpAura.class);
            if (tpAura != null && tpAura.isEnabled()) {
                position = tpAura.getRenderPosition(target);
            }
        }

        if (position == null) return;

        Vec3d camPos = camera.getPos();
        double minX = position.x - 0.35 - camPos.x;
        double minY = position.y - camPos.y;
        double minZ = position.z - 0.35 - camPos.z;
        double maxX = position.x + 0.35 - camPos.x;
        double maxY = position.y + 0.7 - camPos.y;
        double maxZ = position.z + 0.35 - camPos.z;

        int color = ColorProvider.getThemeColor();
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(2.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        drawLineBox(buffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private void drawLineBox(BufferBuilder buffer, Matrix4f matrix, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float r, float g, float b, float a) {
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    @Override
    public void onEnable() {
        this.lastYaw = 0.0f;
        this.lastPitch = 0.0f;
        target = null;
        razvorotikTicks = 0;
        snapActive = false;
        snapTimer = 0;
        shieldPhase = 0;
        grim1204Rotation.reset(this);
        neuroRotation.reset(this);
        Onetap.getInstance().getModuleStorage().setSpeedAcceleration(0);

        if (!renderListenerRegistered) {
            WorldRenderEvents.LAST.register(renderListener);
            renderListenerRegistered = true;
        }

        super.onEnable();
    }

    @Override
    public void onDisable() {
        target = null;
        ticksToAttack = 0;
        speedAcceleration = 0;
        interpolationRotationInitialized = false;
        interpolationRotationTarget = null;
        targetOvershootYaw = 0;
        targetOvershootPitch = 0;
        jerkSpeedMultiplier = 1.0f;
        razvorotikTicks = 0;
        snapActive = false;
        snapTimer = 0;
        shieldPhase = 0;
        neuroRotation.reset(this);
        grim1204Rotation.reset(this);
        aresMineRotation.reset(this);
        Onetap.getInstance().getModuleStorage().setSpeedAcceleration(0);
        Onetap.getInstance().getModuleStorage().setRandomness(1);
        RotationComponent.getInstance().clearMoveFixMode("KillAura");
        RotationComponent.getInstance().stopRotation();
        freezeExpired = false;
        stopFreeze();
        super.onDisable();
    }
}
