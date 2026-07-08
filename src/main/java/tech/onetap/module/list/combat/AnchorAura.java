package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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

@ModuleInformation(moduleName = "AnchorAura", moduleCategory = ModuleCategory.COMBAT)
public class AnchorAura extends Module {
    private final BooleanSetting autoPlace = new BooleanSetting("Размещение", true);
    private final BooleanSetting autoBreak = new BooleanSetting("Взрыв", true);
    private final BooleanSetting antiSuicide = new BooleanSetting("Анти суицид", true);
    private final BooleanSetting airPlace = new BooleanSetting("Airplace", false);
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
    private final SliderSetting chargeDelay = new SliderSetting("Charge Delay", 0.0f, 0.0f, 20.0f, 1.0f);
    private final SliderSetting breakDelay = new SliderSetting("Break Delay", 0.0f, 0.0f, 20.0f, 1.0f);

    private LivingEntity bestTarget;
    private BlockPos bestPlacePos;
    private BlockHitResult bestPlaceHitResult;
    private double bestPlaceDamage;
    private BlockPos bestBreakPos;
    private BlockHitResult bestBreakHitResult;
    private double bestBreakDamage;
    private int placeTimer;
    private int chargeTimer;
    private int breakTimer;
    private int placeSearchCooldown;
    private BlockPos placedAnchorPos;

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (mc.world.getDimension().respawnAnchorWorks()) return;

        if (placeTimer > 0) placeTimer--;
        if (chargeTimer > 0) chargeTimer--;
        if (breakTimer > 0) breakTimer--;
        if (placeSearchCooldown > 0) placeSearchCooldown--;

        findTarget();
        if (bestTarget == null) {
            resetActionState();
            return;
        }

        findBestBreakPosition();
        preferPlacedAnchor();
        if (autoBreak.getValue() && bestBreakPos != null && bestBreakHitResult != null) {
            BlockState state = mc.world.getBlockState(bestBreakPos);
            int charges = getAnchorCharges(state);

            if (charges <= 0) {
                if (chargeTimer <= 0 && chargeAnchor(bestBreakHitResult)) {
                    chargeTimer = chargeDelay.getIntValue();
                    return;
                }
            } else if (breakTimer <= 0 && explodeAnchor(bestBreakHitResult)) {
                if (bestBreakPos.equals(placedAnchorPos)) {
                    placedAnchorPos = null;
                }
                breakTimer = breakDelay.getIntValue();
                return;
            }
        }

        if (!autoPlace.getValue() || !hasAnchorAvailable() || !hasGlowstoneAvailable()) {
            bestPlacePos = null;
            bestPlaceHitResult = null;
            bestPlaceDamage = 0.0;
            return;
        }

        if (placeSearchCooldown <= 0) {
            findBestPlacePosition();
        }

