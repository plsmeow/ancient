package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.Onetap;
import tech.onetap.event.list.ChatEvent;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@ModuleInformation(moduleName = "Hide", moduleDesc = "Анхук", moduleCategory = ModuleCategory.RENDER)
public class Hide extends Module {

    public static volatile boolean isActive = false;

    private final int[] code = new int[4];
    private final Map<String, Boolean> savedStates = new HashMap<>();
    private int tickCounter = 0;
    private boolean chatCleared = false;

    @Override
    public void onEnable() {
        super.onEnable();
        isActive = true;

        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            code[i] = random.nextInt(10);
        }

        String codeStr = String.valueOf(code[0]) + code[1] + code[2] + code[3];
        logDirect("Код dehide: " + Formatting.AQUA + codeStr);

        savedStates.clear();
        for (Module module : Onetap.getInstance().getModuleStorage().getModules()) {
            if (module == this) continue;
            savedStates.put(module.getName(), module.isEnabled());
            if (module.isEnabled()) {
                module.setEnabled(false);
            }
        }

        tickCounter = 0;
        chatCleared = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isActive = false;

        for (Module module : Onetap.getInstance().getModuleStorage().getModules()) {
            if (module == this) continue;
            Boolean wasEnabled = savedStates.get(module.getName());
            if (wasEnabled != null && wasEnabled) {
                module.setEnabled(true);
            }
        }
        savedStates.clear();
    }

    @Subscribe
    public void onChat(ChatEvent event) {
        if (!isActive) return;

        String msg = event.getMessage();
        String codeStr = "" + code[0] + code[1] + code[2] + code[3];

        if (msg.equals(codeStr)) {
            event.setCancelled(true);
            setEnabled(false);
        }
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (!isActive) return;

        tickCounter++;
        if (!chatCleared && tickCounter >= 200) {
            chatCleared = true;
            if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
                mc.inGameHud.getChatHud().clear(false);
            }
        }
    }
}
