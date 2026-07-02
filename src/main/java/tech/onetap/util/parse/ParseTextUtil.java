package tech.onetap.util.parse;

import net.minecraft.text.*;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.msdf.MsdfFont;

import java.util.ArrayList;
import java.util.List;

public class ParseTextUtil implements IMinecraft {
    public static List<MsdfFont.ColoredGlyph> parseTextToColoredGlyphs(Text text) {
        List<MsdfFont.ColoredGlyph> result = new ArrayList<>();
        parseTextRecursive(text, 0xFFFFFFFF, result);
        return result;
    }

    private static void parseTextRecursive(Text text, int currentColor, List<MsdfFont.ColoredGlyph> result) {
        Style style = text.getStyle();
        int color = style.getColor() != null ? style.getColor().getRgb() | 0xFF000000 : currentColor;

        TextContent content = text.getContent();
        String raw = "";

        if (content instanceof PlainTextContent.Literal literal) {
            raw = literal.string();
        } else if (content instanceof TranslatableTextContent translatable) {
            raw = translatable.getKey();
        } else if (content instanceof KeybindTextContent keybind) {
            raw = keybind.getKey();
        }

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            result.add(new MsdfFont.ColoredGlyph(c, color));
        }

        for (Text sibling : text.getSiblings()) {
            parseTextRecursive(sibling, color, result);
        }
    }
}