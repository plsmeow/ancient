package tech.onetap.util.macro;

import com.google.common.eventbus.Subscribe;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import net.minecraft.util.Formatting;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.module.list.render.Hide;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.QuickLogger;
import tech.onetap.util.keyboard.KeyStorage;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Getter
public class MacroRepository implements IMinecraft, QuickLogger {

    private final File file = new File(".options/macros.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MacroRepository() {
        Onetap.getInstance().getEventBus().register(this);
    }

    private final List<Macro> macroList = new ArrayList<>();

    public boolean isEmpty() {
        return macroList.isEmpty();
    }

    public void addMacro(String message, int key) {
        macroList.add(new Macro(message, key));
    }

    public boolean hasMacro(String macroName) {
        for (Macro macro : macroList) {
            if (KeyStorage.getKey(macro.key()).equalsIgnoreCase(macroName)) {
                return true;
            }
        }
        return false;
    }

    public void deleteMacro(String name) {
        macroList.removeIf(macro -> KeyStorage.getKey(macro.key()).equalsIgnoreCase(name));
    }

    public void clearList() {
        macroList.clear();
    }

    public void save() {
        try {
            file.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(macroList, writer);
            }
        } catch (IOException e) {
            logDirect("Ошибка при сохранении макросов: " + e.getMessage(), Formatting.RED);
        }
    }

    public boolean removeByKey(int key) {
        return macroList.removeIf(m -> m.key() == key);
    }

    public void load() {
        if (!file.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<Macro>>() {}.getType();
            List<Macro> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                macroList.clear();
                macroList.addAll(loaded);
            }
        } catch (IOException e) {
            logDirect("Ошибка при загрузке макросов: " + e.getMessage(), Formatting.RED);
        }
    }

    @Subscribe
    public void onKey(EventKeyInput event) {
        if (Hide.isActive) return;
        int key = event.getKey();
        if (key == -1) return;
        if (mc.player == null || event.getAction() == 0 || mc.currentScreen != null) {
            return;
        }

        for (Macro macro : macroList) {
            if (macro.key() != key) continue;
            String msg = macro.message();
            if (msg.startsWith(".")) {
                msg = msg.substring(1);
                Onetap.getInstance().getCommandDispatcher().runCommand(msg);
            } else {
                if (msg.startsWith("/")) mc.getNetworkHandler().sendChatCommand(msg.replaceFirst("/", ""));
                else mc.getNetworkHandler().sendChatMessage(msg);
            }
        }
    }
}