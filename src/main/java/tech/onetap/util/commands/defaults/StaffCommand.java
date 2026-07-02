package tech.onetap.util.commands.defaults;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.Onetap;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.helpers.Paginator;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.staff.Staff;
import tech.onetap.util.staff.StaffManager;

import java.util.*;
import java.util.stream.Stream;

import static tech.onetap.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class StaffCommand extends Command {

    private final StaffManager staffManager;

    public StaffCommand(Onetap onetap) {
        super("staff");
        this.staffManager = onetap.getStaffManager();
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        switch (action) {
            case "add" -> handleAddStaff(args);
            case "remove" -> handleRemoveStaff(args);
            case "list" -> handleListStaff(args, label);
            case "clear" -> handleClearStaff(args);
            default -> logDirect("Неизвестная подкоманда. Используй add/remove/list/clear.", Formatting.GRAY);
        }
    }

    private void handleAddStaff(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        if (StaffManager.isStaff(name)) {
            logDirect("Этот игрок уже в списке модераторов", Formatting.GRAY);
            return;
        }

        StaffManager.addStaff(new Staff(name));
        logDirect("Игрок с именем " + Formatting.WHITE + name + Formatting.GRAY + " успешно добавлен в список модераторов");
    }

    private void handleRemoveStaff(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        if (!StaffManager.isStaff(name)) {
            logDirect("Такой игрок в списке модераторов не найден", Formatting.GRAY);
            return;
        }

        StaffManager.removeStaff(name);
        logDirect("Игрок с именем " + Formatting.WHITE + name + Formatting.GRAY + " успешно удален из списка модераторов");
    }

    private void handleListStaff(IArgConsumer args, String label) throws CommandException {
        args.requireMax(1);
        Set<Staff> staffSet = StaffManager.getStaffList();

        List<Staff> sorted = staffSet.stream()
                .sorted(Comparator.comparing(s -> s.name.toLowerCase()))
                .toList();

        Paginator.paginate(
                args, new Paginator<>(sorted),
                () -> logDirect("Список модераторов:", Formatting.GRAY),
                staff -> {
                    Text nameText = Text.literal(Formatting.GRAY + "- " + Formatting.WHITE + staff.name);
                    Text deleteText = Text.literal(Formatting.RED + " [Удалить]")
                            .styled(style -> style.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FORCE_COMMAND_PREFIX + "staff remove " + staff.name
                            )).withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Click to delete staff")
                            )));

                    return nameText.copy().append(deleteText);
                },
                FORCE_COMMAND_PREFIX + label
        );
    }

    private void handleClearStaff(IArgConsumer args) throws CommandException {
        args.requireMax(1);
        StaffManager.clearStaff();
        logDirect("Список модераторов очищен", Formatting.GRAY);
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
            if (action.equals("remove") && args.hasExactly(2)) {
                String prefix = args.peekString(1).toLowerCase(Locale.ROOT);
                return StaffManager.getStaffList().stream()
                        .map(staff -> staff.name)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted()
                        .distinct();
            }
            if (action.equals("add") && args.hasExactly(2)) {
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
        return "Управление списком модераторов";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Эта команда позволяет управлять списком модераторов.",
                "",
                "Использование:",
                "> staff add <name> - Добавляет игрока в список модераторов.",
                "> staff remove <name> - Удаляет игрока из списка модераторов.",
                "> staff list - Показывает список модераторов.",
                "> staff clear - очищает весь список модераторов."
        );
    }
}