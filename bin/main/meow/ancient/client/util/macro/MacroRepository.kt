package meow.ancient.client.util.macro

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.command.CommandManager
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.event.EventManager
import meow.ancient.event.events.KeyPressEvent
import sweetie.evaware.flora.api.Commando
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class Macro(val message: String, val key: Int)

object MacroRepository {
    private val file = File(".options/macros.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val macros: MutableList<Macro> = CopyOnWriteArrayList()

    init {
        load()
        EventManager.register(this)
    }

    fun addMacro(message: String, key: Int) {
        macros.add(Macro(message, key))
    }

    fun removeByKey(key: Int): Boolean {
        return macros.removeIf { it.key == key }
    }

    fun clear() {
        macros.clear()
    }

    fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(macros))
        } catch (_: Exception) {
        }
    }

    fun load() {
        if (!file.exists()) return
        try {
            val listType = object : TypeToken<ArrayList<Macro>>() {}.type
            val loaded: List<Macro>? = gson.fromJson(file.readText(), listType)
            if (loaded != null) {
                macros.clear()
                macros.addAll(loaded)
            }
        } catch (_: Exception) {
        }
    }

    @Commando
    fun onKeyPress(event: KeyPressEvent) {
        if (event.action != InputConstants.PRESS) return
        val key = event.key
        if (key == InputConstants.UNKNOWN.value) return
        val player = mc.player ?: return
        if (currentScreen != null) return

        for (macro in macros) {
            if (macro.key != key) continue
            val msg = macro.message
            val prefix = CommandManager.prefix
            if (msg.startsWith(prefix)) {
                CommandManager.execute(msg.substring(prefix.length))
            } else {
                if (msg.startsWith("/")) {
                    player.connection.sendCommand(msg.removePrefix("/"))
                } else {
                    player.connection.sendChat(msg)
                }
            }
        }
    }
}
