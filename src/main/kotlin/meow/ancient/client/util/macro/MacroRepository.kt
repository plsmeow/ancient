package meow.ancient.client.util.macro

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.command.CommandManager
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.event.EventManager
import meow.ancient.event.events.KeyPressEvent
import meow.ancient.module.modules.render.HideModule
import sweetie.evaware.flora.api.Commando
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class Macro(val message: String, val key: Int)

object MacroRepository {
    private val dir = File(mc.gameDirectory, "ancient")
    private val file = File(dir, "macros.json")
    private val legacyFile = File(".options/macros.json")
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
            dir.mkdirs()
            file.writeText(gson.toJson(macros))
        } catch (_: Exception) {
        }
    }

    fun load() {
        val targetFile = when {
            file.exists() -> file
            legacyFile.exists() -> legacyFile
            else -> null
        } ?: return
        try {
            val listType = object : TypeToken<ArrayList<Macro>>() {}.type
            val loaded: List<Macro>? = gson.fromJson(targetFile.readText(), listType)
            if (loaded != null) {
                macros.clear()
                macros.addAll(loaded)
            }
            if (targetFile === legacyFile && !file.exists()) {
                save()
            }
        } catch (_: Exception) {
        }
    }

    @Commando
    fun onKeyPress(event: KeyPressEvent) {
        if (HideModule.isActive) return
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
