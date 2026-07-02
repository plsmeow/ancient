package tech.onetap.module.settings;

import java.util.function.Supplier;

public interface ISetting {
    Setting setVisible(Supplier<Boolean> visible);
}