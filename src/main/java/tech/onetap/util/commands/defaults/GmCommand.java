package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class GmCommand extends Command {
    public GmCommand() {
        super("gm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        if (MinecraftClient.getInstance().getNetworkHandler() == null) {
            logDirect(Formatting.RED + "Вы не находитесь на сервере.");
            return;
        }

        PlayerListEntry entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                .filter(info -> info.getProfile().getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);

        if (entry == null) {
            logDirect(Formatting.RED + "Игрок " + name + " не найден.");
            return;
        }

        GameMode gameMode = entry.getGameMode();
        if (gameMode == null) {
            logDirect(Formatting.RED + "Не удалось определить gamemode игрока " + entry.getProfile().getName() + ".");
            return;
        }

        Formatting color = switch (gameMode) {
            case CREATIVE -> Formatting.GOLD;
            case ADVENTURE -> Formatting.GREEN;
            case SPECTATOR -> Formatting.AQUA;
            default -> Formatting.GRAY;
        };

        logDirect("Игрок " + Formatting.WHITE + entry.getProfile().getName()
                + Formatting.GRAY + " — gamemode: " + color + gameMode.getName());
    }

    @Override
    public String getShortDesc() {
        return "Показывает gamemode игрока";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Показывает игровой режим игрока (берётся из таб-листа)",
                "",
                "> gm <ник> — вывести gamemode игрока",
                "Возможные значения: survival, creative, adventure, spectator"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.peekString().toLowerCase(Locale.ROOT);
            return MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                    .map(info -> info.getProfile().getName())
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .distinct();
        }
        return Stream.empty();
    }
}
