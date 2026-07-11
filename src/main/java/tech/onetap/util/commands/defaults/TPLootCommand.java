package tech.onetap.util.commands.defaults;

import tech.onetap.Onetap;
import tech.onetap.module.list.player.TPLoot;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class TPLootCommand extends Command {

    public TPLootCommand() {
        super("tploot");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        TPLoot module = Onetap.getInstance().getModuleStorage().get(TPLoot.class);
        if (module == null) {
            logDirect("§cМодуль TPLoot не найден");
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
                    logDirect("§cИспользование: .tploot add <название предмета>");
                    return;
                }
                module.addItem(args.getString().toLowerCase(Locale.US));
            }
            case "remove" -> {
                if (!args.hasAny()) {
                    logDirect("§cИспользование: .tploot remove <название предмета>");
                    return;
                }
                module.removeItem(args.getString().toLowerCase(Locale.US));
            }
            case "clear" -> module.clearItems();
            case "list" -> {
                var items = module.getTargetItems();
                if (items.isEmpty()) {
                    logDirect("§7Список пуст");
                } else {
                    logDirect("§7Предметы: §f" + String.join(", ", items.stream()
                            .map(item -> net.minecraft.registry.Registries.ITEM.getId(item).toString())
                            .toList()));
                }
            }
            default -> printHelp();
        }
    }

    private void printHelp() {
        TPLoot module = Onetap.getInstance().getModuleStorage().get(TPLoot.class);
        logDirect("§7Предметы: §f" + (module == null ? 0 : module.getTargetItems().size()));
        logDirect("§e.tploot add <предмет> §7- добавить предмет");
        logDirect("§e.tploot remove <предмет> §7- удалить предмет");
        logDirect("§e.tploot clear §7- очистить список");
        logDirect("§e.tploot list §7- показать список");
    }

    @Override
    public String getShortDesc() {
        return "Управление автолутом TPLoot";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Управляет списком предметов для модуля TPLoot",
                "",
                "Использование:",
                "> tploot add <предмет> - добавляет предмет в список",
                "> tploot remove <предмет> - удаляет предмет из списка",
                "> tploot clear - очищает весь список",
                "> tploot list - показывает текущий список"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws tech.onetap.util.commands.api.exception.CommandNotEnoughArgumentsException {
        TPLoot module = Onetap.getInstance().getModuleStorage().get(TPLoot.class);
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
                List<String> allItems = new ArrayList<>();
                for (var item : net.minecraft.registry.Registries.ITEM) {
                    allItems.add(net.minecraft.registry.Registries.ITEM.getId(item).toString());
                }
                return allItems.stream().filter(item -> item.startsWith(prefix));
            }

            if (sub.equals("remove")) {
                return module.getTargetItems().stream()
                        .map(item -> net.minecraft.registry.Registries.ITEM.getId(item).toString())
                        .filter(item -> item.startsWith(prefix));
            }
        }

        return Stream.empty();
    }
}
