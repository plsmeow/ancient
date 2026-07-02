package tech.onetap.util.text;

import lombok.Data;

import java.util.List;

@Data
public class BetterText {
    private List<String> texts;
    public final StringBuilder output = new StringBuilder();
    private int delay;
    private int textIndex = 0;
    private int charIndex = 0;
    private boolean forward = true;
    private long lastUpdateTime = System.currentTimeMillis();

    public BetterText(List<String> texts, int delay) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Text list cannot be null or empty");
        }
        this.texts = texts;
        this.delay = delay;
    }

    public void update() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= 100) {
            lastUpdateTime = currentTime;

            String currentText = texts.get(textIndex);
            if (currentText == null || currentText.isEmpty()) return;

            if (forward) {
                if (charIndex < currentText.length()) {
                    output.append(currentText.charAt(charIndex));
                    charIndex++;
                } else {
                    forward = false;
                    lastUpdateTime = currentTime + delay;
                }
            } else {
                if (charIndex > 0) {
                    output.deleteCharAt(charIndex - 1);
                    charIndex--;
                } else {
                    forward = true;
                    textIndex = (textIndex + 1) % texts.size();
                }
            }
        }
    }
}