package tech.onetap.module.list.render;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

@ModuleInformation(moduleName = "Client Sounds", moduleDesc = "Создает звук при включении/выключении функции", moduleCategory = ModuleCategory.RENDER)
public class ClientSounds extends Module {

    private static ClientSounds INSTANCE;

    private final ModeSetting soundMode = new ModeSetting("Мод звука", "Первый", "Первый", "Второй", "Третий");

    private static final Identifier ENABLE_ID = Identifier.of("mre", "module_enable");
    private static final Identifier DISABLE_ID = Identifier.of("mre", "module_disable");
    private static final Identifier ENABLE_ID1 = Identifier.of("mre", "module_enable1");
    private static final Identifier DISABLE_ID1 = Identifier.of("mre", "module_disable1");
    private static final Identifier ENABLE_ID2 = Identifier.of("mre", "module_enable2");
    private static final Identifier DISABLE_ID2 = Identifier.of("mre", "module_disable2");

    private static final SoundEvent ENABLE_EVENT = SoundEvent.of(ENABLE_ID);
    private static final SoundEvent DISABLE_EVENT = SoundEvent.of(DISABLE_ID);
    private static final SoundEvent ENABLE_EVENT1 = SoundEvent.of(ENABLE_ID1);
    private static final SoundEvent DISABLE_EVENT1 = SoundEvent.of(DISABLE_ID1);
    private static final SoundEvent ENABLE_EVENT2 = SoundEvent.of(ENABLE_ID2);
    private static final SoundEvent DISABLE_EVENT2 = SoundEvent.of(DISABLE_ID2);

    private static net.minecraft.client.sound.SoundInstance lastSound;

    public ClientSounds() {
        INSTANCE = this;
    }

    public static void play(boolean enabled) {
        if (INSTANCE == null || INSTANCE.mc.getSoundManager() == null) return;

        if (!INSTANCE.isEnabled() && INSTANCE != Onetap.getInstance().getModuleStorage().get(ClientSounds.class)) {
            return;
        }

        if (lastSound != null) {
            INSTANCE.mc.getSoundManager().stop(lastSound);
        }

        SoundEvent soundToPlay = switch (INSTANCE.soundMode.getValue()) {
            case "Второй" -> enabled ? ENABLE_EVENT1 : DISABLE_EVENT1;
            case "Третий" -> enabled ? ENABLE_EVENT2 : DISABLE_EVENT2;
            default -> enabled ? ENABLE_EVENT : DISABLE_EVENT;
        };

        lastSound = PositionedSoundInstance.master(soundToPlay, 1f, 44.0f);
        INSTANCE.mc.getSoundManager().play(lastSound);
    }
} 