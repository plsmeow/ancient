package tech.onetap.util.render.msdf;

import com.google.common.base.Suppliers;

import java.util.function.Supplier;

public class Fonts {
    public static final Supplier<MsdfFont> BIKO = Suppliers.memoize(() -> MsdfFont.builder().atlas("biko").data("biko").build());
    public static final Supplier<MsdfFont> SFBOLD = Suppliers.memoize(() -> MsdfFont.builder().atlas("bold").data("bold").build());
    public static final Supplier<MsdfFont> SFSEMIBOLD = Suppliers.memoize(() -> MsdfFont.builder().atlas("semibold").data("semibold").build());
    public static final Supplier<MsdfFont> SFMEDIUM = Suppliers.memoize(() -> MsdfFont.builder().atlas("medium").data("medium").build());
    public static final Supplier<MsdfFont> SFREGULAR = Suppliers.memoize(() -> MsdfFont.builder().atlas("regular").data("regular").build());
    public static final Supplier<MsdfFont> ICONS = Suppliers.memoize(() -> MsdfFont.builder().atlas("icons").data("icons").build());
    public static final Supplier<MsdfFont> ICONS_NURIK = Suppliers.memoize(() -> MsdfFont.builder().atlas("icons_nurik").data("icons_nurik").build());
    public static final Supplier<MsdfFont> MOONWARD = Suppliers.memoize(() -> MsdfFont.builder().atlas("moonward").data("moonward").build());

    public static final Supplier<MsdfFont> ICONS2 = Suppliers.memoize(() -> MsdfFont.builder().atlas("icons2").data("icons2").build());
    public static final Supplier<MsdfFont> HUD_ICONS = Suppliers.memoize(() -> MsdfFont.builder().atlas("hud_icons").data("hud_icons").build());
    public static final Supplier<MsdfFont> LUPA = Suppliers.memoize(() -> MsdfFont.builder().atlas("lupa").data("lupa").build());
    public static final Supplier<MsdfFont> RUBIK = Suppliers.memoize(() -> MsdfFont.builder().atlas("rubik").data("rubik").build());
}