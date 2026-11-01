package meow.ancient.client.gui.material

import meow.ancient.client.util.other.color.Color4b

data class M3Palette(
    val name: String,
    val surface: Color4b,
    val surfaceDim: Color4b,
    val surfaceBright: Color4b,
    val surfaceContainerLowest: Color4b,
    val surfaceContainerLow: Color4b,
    val surfaceContainer: Color4b,
    val surfaceContainerHigh: Color4b,
    val surfaceContainerHighest: Color4b,
    val onSurface: Color4b,
    val onSurfaceVariant: Color4b,
    val outline: Color4b,
    val outlineVariant: Color4b,
    val primary: Color4b,
    val onPrimary: Color4b,
    val primaryContainer: Color4b,
    val onPrimaryContainer: Color4b,
    val secondary: Color4b,
    val secondaryContainer: Color4b,
    val onSecondaryContainer: Color4b,
    val tertiary: Color4b,
    val tertiaryContainer: Color4b,
    val error: Color4b = Color4b.fromHex("#f2b8b5"),
    val scrim: Color4b = Color4b(0, 0, 0, 160),
    val stateHover: Color4b = Color4b(255, 255, 255, 18),
    val statePressed: Color4b = Color4b(255, 255, 255, 32),
    val stateFocus: Color4b = Color4b(255, 255, 255, 25)
)

object M3Theme {
    val MATERIAL: M3Palette get() = ThemeManager.MATERIAL
    val current: M3Palette get() = ThemeManager.currentTheme
}
