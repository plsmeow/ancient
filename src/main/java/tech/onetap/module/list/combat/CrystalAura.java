package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;

@ModuleInformation(moduleName = "CrystalAura", moduleCategory = ModuleCategory.COMBAT)
public class CrystalAura extends Module {

    private final BooleanSetting autoPlace = new BooleanSetting("Размещение", true);
    private final BooleanSetting autoBreak = new BooleanSetting("Взрыв", true);
    private final BooleanSetting noPlayer = new BooleanSetting("Не взрывать себя", true);
    private final SliderSetting range = new SliderSetting("Range", 4.0f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting placeDelay = new SliderSetting("Place Delay", 0f, 0f, 20f, 1f);
    private final SliderSetting breakDelay = new SliderSetting("Break Delay", 0f, 0f, 20f, 1f);
//мега кристалл аура от винеку
    private float rotationYaw;
    private float rotationPitch;
    private boolean rotating = false;
    private EndCrystalEntity targetCrystal = null;
    private BlockPos targetPos = null;
    private int breakTimer = 0;
    private int placeTimer = 0;

    private BlockPos lastPlacedPos = null;
    private long lastPlaceTime = 0;

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;

        PlayerEntity target = findTarget();
        if (target == null) {
            rotating = false;
            return;
        }

        if (autoBreak.getValue()) {
            targetCrystal = findBestCrystal(target);

            if (targetCrystal != null) {
                calcGrimRotations(targetCrystal.getBoundingBox().getCenter());

                if (rotating && mc.player != null) {
                    mc.player.setYaw(rotationYaw);
                    mc.player.setPitch(rotationPitch);
                }

                if (breakTimer >= breakDelay.getValue()) {
                    attackCrystal(targetCrystal);
                    breakTimer = 0;
                } else {
                    breakTimer++;
                }
            }
        }

        if (autoPlace.getValue()) {
            targetPos = findBestPlacePos(target);

            if (targetPos != null && targetCrystal == null) {
                if (lastPlacedPos != null && lastPlacedPos.equals(targetPos) &&
                        System.currentTimeMillis() - lastPlaceTime < 50) {
                    return;
                }

                if (!hasCrystalInHand()) {
                    switchToCrystal();
                }

                if (hasCrystalInHand()) {
                    Vec3d placeVec = targetPos.toCenterPos().add(0, 1, 0);
                    calcGrimRotations(placeVec);

                    if (rotating && mc.player != null) {
                        mc.player.setYaw(rotationYaw);
                        mc.player.setPitch(rotationPitch);
                    }

                    if (placeTimer >= placeDelay.getValue()) {
                        placeCrystal(targetPos);
                        lastPlacedPos = targetPos;
                        lastPlaceTime = System.currentTimeMillis();
                        placeTimer = 0;
                    } else {
                        placeTimer++;
                    }
                }
            }
        }
    }

    private PlayerEntity findTarget() {
        if (mc.world == null || mc.player == null) return null;

        PlayerEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.isDead() || player.getHealth() <= 0) continue;

            if (FriendRepository.getFriends().contains(player.getName().getString())) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > range.getValue()) continue;

            if (dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }

        return closest;
    }

    private EndCrystalEntity findBestCrystal(PlayerEntity target) {
        if (mc.world == null || mc.player == null) return null;

        EndCrystalEntity bestCrystal = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive()) continue;

            double distance = mc.player.distanceTo(crystal);
            if (distance > range.getValue()) continue;

            if (target != null) {
                double targetY = target.getY();
                double crystalY = crystal.getY();

                if (targetY < crystalY - 1.0) continue;
            }

            float crystalYCheck = (float) crystal.getY();
            float playerY = (float) mc.player.getY() + 0.5f;

            if (noPlayer.getValue() && crystalYCheck < playerY) {
                continue;
            }

            if (distance < closestDistance) {
                closestDistance = distance;
                bestCrystal = crystal;
            }
        }

        return bestCrystal;
    }

    private BlockPos findBestPlacePos(PlayerEntity target) {
        if (mc.world == null || mc.player == null || target == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos bestPos = null;
        double closestToTarget = Double.MAX_VALUE;

        int r = (int) Math.ceil(range.getValue());

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (!canPlaceCrystal(pos)) continue;

                    double distToPlayer = mc.player.getPos().distanceTo(pos.toCenterPos());
                    if (distToPlayer > range.getValue()) continue;

                    double targetY = target.getY();
                    double blockY = pos.getY();

                    if (targetY < blockY - 0.2) continue;

                    double distToTarget = target.getPos().distanceTo(pos.toCenterPos());

                    if (distToTarget < closestToTarget) {
                        closestToTarget = distToTarget;
                        bestPos = pos;
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        if (mc.world == null) return false;

        if (!mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN) &&
                !mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
            return false;
        }

        if (!mc.world.getBlockState(pos.up()).isAir()) return false;
        if (!mc.world.getBlockState(pos.up(2)).isAir()) return false;

        Box box = new Box(pos.up()).expand(0, 1, 0);
        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (entity instanceof EndCrystalEntity) continue;
            return false;
        }

        return true;
    }

    private void placeCrystal(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        boolean offhand = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);
        boolean mainhand = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL);

        if (!offhand && !mainhand) return;

        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;

        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);

        BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                Direction.UP,
                pos,
                false
        );

        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand);
    }

    private boolean hasCrystalInHand() {
        if (mc.player == null) return false;
        return mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) ||
                mc.player.getMainHandStack().isOf(Items.END_CRYSTAL);
    }

    private void switchToCrystal() {
        if (mc.player == null) return;

        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
                mc.player.getInventory().selectedSlot = i;
                return;
            }
        }
    }

    private void calcGrimRotations(Vec3d vec) {
        if (mc.player == null) return;

        float yawDelta = MathHelper.wrapDegrees(
                (float) MathHelper.wrapDegrees(
                        Math.toDegrees(Math.atan2(
                                vec.z - mc.player.getZ(),
                                vec.x - mc.player.getX()
                        )) - 90
                ) - rotationYaw
        );

        float pitchDelta = (float) (
                -Math.toDegrees(Math.atan2(
                        vec.y - (mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose())),
                        Math.sqrt(
                                Math.pow(vec.x - mc.player.getX(), 2) +
                                        Math.pow(vec.z - mc.player.getZ(), 2)
                        )
                ))
        ) - rotationPitch;

        float angleToRad = (float) Math.toRadians(27 * (mc.player.age % 30));
        yawDelta = (float) (yawDelta + Math.sin(angleToRad) * 3) + randomFloat(-1f, 1f);
        pitchDelta = pitchDelta + randomFloat(-0.6f, 0.6f);

        if (yawDelta > 180) yawDelta = yawDelta - 180;

        float clampedYawDelta = MathHelper.clamp(Math.abs(yawDelta), -180f, 180f);
        float clampedPitchDelta = MathHelper.clamp(pitchDelta, -45, 45);

        float newYaw = rotationYaw + (yawDelta > 0 ? clampedYawDelta : -clampedYawDelta);
        float newPitch = MathHelper.clamp(rotationPitch + clampedPitchDelta, -90.0F, 90.0F);

        double gcdFix = Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0) * 1.2;
        rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcdFix);
        rotationPitch = (float) (newPitch - (newPitch - rotationPitch) % gcdFix);

        rotating = true;
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (mc.player == null || mc.interactionManager == null) return;

        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private float randomFloat(float min, float max) {
        return min + (float) Math.random() * (max - min);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        rotating = false;
        targetCrystal = null;
        targetPos = null;
        lastPlacedPos = null;
        breakTimer = 0;
        placeTimer = 0;
    }
}