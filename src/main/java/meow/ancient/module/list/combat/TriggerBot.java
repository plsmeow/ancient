package meow.ancient.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import lombok.Getter;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.effect.StatusEffects;
import meow.ancient.Ancient;
import meow.ancient.event.list.EventChangeSprint;
import meow.ancient.event.list.EventTick;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.list.combat.AntiBot;
import meow.ancient.module.list.movement.AirStuck;
import meow.ancient.module.list.movement.Flight;
import meow.ancient.module.list.movement.GroundSpoof;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.module.settings.ModeListSetting;
import meow.ancient.module.settings.SliderSetting;
import meow.ancient.util.base.Instance;
import meow.ancient.util.friend.FriendRepository;
import meow.ancient.util.math.BestPoint;
import meow.ancient.util.player.combat.RaytraceUtil;
import meow.ancient.util.player.other.InventoryUtil;
import meow.ancient.util.player.other.WorldUtils;
import meow.ancient.util.player.simulate.SimulatedPlayer;
import meow.ancient.util.text.ValueUnit;

import java.util.List;

@ModuleInformation(moduleName = "Trigger Bot", moduleDesc = "Автоматически атакует сущность под прицелом (NoRot)", moduleCategory = ModuleCategory.COMBAT)
public class TriggerBot extends Module {

