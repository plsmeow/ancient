package tech.onetap.module.settings;

import java.util.function.Supplier;

public abstract class Setting implements ISetting {
    private final String name;
    public Supplier<Boolean> visible = () -> true;

    // ===== Система биндов =====
    protected int key = -1;          // привязанная клавиша (-1 = нет бинда)
    protected boolean bindActive;    // включён ли сейчас бинд (для логики set/restore)
    protected String bindValue;      // целевое значение, которое выставляется при включении бинда
    protected String savedValue;     // исходное значение, к которому возвращаемся при выключении

    public Setting(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean isBound() {
        return key != -1;
    }

    public boolean isBindActive() {
        return bindActive;
    }

    public String getBindValue() {
        return bindValue;
    }

    public void setBindValue(String bindValue) {
        this.bindValue = bindValue;
    }

    /** Поддерживает ли тип настройки бинд "включил - поставил значение, выключил - вернул". */
    public boolean canBind() {
        return false;
    }

    /** Назначить клавишу и запомнить текущее значение как целевое для бинда. */
    public void bindTo(int key) {
        this.key = key;
        this.bindValue = getValueAsString();
        this.bindActive = false;
    }

    /** Снять бинд, вернув исходное значение, если оно сейчас применено. */
    public void unbind() {
        if (bindActive && savedValue != null) setValueFromString(savedValue);
        this.key = -1;
        this.bindActive = false;
    }

    /** Вызывается при нажатии привязанной клавиши. По умолчанию: выставить значение / вернуть обратно. */
    public void triggerBind() {
        if (!bindActive) {
            savedValue = getValueAsString();
            if (bindValue != null) setValueFromString(bindValue);
            bindActive = true;
        } else {
            if (savedValue != null) setValueFromString(savedValue);
            bindActive = false;
        }
    }

    /** Значение, которое показывается в списке Keybinds. */
    public String getBindDisplayValue() {
        return bindValue != null ? bindValue : getValueAsString();
    }

    public abstract String getValueAsString();
    public abstract void setValueFromString(String value);
}
