package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.helpers.Paginator;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.target.TargetRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static tech.onetap.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class TargetCommand extends Command {

    public TargetCommand() {
        super("target");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        switch (action) {
            case "add" -> handleAdd(args);
            case "remove" -> handleRemove(args);
            case "list" -> handleList(args, label);
            case "clear" -> handleClear(args);
            default -> logDirect("§7Неизвестная подкоманда. Используй add/remove/list/clear.");
        }
    }

    private void handleAdd(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        if (TargetRepository.isTarget(name)) {
            logDirect("§7Этот игрок уже в списке таргетов");
            return;
        }

        TargetRepository.addTarget(name);
        logDirect("§7Игрок §f" + name + " §7добавлен в таргеты");
    }

    private void handleRemove(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        if (!TargetRepository.isTarget(name)) {
            logDirect("§7Этот игрок не найден в списке таргетов");
            return;
        }

        TargetRepository.removeTarget(name);
        logDirect("§7Игрок §f" + name + " §7удалён из таргетов");
    }

    private void handleList(IArgConsumer args, String label) throws CommandException {
        args.requireMax(1);
        List<String> targets = TargetRepository.getTargets();

        Paginator.paginate(
                args,
                new Paginator<>(targets),
                () -> logDirect("§7Список таргетов:", Formatting.GRAY),
                target -> {
                    Text nameText = Text.literal(Formatting.GRAY + "- " + Formatting.RED + target);
                    Text deleteText = Text.literal(Formatting.RED + " [Удалить]")
                            .styled(style -> style.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FORCE_COMMAND_PREFIX + "target remove " + target
                            )).withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Click to delete target")
                            )));

                    return nameText.copy().append(deleteText);
                },
                FORCE_COMMAND_PREFIX + label
        );
    }

    private void handleClear(IArgConsumer args) throws CommandException {
        args.requireMax(1);
        TargetRepository.clear();
        logDirect("§7Список таргетов очищен");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny() && args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .prepend("add", "remove", "list", "clear")
                    .filterPrefix(args.getString())
                    .sortAlphabetically()
                    .stream();
        } else if (args.hasAny()) {
            String action = args.peekString(0).toLowerCase(Locale.ROOT);
            if ((action.equals("remove")) && args.hasExactly(2)) {
                String prefix = args.peekString(1).toLowerCase(Locale.ROOT);
                return TargetRepository.getTargets().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted()
                        .distinct();
            }
            if ((action.equals("add")) && args.hasExactly(2)) {
                String prefix = args.peekString(1).toLowerCase(Locale.ROOT);
                return MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                        .map(info -> info.getProfile().getName())
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted()
                        .distinct();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление таргетами для KillAura/CrystalAura";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Управляет списком приоритетных таргетов.",
                "Игроки из этого списка будут выбраны KillAura и CrystalAura",
                "даже если уже залочены на другом враге.",
                "",
                "Использование:",
                "> target add <name> - добавить игрока в таргеты",
                "> target remove <name> - удалить игрока из таргетов",
                "> target list - показать список таргетов",
                "> target clear - очистить список таргетов"
        );
    }
}
