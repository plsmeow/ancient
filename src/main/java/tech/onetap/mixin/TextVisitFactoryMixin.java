package tech.onetap.mixin;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.Style;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tech.onetap.Onetap;
import tech.onetap.module.list.misc.NameProtect;
import tech.onetap.module.list.render.PrefixFixer;

@Mixin(TextVisitFactory.class)
public class TextVisitFactoryMixin {

    @Redirect(
        method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/text/TextVisitFactory;visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            ordinal = 0
        )
    )
    private static boolean adjustText(String text, int startingIndex, Style style, Style styleOverride, CharacterVisitor visitor) {
        return TextVisitFactory.visitFormatted(protect(text, style), startingIndex, style, styleOverride, visitor);
    }

    @Unique
    private static String protect(String string, Style style) {
        string = Onetap.getInstance().getModuleStorage().get(NameProtect.class).getCustomName(string);
        PrefixFixer prefixFixer = Onetap.getInstance().getModuleStorage().get(PrefixFixer.class);
        if (prefixFixer != null) string = prefixFixer.replace(string, style);
        return string;
    }
}
