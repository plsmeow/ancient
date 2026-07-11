package tech.onetap.util.commands.defaults;

import tech.onetap.Onetap;
import tech.onetap.module.list.render.BlockEsp;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class BlockEspCommand extends Command {

    public BlockEspCommand() {
        super("blockesp");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        BlockEsp module = Onetap.getInstance().getModuleStorage().get(BlockEsp.class);
        if (module == null) {
            logDirect("§cМодуль BlockEsp не найден");
            return;
        }

        if (!args.hasAny()) {
            printHelp();
            return;
        }

        String sub = args.getString().toLowerCase(Locale.US);
        switch (sub) {
            case "add" -> {
                if (!args.hasAny()) {
                    logDirect("§cИспользование: .blockesp add <название блока>");
                    return;
                }
                String blockName = args.getString().toLowerCase(Locale.US);
                module.addBlock(blockName);
            }
            case "remove" -> {
                if (!args.hasAny()) {
                    logDirect("§cИспользование: .blockesp remove <название блока>");
                    return;
                }
                String blockName = args.getString().toLowerCase(Locale.US);
                module.removeBlock(blockName);
            }
            case "clear" -> module.clearBlocks();
            case "list" -> {
                var blocks = module.getTargetBlocks();
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
        logDirect("§7Блоки: §f" + Onetap.getInstance().getModuleStorage().get(BlockEsp.class).getTargetBlocks().size());
        logDirect("§e.blockesp add <блок> §7- добавить блок");
        logDirect("§e.blockesp remove <блок> §7- удалить блок");
        logDirect("§e.blockesp clear §7- очистить список");
        logDirect("§e.blockesp list §7- показать список");
    }

    @Override
    public String getShortDesc() {
        return "Управление подсветкой блоков";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Управляет списком блоков для модуля BlockEsp",
                "",
                "Использование:",
                "> blockesp add <блок> - добавляет блок в список",
                "> blockesp remove <блок> - удаляет блок из списка",
                "> blockesp clear - очищает весь список",
                "> blockesp list - показывает текущий список"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws tech.onetap.util.commands.api.exception.CommandNotEnoughArgumentsException {
        BlockEsp module = Onetap.getInstance().getModuleStorage().get(BlockEsp.class);
        if (module == null) return Stream.empty();

        if (args.hasExactlyOne()) {
            String first = args.peekString().toLowerCase(Locale.US);
            if (first.isEmpty()) {
                return Stream.of("add", "remove", "clear", "list");
            }
            return Stream.of("add", "remove", "clear", "list")
                    .filter(s -> s.startsWith(first));
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
                return module.getTargetBlocks().stream()
                        .map(b -> net.minecraft.registry.Registries.BLOCK.getId(b).toString())
                        .filter(b -> b.startsWith(prefix));
            }
        }

        return Stream.empty();
    }
}
