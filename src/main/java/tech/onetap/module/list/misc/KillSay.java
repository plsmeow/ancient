package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInformation(moduleName = "KillSay", moduleDesc = "Пишет сообщение при убийстве игрока", moduleCategory = ModuleCategory.MISC)
public class KillSay extends Module {

    private static final String[] KILL_MESSAGES = {
            "!{player} ликVидирOVан / ancient",
            "!{player} был удалён из этого мира / ancient",
            "!{player} спавн тебя ждет / ancient",
            "!{player} -ezz / ancient",
            "!{player} прощай мир... / ancient"
    };

    private UUID lastTargetUuid = null;
    private String lastTargetName = null;

    @Subscribe
    private void onAttack(EventAttack event) {
        if (mc.player == null || !(event.getEntity() instanceof PlayerEntity player) || player == mc.player) return;

        lastTargetUuid = player.getUuid();
        lastTargetName = player.getName().getString();
    }

    @Subscribe
    private void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null || event.getType() != EventPacket.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet) || packet.getStatus() != 3) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player) || player == mc.player || lastTargetUuid == null) return;
        if (!player.getUuid().equals(lastTargetUuid)) return;

        sendKillMessage(lastTargetName != null ? lastTargetName : player.getName().getString());
        lastTargetUuid = null;
        lastTargetName = null;
    }

    private void sendKillMessage(String playerName) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        String msg = KILL_MESSAGES[ThreadLocalRandom.current().nextInt(KILL_MESSAGES.length)]
                .replace("{player}", playerName);
        mc.getNetworkHandler().sendChatMessage(msg);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastTargetUuid = null;
        lastTargetName = null;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        lastTargetUuid = null;
        lastTargetName = null;
    }
}
