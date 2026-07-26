package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.List;
import java.util.stream.Stream;

public class HClipCommand extends Command {
    public HClipCommand() {
        super("hclip");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String input = args.getString();

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ClientWorld world = MinecraftClient.getInstance().world;

        double distance;
        try {
            distance = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            logDirect(Formatting.RED + input + " не является числом.");
            return;
        }

        if (distance == 0) {
            logDirect(Formatting.RED + "Не удалось выполнить телепортацию.");
            return;
        }

        // Направление взгляда игрока (только по горизонтали)
        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw);

        double dx = -Math.sin(rad) * distance;
        double dz = Math.cos(rad) * distance;

        double x = player.getX() + dx;
        double y = player.getY();
        double z = player.getZ() + dz;

        // Необязательный второй аргумент — тип байпаса (pos/bypass/vault)
        String bypass = null;
        if (args.hasAny()) {
            bypass = args.getString().toLowerCase();
            if (!ClipBypass.BYPASS_TYPES.contains(bypass)) {
                logDirect(Formatting.RED + "Неизвестный тип байпаса: " + bypass);
                logDirect(Formatting.GRAY + "Доступные: " + String.join(", ", ClipBypass.BYPASS_TYPES));
                return;
            }
        }

        ClipBypass.teleport(x, y, z, bypass);

        logDirect("Телепортировано на " + (int) distance + " блоков по горизонтали"
                + (bypass != null ? " [" + bypass + "]" : ""));
    }

    @Override
    public String getShortDesc() {
        return "Телепорт по горизонтали";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Телепортирует игрока по горизонтали в направлении взгляда",
                "",
                "> hclip <расстояние> — телепорт вперёд/назад на определённое количество блоков",
                "Положительное число — вперёд, отрицательное — назад",
                "",
                "Необязательный второй аргумент — тип байпаса:",
                "> hclip <расстояние> [pos|bypass|vault]",
                "",
                "Без указания типа используется дефолтная логика (pos)."
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactly(2)) {
            String prefix = args.peekString().toLowerCase();
            return ClipBypass.BYPASS_TYPES.stream().filter(s -> s.startsWith(prefix));
        }
        return Stream.empty();
    }
}