    public final SliderSetting distance = new SliderSetting(
            "Дистанция", ValueUnit.countable("блок", "блока", "блоков"), 3.0f, 2.0f, 6.0f, 0.1f
    );
    private final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Монстры", true),
            new BooleanSetting("Животные", true)
    );
    public final BooleanSetting raycastCheck = new BooleanSetting("Проверка на наведение", true);
    public final BooleanSetting noWallHit = new BooleanSetting("Не бить через стены", false);
    public final BooleanSetting pauseEating = new BooleanSetting("Не бить при еде", true);
    public final BooleanSetting onlyCriticals = new BooleanSetting("Только криты", true);
    public final BooleanSetting onlySpace = new BooleanSetting("Только с пробелом", false);
    public final BooleanSetting breakShield = new BooleanSetting("Ломать щит", true);
    public final BooleanSetting forceBreakShield = new BooleanSetting("Ломать щит без задержки", true)
            .setVisible(breakShield::getValue);
    public final BooleanSetting unblockShield = new BooleanSetting("Отжимать щит", false);
    private final SliderSetting unblockShieldDelay = new SliderSetting(
            "Отжатие: тиков до удара", ValueUnit.countable("тик", "тика", "тиков"), 2, 1, 6, 1
    ).setVisible(unblockShield::getValue);
    public final BooleanSetting breakSwing = new BooleanSetting("Ломать swing", false);

    private final List<Item> AXES = List.of(
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
            Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE
    );

    @Getter
    private LivingEntity target;
    private int ticksToAttack;
    private int shieldPhase;
    private int shieldTicks;

    @Override
    public void onDisable() {
        ticksToAttack = 0;
        target = null;
        shieldPhase = 0;
        shieldTicks = 0;
        super.onDisable();
    }

    @EventHandler
    private void onChangeSprint(EventChangeSprint e) {
        if (canStopSprinting()) e.setSprinting(false);
    }

    @EventHandler
    private void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        // Если включена KillAura, не дублируем удары
        KillAura killAura = Ancient.getInstance().getModuleStorage().get(KillAura.class);
        if (killAura != null && killAura.isEnabled()) return;

        if (ticksToAttack > 0) ticksToAttack--;

        updateTarget();

        if (target != null) {
            MaceKill maceKill = Ancient.getInstance().getModuleStorage().get(MaceKill.class);
            maceKill.updateFunskyState(target);

            if (canStopSprinting()) {
                mc.player.setSprinting(false);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }

            boolean attackReady = handleShieldUnblock(canAttack());
            if (attackReady) {
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));

                AutoMace autoMace = Ancient.getInstance().getModuleStorage().get(AutoMace.class);
                autoMace.prepareAttack();

                boolean funskySimpleHit = maceKill.isFunskyActive() && !maceKill.isFunskyWaitingSmash();
                boolean funskyAny = maceKill.isEnabled() && maceKill.isFunskyActive();

                // Приоритет свапов: ShieldBreak (топор) > AutoMace > BreachSwap
                int previousSlot = swapToAxe();
                if (previousSlot == -1 && !funskySimpleHit) {
                    previousSlot = autoMace.swapToMace();
                }
                if (previousSlot == -1 && !funskySimpleHit) {
                    BreachSwap breachSwap = breachSwap();
                    if (breachSwap != null) {
                        previousSlot = breachSwap.swapToBreachMace();
                    }
                }

                // DamageSwap: offhand swap перед ударом (не активен при GroundSpoof/FunskyMace)
                DamageSwap damageSwap = Ancient.getInstance().getModuleStorage().get(DamageSwap.class);
                boolean groundSpoofActive = Ancient.getInstance().getModuleStorage()
                        .get(GroundSpoof.class).isEnabled();
                boolean damageSwapMoved = false;
                if (damageSwap != null && damageSwap.isEnabled()
                        && previousSlot == -1 && !groundSpoofActive && !funskyAny) {
                    damageSwapMoved = damageSwap.beforeAttack();
                }

                BoatAura boatAura = Instance.get(BoatAura.class);
                TpAura tpAura = Instance.get(TpAura.class);
                boolean boatAuraMoved = boatAura != null && boatAura.beforeAttack(target);
                boolean tpAuraMoved = !boatAuraMoved && tpAura != null && tpAura.beforeAttack(target);

                Criticals crits = Ancient.getInstance().getModuleStorage().get(Criticals.class);

                maceKill.killAuraTriggered = true;

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

                if (damageSwapMoved) {
                    damageSwap.afterAttack();
                }

                if (previousSlot != -1) {
                    swapBack(previousSlot);
                }
                autoMace.scheduleAutoMaceElytraBack();

                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));

                if (maceKill.isEnabled() && maceKill.isFunskyActive() && maceKill.isFunskyWaitingSmash()) {
                    maceKill.onFunskySmash();
                }

                if (maceKill.isEnabled() && maceKill.isCustomDelayEnabled()) {
                    maceKill.resetAttackDelay();
                } else if (!autoMace.isForceAutoMaceReady(target) && !isForceBreakShieldReady()
                        && !Ancient.getInstance().getModuleStorage().get(FunskyMace.class).isEnabled()) {
                    ticksToAttack = 10;
                }
            }
        } else {
            shieldPhase = 0;
        }
    }

    private void updateTarget() {
        if (mc.player == null || mc.world == null) {
            target = null;
            return;
        }

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVector();
        double reach = distance.getValue();

        // 1. Быстрый путь: ванильный targetedEntity под прицелом
        if (mc.targetedEntity instanceof LivingEntity living && isValidEntity(living)) {
            double d = eyePos.distanceTo(BestPoint.getNearestPoint(living));
            if (d <= reach && (!noWallHit.getValue() || BestPoint.hasVisiblePoint(living, reach))) {
                best = living;
                bestDist = d;
            }
        }

        // 2. Рейкаст через RaytraceUtil по хитбоксам сущностей на дистанцию модуля (без ванильного лимита в 3 блока)
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidEntity(living)) continue;

            boolean aimed = RaytraceUtil.rayTrace(lookVec, reach, living.getBoundingBox().expand(0.1));
            if (!aimed && raycastCheck.getValue()) continue;

            double dist = eyePos.distanceTo(BestPoint.getNearestPoint(living));
            if (dist <= reach && dist < bestDist) {
                if (noWallHit.getValue() && !BestPoint.hasVisiblePoint(living, reach)) continue;
                best = living;
                bestDist = dist;
            }
        }

        this.target = best;
    }

    public boolean canAttack() {
        if (target == null || mc.player == null || mc.world == null) return false;

        boolean eating = mc.player.isUsingItem() && !isSelfBlockingShield();
        if (eating && pauseEating.getValue()) return false;

        if (target.isDead() || !target.isAlive()) return false;

        if (!isInAttackDistance(mc.player, target)) return false;

        if (noWallHit.getValue() && !BestPoint.hasVisiblePoint(target, distance.getValue())) return false;

        // Проверка наведения: вектор взгляда должен пересекать хитбокс
        if (raycastCheck.getValue()
                && !RaytraceUtil.rayTrace(mc.player.getRotationVector(), distance.getValue(), target.getBoundingBox().expand(0.1))
                && mc.targetedEntity != target) {
            return false;
        }

        if (isForceBreakShieldReady()) {
            return canCritical();
        }

        FunskyMace funskyMace = Ancient.getInstance().getModuleStorage().get(FunskyMace.class);
        if (funskyMace != null && funskyMace.isEnabled()) {
            return true;
        }

        MaceKill maceKill = Ancient.getInstance().getModuleStorage().get(MaceKill.class);
        if (maceKill != null && maceKill.isEnabled() && maceKill.isFunskyActive() && maceKill.isFunskyWaitingSmash()) {
            if (target.hurtTime > 0) return false;
            if (!maceKill.isFunskySmashReady()) return false;
        } else if (maceKill != null && maceKill.isEnabled() && maceKill.isFunskyActive() && maceKill.isFunskyTimerMode()) {
            return false;
        } else if (maceKill != null && maceKill.isEnabled() && maceKill.isCustomDelayEnabled()) {
            if (maceKill.getAttackTicks() > 0) return false;
        } else if (!Ancient.getInstance().getModuleStorage().get(AutoMace.class).isForceAutoMaceReady(target)) {
            BreachSwap breachSwap = breachSwap();
            if (breachSwap != null && breachSwap.isActive()) {
                if (!breachSwap.isVirtualCooldownReady()) return false;
            } else {
                if (mc.player.getAttackCooldownProgress(0.5f) < 0.98f) return false;
            }
            if (ticksToAttack > 0) return false;
        }

        return canCritical();
    }

    public boolean canCritical() {
        if (!onlyCriticals.getValue()) return true;

        // GroundSpoof: если включён спуф OnGround (mode False), пакеты движения идут с onGround = false,
        // сервер считает игрока в воздухе — криты и смэш булавы проходят с земли мгновенно
        boolean groundSpoofActive = Ancient.getInstance().getModuleStorage().get(GroundSpoof.class).isEnabled()
                && Ancient.getInstance().getModuleStorage().get(GroundSpoof.class).mode.is("False");
        if (groundSpoofActive) return true;

        AutoMace autoMace = Ancient.getInstance().getModuleStorage().get(AutoMace.class);
        if (autoMace != null && autoMace.isForceAutoMaceReady(target)) return true;

        FunskyMace funskyMace = Ancient.getInstance().getModuleStorage().get(FunskyMace.class);
        if (funskyMace != null && funskyMace.isEnabled()) return true;

        MaceKill maceKill = Ancient.getInstance().getModuleStorage().get(MaceKill.class);
        if (maceKill != null && maceKill.isEnabled()) return true;

        Criticals crits = Ancient.getInstance().getModuleStorage().get(Criticals.class);
        if (crits != null && crits.isEnabled()) return true;

        boolean notCrit = mc.player.isInLava()
                || mc.player.isClimbing()
                || mc.player.isSubmergedIn(FluidTags.WATER)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || WorldUtils.isInWeb()
                || mc.player.isGliding()
                || mc.player.hasVehicle()
                || mc.player.getAbilities().flying
                || Ancient.getInstance().getModuleStorage().get(AirStuck.class).isEnabled()
                || Ancient.getInstance().getModuleStorage().get(Flight.class).isEnabled()
                || mc.player.isOnGround() && !mc.options.jumpKey.isPressed() && !onlySpace.getValue();

        boolean serverConfirmedFall = !mc.player.isOnGround()
                && mc.player.fallDistance > 0f
                && mc.player.getVelocity().y < 0.0;

        return notCrit || serverConfirmedFall;
    }

    private boolean isValidEntity(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity instanceof ArmorStandEntity) return false;

        PlayerEntity player = mc.player;
        if (player == null) return false;

        if (entity instanceof PlayerEntity p) {
            if (p.getArmor() != 0 && !targets.isEnabled("Игроки")) return false;
            if (p.getArmor() == 0 && !targets.isEnabled("Голые")) return false;
            if (Ancient.getInstance().getModuleStorage().get(AntiBot.class).isBot(p)) return false;
            if (!FriendRepository.shouldAttack(p)) return false;
        } else if (entity instanceof HostileEntity || entity instanceof AmbientEntity) {
            if (!targets.isEnabled("Монстры")) return false;
        } else if (entity instanceof PassiveEntity || entity instanceof FishEntity) {
            if (!targets.isEnabled("Животные")) return false;
        } else {
            return false;
        }

        Vec3d nearest = BestPoint.getNearestPoint(entity);
        if (nearest == null || player.getEyePos().distanceTo(nearest) > distance.getValue()) {
            return false;
        }

        return true;
    }

    private boolean isInAttackDistance(PlayerEntity player, LivingEntity entity) {
        Vec3d nearestPoint = BestPoint.getNearestPoint(entity);
        if (nearestPoint == null) return false;
        return player.getEyePos().distanceTo(nearestPoint) <= distance.getValue();
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
        int lead = Math.max(1, (int) unblockShieldDelay.getValue());

        if (shieldPhase == 0) {
            if (manualShield && (attackReady || isHitImminent(lead))) {
                mc.interactionManager.stopUsingItem(mc.player);
                shieldPhase = 1;
                shieldTicks = 0;
                return false;
            }
            return attackReady;
        }

        shieldTicks++;

        if (!mc.options.useKey.isPressed()) {
            shieldPhase = 0;
            return attackReady;
        }

        if (attackReady && shieldTicks >= 1 && !mc.player.isUsingItem()) {
            return true;
        }

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

    private boolean isTargetBlocking() {
        return target != null && target.isUsingItem() && target.getActiveItem().isOf(Items.SHIELD);
    }

    private boolean isForceBreakShieldReady() {
        int axeSlot = -1;
        for (Item axe : AXES) {
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
        for (Item axe : AXES) {
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

    private BreachSwap breachSwap() {
        return Ancient.getInstance().getModuleStorage().get(BreachSwap.class);
    }

    public boolean canStopSprinting() {
        if (target == null) return false;
        if (!Ancient.getInstance().getIdealHitUtils().cooldownIsReached(true)) return false;
        if (ticksToAttack > 1) return false;
        boolean groundSpoofActive = Ancient.getInstance().getModuleStorage().get(GroundSpoof.class).isEnabled();
        if (!groundSpoofActive && SimulatedPlayer.simulateLocalPlayer(1).fallDistance == 0) return false;
        return true;
    }
}
