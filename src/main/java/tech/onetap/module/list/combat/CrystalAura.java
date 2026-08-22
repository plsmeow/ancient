package tech.onetap.module.list.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventEntitySpawn;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.math.CrystalDamageCalculator;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.CrystalTracker;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;
import tech.onetap.util.server.Server;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "CrystalAura", moduleCategory = ModuleCategory.COMBAT)
public class CrystalAura extends Module {
    private final BooleanSetting autoPlace = new BooleanSetting("AutoPlace", true);
    private final BooleanSetting autoBreak = new BooleanSetting("AutoBreak", true);
    private final BooleanSetting breakOnSpawn = new BooleanSetting("BreakOnSpawn", true);
    private final BooleanSetting antiSuicide = new BooleanSetting("AntiSuicide", true);
    private final BooleanSetting ignoreTerrain = new BooleanSetting("IgnoreTerrain", true);
    private final BooleanSetting assumeBestArmor = new BooleanSetting("AssumeBestArmor", false);
    private final BooleanSetting protectFriends = new BooleanSetting("ProtectFriends", true);
    private final BooleanSetting sacrificeTotem = new BooleanSetting("SacrificeTotem", true);
    private final BooleanSetting antiWeakness = new BooleanSetting("AntiWeakness", true);
    private final ModeSetting interactMode = new ModeSetting("Interact", "Strict", "Strict", "Default");
    private final ModeSetting targetLogic = new ModeSetting("TargetLogic", "Distance", "Distance", "HP", "FOV");
    private final ModeSetting moveFix = new ModeSetting("MoveFix", "Focused", "Free", "Focused", "None");
    private final ModeSetting rotationMode = new ModeSetting("Rotate", "Vanilla", "Vanilla", "None");
    private final SliderSetting targetRange = new SliderSetting("TargetRange", 10.0f, 1.0f, 16.0f, 0.1f);
    private final SliderSetting placeRange = new SliderSetting("PlaceRange", 5.0f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting placeWallsRange = new SliderSetting("PlaceWallRange", 3.5f, 0.0f, 6.0f, 0.1f);
    private final SliderSetting breakRange = new SliderSetting("BreakRange", 5.0f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting breakWallsRange = new SliderSetting("BreakWallRange", 3.5f, 0.0f, 6.0f, 0.1f);
    private final SliderSetting minDamage = new SliderSetting("MinDamage", 6.0f, 0.0f, 20.0f, 0.1f);
    private final SliderSetting maxSelfDamage = new SliderSetting("MaxSelfDamage", 10.0f, 0.0f, 20.0f, 0.1f);
    private final SliderSetting facePlaceHp = new SliderSetting("FacePlaceHp", 5.0f, 0.0f, 20.0f, 0.5f);
    private final SliderSetting armorBreaker = new SliderSetting("Armor %", 5.0f, 0.0f, 40.0f, 1.0f);
    private final SliderSetting placeDelay = new SliderSetting("PlaceDelay", 0.0f, 0.0f, 20.0f, 1.0f);
    private final SliderSetting breakDelay = new SliderSetting("BreakDelay", 0.0f, 0.0f, 20.0f, 1.0f);
    private final SliderSetting lowDelay = new SliderSetting("LowDelay", 0.0f, 0.0f, 20.0f, 1.0f);
    private final SliderSetting extrapolation = new SliderSetting("Extrapolation", 0.0f, 0.0f, 20.0f, 1.0f);
    private final SliderSetting attempts = new SliderSetting("MaxAttempts", 5.0f, 1.0f, 30.0f, 1.0f);
    private final BooleanSetting render = new BooleanSetting("Render", true);

    private static final double MAX_EXPLOSION_DISTANCE_SQ = 144.0;

    private final CrystalTracker tracker = new CrystalTracker();

    private PlayerEntity target;
    private CrystalCandidate bestCrystal;
    private PlaceCandidate bestPlace;

    private Box targetBox;
    private Box selfBox;
    private Vec3d selfEyes;
    private float targetProtection;
    private boolean facePlacing;

    private int placeTimer;
    private int breakTimer;

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;

        tracker.configure(attempts.getIntValue(), ping());
        tracker.update();

        target = findTarget();
        if (target == null) {
            reset();
            return;
        }

        prepareContext();

        bestCrystal = autoBreak.getValue() ? findBestCrystal() : null;
        bestPlace = autoPlace.getValue() && hasCrystal() ? findBestPlace() : null;

        facePlacing = bestPlace != null && bestPlace.damage < minDamage.getFloatValue()
                || bestCrystal != null && bestCrystal.damage < minDamage.getFloatValue();

        if (bestCrystal != null && breakTimer <= 0) {
            attackCrystal(bestCrystal.crystal);
            breakTimer = delay(breakDelay);
        }

        if (bestPlace != null && placeTimer <= 0) {
            placeCrystal(bestPlace.hitResult);
            placeTimer = delay(placeDelay);
        }
    }

    /** Свежий кристалл ломаем в том же тике, в котором пришёл его spawn-пакет. */
    @EventHandler
    public void onEntitySpawn(EventEntitySpawn event) {
        if (!(event.getEntity() instanceof EndCrystalEntity crystal)) return;
        if (mc.player == null || mc.world == null) return;

        tracker.confirmSpawn(crystal.getPos());

        if (!breakOnSpawn.getValue() || !autoBreak.getValue() || target == null) return;
        if (breakTimer > 0) return;

        prepareContext();

        CrystalCandidate candidate = evaluateCrystal(crystal);
        if (candidate == null) return;
        if (!passesMinDamage(candidate.damage)) return;

        attackCrystal(crystal);
        breakTimer = delay(breakDelay);
    }

    /** Взрыв на сервере убивает все кристаллы в радиусе — помечаем их, чтобы не бить в пустоту. */
    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof ExplosionS2CPacket packet)) return;
        if (mc.world == null) return;

        Vec3d center = packet.center();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal && crystal.squaredDistanceTo(center) <= MAX_EXPLOSION_DISTANCE_SQ) {
                tracker.setDead(crystal.getId());
            }
        }
    }

    @EventHandler
    public void onWorldRender(EventWorldRender event) {
        if (!render.getValue() || mc.player == null || mc.world == null || bestPlace == null) return;

        BlockPos pos = bestPlace.pos;
        int color = ColorProvider.getThemeColor();
        MatrixStack matrices = event.getMatrixStack();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        matrices.push();

        double minX = pos.getX() - camPos.x;
        double minY = pos.getY() - camPos.y;
        double minZ = pos.getZ() - camPos.z;

        drawFilled(matrices, minX, minY, minZ, minX + 1.0, minY + 1.0, minZ + 1.0, color);
        drawOutline(matrices, minX, minY, minZ, minX + 1.0, minY + 1.0, minZ + 1.0, color);

        matrices.pop();
    }

    private void prepareContext() {
        int targetTicks = extrapolation.getIntValue();
        int selfTicks = selfPredictTicks();

        targetBox = targetTicks > 0 ? PredictUtils.predictBox(target, targetTicks) : target.getBoundingBox();
        selfBox = selfTicks > 0 ? PredictUtils.predictBox(mc.player, selfTicks) : mc.player.getBoundingBox();
        selfEyes = mc.player.getEyePos();
        targetProtection = assumeBestArmor.getValue()
                ? CrystalDamageCalculator.BEST_ARMOR_PROTECTION
                : CrystalDamageCalculator.NO_PROTECTION_OVERRIDE;
    }

    /** Цель — любой игрок в радиусе, кроме друзей из .friend. */
    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double rangeSq = targetRange.getValue() * targetRange.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive() || player.isSpectator() || player.getHealth() <= 0.0f) continue;
            if (!FriendRepository.shouldAttack(player)) continue;
            if (mc.player.squaredDistanceTo(player) > rangeSq) continue;

            double score = targetScore(player);
            if (score < bestScore) {
                best = player;
                bestScore = score;
            }
        }

        return best;
    }

    private double targetScore(PlayerEntity player) {
        if (targetLogic.is("HP")) return player.getHealth() + player.getAbsorptionAmount();
        if (targetLogic.is("FOV")) {
            var angle = RotationUtil.calculate(player.getBoundingBox().getCenter());
            return RotationUtil.getAngleDifference(angle.x, mc.player.getYaw());
        }
        return mc.player.squaredDistanceTo(player);
    }

    private CrystalCandidate findBestCrystal() {
        List<CrystalCandidate> candidates = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;

            CrystalCandidate candidate = evaluateCrystal(crystal);
            if (candidate != null) candidates.add(candidate);
        }

        return pickBest(candidates);
    }

    private CrystalCandidate evaluateCrystal(EndCrystalEntity crystal) {
        if (!crystal.isAlive()) return null;
        if (tracker.isDead(crystal.getId()) || tracker.isCrystalStuck(crystal.getId())) return null;
        if (targetBox == null) return null;

        Vec3d center = crystal.getBoundingBox().getCenter();
        if (!inReach(center, crystal.getBlockPos(), breakRange.getFloatValue(), breakWallsRange.getFloatValue())) return null;

        Vec3d explosionPos = crystal.getPos();
        if (targetBox.getCenter().squaredDistanceTo(explosionPos) > MAX_EXPLOSION_DISTANCE_SQ) return null;

        float damage = CrystalDamageCalculator.crystalDamage(explosionPos, target, targetBox, ignoreTerrain.getValue(), targetProtection);
        if (damage < 1.5f) return null;

        float selfDamage = selfDamage(explosionPos);
        boolean override = shouldOverrideSelfDamage(damage, selfDamage);
        if (!isSafe(damage, selfDamage, override)) return null;

        return new CrystalCandidate(crystal, damage, selfDamage, override);
    }

    private PlaceCandidate findBestPlace() {
        List<PlaceCandidate> candidates = new ArrayList<>();

        BlockPos origin = mc.player.getBlockPos();
        int radius = (int) Math.ceil(placeRange.getValue());
        Vec3d targetCenter = targetBox.getCenter();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

                    PlaceCandidate candidate = evaluatePlace(pos, targetCenter);
                    if (candidate != null) candidates.add(candidate);
                }
            }
        }

        return pickBest(candidates);
    }

    private PlaceCandidate evaluatePlace(BlockPos base, Vec3d targetCenter) {
        BlockState state = mc.world.getBlockState(base);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return null;
        if (!mc.world.getBlockState(base.up()).isAir()) return null;

        Vec3d crystalPos = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        if (targetCenter.squaredDistanceTo(crystalPos) > MAX_EXPLOSION_DISTANCE_SQ) return null;

        BlockPos immutable = base.toImmutable();
        if (tracker.isPositionStuck(immutable)) return null;
        if (!inReach(crystalPos, immutable, placeRange.getFloatValue(), placeWallsRange.getFloatValue())) return null;
        if (isBlockedByEntity(immutable)) return null;

        float damage = CrystalDamageCalculator.crystalDamage(crystalPos, target, targetBox, ignoreTerrain.getValue(), targetProtection);
        if (damage < 1.5f) return null;

        float selfDamage = selfDamage(crystalPos);
        boolean override = shouldOverrideSelfDamage(damage, selfDamage);
        if (!isSafe(damage, selfDamage, override)) return null;

        BlockHitResult hitResult = interactResult(immutable, crystalPos);
        if (hitResult == null) return null;

        return new PlaceCandidate(immutable, hitResult, damage, selfDamage, override);
    }

    private float selfDamage(Vec3d explosionPos) {
        float selfDamage = CrystalDamageCalculator.crystalDamage(explosionPos, mc.player, selfBox, ignoreTerrain.getValue(), CrystalDamageCalculator.NO_PROTECTION_OVERRIDE);

        if (protectFriends.getValue()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (FriendRepository.shouldAttack(player)) continue;

                float friendDamage = CrystalDamageCalculator.crystalDamage(explosionPos, player, ignoreTerrain.getValue());
                if (friendDamage > selfDamage) selfDamage = friendDamage;
            }
        }

        return selfDamage;
    }

    /**
     * Лучший кандидат: сначала фильтр по MinDamage/фейсплейсу, затем сравнение
     * с приоритетом меньшего самоурона при сопоставимом уроне по цели.
     */
    private <T extends Candidate> T pickBest(List<T> candidates) {
        T best = null;

        for (T candidate : candidates) {
            if (!passesMinDamage(candidate.damage())) continue;

            if (best != null && candidate.override()
                    && targetHealth() < best.damage() && best.selfDamage() < candidate.selfDamage()) continue;

            if (best == null) {
                best = candidate;
                continue;
            }

            boolean dropOverride = best.override() && candidate.damage() > targetHealth() && candidate.selfDamage() < best.selfDamage();
            float delta = dropOverride ? 10.0f : 1.0f;

            if (Math.abs(best.damage() - candidate.damage()) < delta && Math.abs(best.selfDamage() - candidate.selfDamage()) > 1.0f) {
                if (best.selfDamage() >= candidate.selfDamage()) best = candidate;
            } else if (candidate.damage() > best.damage()) {
                best = candidate;
            }
        }

        return best;
    }

    private boolean passesMinDamage(float damage) {
        return damage >= minDamage.getFloatValue() || shouldFacePlace(damage);
    }

    /** Фейсплейс: цель добивается, у неё мало HP или разбита броня. */
    private boolean shouldFacePlace(float damage) {
        if (target == null) return false;

        float health = targetHealth();
        if (health - damage < 0.0f) return true;
        if (health <= facePlaceHp.getFloatValue()) return true;

        if (armorBreaker.getValue() > 0.0) {
            for (ItemStack armor : target.getArmorItems()) {
                if (armor.isEmpty() || armor.getMaxDamage() <= 0) continue;
                float durability = (armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage() * 100.0f;
                if (durability < armorBreaker.getValue()) return true;
            }
        }

        return false;
    }

    private boolean isSafe(float damage, float selfDamage, boolean override) {
        if (override) return true;
        if (antiSuicide.getValue() && selfDamage + 0.5f > selfHealth()) return false;
        return selfDamage <= maxSelfDamage.getValue();
    }

    /**
     * Игнорируем MaxSelfDamage, если этим взрывом мы убиваем или снимаем тотем,
     * а сами при этом остаёмся живы (или готовы разменять тотем).
     */
    private boolean shouldOverrideSelfDamage(float damage, float selfDamage) {
        if (target == null || selfDamage <= maxSelfDamage.getValue()) return false;

        boolean targetHasTotem = hasTotem(target);
        boolean selfHasTotem = hasTotem(mc.player);

        boolean lethalToTarget = damage > targetHealth();
        boolean lethalToSelf = selfDamage > selfHealth();

        if (lethalToSelf && selfHasTotem && lethalToTarget && !targetHasTotem) return sacrificeTotem.getValue();

        return lethalToTarget && !lethalToSelf;
    }

    private boolean hasTotem(LivingEntity entity) {
        return entity.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || entity.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    private float targetHealth() {
        return target.getHealth() + target.getAbsorptionAmount();
    }

    private float selfHealth() {
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    private boolean inReach(Vec3d vec, BlockPos ignored, float range, float wallsRange) {
        double distanceSq = selfEyes.squaredDistanceTo(vec);
        if (distanceSq > range * range) return false;
        if (distanceSq <= wallsRange * wallsRange) return true;

        return canSee(vec, ignored);
    }

    private boolean canSee(Vec3d vec, BlockPos ignored) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(selfEyes, vec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(ignored);
    }

    private boolean isBlockedByEntity(BlockPos base) {
        Box box = new Box(base.up()).expand(0.0, 1.0, 0.0);

        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (entity.isSpectator()) continue;
            if (entity instanceof ExperienceOrbEntity || entity instanceof ItemEntity) continue;
            if (entity instanceof EndCrystalEntity crystal && tracker.isDead(crystal.getId())) continue;
            return true;
        }

        return false;
    }

    private BlockHitResult interactResult(BlockPos base, Vec3d crystalPos) {
        if (interactMode.is("Default")) {
            Direction side = mc.world.isInBuildLimit(base.up()) ? Direction.UP : Direction.DOWN;
            return new BlockHitResult(crystalPos, side, base, false);
        }

        Direction side = strictSide(base);
        if (side == null) return null;

        Vec3d point = visiblePoint(base, side);
        if (point == null) return null;

        return new BlockHitResult(point, side, base, false);
    }

    /** Сторона, к которой действительно можно дотянуться: сверху, снизу или ближайшая боковая. */
    private Direction strictSide(BlockPos base) {
        double centerY = base.getY() + 0.5;

        if (selfEyes.y > centerY) return Direction.UP;
        if (selfEyes.y < base.getY() && mc.world.getBlockState(base.down()).isReplaceable()) return Direction.DOWN;

        Direction best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction side : Direction.Type.HORIZONTAL) {
            if (!mc.world.getBlockState(base.offset(side)).isReplaceable()) continue;

            Vec3d point = new Vec3d(
                    base.getX() + 0.5 + side.getOffsetX() * 0.5,
                    base.getY() + 0.99,
                    base.getZ() + 0.5 + side.getOffsetZ() * 0.5
            );

            double distance = selfEyes.squaredDistanceTo(point);
            if (distance < bestDistance) {
                best = side;
                bestDistance = distance;
            }
        }

        return best != null ? best : Direction.UP;
    }

    /** Первая точка на грани, до которой есть прямая видимость и она в пределах дистанции. */
    private Vec3d visiblePoint(BlockPos base, Direction side) {
        Box face = faceBox(side);
        double range = placeRange.getValue();
        double walls = placeWallsRange.getValue();

        for (double a = 0.0; a <= 0.7001; a += 0.1) {
            for (double b = 0.0; b <= 0.7001; b += 0.1) {
                Vec3d point = switch (side.getAxis()) {
                    case X -> new Vec3d(base.getX() + face.minX, base.getY() + face.minY + a, base.getZ() + face.minZ + b);
                    case Y -> new Vec3d(base.getX() + face.minX + a, base.getY() + face.minY, base.getZ() + face.minZ + b);
                    case Z -> new Vec3d(base.getX() + face.minX + a, base.getY() + face.minY + b, base.getZ() + face.minZ);
                };

                double distanceSq = selfEyes.squaredDistanceTo(point);
                if (distanceSq > range * range) continue;
                if (distanceSq > walls * walls && !canSee(point, base)) continue;

                return point;
            }
        }

        return null;
    }

    private Box faceBox(Direction side) {
        return switch (side) {
            case UP -> new Box(0.15, 1.0, 0.15, 0.85, 1.0, 0.85);
            case DOWN -> new Box(0.15, 0.0, 0.15, 0.85, 0.0, 0.85);
            case EAST -> new Box(1.0, 0.15, 0.15, 1.0, 0.85, 0.85);
            case WEST -> new Box(0.0, 0.15, 0.15, 0.0, 0.85, 0.85);
            case NORTH -> new Box(0.15, 0.15, 0.0, 0.85, 0.85, 0.0);
            case SOUTH -> new Box(0.15, 0.15, 1.0, 0.85, 0.85, 1.0);
        };
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        int previousSlot = -1;

        if (antiWeakness.getValue() && isWeakened()) {
            int slot = InventoryUtil.searchHotbarStack(stack -> stack.getItem() instanceof SwordItem || stack.getItem() instanceof MiningToolItem);
            if (slot != -1 && slot != mc.player.getInventory().selectedSlot) {
                previousSlot = mc.player.getInventory().selectedSlot;
                switchSlot(slot);
            }
        }

        rotateTo(crystal.getBoundingBox().getCenter(), false);

        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        tracker.onAttack(crystal, true);

        // Сервер уничтожит все кристаллы в радиусе взрыва — они больше не цели.
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity other && other.squaredDistanceTo(crystal) <= MAX_EXPLOSION_DISTANCE_SQ) {
                tracker.setDead(other.getId());
            }
        }

        if (previousSlot != -1) switchSlot(previousSlot);
    }

    private void placeCrystal(BlockHitResult hitResult) {
        Hand hand = crystalHand();
        if (hand == null) return;
        if (isBlockedByEntity(hitResult.getBlockPos())) return;

        int previousSlot = -1;

        if (hand == Hand.MAIN_HAND && !mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            int slot = InventoryUtil.searchItemHotbar(Items.END_CRYSTAL);
            if (slot == -1) return;

            previousSlot = mc.player.getInventory().selectedSlot;
            switchSlot(slot);
        }

        rotateTo(hitResult.getPos(), true);

        Hand usedHand = hand;
        mc.interactionManager.sendSequencedPacket(mc.world, sequence -> new PlayerInteractBlockC2SPacket(usedHand, hitResult, sequence));
        mc.player.swingHand(usedHand);

        tracker.onPlace(hitResult.getBlockPos(), true);

        if (previousSlot != -1) switchSlot(previousSlot);
    }

    private void switchSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private boolean isWeakened() {
        StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
        if (weakness == null) return false;

        StatusEffectInstance strength = mc.player.getStatusEffect(StatusEffects.STRENGTH);
        return strength == null || strength.getAmplifier() < weakness.getAmplifier();
    }

    private Hand crystalHand() {
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        if (InventoryUtil.searchItemHotbar(Items.END_CRYSTAL) != -1) return Hand.MAIN_HAND;
        return null;
    }

    private boolean hasCrystal() {
        return crystalHand() != null;
    }

    private int delay(SliderSetting setting) {
        return facePlacing ? lowDelay.getIntValue() : setting.getIntValue();
    }

    /**
     * Пока сервер подтверждает спавн кристалла, мы успеваем сместиться:
     * ping (спавн) + ping/2 (наша атака) в тиках.
     */
    private int selfPredictTicks() {
        return (int) Math.ceil(ping() * 1.5f / 50.0f);
    }

    private int ping() {
        return mc.getNetworkHandler() == null ? 0 : Server.getPing(mc.player);
    }

    private void rotateTo(Vec3d vec, boolean place) {
        if (rotationMode.is("None")) return;

        Rotation rotation = new Rotation(RotationUtil.calculate(vec));
        RotationComponent.update(rotation, 360, 360, 360, 360, 0, place ? 51 : 52, false, getMoveFixMode(), "CrystalAura");
    }

    private MoveFixMode getMoveFixMode() {
        if (moveFix.is("Free")) return MoveFixMode.FREE;
        if (moveFix.is("None")) return MoveFixMode.NONE;
        return MoveFixMode.CORRECT;
    }

    private void reset() {
        bestCrystal = null;
        bestPlace = null;
        facePlacing = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationComponent.getInstance().clearMoveFixMode("CrystalAura");
        RotationComponent.getInstance().stopRotation();
        tracker.reset();
        target = null;
        targetBox = null;
        selfBox = null;
        placeTimer = 0;
        breakTimer = 0;
        reset();
    }

    private interface Candidate {
        float damage();

        float selfDamage();

        boolean override();
    }

    private record CrystalCandidate(EndCrystalEntity crystal, float damage, float selfDamage, boolean override) implements Candidate {
    }

    private record PlaceCandidate(BlockPos pos, BlockHitResult hitResult, float damage, float selfDamage, boolean override) implements Candidate {
    }

    private void drawOutline(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(2.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = ColorProvider.red(color) / 255f;
        float g = ColorProvider.green(color) / 255f;
        float b = ColorProvider.blue(color) / 255f;

        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private void drawFilled(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float r = ColorProvider.red(color) / 255f;
        float g = ColorProvider.green(color) / 255f;
        float b = ColorProvider.blue(color) / 255f;
        float a = 130 / 500f;

        quad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        quad(buffer, matrix, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        quad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        quad(buffer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        quad(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        quad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, 1.0f);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, 1.0f);
    }

    private void quad(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x3, (float) y3, (float) z3).color(r, g, b, a);
        buffer.vertex(matrix, (float) x4, (float) y4, (float) z4).color(r, g, b, a);
    }
}
