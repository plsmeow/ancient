package tech.onetap.util.render;

/**
 * Holder for the shared {@link Draw2D} instance used by the Delta-styled UI.
 */
public final class Delta2DHolder {
    private static final Draw2D INSTANCE = new Draw2D();

    public static Draw2D get() {
        return INSTANCE;
    }

    private Delta2DHolder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
