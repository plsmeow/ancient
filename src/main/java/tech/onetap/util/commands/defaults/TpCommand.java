package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Formatting;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class TpCommand extends Command {
    public TpCommand() {
        super("tp");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String first = args.getString();

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ClientWorld world = MinecraftClient.getInstance().world;

        AbstractClientPlayerEntity target = world.getPlayers().stream()
                .filter(p -> p.getName().getString().equalsIgnoreCase(first))
                .findFirst().orElse(null);

        // Числовой ник считаем игроком, если оставшиеся аргументы не образуют
        // полные координаты (ещё минимум два числа после первого)
        boolean coordsMode = parseDouble(first) != null
                && (target == null || (args.has(2)
                        && parseDouble(args.peekString(0)) != null
                        && parseDouble(args.peekString(1)) != null));

        if (coordsMode) {
            // Режим координат: tp <x> <y> <z> [bypass]
            double x = parseDouble(first);

            if (args.hasAtMostOne()) {
                logDirect(Formatting.RED + "Недостаточно аргументов.");
                logDirect(Formatting.GRAY + "Использование: " + label + " <x> <y> <z> [bypass]");
                return;
            }

            String yInput = args.getString();
            String zInput = args.getString();

            Double y = parseDouble(yInput);
            if (y == null) {
                logDirect(Formatting.RED + yInput + " не является числом.");
                return;
            }
            Double z = parseDouble(zInput);
            if (z == null) {
                logDirect(Formatting.RED + zInput + " не является числом.");
                return;
            }

            ClipBypass.BypassArgs bypassArgs = ClipBypass.parseArgs(this, args);
            if (bypassArgs == ClipBypass.INVALID) return;

            ClipBypass.teleport(x, y, z, bypassArgs.mode(), bypassArgs.packets());

            logDirect("Телепортировано на " + formatCoord(x) + " " + formatCoord(y) + " " + formatCoord(z)
                    + (bypassArgs.mode() != null ? " [" + bypassArgs.mode() + "]" : ""));
        } else if (target != null) {
            // Режим игрока: tp <player> [bypass]
            if (target == player) {
                logDirect(Formatting.RED + "Нельзя телепортироваться к самому себе.");
                return;
            }

            ClipBypass.BypassArgs bypassArgs = ClipBypass.parseArgs(this, args);
            if (bypassArgs == ClipBypass.INVALID) return;

            ClipBypass.teleport(target.getX(), target.getY(), target.getZ(), bypassArgs.mode(), bypassArgs.packets());

            logDirect("Телепортировано к игроку " + target.getName().getString()
                    + (bypassArgs.mode() != null ? " [" + bypassArgs.mode() + "]" : ""));
        } else {
            logDirect(Formatting.RED + "Игрок " + first + " не найден.");
        }
    }

    private Double parseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatCoord(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    @Override
    public String getShortDesc() {
        return "Телепорт по координатам или к игроку";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Телепортирует игрока на координаты или к другому игроку",
                "",
                "> tp <x> <y> <z> — телепорт на координаты",
                "> tp <player> — телепорт к игроку",
                "",
                "Необязательный последний аргумент — тип байпаса:",
                "> tp <x> <y> <z> [pos|bypass|vault]",
                "> tp <player> [pos|bypass|vault]",
                "",
                "После режима bypass можно указать количество пакетов:",
                "> tp <x> <y> <z> bypass [пакеты]",
                "> tp <player> bypass [пакеты]",
                "",
                "Без указания типа используется дефолтная логика (pos)."
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.peekString().toLowerCase(Locale.ROOT);
            return MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                    .map(info -> info.getProfile().getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .distinct();
        }
        if (args.hasExactly(2) && isPlayerMode(args.peekString(0))) {
            // Режим игрока — второй аргумент это байпас
            String prefix = args.peekString(1).toLowerCase(Locale.ROOT);
            return ClipBypass.BYPASS_TYPES.stream().filter(s -> s.startsWith(prefix));
        }
        if (args.hasExactly(4) && !isPlayerMode(args.peekString(0))) {
            // Режим координат — четвёртый аргумент это байпас
            String prefix = args.peekString(3).toLowerCase(Locale.ROOT);
            return ClipBypass.BYPASS_TYPES.stream().filter(s -> s.startsWith(prefix));
        }
        if (args.hasExactly(3) && isPlayerMode(args.peekString(0))
                && ClipBypass.BYPASS_TYPES.contains(args.peekString(1).toLowerCase(Locale.ROOT))) {
            // Режим игрока — третий аргумент это пакеты
            return Stream.of("10", "20", "50");
        }
        if (args.hasExactly(5) && !isPlayerMode(args.peekString(0))
                && ClipBypass.BYPASS_TYPES.contains(args.peekString(3).toLowerCase(Locale.ROOT))) {
            // Режим координат — пятый аргумент это пакеты
            return Stream.of("10", "20", "50");
        }
        return Stream.empty();
    }

    /**
     * true, если аргумент следует считать ником игрока:
     * либо это не число, либо онлайн есть игрок с таким (числовым) ником.
     */
    private boolean isPlayerMode(String firstArg) {
        if (parseDouble(firstArg) == null) return true;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return false;
        return world.getPlayers().stream()
                .anyMatch(p -> p.getName().getString().equalsIgnoreCase(firstArg));
    }
}
