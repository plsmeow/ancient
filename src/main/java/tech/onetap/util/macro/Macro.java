package tech.onetap.util.macro;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Macro {
    String message;
    int key;

    public String message() {
        return message;
    }

    public int key() {
        return key;
    }
}