package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Formatting;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.List;
import java.util.stream.Stream;

public class RotationCommand extends Command {
    public RotationCommand() {
        super("r");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(2);
        String type = args.getString().toLowerCase();

        if (!args.hasExactlyOne()) {
            logDirect(Formatting.RED + "Использование: r <yaw|pitch> <значение>");
            return;
        }

        float value;
        try {
            value = Float.parseFloat(args.getString());
        } catch (NumberFormatException e) {
            logDirect(Formatting.RED + "Не является числом.");
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        float yaw = player.getYaw();
        float pitch = player.getPitch();

        switch (type) {
            case "yaw" -> yaw = value;
            case "pitch" -> pitch = value;
            default -> {
                logDirect(Formatting.RED + "Использование: r <yaw|pitch> <значение>");
                return;
            }
        }

        // Останавливаем RotationComponent, чтобы он не перетирал значения
        // и не клампал pitch в [-90, 90].
        // Выставляем ротацию напрямую + шлём пакет с любым (даже "неправильным") питчем.
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision)
        );

        logDirect("Ротация установлена: yaw=" + yaw + ", pitch=" + pitch);
    }

    @Override
    public String getShortDesc() {
        return "Ставит ротацию";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Test"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("yaw", "pitch");
        }
        return Stream.empty();
    }
}