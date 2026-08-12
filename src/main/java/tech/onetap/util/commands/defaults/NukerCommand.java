package tech.onetap.util.commands.defaults;

import tech.onetap.Onetap;
import tech.onetap.module.list.player.Nuker;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class NukerCommand extends Command {
    public NukerCommand() {
        super("nuker");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Nuker module = Onetap.getInstance().getModuleStorage().get(Nuker.class);
        if (module == null) {
            logDirect("§cМодуль Nuker не найден");
            return;
        }
        if (!args.hasAny()) {
            printHelp();
            return;
        }
        switch (args.getString().toLowerCase(Locale.US)) {
            case "add" -> {
                if (!args.hasAny()) {
                    logDirect("§cИспользование: .nuker add <название блока>");
                    return;
                }
                module.addBlock(args.getString());
            }
            case "remove" -> {
                if (!args.hasAny()) {
                    logDirect("§cИспользование: .nuker remove <название блока>");
                    return;
                }
                module.removeBlock(args.getString());
            }
            case "clear" -> module.clearBlocks();
            case "list" -> {
                var blocks = module.getWhitelist();
                if (blocks.isEmpty()) {
                    logDirect("§7Список пуст");
                } else {
                    logDirect("§7Блоки: §f" + String.join(", ", blocks.stream()
                            .map(b -> net.minecraft.registry.Registries.BLOCK.getId(b).toString())
                            .toList()));
                }
            }
            default -> printHelp();
        }
    }

    private void printHelp() {
        Nuker module = Onetap.getInstance().getModuleStorage().get(Nuker.class);
        logDirect("§7Блоков в whitelist: §f" + (module == null ? 0 : module.getWhitelist().size()));
        logDirect("§e.nuker add <блок> §7- добавить блок");
        logDirect("§e.nuker remove <блок> §7- удалить блок");
        logDirect("§e.nuker clear §7- очистить список");
        logDirect("§e.nuker list §7- показать список");
    }

    @Override
    public String getShortDesc() {
        return "Управление whitelist Nuker";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Управляет whitelist блоков для Nuker",
                "",
                "> nuker add <блок> - добавляет блок",
                "> nuker remove <блок> - удаляет блок",
                "> nuker clear - очищает список",
                "> nuker list - показывает список"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws tech.onetap.util.commands.api.exception.CommandNotEnoughArgumentsException {
        Nuker module = Onetap.getInstance().getModuleStorage().get(Nuker.class);
        if (module == null) return Stream.empty();
        if (args.hasExactlyOne()) {
            String prefix = args.peekString().toLowerCase(Locale.US);
            return Stream.of("add", "remove", "clear", "list").filter(s -> s.startsWith(prefix));
        }
        if (args.hasExactly(2)) {
            String sub = args.getConsumed().getLast().getValue().toLowerCase(Locale.US);
            String prefix = args.peekString().toLowerCase(Locale.US);
            if (sub.equals("add")) {
                List<String> allBlocks = new ArrayList<>();
                for (var block : net.minecraft.registry.Registries.BLOCK) {
                    allBlocks.add(net.minecraft.registry.Registries.BLOCK.getId(block).toString());
                }
                return allBlocks.stream().filter(b -> b.startsWith(prefix));
            }
            if (sub.equals("remove")) {
                return module.getWhitelist().stream()
                        .map(b -> net.minecraft.registry.Registries.BLOCK.getId(b).toString())
                        .filter(b -> b.startsWith(prefix));
            }
        }
        return Stream.empty();
    }
}
