package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;

import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "High Jump", moduleCategory = ModuleCategory.MOVEMENT)
public class HighJump extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Funtime", "Funtime", "Grim Shulker");
    private final SliderSetting funtimeJumpY = new SliderSetting("Funtime JumpY", 0.42, 0.1, 1.0, 0.01).setVisible(() -> mode.is("Funtime"));
    private final SliderSetting shulkerJumpY = new SliderSetting("Shulker JumpY", 1.0, 0.5, 5.0, 0.1).setVisible(() -> mode.is("Grim Shulker"));

    @Subscribe
    public void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Funtime")) {
            handleFuntimeSnow();
        } else if (mode.is("Grim Shulker")) {
            handleGrimShulker();
        }
    }

    private void handleFuntimeSnow() {
        if (mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, funtimeJumpY.getFloatValue(), mc.player.getVelocity().z);
        }
    }

    private void handleGrimShulker() {
        BlockPos origin = mc.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterate(origin.add(-1, -1, -1), origin.add(1, 1, 1))) {
            BlockEntity be = mc.world.getBlockEntity(pos);
            if (!(be instanceof ShulkerBoxBlockEntity shulker)) continue;

            ShulkerBoxBlockEntity.AnimationStage stage = shulker.getAnimationStage();
            if (stage != ShulkerBoxBlockEntity.AnimationStage.OPENING
                    && stage != ShulkerBoxBlockEntity.AnimationStage.OPENED) continue;

            double dx = mc.player.getX() - (pos.getX() + 0.5);
            double dz = mc.player.getZ() - (pos.getZ() + 0.5);
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDistance > 1.0) continue;

            mc.player.setVelocity(mc.player.getVelocity().x, shulkerJumpY.getFloatValue(), mc.player.getVelocity().z);
            break;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }
}
