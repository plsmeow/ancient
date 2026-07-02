package tech.onetap.util.cursor;

public class CursorManager {
    private static boolean hand = false;
    private static boolean iBeam = false;
    private static boolean click = false;

    public static void requestHand() {
        hand = true;
    }
    public static void requestIBeam() {
        iBeam = true;
    }
    public static void requestClick() {
        click = true;
    }

    public static boolean shouldBeHand() {
        return hand;
    }
    public static boolean shouldIBeam() {
        return iBeam;
    }
    public static boolean shouldClick() {
        return click;
    }

    public static void reset() {
        hand = false;
    }
    public static void resetIBeam() {
        iBeam = false;
    }
    public static void resetClick() {
        click = false;
    }
}