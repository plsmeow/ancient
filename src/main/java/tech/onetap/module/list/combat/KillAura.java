package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
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
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import tech.onetap.Onetap;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.event.list.EventChangeSprint;
import tech.onetap.event.list.EventTick;
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
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;
import tech.onetap.util.text.ValueUnit;
import tech.onetap.util.neuro.rotation.AIRotationRecorder;
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
    public final ModeSetting moveFix = new ModeSetting("MoveFix", "Сфокусированная", "Свободный", "Сфокусированная");
    public final SliderSetting snapHoldTicks = new SliderSetting("Snap tick", ValueUnit.countable("тик", "тика", "тиков"), 2, 1, 10, 1)
            .setVisible(() -> rotation.is("Snap"));

    public final SliderSetting distance = new SliderSetting("Дистанция", ValueUnit.countable("блок", "блока", "блоков"), 3, 2, 6, 0.1f);
    public final SliderSetting elytraDistance = new SliderSetting("Дистанция (Элитры)", 300, 3, 500, 10);
    private final SliderSetting preRotation = new SliderSetting("Пре дистанция", ValueUnit.countable("блок", "блока", "блоков"), 1.5f, 0, 3, 0.1f);
    private final BooleanSetting stopWhileEating = new BooleanSetting("Не бить при еде", false);
    public final BooleanSetting breakSwing = new BooleanSetting("Ломать swing", false);
    public final BooleanSetting breakShield = new BooleanSetting("Ломать щит", true);
    public final BooleanSetting forceBreakShield = new BooleanSetting("Ломать щит без задержки", true)
            .setVisible(breakShield::getValue);
    private final List<net.minecraft.item.Item> AXES = List.of(
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
            Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE
    );
    public final BooleanSetting raycastCheck = new BooleanSetting("Проверка на наведение", true);
    public final BooleanSetting smartAim = new BooleanSetting("Умное наведение", true);
    public final BooleanSetting predictate = new BooleanSetting("Предикт на элитрах", true);
    public final SliderSetting predictValue = new SliderSetting("Предикт значение", 3, 1, 5, 0.1f);

    public final BooleanSetting hitAfterOvertake = new BooleanSetting("Бить токо после перегона", true);

    public final BooleanSetting onlySpace = new BooleanSetting("Только с пробелом", true);
    public final BooleanSetting clientLook = new BooleanSetting("Клиент лук", true);
    public final BooleanSetting showPredictPoint = new BooleanSetting("Показать предикт точку", true);
    public final BooleanSetting elytraTurnaround = new BooleanSetting("Разворот на элитрах", true);

    public static final BooleanSetting useResolver = new BooleanSetting("Резольвер (Elytra)", true);

    public final SliderSetting neuroYawMultiplier = new SliderSetting("Yaw множитель", 1.0, 0.5, 2.0, 0.05)
            .setVisible(() -> rotation.is("Neuro"));
    public final SliderSetting neuroPitchMultiplier = new SliderSetting("Pitch множитель", 1.0, 0.5, 2.0, 0.05)
            .setVisible(() -> rotation.is("Neuro"));
    public final BooleanSetting neuroCorrection = new BooleanSetting("Интерполяция", false)
            .setVisible(() -> rotation.is("Neuro"));


    public boolean isResolving = false;
    public Vec3d resolverPoint = null;
    private final StopWatch resolverTimer = new StopWatch();

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

    // Поля для логики Snap (используются также в canAttack/onUpdate)
    public boolean snapActive = false;
    public int snapTimer = 0;

    public boolean isSnapActive() {
        return snapActive;
    }

    public tech.onetap.util.rotation.MoveFixMode getMoveFixMode() {
        return moveFix.is("Свободный") ? tech.onetap.util.rotation.MoveFixMode.FREE : tech.onetap.util.rotation.MoveFixMode.CORRECT;
    }

    private final StopWatch turnaroundTimer = new StopWatch();

    public float preddict;
    public float lastYaw;
    public float lastPitch;
    private float velocityYaw = 0.0F;

    private boolean renderListenerRegistered = false;
    private final WorldRenderEvents.Last renderListener = context -> {
        if (isEnabled() && showPredictPoint.getValue()) {
            renderPredictPoint(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
        }
    };

    private void findResolverPoint() {
        if (mc.player == null || mc.world == null) return;
        Vec3d eye = mc.player.getEyePos();

        float oppositeYaw = mc.player.getYaw() + 180f;
        float searchPitch = -50f;

        int[] yawOffsets = {0, 30, -30, 45, -45, 60, -60, 90, -90};

        for (int offset : yawOffsets) {
            float testYaw = oppositeYaw + offset;

            float radYaw = (float) Math.toRadians(testYaw);
            float radPitch = (float) Math.toRadians(searchPitch);

            double x = -Math.sin(radYaw) * Math.cos(radPitch);
            double y = -Math.sin(radPitch);
            double z = Math.cos(radYaw) * Math.cos(radPitch);

            Vec3d checkVec = new Vec3d(x, y, z).normalize().multiply(8.0);
            Vec3d endPoint = eye.add(checkVec);

            if (mc.world.raycast(new RaycastContext(eye, endPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS) {
                resolverPoint = endPoint;
                return;
            }
        }
        resolverPoint = null;
    }

    @Subscribe
    private void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null) return;
        if (target == null && !rotation.is("Universal")) return;

        Onetap.getInstance().getModuleStorage().setRandomness(1);

        if (AIRotationRecorder.isRecording()) {
            return;
        }

        if (isResolving && target != null) {
            if (resolverTimer.isReached(300)) {
                isResolving = false;
            } else if (resolverPoint != null) {
                var rot = new Rotation(RotationUtil.calculate(resolverPoint));
                RotationComponent.update(rot, 360, 360, 360, 360, 0, 1, clientLook.getValue(), getMoveFixMode(), "KillAura");
                lastYaw = rot.getYaw();
                lastPitch = rot.getPitch();
                return;
            }
        }

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
            case "GrimFun" -> grimFunRotation.update(this, target);
            case "Neuro" -> neuroRotation.update(this, target);
            case "AresMine" -> aresMineRotation.update(this, target);
        }
    }

    @Subscribe
    private void onChangeSprint(EventChangeSprint e) {
        if (canStopSprinting()) e.setSprinting(false);
    }

    @Subscribe
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

            if (canAttack()) {
                if (useResolver.getValue() && mc.player.isGliding()) {
                    mc.player.setVelocity(0, 0, 0);
                    findResolverPoint();
                    if (resolverPoint != null) {
                        isResolving = true;
                        resolverTimer.reset();
                    }
                }
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

                mc.interactionManager.attackEntity(mc.player, target);

                crits.killAuraTriggered = false;
                maceKill.killAuraTriggered = false;

                mc.player.swingHand(breakSwing.getValue() ? Hand.OFF_HAND : Hand.MAIN_HAND);

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
            slothRotation.reset(this);
            sloth2Rotation.reset(this);
            if (!rotation.is("Universal")) {
                universalRotation.reset(this);
            }
            grimFunRotation.reset(this);
            neuroRotation.reset(this);
            aresMineRotation.reset(this);
        }
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

        boolean eating = mc.player.isUsingItem();

        if (eating && stopWhileEating.getValue()) return false;

        PlayerEntity player = Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer != null ?
                Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer : mc.player;

        if (!isInAttackDistance(player, target)) return false;

        isTurnaroundActive = false;
        if (target.isGliding()) {
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

    private boolean isInAttackDistance(PlayerEntity player, LivingEntity entity) {
        if (canReachWithPositionAura(entity)) return true;

        Vec3d nearestPoint = BestPoint.getNearestPoint(entity);
        if (nearestPoint == null) return false;

        double attackDistance = player.isGliding() ? elytraDistance.getValue() : distance.getValue();
        return player.getEyePos().distanceTo(nearestPoint) <= attackDistance;
    }

    private double getTargetSearchDistance(PlayerEntity player) {
        double searchDistance = player.isGliding() ? elytraDistance.getValue() : distance.getValue() + preRotation.getValue();

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
        if (target == null || !target.isGliding()) return;

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
        isResolving = false;
        resolverPoint = null;
        neuroRotation.reset(this);
        aresMineRotation.reset(this);
        Onetap.getInstance().getModuleStorage().setSpeedAcceleration(0);
        Onetap.getInstance().getModuleStorage().setRandomness(1);
        RotationComponent.getInstance().clearMoveFixMode("KillAura");
        RotationComponent.getInstance().stopRotation();
        super.onDisable();
    }
}
