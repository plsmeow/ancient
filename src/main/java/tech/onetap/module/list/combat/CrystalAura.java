package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
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
import org.joml.Matrix4f;
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
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "CrystalAura", moduleCategory = ModuleCategory.COMBAT)
public class CrystalAura extends Module {
    private final BooleanSetting autoPlace = new BooleanSetting("Размещение", true);
    private final BooleanSetting autoBreak = new BooleanSetting("Взрыв", true);
    private final BooleanSetting antiSuicide = new BooleanSetting("Анти суицид", true);
    private final BooleanSetting ignoreTerrain = new BooleanSetting("Ignore terrain", false);
    private final ModeSetting moveFix = new ModeSetting("MoveFix", "Сфокусированная", "Свободный", "Сфокусированная");
    private final ModeSetting rotationMode = new ModeSetting("Ротация", "Vanilla", "Vanilla", "None");
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

    @Subscribe
    public void onWorldRender(EventWorldRender event) {
        if (mc.player == null || mc.world == null || bestPlacePos == null) return;

        int color = ColorProvider.getThemeColor();
        MatrixStack matrices = event.getMatrixStack();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        matrices.push();

        double minX = bestPlacePos.getX() - camPos.x;
        double minY = bestPlacePos.getY() - camPos.y;
        double minZ = bestPlacePos.getZ() - camPos.z;
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        drawFilled(matrices, minX, minY, minZ, maxX, maxY, maxZ, color);
        drawOutline(matrices, minX, minY, minZ, maxX, maxY, maxZ, color);

        matrices.pop();
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

        float selfDamage = CrystalDamageCalculator.calculateCrystalDamage(crystal, mc.player, 0.0, ignoreTerrain.getValue());
        if (selfDamage > maxSelfDamage.getValue()) return 0.0;
        if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) return 0.0;

        float targetDamage = CrystalDamageCalculator.calculateCrystalDamage(crystal, bestTarget, 0.0, ignoreTerrain.getValue());
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
        float selfDamage = CrystalDamageCalculator.calculateCrystalDamage(fakeCrystal, mc.player, 0.0, ignoreTerrain.getValue());
        if (selfDamage > maxSelfDamage.getValue()) return 0.0;
        if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) return 0.0;

        float targetDamage = CrystalDamageCalculator.calculateCrystalDamage(fakeCrystal, bestTarget, 0.0, ignoreTerrain.getValue());
        if (targetDamage < minDamage.getValue()) return 0.0;

        return targetDamage;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return false;
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;

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
        if (rotationMode.is("None")) return;

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
