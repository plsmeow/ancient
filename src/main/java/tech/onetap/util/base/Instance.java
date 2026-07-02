package tech.onetap.util.base;

import lombok.experimental.UtilityClass;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.util.rotation.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@UtilityClass
public class Instance {
    private final ConcurrentMap<Class<? extends Module>, Module> instances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<? extends Component>, Component> componentInstances = new ConcurrentHashMap<>();

    public <T extends Module> T get(Class<T> clazz) {
        return clazz.cast(instances.computeIfAbsent(clazz, instance -> Onetap.getInstance().getModuleStorage().get(instance)));
    }

    public <T extends Component> T getComponent(Class<T> clazz) {
        return clazz.cast(componentInstances.computeIfAbsent(clazz, instance -> Onetap.getInstance().getComponentManager().get(instance)));
    }

    public <T extends Module> Supplier<T> getSupplier(Class<T> clazz) {
        return () -> clazz.cast(instances.computeIfAbsent(clazz, instance -> Onetap.getInstance().getModuleStorage().get(instance)));
    }

    public <T extends Module> T get(final String module) {
        return Onetap.getInstance().getModuleStorage().get(module);
    }

    public List<Module> get(final ModuleCategory category) {
        return Onetap.getInstance().getModuleStorage().get(category);
    }
}
