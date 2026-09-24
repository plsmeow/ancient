package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableObject;
import meow.ancient.event.list.EventPacket;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.util.friend.Friend;
import meow.ancient.util.friend.FriendRepository;
import meow.ancient.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "Auto Tpaccept", moduleDesc = "Автоматически принимает запросы телепортации", moduleCategory = ModuleCategory.PLAYER)
public class AutoTpaccept extends Module {

    public ActionResult interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        if (!mc.world.getWorldBorder().contains(hitResult.getBlockPos())) {
            return ActionResult.FAIL;
        } else {
            MutableObject<ActionResult> mutableObject = new MutableObject();
            mutableObject.setValue(mc.interactionManager.interactBlockInternal(player, hand, hitResult));
            NetworkUtils.sendSilentPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, 0));
            return mutableObject.getValue();
        }
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;

        if (e.getPacket() instanceof GameMessageS2CPacket p) {
            String raw = p.content().getString();
            if (raw.contains("телепортироваться") || raw.contains("has requested teleport") || raw.contains("просит к вам телепортироваться") || raw.contains("запрашивает телепорт к вам")) {
                boolean yes = false;

                for (Friend friend : FriendRepository.getFriends()) {
                    if (raw.contains(friend.name())) {
                        yes = true;
                        break;
                    }
                }

                if (!yes) return;

                mc.getNetworkHandler().sendChatCommand("tpaccept");
            }
        }
    }
}