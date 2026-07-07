package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.math.CrystalDamageCalculator;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "CrystalAura", moduleCategory = ModuleCategory.COMBAT)
public class CrystalAura extends Module {
    private final BooleanSetting autoPlace = new BooleanSetting("Размещение", true);
    private final BooleanSetting autoBreak = new BooleanSetting("Взрыв", true);
    private final BooleanSetting antiSuicide = new BooleanSetting("Анти суицид", true);
    private final ModeSetting moveFix = new ModeSetting("MoveFix", "Сфокусированная", "Свободный", "Сфокусированная");
    private final SliderSetting targetRange = new SliderSetting("Target Range", 10.0f, 1.0f, 16.0f, 0.1f);
    private final SliderSetting placeRange = new SliderSetting("Place Range", 4.5f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting breakRange = new SliderSetting("Break Range", 4.5f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting wallsRange = new SliderSetting("Walls Range", 4.5f, 0.0f, 6.0f, 0.1f);
    private final SliderSetting minDamage = new SliderSetting("Min Damage", 6.0f, 0.0f, 20.0f, 0.1f);
    private final SliderSetting maxSelfDamage = new SliderSetting("Max Self Damage", 6.0f, 0.0f, 20.0f, 0.1f);
    private final SliderSetting placeDelay = new SliderSetting("Place Delay", 0.0f, 0.0f, 20.0f, 1.0f);
    private final SliderSetting breakDelay = new SliderSetting("Break Delay", 0.0f, 0.0f, 20.0f, 1.0f);

    private LivingEntity bestTarget;
    private EndCrystalEntity bestCrystal;
    private BlockPos bestPlacePos;
    private BlockHitResult bestPlaceHitResult;
    private double bestPlaceDamage;
    private int placeTimer;
    private int breakTimer;

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;

        findTarget();

        if (bestTarget == null) {
            resetActionState();
            return;
        }

        findBestCrystal();
        if (autoBreak.getValue() && bestCrystal != null && breakTimer <= 0) {
            attackCrystal(bestCrystal);
            breakTimer = breakDelay.getIntValue();
            if (placeTimer < 1) placeTimer = 1;
            return;
        }

        findBestPlacePosition();
        if (autoPlace.getValue() && bestPlacePos != null && bestPlaceHitResult != null && placeTimer <= 0) {
            placeCrystal(bestPlaceHitResult);
            placeTimer = placeDelay.getIntValue();
        }
    }

    private void findTarget() {
        bestTarget = null;
        double bestDistance = targetRange.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!FriendRepository.shouldAttack(player)) continue;

            double distance = mc.player.squaredDistanceTo(player);
            if (distance > targetRange.getValue() * targetRange.getValue()) continue;

            if (bestTarget == null || distance < bestDistance * bestDistance) {
                bestTarget = player;
                bestDistance = Math.sqrt(distance);
            }
        }
    }

    private void findBestCrystal() {
        bestCrystal = null;
        double bestDamage = 0.0;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive()) continue;

            double damage = getBreakDamage(crystal);
            if (damage <= 0.0) continue;

            if (damage > bestDamage) {
                bestDamage = damage;
                bestCrystal = crystal;
            }
        }
    }

    private void findBestPlacePosition() {
        bestPlacePos = null;
        bestPlaceHitResult = null;
        bestPlaceDamage = 0.0;

        if (!hasCrystalAvailable()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int radius = (int) Math.ceil(placeRange.getValue());

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    double damage = getPlaceDamage(pos);
                    if (damage <= 0.0) continue;

                    if (damage > bestPlaceDamage) {
                        BlockHitResult hitResult = getPlaceInfo(pos);
                        if (hitResult == null) continue;

                        bestPlaceDamage = damage;
                        bestPlacePos = pos;
                        bestPlaceHitResult = hitResult;
                    }
                }
            }
        }
    }

    private double getBreakDamage(EndCrystalEntity crystal) {
        if (bestTarget == null) return 0.0;

        Vec3d crystalPos = crystal.getPos();
        BlockPos obsidianPos = crystal.getBlockPos().down();

        if (isOutOfRange(crystalPos, crystal.getBlockPos(), false)) return 0.0;

        float selfDamage = CrystalDamageCalculator.calculateCrystalDamage(crystal, mc.player, 0.0);
        if (selfDamage > maxSelfDamage.getValue()) return 0.0;
        if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) return 0.0;

        float targetDamage = CrystalDamageCalculator.calculateCrystalDamage(crystal, bestTarget, 0.0);
        if (targetDamage < minDamage.getValue()) return 0.0;

        return targetDamage;
    }

    private double getPlaceDamage(BlockPos pos) {
        if (bestTarget == null) return 0.0;
        if (!canPlaceCrystal(pos)) return 0.0;

        Vec3d crystalVec = Vec3d.ofCenter(pos).add(0.0, 1.0, 0.0);
        BlockPos crystalPos = pos.up();

        if (isOutOfRange(crystalVec, crystalPos, true)) return 0.0;

        EndCrystalEntity fakeCrystal = new EndCrystalEntity(mc.world, crystalVec.x, crystalVec.y, crystalVec.z);
        float selfDamage = CrystalDamageCalculator.calculateCrystalDamage(fakeCrystal, mc.player, 0.0);
        if (selfDamage > maxSelfDamage.getValue()) return 0.0;
        if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) return 0.0;

        float targetDamage = CrystalDamageCalculator.calculateCrystalDamage(fakeCrystal, bestTarget, 0.0);
        if (targetDamage < minDamage.getValue()) return 0.0;

        return targetDamage;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return false;
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;
        if (!mc.world.getBlockState(pos.up(2)).isAir()) return false;

        Box crystalBox = new Box(pos.up());
        for (Entity entity : mc.world.getOtherEntities(null, crystalBox)) {
            if (entity instanceof EndCrystalEntity) continue;
            if (!entity.isSpectator()) return false;
        }

        return true;
    }

    private boolean isOutOfRange(Vec3d vec, BlockPos blockPos, boolean place) {
        double range = place ? placeRange.getValue() : breakRange.getValue();
        double walls = wallsRange.getValue();

        Vec3d eyes = mc.player.getEyePos();
        double distance = eyes.distanceTo(vec);
        if (distance > range) return true;

        BlockHitResult raycast = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eyes,
                vec,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        boolean visible = raycast.getType() == net.minecraft.util.hit.HitResult.Type.MISS || raycast.getBlockPos().equals(blockPos);
        return !visible && distance > walls;
    }

    private BlockHitResult getPlaceInfo(BlockPos blockPos) {
        Vec3d eyes = mc.player.getEyePos();

        for (Direction side : Direction.values()) {
            Vec3d hitVec = Vec3d.ofCenter(blockPos).add(
                    side.getVector().getX() * 0.5,
                    side.getVector().getY() * 0.5,
                    side.getVector().getZ() * 0.5
            );

            BlockHitResult result = mc.world.raycast(new net.minecraft.world.RaycastContext(
                    eyes,
                    hitVec,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    mc.player
            ));

            if (result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && result.getBlockPos().equals(blockPos)) {
                return result;
            }
        }

        Direction side = blockPos.getY() > eyes.y ? Direction.DOWN : Direction.UP;
        return new BlockHitResult(Vec3d.ofCenter(blockPos), side, blockPos, false);
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        rotateTo(crystal.getBoundingBox().getCenter(), false);
        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void placeCrystal(BlockHitResult hitResult) {
        Hand hand = getCrystalHand();
        if (hand == null) return;

        int previousSlot = mc.player.getInventory().selectedSlot;
        boolean switched = false;

        if (hand == Hand.MAIN_HAND && !mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            int crystalSlot = InventoryUtil.searchItemHotbar(Items.END_CRYSTAL);
            if (crystalSlot == -1) return;

            mc.player.getInventory().selectedSlot = crystalSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(crystalSlot));
            switched = true;
        }

        rotateTo(hitResult.getPos(), true);
        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand);

        if (switched) {
            mc.player.getInventory().selectedSlot = previousSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }
    }

    private void rotateTo(Vec3d vec, boolean place) {
        Rotation rotation = new Rotation(RotationUtil.calculate(vec));
        RotationComponent.update(rotation, 360, 360, 360, 360, 0, place ? 51 : 52, false, getMoveFixMode(), "CrystalAura");
    }

    private MoveFixMode getMoveFixMode() {
        return moveFix.is("Свободный") ? MoveFixMode.FREE : MoveFixMode.CORRECT;
    }

    private Hand getCrystalHand() {
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        if (InventoryUtil.searchItemHotbar(Items.END_CRYSTAL) != -1) return Hand.MAIN_HAND;
        return null;
    }

    private boolean hasCrystalAvailable() {
        return getCrystalHand() != null;
    }

    private void resetActionState() {
        bestCrystal = null;
        bestPlacePos = null;
        bestPlaceHitResult = null;
        bestPlaceDamage = 0.0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationComponent.getInstance().clearMoveFixMode("CrystalAura");
        RotationComponent.getInstance().stopRotation();
        bestTarget = null;
        resetActionState();
        placeTimer = 0;
        breakTimer = 0;
    }
}
