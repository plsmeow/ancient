package meow.ancient.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import meow.ancient.event.list.EventAttack;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.util.friend.Friend;
import meow.ancient.util.friend.FriendRepository;

@ModuleInformation(moduleName = "No Friend Damage", moduleDesc = "Не даёт атаковать друзей", moduleCategory = ModuleCategory.COMBAT)
public class NoFriendDamage extends Module {

    @EventHandler
    private void onAttack(EventAttack e) {
        for (Friend friend : FriendRepository.getFriends()) {
            if (e.getEntity() == mc.player) continue;
            if (!e.getEntity().getNameForScoreboard().equals(friend.name())) continue;
            e.cancelEvent();
        }
    }
}