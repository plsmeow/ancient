package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import meteordevelopment.discordipc.DiscordIPC;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.discord.ExtendedRichPresence;

@ModuleInformation(moduleName = "Discord RPC", moduleDesc = "Discord Rich Presence", moduleCategory = ModuleCategory.MISC)
public class DiscordRPC extends Module {

    private final ExtendedRichPresence rpc = new ExtendedRichPresence();
    private static final long APPLICATION_ID = 1469299305268645991L;
    private boolean buttonsAdded = false;

    @Override
    public void onEnable() {
        super.onEnable();
        DiscordIPC.start(APPLICATION_ID, null);
        rpc.setStart(System.currentTimeMillis() / 1000);
        buttonsAdded = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        DiscordIPC.stop();
    }

    @Subscribe
    private void onTick(EventTick e) {
        String details;

        if (mc.player != null) {
            if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
                details = "На сервере: " + mc.getCurrentServerEntry().address;
            } else if (mc.isInSingleplayer()) {
                details = "В одиночной игре";
            } else {
                details = "В игре";
            }
        } else if (mc.currentScreen != null) {
            String screenName = mc.currentScreen.getClass().getSimpleName();
            
            if (mc.currentScreen instanceof TitleScreen) {
                details = "В главном меню";
            } else if (mc.currentScreen instanceof MultiplayerScreen) {
                details = "В списке серверов";
            } else if (mc.currentScreen instanceof OptionsScreen) {
                details = "В настройках";
            } else if (screenName.contains("Alt") || screenName.contains("Account")) {
                details = "Выбирает аккаунт";
            } else {
                details = "В главном меню";
            }
        } else {
            details = "В главном меню";
        }

        rpc.setDetails(details);
        
        String username = "я хуесос";
        int uid = 1488;
        String discordId = "123";
        String discordAvatar = "asda";
        
        rpc.setState("User: " + username + " | UID: " + uid);
        rpc.setLargeImage("logo", "Ancient 1.21.4");

        if (discordId != null && discordAvatar != null) {
            String avatarUrl = "mp:avatars/" + discordId + "/" + discordAvatar;
            rpc.setSmallImage(avatarUrl, username);
        }

        if (!buttonsAdded) {
            rpc.addButton("Дискорд чита", "https://discord.gg/VxArvpNs28");
            buttonsAdded = true;
        }

        DiscordIPC.setActivity(rpc);
    }
}
