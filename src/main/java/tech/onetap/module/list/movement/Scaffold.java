package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "Scaffold", moduleCategory = ModuleCategory.MOVEMENT)
public class Scaffold extends Module {

    // Переключатель режимов ротаций по аналогии с KillAura
    private final ModeSetting rotationMode = new ModeSetting("Ротация", "Grim", "Grim", "Vanilla");
    private final BooleanSetting clientLook = new BooleanSetting("Клиент лук", true);

    private BlockData currentBlock;

    @Subscribe
    private void onTick(final EventTick event) {
        RotationComponent.getInstance().forceFreeMovement(true);

        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            return;
        }

        preAction();
        postAction();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationComponent.getInstance().forceFreeMovement(false);
    }

    private void preAction() {
        currentBlock = null;

        if (findBlockSlot() == -1 && !isHoldingBlock(Hand.OFF_HAND)) {
            return;
        }

        BlockPos placePos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1.0, mc.player.getZ());
        if (!mc.world.getBlockState(placePos).isReplaceable()) {
            return;
        }

        currentBlock = checkNearBlocksExtended(placePos);
    }

    private void postAction() {
        if (currentBlock == null) {
            return;
        }

        if (mc.world.getBlockCollisions(
                mc.player,
                mc.player.getBoundingBox().expand(-0.3, 0.0, -0.3).offset(0.0, -0.5, 0.0)
        ).iterator().hasNext()) {
            return;
        }

        int slot = findBlockSlot();
        boolean offhand = isHoldingBlock(Hand.OFF_HAND);
        if (slot == -1 && !offhand) {
            return;
        }

        int oldSlot = mc.player.getInventory().selectedSlot;
        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;

        if (!offhand && oldSlot != slot) {
            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        BlockHitResult hitResult = createHitResult(currentBlock);
        float[] rotations = calculateAngle(hitResult.getPos());

        // --- ВЫБОР РЕЖИМА РОТАЦИИ ---
        float finalYaw = rotations[0];
        float finalPitch = rotations[1];

        if (rotationMode.is("Vanilla")) {
            // Режим Vanilla: Простой поворот на геометрический центр целевой грани блока
            Vec3d blockCenter = Vec3d.ofCenter(currentBlock.position());
            float[] vanillaRots = calculateAngle(blockCenter);
            finalYaw = vanillaRots[0];
            finalPitch = vanillaRots[1];
        } else if (rotationMode.is("Grim")) {
            // Режим Grim: Фиксируем углы под сетку чувствительности мыши (GCD), чтобы защитить от детектов углов
            float gcd = GCDFixer.getGCDValue();
            if (gcd > 0.0f) {
                finalYaw = mc.player.getYaw() + (float) Math.round((finalYaw - mc.player.getYaw()) / gcd) * gcd;
                finalPitch = mc.player.getPitch() + (float) Math.round((finalPitch - mc.player.getPitch()) / gcd) * gcd;
            }
        }

        // Задаем лимиты для Pitch во избежание флагов
        finalPitch = MathHelper.clamp(finalPitch, -89.9f, 89.9f);

        // --- КЛИЕНТСКАЯ ОБРАБОТКА (ЧЕРЕЗ КОМПОНЕНТ КЛИЕНТА) ---
        Rotation targetRotation = new Rotation(finalYaw, finalPitch);
        // Скаффолд всегда использует свободную коррекцию движения, независимо от режима MoveFix.
        RotationComponent.getInstance().forceFreeMovement(true);
        RotationComponent.update(targetRotation, 360, 360, 360, 360, 0, 1, clientLook.getValue());

        // Ставим блок легитно через клиентский менеджер
        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand); // Заменяем пакет взмаха на клиентскую анимацию

        if (!offhand && oldSlot != slot) {
            mc.player.getInventory().selectedSlot = oldSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }
    }

    private BlockHitResult createHitResult(BlockData blockData) {
        // Рандомизация точки клика в пределах пикселей грани для обхода проверок Grim Raytrace
        double offsetMultiplier = 0.1 + Math.random() * 0.8;

        Vec3d hitVec = new Vec3d(
                blockData.position().getX() + (blockData.facing().getAxis() == Direction.Axis.X ? (blockData.facing() == Direction.EAST ? 1.0 : 0.0) : offsetMultiplier),
                blockData.position().getY() + (blockData.facing().getAxis() == Direction.Axis.Y ? (blockData.facing() == Direction.UP ? 1.0 : 0.0) : offsetMultiplier),
                blockData.position().getZ() + (blockData.facing().getAxis() == Direction.Axis.Z ? (blockData.facing() == Direction.SOUTH ? 1.0 : 0.0) : offsetMultiplier)
        );

        return new BlockHitResult(hitVec, blockData.facing(), blockData.position(), false);
    }

    private BlockData checkNearBlocksExtended(BlockPos blockPos) {
        int[][] offsets = {
                {0, 0, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {-2, 0, 0}, {2, 0, 0}, {0, 0, 2}, {0, 0, -2}, {0, -1, 0},
                {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}
        };

        for (int[] offset : offsets) {
            BlockData data = checkNearBlocks(blockPos.add(offset[0], offset[1], offset[2]));
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private BlockData checkNearBlocks(BlockPos blockPos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = blockPos.offset(direction);
            if (!mc.world.getBlockState(neighbor).isReplaceable()) {
                return new BlockData(neighbor, direction.getOpposite());
            }
        }
        return null;
    }

    private int findBlockSlot() {
        if (isHoldingBlock(Hand.MAIN_HAND)) {
            return mc.player.getInventory().selectedSlot;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isValidBlockItem(mc.player.getInventory().getStack(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isHoldingBlock(Hand hand) {
        return isValidBlockItem(mc.player.getStackInHand(hand).getItem());
    }

    private boolean isValidBlockItem(net.minecraft.item.Item item) {
        return item instanceof BlockItem blockItem && !blockItem.getBlock().getDefaultState().isReplaceable();
    }

    private float[] calculateAngle(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        double diffX = target.x - eyes.x;
        double diffY = target.y - eyes.y;
        double diffZ = target.z - eyes.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new float[]{yaw, pitch};
    }

    private record BlockData(BlockPos position, Direction facing) {
    }
}