package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Anti Bot", moduleCategory = ModuleCategory.COMBAT)
public class AntiBot extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Normal", "Normal", "Test");

    private final List<PlayerEntity> botsMap = new ArrayList<>();

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.world == null || mc.player == null) return;

        for (var entity : mc.world.getPlayers()) {
            if (mc.player == entity) continue;

            var isBot = false;

            switch (mode.getValue()) {
                case "Normal" -> {
                    // Проверяем части брони по отдельности в цикле, чтобы убедиться, что они подходят под условия
                    for (var i = 0; i < 4; i++) {
                        var armorPiece = entity.getInventory().armor.get(i);

                        if (!armorPiece.isEmpty()
                                && armorPiece.isEnchantable()
                                && !armorPiece.isDamaged()
                                && (entity.getInventory().armor.get(0).getItem() == Items.LEATHER_BOOTS
                                || entity.getInventory().armor.get(1).getItem() == Items.LEATHER_LEGGINGS
                                || entity.getInventory().armor.get(2).getItem() == Items.LEATHER_CHESTPLATE
                                || entity.getInventory().armor.get(3).getItem() == Items.LEATHER_HELMET
                                || entity.getInventory().armor.get(0).getItem() == Items.IRON_BOOTS
                                || entity.getInventory().armor.get(1).getItem() == Items.IRON_LEGGINGS
                                || entity.getInventory().armor.get(2).getItem() == Items.IRON_CHESTPLATE
                                || entity.getInventory().armor.get(3).getItem() == Items.IRON_HELMET)
                                && !entity.getMainHandStack().isEmpty()
                                && entity.getOffHandStack().isEmpty()
                                && entity.getHungerManager().getFoodLevel() == 20) {

                            isBot = true;
                            break;
                        }
                    }
                }
                case "Test" -> {
                    if (mc.getNetworkHandler() != null) {
                        // Если игрока нет в актуальном TabList (списке сетевых подключений), считаем его ботом
                        var playerEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
                        if (playerEntry == null) {
                            isBot = true;
                        }
                    }
                }
            }

            if (isBot) {
                if (!botsMap.contains(entity)) botsMap.add(entity);
            } else {
                botsMap.remove(entity);
            }
        }
    }

    @Override
    public void onDisable() {
        botsMap.clear();
        super.onDisable();
    }

    public boolean isBot(PlayerEntity player) {
        return botsMap.contains(player);
    }
}