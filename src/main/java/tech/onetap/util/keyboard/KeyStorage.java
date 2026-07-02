package tech.onetap.util.keyboard;

import org.lwjgl.glfw.GLFW;
import tech.onetap.util.IMinecraft;

import java.util.HashMap;
import java.util.Map;

public class KeyStorage implements IMinecraft {
    public static final Map<String, Integer> keyMap = new HashMap<>();
    private static final Map<Integer, String> reverseKeyMap = new HashMap<>();

    public static String getKey(int integer) {
        return KeyStorage.getReverseKey(integer);
    }

    public static String getReverseKey(int key) {
        return reverseKeyMap.getOrDefault(key, "");
    }

    public static Integer getKey(String key) {
        return keyMap.getOrDefault(key, -1);
    }

    static {
        putMappings();
        reverseMappings();
    }

    private static void putMappings() {
        for (char c = 'A'; c <= 'Z'; c++) {
            keyMap.put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'A'));
        }

        for (char c = '0'; c <= '9'; c++) {
            keyMap.put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        }

        for (int i = 1; i <= 12; i++) {
            keyMap.put("F" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }

        keyMap.put("MOUSE1", 0);
        keyMap.put("MOUSE2", 1);
        keyMap.put("MOUSE3", 2);
        keyMap.put("MOUSE4", 3);
        keyMap.put("MOUSE5", 4);
        keyMap.put("NUMPAD1", GLFW.GLFW_KEY_KP_1);
        keyMap.put("NUMPAD2", GLFW.GLFW_KEY_KP_2);
        keyMap.put("NUMPAD3", GLFW.GLFW_KEY_KP_3);
        keyMap.put("NUMPAD4", GLFW.GLFW_KEY_KP_4);
        keyMap.put("NUMPAD5", GLFW.GLFW_KEY_KP_5);
        keyMap.put("NUMPAD6", GLFW.GLFW_KEY_KP_6);
        keyMap.put("NUMPAD7", GLFW.GLFW_KEY_KP_7);
        keyMap.put("NUMPAD8", GLFW.GLFW_KEY_KP_8);
        keyMap.put("NUMPAD9", GLFW.GLFW_KEY_KP_9);
        keyMap.put("NUMPAD_DECIMAL", GLFW.GLFW_KEY_KP_DECIMAL);
        keyMap.put("NUMPAD_DIVIDE", GLFW.GLFW_KEY_KP_DIVIDE);
        keyMap.put("NUMPAD_MULTIPLY", GLFW.GLFW_KEY_KP_MULTIPLY);
        keyMap.put("NUMPAD_SUBTRACT", GLFW.GLFW_KEY_KP_SUBTRACT);
        keyMap.put("NUMPAD_ADD", GLFW.GLFW_KEY_KP_ADD);
        keyMap.put("NUMPAD_ENTER", GLFW.GLFW_KEY_KP_ENTER);
        keyMap.put("NUMPAD_EQUAL", GLFW.GLFW_KEY_KP_EQUAL);

        keyMap.put("SPACE", GLFW.GLFW_KEY_SPACE);
        keyMap.put("ENTER", GLFW.GLFW_KEY_ENTER);
        keyMap.put("ESCAPE", GLFW.GLFW_KEY_ESCAPE);
        keyMap.put("HOME", GLFW.GLFW_KEY_HOME);
        keyMap.put("INSERT", GLFW.GLFW_KEY_INSERT);
        keyMap.put("DELETE", GLFW.GLFW_KEY_DELETE);
        keyMap.put("END", GLFW.GLFW_KEY_END);
        keyMap.put("PAGEUP", GLFW.GLFW_KEY_PAGE_UP);
        keyMap.put("PAGEDOWN", GLFW.GLFW_KEY_PAGE_DOWN);
        keyMap.put("RIGHT", GLFW.GLFW_KEY_RIGHT);
        keyMap.put("LEFT", GLFW.GLFW_KEY_LEFT);
        keyMap.put("DOWN", GLFW.GLFW_KEY_DOWN);
        keyMap.put("UP", GLFW.GLFW_KEY_UP);
        keyMap.put("RSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        keyMap.put("LSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        keyMap.put("RCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        keyMap.put("LCTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        keyMap.put("RALT", GLFW.GLFW_KEY_RIGHT_ALT);
        keyMap.put("LALT", GLFW.GLFW_KEY_LEFT_ALT);
        keyMap.put("RSUPER", GLFW.GLFW_KEY_RIGHT_SUPER);
        keyMap.put("LSUPER", GLFW.GLFW_KEY_LEFT_SUPER);
        keyMap.put("MENU", GLFW.GLFW_KEY_MENU);
        keyMap.put("CAPS_LOCK", GLFW.GLFW_KEY_CAPS_LOCK);
        keyMap.put("NUM_LOCK", GLFW.GLFW_KEY_NUM_LOCK);
        keyMap.put("SCROLL_LOCK", GLFW.GLFW_KEY_SCROLL_LOCK);
        keyMap.put("PRINT_SCREEN", GLFW.GLFW_KEY_PRINT_SCREEN);

        keyMap.put("APOSTROPHE", GLFW.GLFW_KEY_APOSTROPHE);
        keyMap.put("SLASH", GLFW.GLFW_KEY_SLASH);
        keyMap.put("MINUS", GLFW.GLFW_KEY_MINUS);
        keyMap.put("EQUAL", GLFW.GLFW_KEY_EQUAL);
        keyMap.put("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
        keyMap.put("BACKSLASH", GLFW.GLFW_KEY_BACKSLASH);
        keyMap.put("PERIOD", GLFW.GLFW_KEY_PERIOD);
        keyMap.put("COMMA", GLFW.GLFW_KEY_COMMA);
        keyMap.put("PAUSE", GLFW.GLFW_KEY_PAUSE);
        keyMap.put("GRAVE", GLFW.GLFW_KEY_GRAVE_ACCENT);
    }

    private static void reverseMappings() {
        for (Map.Entry<String, Integer> entry : keyMap.entrySet()) {
            reverseKeyMap.put(entry.getValue(), entry.getKey());
        }
    }
}