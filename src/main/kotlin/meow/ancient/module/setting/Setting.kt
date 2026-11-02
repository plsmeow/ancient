package meow.ancient.module.setting

import com.google.gson.JsonElement
import meow.ancient.client.util.bind.SettingBindingManager
import meow.ancient.module.Module
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Setting<T>(
    val name: String,
    value: T,
    val defaultValue: T,
    val description: String = "",
    val isVisible: () -> Boolean = { true }
) : ReadWriteProperty<Any?, T> {
    private val listeners = mutableListOf<(T) -> Unit>()
    private var storedValue: T = value
    private var overriddenValue: T = value
    private var hasOverride = false
    private var serializingBase = false
    private var storedBinding: SettingBinding? = null

    val baseValue: T
        get() = storedValue

    var value: T
        get() = if (hasOverride && !serializingBase) overriddenValue else storedValue
        set(newValue) = set(newValue)

    var binding: SettingBinding?
        get() = storedBinding
        set(newBinding) = SettingBindingManager.configure(this, newBinding)

    internal fun storeBinding(binding: SettingBinding?) {
        storedBinding = binding?.let { it.copy(value = it.value.deepCopy()) }
    }

    internal fun setOverride(newValue: T) {
        val previous = value
        overriddenValue = newValue
        hasOverride = true
        notifyChange(previous)
    }

    internal fun clearOverride() {
        val previous = value
        hasOverride = false
        overriddenValue = storedValue
        notifyChange(previous)
    }

    private fun notifyChange(previous: T) {
        val current = value
        if (previous != current) listeners.forEach { it(current) }
    }

    fun serializeBase(): JsonElement {
        val previous = serializingBase
        serializingBase = true
        return try {
            serialize()
        } finally {
            serializingBase = previous
        }
    }

    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): Setting<T> {
        if (thisRef is Module && !thisRef.settings.contains(this)) {
            thisRef.settings.add(this)
        }
        return this
    }

    fun addListener(listener: (T) -> Unit): Setting<T> {
        listeners.add(listener)
        return this
    }

    open fun set(newValue: T) {
        val previous = value
        storedValue = newValue
        notifyChange(previous)
    }

    fun reset() {
        set(defaultValue)
    }

    abstract fun serialize(): JsonElement
    abstract fun deserialize(element: JsonElement)

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        set(value)
    }
}
