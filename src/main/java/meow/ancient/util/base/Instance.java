package meow.ancient.util.base;

import lombok.experimental.UtilityClass;
import meow.ancient.Ancient;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.util.rotation.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@UtilityClass
public class Instance {
    private final ConcurrentMap<Class<? extends Module>, Module> instances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<? extends Component>, Component> componentInstances = new ConcurrentHashMap<>();

    public <T extends Module> T get(Class<T> clazz) {
        return clazz.cast(instances.computeIfAbsent(clazz, instance -> Ancient.getInstance().getModuleStorage().get(instance)));
    }

    public <T extends Component> T getComponent(Class<T> clazz) {
        return clazz.cast(componentInstances.computeIfAbsent(clazz, instance -> Ancient.getInstance().getComponentManager().get(instance)));
    }

    public <T extends Module> Supplier<T> getSupplier(Class<T> clazz) {
        return () -> clazz.cast(instances.computeIfAbsent(clazz, instance -> Ancient.getInstance().getModuleStorage().get(instance)));
    }

    public <T extends Module> T get(final String module) {
        return Ancient.getInstance().getModuleStorage().get(module);
    }

    public List<Module> get(final ModuleCategory category) {
        return Ancient.getInstance().getModuleStorage().get(category);
    }
}
