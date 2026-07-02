package tech.onetap.module;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleInformation {
    String moduleName();
    String moduleDesc() default "";
    ModuleCategory moduleCategory();
    int moduleKeybind() default -1;
}