package tech.onetap.util.render.msdf;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FormattedTextProcessor {

    public static List<TextSegment> processText(Text text, int defaultColor) {
        List<TextSegment> segments = new ArrayList<>();
        text.visit((style, string) -> {
            if (!string.isEmpty()) {
                int color = extractColor(style, defaultColor);
                boolean bold = style.isBold();
                boolean italic = style.isItalic();
                boolean underlined = style.isUnderlined();
                boolean strikethrough = style.isStrikethrough();

                segments.add(new TextSegment(string, color, bold, italic, underlined, strikethrough));
            }
            return Optional.empty();
        }, Style.EMPTY);

        return segments;
    }

    private static int extractColor(Style style, int defaultColor) {
        TextColor textColor = style.getColor();
        if (textColor != null) {
            return textColor.getRgb() | 0xFF000000; // альфа
        }
        return defaultColor;
    }

    /**
         * Хз болд италик, андерлайн и страйк не работает
         * но мб когда-нибудь заработает
         */
        public record TextSegment(String text, int color, boolean bold, boolean italic, boolean underlined,
                                  boolean strikethrough) {
    }
}