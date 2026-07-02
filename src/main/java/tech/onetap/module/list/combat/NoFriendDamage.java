package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;

@ModuleInformation(moduleName = "No Friend Damage", moduleCategory = ModuleCategory.COMBAT)
public class NoFriendDamage extends Module {

    @Subscribe
    private void onAttack(EventAttack e) {
        for (Friend friend : FriendRepository.getFriends()) {
            if (e.getEntity() == mc.player) continue;
            if (!e.getEntity().getNameForScoreboard().equals(friend.name())) continue;
            e.cancelEvent();
        }
    }
}