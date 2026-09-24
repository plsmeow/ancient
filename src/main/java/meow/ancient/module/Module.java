package meow.ancient.module;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import meow.ancient.Ancient;
import meow.ancient.module.list.render.ClientSounds;
import meow.ancient.module.list.render.Interface;
import meow.ancient.module.settings.ModeSetting;
import meow.ancient.module.settings.Setting;
import meow.ancient.util.IMinecraft;
import meow.ancient.util.QuickLogger;
import meow.ancient.util.base.Instance;
import meow.ancient.util.render.math.Animation;
import meow.ancient.util.render.math.Easing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class Module implements IMinecraft, QuickLogger {
    private final String name, desc;
    private final ModuleCategory category;
    private int key;
    private boolean enabled;
    private final Animation animation = new Animation(Easing.BACK_OUT, 450);

    public final MinecraftClient mc = MinecraftClient.getInstance();

    private final List<Setting> settings = new ArrayList<>();

    public Module() {
        ModuleInformation information = getClass().getAnnotation(ModuleInformation.class);

        this.name = information.moduleName();
        this.desc = information.moduleDesc();
        this.category = information.moduleCategory();
        this.key = information.moduleKeybind();
    }

    public List<Setting> getSettings() {
        return Arrays.stream(this.getClass().getDeclaredFields()).map(field -> {
            try {
                field.setAccessible(true);
                return field.get(this);
            } catch (IllegalAccessException e) {
                ;
            }
            return null;
        }).filter(field -> field instanceof Setting).map(field -> (Setting) field).collect(Collectors.toList());
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
            Interface.NotificationManager.post(this.name, enabled);
        }
        if (name.equals("ClickGui")) return;
        ClientSounds soundsModule = Instance.get(ClientSounds.class);
        if (soundsModule != null && (soundsModule.isEnabled() || this instanceof ClientSounds)) {
            ClientSounds.play(this.isEnabled());
        }
    }

    protected ModeSetting modeCreate() {
        return new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim", "Polar");
    }

    public void onEnable() {
        Ancient.getInstance().getEventBus().subscribe(this);
    }

    public void onDisable() {
        Ancient.getInstance().getEventBus().unsubscribe(this);
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }
}