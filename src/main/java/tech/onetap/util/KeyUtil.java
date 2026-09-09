package tech.onetap.util;

import java.util.Locale;

/**
 * GLFW key-code to label mapping ported from DeltaClient (aethereal.util.KeyUtil),
 * including mouse button pseudo-codes (-100..-93) used by module keybinds.
 */
public enum KeyUtil {
    UNKNOWN(-1, "N/A"),
    SPACE(32, "Space"),
    APOSTROPHE(39, "'"),
    COMMA(44, ","),
    MINUS(45, "-"),
    PERIOD(46, "."),
    SLASH(47, "/"),
    SEMICOLON(59, ";"),
    EQUAL(61, "="),
    LEFT_BRACKET(91, "["),
    BACKSLASH(92, "\\"),
    RIGHT_BRACKET(93, "]"),
    GRAVE_ACCENT(96, "`"),
    NUM0(48, "0"),
    NUM1(49, "1"),
    NUM2(50, "2"),
    NUM3(51, "3"),
    NUM4(52, "4"),
    NUM5(53, "5"),
    NUM6(54, "6"),
    NUM7(55, "7"),
    NUM8(56, "8"),
    NUM9(57, "9"),
    A(65, "A"),
    B(66, "B"),
    C(67, "C"),
    D(68, "D"),
    E(69, "E"),
    F(70, "F"),
    G(71, "G"),
    H(72, "H"),
    I(73, "I"),
    J(74, "J"),
    K(75, "K"),
    L(76, "L"),
    M(77, "M"),
    N(78, "N"),
    O(79, "O"),
    P(80, "P"),
    Q(81, "Q"),
    R(82, "R"),
    S(83, "S"),
    T(84, "T"),
    U(85, "U"),
    V(86, "V"),
    W(87, "W"),
    X(88, "X"),
    Y(89, "Y"),
    Z(90, "Z"),
    ESC(256, "Esc"),
    ENTER(257, "Enter"),
    TAB(258, "Tab"),
    BACKSPACE(259, "Back"),
    INSERT(260, "Ins"),
    DELETE(261, "Del"),
    RIGHT(262, "→"),
    LEFT(263, "←"),
    DOWN(264, "↓"),
    UP(265, "↑"),
    PAGE_UP(266, "PgUp"),
    PAGE_DOWN(267, "PgDn"),
    HOME(268, "Home"),
    END(269, "End"),
    CAPS_LOCK(280, "Caps"),
    SCROLL_LOCK(281, "ScrLk"),
    NUM_LOCK(282, "NumLk"),
    PRINT_SCREEN(283, "PrtSc"),
    PAUSE(284, "Pause"),
    F1(290, "F1"),
    F2(291, "F2"),
    F3(292, "F3"),
    F4(293, "F4"),
    F5(294, "F5"),
    F6(295, "F6"),
    F7(296, "F7"),
    F8(297, "F8"),
    F9(298, "F9"),
    F10(299, "F10"),
    F11(300, "F11"),
    F12(301, "F12"),
    F13(302, "F13"),
    F14(303, "F14"),
    F15(304, "F15"),
    F16(305, "F16"),
    F17(306, "F17"),
    F18(307, "F18"),
    F19(308, "F19"),
    F20(309, "F20"),
    F21(310, "F21"),
    F22(311, "F22"),
    F23(312, "F23"),
    F24(313, "F24"),
    F25(314, "F25"),
    KP_0(320, "Num 0"),
    KP_1(321, "Num 1"),
    KP_2(322, "Num 2"),
    KP_3(323, "Num 3"),
    KP_4(324, "Num 4"),
    KP_5(325, "Num 5"),
    KP_6(326, "Num 6"),
    KP_7(327, "Num 7"),
    KP_8(328, "Num 8"),
    KP_9(329, "Num 9"),
    KP_DECIMAL(330, "Num ."),
    KP_DIVIDE(331, "Num /"),
    KP_MULTIPLY(332, "Num *"),
    KP_SUBTRACT(333, "Num -"),
    KP_ADD(334, "Num +"),
    KP_ENTER(335, "Num Enter"),
    KP_EQUAL(336, "Num ="),
    LEFT_SHIFT(340, "LShift"),
    RIGHT_SHIFT(344, "RShift"),
    LEFT_CONTROL(341, "LCtrl"),
    RIGHT_CONTROL(345, "RCtrl"),
    LEFT_ALT(342, "LAlt"),
    RIGHT_ALT(346, "RAlt"),
    LEFT_SUPER(343, "LSuper"),
    RIGHT_SUPER(347, "RSuper"),
    MENU(348, "Menu"),
    LMB(-100, "LMB"),
    RMB(-99, "RMB"),
    MMB(-98, "MMB"),
    M1(-97, "M1"),
    M2(-96, "M2"),
    M3(-95, "M3"),
    M4(-94, "M4"),
    M5(-93, "M5"),
    WORLD_1(161, "World 1"),
    WORLD_2(162, "World 2");

    private final int keyCode;
    private final String keyLabel;

    KeyUtil(int code, String label) {
        this.keyCode = code;
        this.keyLabel = label;
    }

    public static KeyUtil byCode(int code) {
        for (KeyUtil key : values()) {
            if (key.keyCode == code) {
                return key;
            }
        }
        return UNKNOWN;
    }

    public static String getKeyName(int code) {
        return byCode(code).keyLabel;
    }

    public static KeyUtil byName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        String n = name.toUpperCase(Locale.ROOT);
        switch (n) {
            case "ЛКМ", "LEFT", "LEFT_BUTTON" -> { return LMB; }
            case "ПКМ", "RIGHT", "RIGHT_BUTTON" -> { return RMB; }
            case "СКМ", "MIDDLE", "WHEEL", "MIDDLE_BUTTON" -> { return MMB; }
            case "MB4", "SIDE1", "BACK", "MOUSE4" -> { return M1; }
            case "MB5", "SIDE2", "FORWARD", "MOUSE5" -> { return M2; }
            case "MB6", "MOUSE6" -> { return M3; }
            case "MB7", "MOUSE7" -> { return M4; }
            case "MB8", "MOUSE8" -> { return M5; }
            default -> { }
        }
        try {
            return valueOf(n);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public int getCode() {
        return this.keyCode;
    }

    public String getLabel() {
        return this.keyLabel;
    }
}
