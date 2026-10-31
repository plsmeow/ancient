package meow.ancient.client.gui.material

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.other.color.shiftHue
import java.io.File

object ThemeManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val MATERIAL = M3Palette(
        name = "Material",
        surface = Color4b(20, 18, 24, 245),
        surfaceDim = Color4b(17, 15, 21, 250),
        surfaceBright = Color4b(59, 56, 62, 255),
        surfaceContainerLowest = Color4b(15, 13, 19, 250),
        surfaceContainerLow = Color4b(29, 27, 32, 245),
        surfaceContainer = Color4b(33, 31, 38, 250),
        surfaceContainerHigh = Color4b(43, 41, 48, 255),
        surfaceContainerHighest = Color4b(54, 52, 59, 255),
        onSurface = Color4b(230, 224, 233, 255),
        onSurfaceVariant = Color4b(202, 196, 208, 255),
        outline = Color4b(147, 143, 153, 255),
        outlineVariant = Color4b(73, 69, 79, 140),
        primary = Color4b.fromHex("#d0bcff"),
        onPrimary = Color4b.fromHex("#381e72"),
        primaryContainer = Color4b.fromHex("#4f378b"),
        onPrimaryContainer = Color4b.fromHex("#eaddff"),
        secondary = Color4b.fromHex("#ccc2dc"),
        secondaryContainer = Color4b.fromHex("#4a4458"),
        onSecondaryContainer = Color4b.fromHex("#e8def8"),
        tertiary = Color4b.fromHex("#efb8c8"),
        tertiaryContainer = Color4b.fromHex("#633b48")
    )

    val CRIMSON = M3Palette(
        name = "Crimson",
        surface = Color4b(26, 17, 18, 245),
        surfaceDim = Color4b(21, 14, 15, 250),
        surfaceBright = Color4b(66, 54, 55, 255),
        surfaceContainerLowest = Color4b(18, 12, 13, 250),
        surfaceContainerLow = Color4b(34, 25, 26, 245),
        surfaceContainer = Color4b(40, 29, 30, 250),
        surfaceContainerHigh = Color4b(50, 38, 39, 255),
        surfaceContainerHighest = Color4b(62, 48, 49, 255),
        onSurface = Color4b(240, 222, 223, 255),
        onSurfaceVariant = Color4b(216, 195, 196, 255),
        outline = Color4b(160, 140, 141, 255),
        outlineVariant = Color4b(83, 67, 68, 140),
        primary = Color4b.fromHex("#ffb4ab"),
        onPrimary = Color4b.fromHex("#690005"),
        primaryContainer = Color4b.fromHex("#93000a"),
        onPrimaryContainer = Color4b.fromHex("#ffdad6"),
        secondary = Color4b.fromHex("#e7bdb8"),
        secondaryContainer = Color4b.fromHex("#5d3f3d"),
        onSecondaryContainer = Color4b.fromHex("#ffdad6"),
        tertiary = Color4b.fromHex("#f0bc82"),
        tertiaryContainer = Color4b.fromHex("#5f3d10")
    )

    val EMERALD = M3Palette(
        name = "Emerald",
        surface = Color4b(15, 21, 17, 245),
        surfaceDim = Color4b(12, 17, 14, 250),
        surfaceBright = Color4b(52, 60, 54, 255),
        surfaceContainerLowest = Color4b(10, 15, 12, 250),
        surfaceContainerLow = Color4b(22, 29, 24, 245),
        surfaceContainer = Color4b(26, 34, 28, 250),
        surfaceContainerHigh = Color4b(36, 45, 38, 255),
        surfaceContainerHighest = Color4b(46, 56, 48, 255),
        onSurface = Color4b(220, 235, 222, 255),
        onSurfaceVariant = Color4b(194, 209, 196, 255),
        outline = Color4b(138, 154, 140, 255),
        outlineVariant = Color4b(66, 80, 68, 140),
        primary = Color4b.fromHex("#79dd97"),
        onPrimary = Color4b.fromHex("#003919"),
        primaryContainer = Color4b.fromHex("#005227"),
        onPrimaryContainer = Color4b.fromHex("#95f7b1"),
        secondary = Color4b.fromHex("#b7ccb8"),
        secondaryContainer = Color4b.fromHex("#3c4b3e"),
        onSecondaryContainer = Color4b.fromHex("#d3e8d4"),
        tertiary = Color4b.fromHex("#a2ced9"),
        tertiaryContainer = Color4b.fromHex("#234a52")
    )

    val SAPPHIRE = M3Palette(
        name = "Sapphire",
        surface = Color4b(16, 20, 26, 245),
        surfaceDim = Color4b(13, 16, 21, 250),
        surfaceBright = Color4b(53, 58, 67, 255),
        surfaceContainerLowest = Color4b(11, 14, 19, 250),
        surfaceContainerLow = Color4b(23, 27, 34, 245),
        surfaceContainer = Color4b(27, 32, 39, 250),
        surfaceContainerHigh = Color4b(37, 43, 51, 255),
        surfaceContainerHighest = Color4b(47, 54, 63, 255),
        onSurface = Color4b(222, 230, 242, 255),
        onSurfaceVariant = Color4b(195, 204, 217, 255),
        outline = Color4b(139, 148, 160, 255),
        outlineVariant = Color4b(68, 76, 88, 140),
        primary = Color4b.fromHex("#a4c8ff"),
        onPrimary = Color4b.fromHex("#00315f"),
        primaryContainer = Color4b.fromHex("#004785"),
        onPrimaryContainer = Color4b.fromHex("#d4e3ff"),
        secondary = Color4b.fromHex("#bdc7dc"),
        secondaryContainer = Color4b.fromHex("#3e4758"),
        onSecondaryContainer = Color4b.fromHex("#d9e3f8"),
        tertiary = Color4b.fromHex("#d9bee6"),
        tertiaryContainer = Color4b.fromHex("#533c5f")
    )

    val SUNSET = M3Palette(
        name = "Sunset",
        surface = Color4b(24, 18, 14, 245),
        surfaceDim = Color4b(20, 15, 11, 250),
        surfaceBright = Color4b(64, 56, 50, 255),
        surfaceContainerLowest = Color4b(17, 12, 9, 250),
        surfaceContainerLow = Color4b(33, 26, 20, 245),
        surfaceContainer = Color4b(38, 30, 24, 250),
        surfaceContainerHigh = Color4b(49, 39, 32, 255),
        surfaceContainerHighest = Color4b(61, 49, 41, 255),
        onSurface = Color4b(240, 226, 220, 255),
        onSurfaceVariant = Color4b(216, 201, 193, 255),
        outline = Color4b(158, 144, 136, 255),
        outlineVariant = Color4b(82, 70, 63, 140),
        primary = Color4b.fromHex("#ffb77b"),
        onPrimary = Color4b.fromHex("#4d2700"),
        primaryContainer = Color4b.fromHex("#6e3a00"),
        onPrimaryContainer = Color4b.fromHex("#ffdcbe"),
        secondary = Color4b.fromHex("#e2c0a5"),
        secondaryContainer = Color4b.fromHex("#594331"),
        onSecondaryContainer = Color4b.fromHex("#ffdcbe"),
        tertiary = Color4b.fromHex("#c7cc91"),
        tertiaryContainer = Color4b.fromHex("#454b1a")
    )

    val SAKURA = M3Palette(
        name = "Sakura",
        surface = Color4b(25, 17, 20, 245),
        surfaceDim = Color4b(21, 14, 16, 250),
        surfaceBright = Color4b(65, 54, 58, 255),
        surfaceContainerLowest = Color4b(17, 12, 14, 250),
        surfaceContainerLow = Color4b(34, 25, 28, 245),
        surfaceContainer = Color4b(39, 30, 33, 250),
        surfaceContainerHigh = Color4b(50, 39, 43, 255),
        surfaceContainerHighest = Color4b(61, 49, 54, 255),
        onSurface = Color4b(240, 223, 227, 255),
        onSurfaceVariant = Color4b(216, 197, 202, 255),
        outline = Color4b(159, 141, 146, 255),
        outlineVariant = Color4b(82, 68, 73, 140),
        primary = Color4b.fromHex("#ffb0cd"),
        onPrimary = Color4b.fromHex("#5e1133"),
        primaryContainer = Color4b.fromHex("#7b2949"),
        onPrimaryContainer = Color4b.fromHex("#ffd9e2"),
        secondary = Color4b.fromHex("#e2bdc7"),
        secondaryContainer = Color4b.fromHex("#5a4047"),
        onSecondaryContainer = Color4b.fromHex("#ffd9e2"),
        tertiary = Color4b.fromHex("#f0bb9b"),
        tertiaryContainer = Color4b.fromHex("#623c22")
    )

    val NEON_CYAN = M3Palette(
        name = "Neon Cyan",
        surface = Color4b(12, 21, 20, 245),
        surfaceDim = Color4b(10, 17, 16, 250),
        surfaceBright = Color4b(49, 61, 59, 255),
        surfaceContainerLowest = Color4b(8, 15, 14, 250),
        surfaceContainerLow = Color4b(19, 29, 28, 245),
        surfaceContainer = Color4b(23, 35, 34, 250),
        surfaceContainerHigh = Color4b(32, 45, 44, 255),
        surfaceContainerHighest = Color4b(42, 56, 55, 255),
        onSurface = Color4b(218, 237, 234, 255),
        onSurfaceVariant = Color4b(191, 210, 207, 255),
        outline = Color4b(136, 155, 151, 255),
        outlineVariant = Color4b(64, 82, 79, 140),
        primary = Color4b.fromHex("#5eead4"),
        onPrimary = Color4b.fromHex("#003731"),
        primaryContainer = Color4b.fromHex("#005047"),
        onPrimaryContainer = Color4b.fromHex("#7af7e1"),
        secondary = Color4b.fromHex("#b0ccc6"),
        secondaryContainer = Color4b.fromHex("#334b46"),
        onSecondaryContainer = Color4b.fromHex("#cce8e2"),
        tertiary = Color4b.fromHex("#add0ec"),
        tertiaryContainer = Color4b.fromHex("#2e485e")
    )

    val SLATE = M3Palette(
        name = "Slate",
        surface = Color4b(18, 20, 23, 245),
        surfaceDim = Color4b(15, 17, 19, 250),
        surfaceBright = Color4b(56, 58, 62, 255),
        surfaceContainerLowest = Color4b(13, 14, 16, 250),
        surfaceContainerLow = Color4b(26, 28, 32, 245),
        surfaceContainer = Color4b(30, 32, 36, 250),
        surfaceContainerHigh = Color4b(40, 43, 48, 255),
        surfaceContainerHighest = Color4b(50, 53, 59, 255),
        onSurface = Color4b(226, 232, 240, 255),
        onSurfaceVariant = Color4b(203, 213, 225, 255),
        outline = Color4b(148, 163, 184, 255),
        outlineVariant = Color4b(71, 85, 105, 140),
        primary = Color4b.fromHex("#e2e8f0"),
        onPrimary = Color4b.fromHex("#0f172a"),
        primaryContainer = Color4b.fromHex("#334155"),
        onPrimaryContainer = Color4b.fromHex("#f1f5f9"),
        secondary = Color4b.fromHex("#cbd5e1"),
        secondaryContainer = Color4b.fromHex("#475569"),
        onSecondaryContainer = Color4b.fromHex("#e2e8f0"),
        tertiary = Color4b.fromHex("#94a3b8"),
        tertiaryContainer = Color4b.fromHex("#1e293b")
    )

    val presets = listOf(
        MATERIAL,
        CRIMSON,
        EMERALD,
        SAPPHIRE,
        SUNSET,
        SAKURA,
        NEON_CYAN,
        SLATE
    )

    var isCustom: Boolean = false
        private set

    var customPrimary: Color4b = Color4b.fromHex("#d0bcff")
        private set

    var customSurface: Color4b = Color4b(20, 18, 24, 245)
        private set

    var selectedPresetName: String = "Material"
        private set

    var currentTheme: M3Palette = MATERIAL
        private set

    init {
        load()
    }

    fun createPalette(
        name: String,
        primary: Color4b,
        surfaceBase: Color4b
    ): M3Palette {
        val lum = (0.299 * primary.r + 0.587 * primary.g + 0.114 * primary.b) / 255.0
        val onPrimary = if (lum > 0.55) Color4b(24, 20, 32, 255) else Color4b(255, 255, 255, 255)
        val primaryContainer = primary.interpolateTo(Color4b.BLACK, 0.58)
        val onPrimaryContainer = primary.interpolateTo(Color4b.WHITE, 0.42)
        val secondary = primary.interpolateTo(Color4b(180, 180, 190, 255), 0.35)
        val secondaryContainer = secondary.interpolateTo(Color4b.BLACK, 0.6)
        val onSecondaryContainer = secondary.interpolateTo(Color4b.WHITE, 0.4)
        val tertiary = shiftHue(primary, 60)
        val tertiaryContainer = tertiary.interpolateTo(Color4b.BLACK, 0.6)

        return M3Palette(
            name = name,
            surface = surfaceBase,
            surfaceDim = surfaceBase.interpolateTo(Color4b.BLACK, 0.15),
            surfaceBright = surfaceBase.interpolateTo(Color4b.WHITE, 0.15),
            surfaceContainerLowest = surfaceBase.interpolateTo(Color4b.BLACK, 0.25),
            surfaceContainerLow = surfaceBase.interpolateTo(Color4b.WHITE, 0.04),
            surfaceContainer = surfaceBase.interpolateTo(Color4b.WHITE, 0.08),
            surfaceContainerHigh = surfaceBase.interpolateTo(Color4b.WHITE, 0.13),
            surfaceContainerHighest = surfaceBase.interpolateTo(Color4b.WHITE, 0.18),
            onSurface = Color4b(230, 224, 233, 255),
            onSurfaceVariant = Color4b(202, 196, 208, 255),
            outline = Color4b(147, 143, 153, 255),
            outlineVariant = Color4b(73, 69, 79, 140),
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            tertiaryContainer = tertiaryContainer
        )
    }

    fun selectPreset(palette: M3Palette) {
        selectedPresetName = palette.name
        isCustom = false
        currentTheme = palette
        save()
    }

    fun selectPreset(name: String) {
        val found = presets.find { it.name.equals(name, ignoreCase = true) } ?: MATERIAL
        selectPreset(found)
    }

    fun selectCustom() {
        isCustom = true
        currentTheme = createPalette("Custom", customPrimary, customSurface)
        save()
    }

    fun setCustomPrimary(color: Color4b) {
        customPrimary = color
        isCustom = true
        currentTheme = createPalette("Custom", customPrimary, customSurface)
        save()
    }

    fun setCustomSurface(color: Color4b) {
        customSurface = color
        isCustom = true
        currentTheme = createPalette("Custom", customPrimary, customSurface)
        save()
    }

    private fun getFile(): File {
        val dir = File(mc.gameDirectory, "ancient")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "theme.json")
    }

    fun save() {
        try {
            val file = getFile()
            val json = JsonObject()
            json.addProperty("isCustom", isCustom)
            json.addProperty("preset", selectedPresetName)
            json.addProperty("customPrimary", customPrimary.argb)
            json.addProperty("customSurface", customSurface.argb)
            file.writeText(gson.toJson(json))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load() {
        try {
            val file = getFile()
            if (!file.exists()) return
            val json = JsonParser.parseString(file.readText()).asJsonObject
            isCustom = json.get("isCustom")?.asBoolean ?: false
            selectedPresetName = json.get("preset")?.asString ?: "Material"
            if (json.has("customPrimary")) {
                customPrimary = Color4b(json.get("customPrimary").asInt)
            }
            if (json.has("customSurface")) {
                customSurface = Color4b(json.get("customSurface").asInt)
            }

            currentTheme = if (isCustom) {
                createPalette("Custom", customPrimary, customSurface)
            } else {
                presets.find { it.name.equals(selectedPresetName, ignoreCase = true) } ?: MATERIAL
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
