package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "AutoTrap", moduleCategory = ModuleCategory.COMBAT)
public class AutoTrap extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Паутина", "Паутина", "Ягоды", "Общий");
    private final SliderSetting range = new SliderSetting("Дистанция", 4.0f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting delay = new SliderSetting("Задержка (тики)", 2.0f, 0.0f, 10.0f, 1.0f);
    private final BooleanSetting rotate = new BooleanSetting("Ротация", true);

    private int ticks;

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        if (ticks < delay.getValue()) {
            ticks++;
            return;
        }

        PlayerEntity target = getTarget();
        if (target == null) return;

        BlockPos pos = target.getBlockPos();
        if (canPlace(pos)) {
            if (placeBlock(pos)) {
                ticks = 0;
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationComponent.getInstance().clearMoveFixMode("AutoTrap");
        RotationComponent.getInstance().stopRotation();
    }

    private boolean canPlace(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir()) return false;
        if (mc.player.getPos().distanceTo(Vec3d.ofCenter(pos)) > range.getValue()) return false;

        // Проверка для ягод: должны стоять на земле или траве
        if (mode.getValue().equals("Ягоды") || mode.getValue().equals("Общий")) {
            var blockUnder = mc.world.getBlockState(pos.down()).getBlock();
            if (blockUnder != Blocks.DIRT && blockUnder != Blocks.GRASS_BLOCK) return false;
        }

        return true;
    }

    private boolean placeBlock(BlockPos pos) {
        // Получаем предмет в зависимости от режима
        var item = getTargetItem();

        // Используем поиск по предикату, как это сделано в других модулях проекта
        int slot = InventoryUtil.searchItemStack(stack -> stack.getItem() == item);

        if (slot == -1) return false;

        // Поиск поверхности для клика
        Direction targetFacing = null;
        BlockPos neighbor = null;
        for (Direction dir : Direction.values()) {
            BlockPos offset = pos.offset(dir);
            if (!mc.world.getBlockState(offset).isAir()) {
                neighbor = offset;
                targetFacing = dir.getOpposite();
                break;
            }
        }

        if (neighbor == null) return false;

        // Ротация (логика из CrystalAura)
        if (rotate.getValue()) {
            // Вставьте вызов вашего метода ротации, если он вынесен в утилиту
        }

        // Сохраняем текущий слот и переключаемся на нужный
        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        // Отправка пакета клика
        Vec3d hitVec = Vec3d.ofCenter(neighbor).add(Vec3d.of(targetFacing.getVector()).multiply(0.5));
        if (rotate.getValue()) {
            rotateToBlock(pos); // Вызываем наш метод ротации
        }
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, targetFacing, neighbor, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        // Возвращаем слот обратно
        mc.player.getInventory().selectedSlot = oldSlot;
        return true;
    }

    private net.minecraft.item.Item getTargetItem() {
        return switch (mode.getValue()) {
            case "Ягоды" -> Items.SWEET_BERRIES;
            case "Общий" -> {
                // Проверяем наличие паутины через поиск слота (если > -1, значит предмет есть)
                boolean hasCobweb = InventoryUtil.searchItemStack(stack -> stack.getItem() == Items.COBWEB) != -1;
                yield hasCobweb ? Items.COBWEB : Items.SWEET_BERRIES;
            }
            default -> Items.COBWEB;
        };
    }

    private void rotateToBlock(BlockPos pos) {
        // Вычисляем углы до центра блока
        Vec3d eyes = mc.player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);

        double diffX = blockCenter.x - eyes.x;
        double diffY = blockCenter.y - eyes.y;
        double diffZ = blockCenter.z - eyes.z;

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, dist)));

        RotationComponent.update(
                new Rotation(yaw, pitch),
                360, 360, 360, 360, 0, 1, false, MoveFixMode.FREE, "AutoTrap"
        );
    }

    private PlayerEntity getTarget() {
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.distanceTo(mc.player) <= range.getValue())
                .findFirst().orElse(null);
    }
}