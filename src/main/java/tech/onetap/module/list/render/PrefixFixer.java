package tech.onetap.module.list.render;

import net.minecraft.text.Style;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

import java.util.HashMap;
import java.util.Map;

@ModuleInformation(moduleName = "Prefix Fixer", moduleDesc = "Заменяет префиксы сервера на читаемый текст", moduleCategory = ModuleCategory.RENDER)
public class PrefixFixer extends Module {

    private final ModeSetting mode = new ModeSetting("Мод", "FunSky", "FunSky", "Reallyworld", "Aresmine");

    private static final Map<Character, String> FUNSKY_PREFIXES = new HashMap<>();
    private static final Map<Character, String> REALLYWORLD_PREFIXES = new HashMap<>();
    private static final Map<String, String> ARESMINE_GROUPS = new HashMap<>();

    static {
        // FunSky (замена по символам)
        FUNSKY_PREFIXES.put('ꀀ', "Игрок");
        FUNSKY_PREFIXES.put('ꀁ', "Барон");
        FUNSKY_PREFIXES.put('ꀂ', "Страж");
        FUNSKY_PREFIXES.put('ꀃ', "Герой");
        FUNSKY_PREFIXES.put('ꀄ', "Аспид");
        FUNSKY_PREFIXES.put('ꀅ', "Сквид");
        FUNSKY_PREFIXES.put('ꀆ', "Глава");
        FUNSKY_PREFIXES.put('ꀇ', "Элита");
        FUNSKY_PREFIXES.put('ꀈ', "Титан");
        FUNSKY_PREFIXES.put('ꀉ', "Принц");
        FUNSKY_PREFIXES.put('ꀊ', "Князь");
        FUNSKY_PREFIXES.put('ꀋ', "Герцог");
        FUNSKY_PREFIXES.put('ꀌ', "Медиа");
        FUNSKY_PREFIXES.put('ꀍ', "Staff");
        FUNSKY_PREFIXES.put('ꀎ', "D.Helper");
        FUNSKY_PREFIXES.put('ꀏ', "Admin");
        FUNSKY_PREFIXES.put('ꐔ', "Helper");
        FUNSKY_PREFIXES.put('ꐕ', "Moder");

        // Reallyworld (замена по символам, из фикса в Tags)
        REALLYWORLD_PREFIXES.put('ꔲ', "BULL");
        REALLYWORLD_PREFIXES.put('ꕓ', "GHOST");
        REALLYWORLD_PREFIXES.put('ꔨ', "DRAGON");
        REALLYWORLD_PREFIXES.put('ꔂ', "D.MODER");
        REALLYWORLD_PREFIXES.put('ꔦ', "D.ML.ADMIN");
        REALLYWORLD_PREFIXES.put('ꕀ', "HYDRA");
        REALLYWORLD_PREFIXES.put('ꕖ', "BUNNY");
        REALLYWORLD_PREFIXES.put('ꕒ', "RABBIT");
        REALLYWORLD_PREFIXES.put('ꕈ', "COBRA");
        REALLYWORLD_PREFIXES.put('ꔶ', "TIGER");
        REALLYWORLD_PREFIXES.put('ꕠ', "D.HELPER");
        REALLYWORLD_PREFIXES.put('ꔉ', "HELPER");
        REALLYWORLD_PREFIXES.put('ꔆ', "D.MODER");
        REALLYWORLD_PREFIXES.put('ꕄ', "DRACULA");
        REALLYWORLD_PREFIXES.put('ꔰ', "D.ML.ADMIN");
        REALLYWORLD_PREFIXES.put('ꔐ', "D.GL.MODER");
        REALLYWORLD_PREFIXES.put('ꔔ', "D.GL.MODER");
        REALLYWORLD_PREFIXES.put('ꔢ', "D.ST.MODER");
        REALLYWORLD_PREFIXES.put('ꕡ', "ST.HELPER");
        REALLYWORLD_PREFIXES.put('ꕅ', "MEDIA+");
        REALLYWORLD_PREFIXES.put('ꔗ', "MODER");
        REALLYWORLD_PREFIXES.put('ꕗ', "D.ADMIN");
        REALLYWORLD_PREFIXES.put('ꔘ', "D.ST.MODER");
        REALLYWORLD_PREFIXES.put('ꔳ', "ML.ADMIN");
        REALLYWORLD_PREFIXES.put('ꔁ', "MEDIA");
        REALLYWORLD_PREFIXES.put('ꔅ', "YT");
        REALLYWORLD_PREFIXES.put('ꕁ', "LEGENDA");

        // Aresmine (замена по шрифту custom:groups/*, из фикса в Tags)
        ARESMINE_GROUPS.put("default", "Игрок");
        ARESMINE_GROUPS.put("hydra", "Гидра");
        ARESMINE_GROUPS.put("ares", "Aрес");
        ARESMINE_GROUPS.put("aristocrat", "Аристократ");
        ARESMINE_GROUPS.put("cerberus", "Цербер");
        ARESMINE_GROUPS.put("kronos", "Кронос");
        ARESMINE_GROUPS.put("pandar", "Пандар");
        ARESMINE_GROUPS.put("phobos", "Фобос");
        ARESMINE_GROUPS.put("phoenix", "Феникс");
        ARESMINE_GROUPS.put("cold", "Холод");
        ARESMINE_GROUPS.put("heat", "Жара");
        ARESMINE_GROUPS.put("helper", "HELPER");
        ARESMINE_GROUPS.put("moder", "MODER");
        ARESMINE_GROUPS.put("shelper", "ST.HELPER");
        ARESMINE_GROUPS.put("smoder", "ST.MODER");
        ARESMINE_GROUPS.put("summer", "Лето");
        ARESMINE_GROUPS.put("triton", "Тритон");
        ARESMINE_GROUPS.put("winter", "Зима");
        ARESMINE_GROUPS.put("youtuber", "YT");
        ARESMINE_GROUPS.put("admin", "ADMIN");
    }

    /**
     * Подменяет префиксы сервера в строке по паттерну выбранного режима.
     * Вызывается из TextVisitFactoryMixin при рендере любого текста (по логике Animated Name).
     */
    public String replace(String text, Style style) {
        if (!isEnabled() || text == null || text.isEmpty()) {
            return text;
        }

        if (mode.is("FunSky")) {
            return replaceByPattern(text, FUNSKY_PREFIXES);
        }
        if (mode.is("Reallyworld")) {
            return replaceByPattern(text, REALLYWORLD_PREFIXES);
        }
        if (mode.is("Aresmine")) {
            return replaceAresmine(text, style);
        }

        return text;
    }

    /**
     * Aresmine рисует префикс буквой "a" с кастомным шрифтом группы (custom:groups/...),
     * поэтому заменяем "a" только внутри текста с таким шрифтом.
     */
    private String replaceAresmine(String text, Style style) {
        if (style == null || style.getFont() == null) return text;

        String fontPath = style.getFont().toString();
        if (!fontPath.startsWith("custom:groups/")) return text;

        String groupName = fontPath.substring(fontPath.lastIndexOf('/') + 1);
        String prefix = ARESMINE_GROUPS.get(groupName);
        if (prefix == null) return text;

        return text.replace("a", prefix);
    }

    private String replaceByPattern(String text, Map<Character, String> pattern) {
        StringBuilder result = null;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String replacement = pattern.get(c);

            if (replacement != null) {
                if (result == null) {
                    result = new StringBuilder(text.length() + 16).append(text, 0, i);
                }
                result.append(replacement);
            } else if (result != null) {
                result.append(c);
            }
        }

        return result == null ? text : result.toString();
    }
}
