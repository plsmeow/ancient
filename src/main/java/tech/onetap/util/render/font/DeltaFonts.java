package tech.onetap.util.render.font;

import com.google.common.base.Suppliers;

import java.util.function.Supplier;

/**
 * Font registry of the ported DeltaClient UI, mapping the Delta font roles to the
 * copied atlases under mre:fonts (Delta Fonts: a icons, b sf_regular, c onest_regular,
 * d sf_medium, e gt_regular).
 */
public class DeltaFonts {
    public static final Supplier<Font> ICONS = Suppliers.memoize(() -> Font.builder().name("icons").build());
    public static final Supplier<Font> SF_REGULAR = Suppliers.memoize(() -> Font.builder().name("sf_regular").build());
    public static final Supplier<Font> ONEST_REGULAR = Suppliers.memoize(() -> Font.builder().name("onest_regular").build());
    public static final Supplier<Font> SF_MEDIUM = Suppliers.memoize(() -> Font.builder().name("sf_medium").build());
    public static final Supplier<Font> GT_REGULAR = Suppliers.memoize(() -> Font.builder().name("gt_regular").build());

    private DeltaFonts() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
