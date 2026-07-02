package tech.onetap.util.rotation;

import tech.onetap.Onetap;

import java.util.HashMap;

public final class ComponentManager extends HashMap<Class<? extends Component>, Component> {

    public void init() {
        add(new RotationComponent(), new FreeLookComponent());

        values().forEach(component -> Onetap.getInstance().getEventBus().register(component));
    }

    public void add(Component... components) {
        for (Component component : components) {
            this.put(component.getClass(), component);
        }
    }

    public <T extends Component> T get(final Class<T> clazz) {
        return this.values()
                .stream()
                .filter(component -> component.getClass() == clazz)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }
}