        if (bestPlacePos != null && bestPlaceHitResult != null && placeTimer <= 0) {
            if (placeAnchor(bestPlaceHitResult)) {
                placedAnchorPos = bestPlacePos;
                placeTimer = placeDelay.getIntValue();
                placeSearchCooldown = 0;
            }
        }
    }

    @Subscribe
    public void onWorldRender(EventWorldRender event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos renderPos = bestBreakPos != null ? bestBreakPos : bestPlacePos;
        if (renderPos == null) return;

        int color = ColorProvider.getThemeColor();
        MatrixStack matrices = event.getMatrixStack();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        matrices.push();

        double minX = renderPos.getX() - camPos.x;
        double minY = renderPos.getY() - camPos.y;
        double minZ = renderPos.getZ() - camPos.z;
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

    private void findBestBreakPosition() {
        bestBreakPos = null;
        bestBreakHitResult = null;
        bestBreakDamage = 0.0;

        BlockPos playerPos = mc.player.getBlockPos();
        int radius = (int) Math.ceil(breakRange.getValue());

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    double damage = getBreakDamage(pos);
                    if (damage <= 0.0) continue;

                    if (damage > bestBreakDamage) {
                        BlockHitResult hitResult = getAnchorHitResult(pos);
                        if (hitResult == null) continue;

                        bestBreakDamage = damage;
                        bestBreakPos = pos;
                        bestBreakHitResult = hitResult;
                    }
                }
            }
        }
    }

    private void preferPlacedAnchor() {
        if (placedAnchorPos == null) return;

        if (!mc.world.getBlockState(placedAnchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
            placedAnchorPos = null;
            return;
        }

        BlockHitResult hitResult = getAnchorHitResult(placedAnchorPos);
        if (hitResult == null) return;
        if (isOutOfRange(Vec3d.ofCenter(placedAnchorPos), placedAnchorPos, false)) return;

        bestBreakPos = placedAnchorPos;
        bestBreakHitResult = hitResult;
        bestBreakDamage = getBreakDamage(placedAnchorPos);
    }

    private void findBestPlacePosition() {
        bestPlacePos = null;
        bestPlaceHitResult = null;
        bestPlaceDamage = 0.0;

        if (!hasAnchorAvailable()) return;

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

        if (bestPlacePos == null) {
            placeSearchCooldown = 5;
        }
    }

    private double getBreakDamage(BlockPos pos) {
        if (bestTarget == null) return 0.0;

        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) return 0.0;
        if (isOutOfRange(Vec3d.ofCenter(pos), pos, false)) return 0.0;

        Vec3d explosionPos = Vec3d.ofCenter(pos);
        float selfDamage = CrystalDamageCalculator.calculateAnchorDamage(explosionPos, mc.player, 0.0, ignoreTerrain.getValue());
        if (selfDamage > maxSelfDamage.getValue()) return 0.0;
        if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) return 0.0;

        float targetDamage = CrystalDamageCalculator.calculateAnchorDamage(explosionPos, bestTarget, 0.0, ignoreTerrain.getValue());
        if (targetDamage < minDamage.getValue()) return 0.0;

        return targetDamage;
    }

    private double getPlaceDamage(BlockPos pos) {
        if (bestTarget == null) return 0.0;
        if (!canPlaceAnchor(pos)) return 0.0;
        if (isOutOfRange(Vec3d.ofCenter(pos), pos, true)) return 0.0;

        Vec3d explosionPos = Vec3d.ofCenter(pos);
        float selfDamage = CrystalDamageCalculator.calculateAnchorDamage(explosionPos, mc.player, 0.0, ignoreTerrain.getValue());
        if (selfDamage > maxSelfDamage.getValue()) return 0.0;
        if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) return 0.0;

        float targetDamage = CrystalDamageCalculator.calculateAnchorDamage(explosionPos, bestTarget, 0.0, ignoreTerrain.getValue());
        if (targetDamage < minDamage.getValue()) return 0.0;

        return targetDamage;
    }

    private boolean canPlaceAnchor(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!airPlace.getValue() && mc.world.getBlockState(pos.down()).isReplaceable()) return false;

        Box anchorBox = new Box(pos);
        for (Entity entity : mc.world.getOtherEntities(null, anchorBox)) {
            if (entity.isSpectator()) continue;
            if (entity instanceof ItemEntity) continue;
            return false;
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

        boolean visible = raycast.getType() == HitResult.Type.MISS || raycast.getBlockPos().equals(blockPos);
        return !visible && distance > walls;
    }

    private BlockHitResult getPlaceInfo(BlockPos blockPos) {
        if (airPlace.getValue()) {
            Direction side = mc.player.getHorizontalFacing().getOpposite();
            return new BlockHitResult(Vec3d.ofCenter(blockPos), side, blockPos, false);
        }

        Vec3d eyes = mc.player.getEyePos();

        for (Direction side : Direction.values()) {
            BlockPos neighbour = blockPos.offset(side.getOpposite());
            if (mc.world.getBlockState(neighbour).isReplaceable()) continue;

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

            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(neighbour)) {
                return new BlockHitResult(hitVec, side, neighbour, false);
            }
        }

        Direction side = mc.player.getHorizontalFacing().getOpposite();
        return new BlockHitResult(Vec3d.ofCenter(blockPos), side, blockPos.offset(side.getOpposite()), false);
    }

    private BlockHitResult getAnchorHitResult(BlockPos blockPos) {
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

            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(blockPos)) {
                return result;
            }
        }

        Direction side = blockPos.getY() > eyes.y ? Direction.DOWN : Direction.UP;
        return new BlockHitResult(Vec3d.ofCenter(blockPos), side, blockPos, false);
    }

    private boolean placeAnchor(BlockHitResult hitResult) {
        Hand hand = getAnchorHand();
        if (hand == null) return false;

        int previousSlot = mc.player.getInventory().selectedSlot;
        boolean switched = false;

        if (hand == Hand.MAIN_HAND && !mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
            int slot = InventoryUtil.searchItemHotbar(Items.RESPAWN_ANCHOR);
            if (slot == -1) return false;

            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            switched = true;
        }

        rotateTo(Vec3d.ofCenter(bestPlacePos), true);
        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand);

        if (placeDelay.getIntValue() == 0 && chargeTimer > 0) {
            chargeTimer = 0;
        }

        if (switched) {
            mc.player.getInventory().selectedSlot = previousSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }

        return true;
    }

    private boolean chargeAnchor(BlockHitResult hitResult) {
        Hand hand = getGlowstoneHand();
        if (hand == null) return false;

        int previousSlot = mc.player.getInventory().selectedSlot;
        boolean switched = false;

        if (hand == Hand.MAIN_HAND && !mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            int slot = InventoryUtil.searchItemHotbar(Items.GLOWSTONE);
            if (slot == -1) return false;

            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            switched = true;
        }

        rotateTo(Vec3d.ofCenter(bestBreakPos), false);
        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand);

        if (switched) {
            mc.player.getInventory().selectedSlot = previousSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }

        return true;
    }

    private boolean explodeAnchor(BlockHitResult hitResult) {
        Hand hand = getExplodeHand();
        if (hand == null) return false;

        int previousSlot = mc.player.getInventory().selectedSlot;
        boolean switched = false;

        if (hand == Hand.MAIN_HAND && mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            int slot = InventoryUtil.searchHotbarStack(stack -> !stack.isOf(Items.GLOWSTONE));
            if (slot == -1) return false;

            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            switched = true;
            hand = Hand.MAIN_HAND;
        }

        rotateTo(Vec3d.ofCenter(bestBreakPos), false);
        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand);

        if (switched) {
            mc.player.getInventory().selectedSlot = previousSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }

        return true;
    }

    private void rotateTo(Vec3d vec, boolean place) {
        if (rotationMode.is("None")) return;

        Rotation rotation = new Rotation(RotationUtil.calculate(vec));
        RotationComponent.update(rotation, 360, 360, 360, 360, 0, place ? 61 : 62, false, getMoveFixMode(), "AnchorAura");
    }

    private MoveFixMode getMoveFixMode() {
        return moveFix.is("Свободный") ? MoveFixMode.FREE : MoveFixMode.CORRECT;
    }

    private Hand getAnchorHand() {
        if (mc.player.getOffHandStack().isOf(Items.RESPAWN_ANCHOR)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) return Hand.MAIN_HAND;
        if (InventoryUtil.searchItemHotbar(Items.RESPAWN_ANCHOR) != -1) return Hand.MAIN_HAND;
        return null;
    }

    private Hand getGlowstoneHand() {
        if (mc.player.getOffHandStack().isOf(Items.GLOWSTONE)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) return Hand.MAIN_HAND;
        if (InventoryUtil.searchItemHotbar(Items.GLOWSTONE) != -1) return Hand.MAIN_HAND;
        return null;
    }

    private Hand getExplodeHand() {
        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) return Hand.MAIN_HAND;
        if (!mc.player.getOffHandStack().isOf(Items.GLOWSTONE)) return Hand.OFF_HAND;
        if (InventoryUtil.searchHotbarStack(stack -> !stack.isOf(Items.GLOWSTONE)) != -1) return Hand.MAIN_HAND;
        return null;
    }

    private boolean hasAnchorAvailable() {
        return getAnchorHand() != null;
    }

    private boolean hasGlowstoneAvailable() {
        return getGlowstoneHand() != null;
    }

    private int getAnchorCharges(BlockState state) {
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) return 0;
        return state.contains(RespawnAnchorBlock.CHARGES) ? state.get(RespawnAnchorBlock.CHARGES) : 0;
    }

    private void resetActionState() {
        bestPlacePos = null;
        bestPlaceHitResult = null;
        bestPlaceDamage = 0.0;
        bestBreakPos = null;
        bestBreakHitResult = null;
        bestBreakDamage = 0.0;
        placeSearchCooldown = 0;
        placedAnchorPos = null;
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
        RotationComponent.getInstance().clearMoveFixMode("AnchorAura");
        RotationComponent.getInstance().stopRotation();
        bestTarget = null;
        resetActionState();
        placeTimer = 0;
        chargeTimer = 0;
        breakTimer = 0;
        placeSearchCooldown = 0;
        placedAnchorPos = null;
    }
}
