package tech.onetap.util.chat;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.util.IMinecraft;

import java.util.Arrays;

public class ChatUtil implements IMinecraft {
    public static void send(Object message) {
        if (mc.player == null) return;

        mc.player.sendMessage(Text.of("Ancient " + Formatting.DARK_GRAY + "-> " + Formatting.RESET + message.toString()), false);
    }

    public static void send(Object... messages) {
        if (mc.player == null) return;

        mc.player.sendMessage(Text.of("Ancient " + Formatting.DARK_GRAY + "-> " + Formatting.RESET + String.join(",", Arrays.toString(messages))), false);
    }
}