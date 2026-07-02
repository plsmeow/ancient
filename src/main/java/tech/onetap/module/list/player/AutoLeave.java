package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;

@ModuleInformation(moduleName = "Auto Leave", moduleCategory = ModuleCategory.PLAYER)
public class AutoLeave extends Module {

    private final SliderSetting distance = new SliderSetting("Дистанция", 8, 1, 64, 1);
    private final ModeSetting mode = new ModeSetting("Действие", "Hub", "Hub", "Spawn", "Disconnect");

    @Subscribe
    private void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        double triggerDistance = distance.getValue();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (FriendRepository.isFriend(player.getNameForScoreboard())) continue;
            if (mc.player.distanceTo(player) > triggerDistance) continue;

            if (mode.is("Hub")) {
                mc.getNetworkHandler().sendChatCommand("hub");
            } else if (mode.is("Spawn")) {
                mc.getNetworkHandler().sendChatCommand("spawn");
            } else {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("AutoLeave"));
            }

            setEnabled(false);
            return;
        }
    }
}
