package tech.onetap;

import lombok.Getter;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;

import tech.onetap.event.list.EventKeyInput;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleStorage;
import tech.onetap.module.list.render.Hide;
import tech.onetap.util.commands.CommandDispatcher;
import tech.onetap.util.commands.manager.CommandRepository;
import tech.onetap.util.config.ConfigManager;
import tech.onetap.util.draggable.DragManager;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.macro.MacroRepository;
import tech.onetap.util.math.TPSGetter;
import tech.onetap.util.neuro.rotation.TrainingLauncher;
import tech.onetap.util.player.combat.IdealHitUtils;
import tech.onetap.util.player.other.ServerManager;
import tech.onetap.util.rotation.ComponentManager;
import tech.onetap.util.script.ScriptManager;
import tech.onetap.util.staff.StaffManager;
import tech.onetap.util.target.TargetRepository;

import java.io.File;

public class Onetap implements ModInitializer {

    private static Onetap instance;

    @Getter
    private final IEventBus eventBus;

    @Getter
    private final ModuleStorage moduleStorage;
    @Getter
    private final ComponentManager componentManager;
    @Getter
    private final DragManager dragManager;
    @Getter
    private final CommandRepository commandRepository;
    @Getter
    private final MacroRepository macroRepository;
    @Getter
    private final ConfigManager configManager;
    @Getter
    private final CommandDispatcher commandDispatcher;
    @Getter
    private final StaffManager staffManager;
    @Getter
    private final ServerManager serverManager;
    @Getter
    private final TPSGetter tpsGetter;
    @Getter
    private final IdealHitUtils idealHitUtils;
    @Getter
    private final ScriptManager scriptManager;

    public Onetap() {
        instance = this;


        eventBus = new EventBus();
        eventBus.registerLambdaFactory("tech.onetap", (lookupInMethod, klass) ->
            (java.lang.invoke.MethodHandles.Lookup) lookupInMethod.invoke(null, klass, java.lang.invoke.MethodHandles.lookup()));
        eventBus.subscribe(this);



        moduleStorage = new ModuleStorage();
        componentManager = new ComponentManager();
        dragManager = new DragManager();
        macroRepository = new MacroRepository();
        configManager = new ConfigManager();
        staffManager = new StaffManager();
        staffManager.load();
        commandRepository = new CommandRepository();
        commandDispatcher = new CommandDispatcher();
        serverManager = new ServerManager();
        tpsGetter = new TPSGetter();
        idealHitUtils = new IdealHitUtils();
        scriptManager = new ScriptManager();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.save("autocfg");
            getDragManager().saveDraggables();
            getMacroRepository().save();
            FriendRepository.save();
            TargetRepository.save();
            staffManager.save();
        }));
        File dir = new File(".options/configs/");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static Onetap getInstance() {
        return instance == null ? new Onetap() : instance;
    }

    @Override
    public void onInitialize() {
        getModuleStorage().injectRegisterModules();
        componentManager.init();
        dragManager.load();
        macroRepository.load();
        FriendRepository.load();
        TargetRepository.load();
        configManager.load("autocfg");
        // Скрипты тренера (.ai train / .ai improve) живут в .options/ai/neuro
        TrainingLauncher.prepareTools();
    }

    @EventHandler
    private void onModuleKeyPressed(EventKeyInput event) {
        if (Hide.isActive) return;
        for (Module module : getModuleStorage().getModules()) {
            if (event.getAction() == 1 && MinecraftClient.getInstance().currentScreen == null) {
                if (module.getKey() == event.getKey()) {
                    module.toggle();
                }
            }
        }
    }
}