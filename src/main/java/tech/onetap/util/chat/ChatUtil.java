package tech.onetap.util.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;

public final class ChatUtil {
    private ChatUtil() {
    }

    public static void send(Object message) {
        sendDirect(message.toString());
    }

    public static void send(Object... messages) {
        sendDirect(String.join(",", Arrays.toString(messages)));
    }

    private static void sendDirect(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        client.inGameHud.getChatHud().addMessage(
                Text.of("Ancient " + Formatting.DARK_GRAY + "-> " + Formatting.RESET + message)
        );
    }
}