package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventTickEnd;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.player.ElytraHelper;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.base.Instance;
import tech.onetap.util.chat.ChatUtil;

@ModuleInformation(moduleName = "Air Stuck", moduleCategory = ModuleCategory.MOVEMENT)
public class AirStuck extends Module {
    private static final String SWAP_MODE = "Polar";

    private final BooleanSetting autoSwapChest = new BooleanSetting("Свап на нагрудник", true);
    private final BooleanSetting backElytra = new BooleanSetting("Вернуть при выкл", true)
            .setVisible(autoSwapChest::getValue);
    private final BooleanSetting fallCheck = new BooleanSetting("Проверка на падение", true);

    private Vec3d savedVelocity = Vec3d.ZERO;
    private boolean isElytra;

    private double lockX;
    private double lockZ;

    @Subscribe
    private void onPacket(EventPacket e) {
        if (mc.player == null) return;

        if (e.getPacket() instanceof PlayerMoveC2SPacket) e.cancelEvent();
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null) return;

        mc.player.setVelocity(0, 0, 0);
        mc.player.setNoGravity(true);
    }

    @Subscribe
    private void onTickEnd(EventTickEnd e) {
        if (mc.player == null) return;

        // EventTick (HEAD) обнуляет скорость ДО обработки ввода, поэтому WASD всё равно сдвигает игрока
        // по X/Z за время тика. Возвращаем позицию по горизонтали обратно после движения и гасим скорость.
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        mc.player.setPosition(lockX, mc.player.getY(), lockZ);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player == null || mc.world == null) return;

        if (mc.player.fallDistance == 0 && fallCheck.getValue()) {
            ChatUtil.send("Вам нужно падать");
            setEnabled(false);
            return;
        }

        mc.player.setNoGravity(true);

        lockX = mc.player.getX();
        lockZ = mc.player.getZ();

        savedVelocity = mc.player.getVelocity();

        boolean wearingElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;

        if (!wearingElytra || !autoSwapChest.getValue()) return;
        isElytra = true;

        Instance.get(ElytraHelper.class).swap(SWAP_MODE, true);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player == null) return;

        if (mc.player.fallDistance == 0 && fallCheck.getValue()) return;

        if (savedVelocity != null) mc.player.setVelocity(savedVelocity);


        mc.player.setNoGravity(false);

        boolean wearingChestPlate = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;

        if (!wearingChestPlate || !(autoSwapChest.getValue() && backElytra.getValue()) || !isElytra) return;
        isElytra = false;

        Instance.get(ElytraHelper.class).swap(SWAP_MODE, false);
    }
}
