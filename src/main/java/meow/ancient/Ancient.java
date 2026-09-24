package meow.ancient;

import lombok.Getter;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;

import meow.ancient.event.list.EventKeyInput;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleStorage;
import meow.ancient.module.list.render.Hide;
import meow.ancient.util.commands.CommandDispatcher;
import meow.ancient.util.commands.manager.CommandRepository;
import meow.ancient.util.config.ConfigManager;
import meow.ancient.util.draggable.DragManager;
import meow.ancient.util.friend.FriendRepository;
import meow.ancient.util.macro.MacroRepository;
import meow.ancient.util.math.TPSGetter;
import meow.ancient.util.player.combat.IdealHitUtils;
import meow.ancient.util.player.other.ServerManager;
import meow.ancient.util.rotation.ComponentManager;
import meow.ancient.util.script.ScriptManager;
import meow.ancient.util.staff.StaffManager;
import meow.ancient.util.target.TargetRepository;

import java.io.File;

public class Ancient implements ModInitializer {

    private static Ancient instance;

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

    public Ancient() {
        instance = this;


        eventBus = new EventBus();
        eventBus.registerLambdaFactory("meow.ancient", (lookupInMethod, klass) ->
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

    public static Ancient getInstance() {
        return instance == null ? new Ancient() : instance;
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
        meow.ancient.util.neuro.rotation.NeuroModel.getActive();
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