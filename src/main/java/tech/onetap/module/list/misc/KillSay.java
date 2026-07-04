package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;

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

    private UUID targetUuid = null;
    private String targetName = null;

    @Subscribe
    private void onTick(EventTick ignored) {
        KillAura aura = Onetap.getInstance().getModuleStorage().get(KillAura.class);
        if (aura == null || !aura.isEnabled() || aura.getTarget() == null) {
            targetUuid = null;
            targetName = null;
            return;
        }

        if (aura.getTarget().getUuid().equals(targetUuid)) return;

        targetUuid = aura.getTarget().getUuid();
        targetName = aura.getTarget().getName().getString();
    }

    @Subscribe
    private void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null || event.getType() != EventPacket.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet) || packet.getStatus() != 3) return;
        if (targetUuid == null) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player) || player == mc.player) return;
        if (!player.getUuid().equals(targetUuid)) return;

        sendKillMessage(targetName != null ? targetName : player.getName().getString());
        targetUuid = null;
        targetName = null;
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
        targetUuid = null;
        targetName = null;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        targetUuid = null;
        targetName = null;
    }
}
