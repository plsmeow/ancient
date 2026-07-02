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
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static tech.onetap.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendCommand extends Command {

    public FriendCommand(Onetap onetap) {
        super("friend");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        switch (action) {
            case "add" -> handleAddFriend(args);
            case "remove" -> handleRemoveFriend(args);
            case "list" -> handleListFriends(args, label);
            case "clear" -> handleClearFriends(args);
            default -> logDirect("Неизвестная подкоманда. Используй add/remove/list/clear.", Formatting.GRAY);
        }
    }

    private void handleAddFriend(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        if (FriendRepository.isFriend(name)) {
            logDirect("Этот игрок уже в списке друзей", Formatting.GRAY);
            return;
        }

        FriendRepository.addFriend(name);
        logDirect(Formatting.GRAY + "Игрок с именем " + Formatting.WHITE + name + Formatting.GRAY + " успешно добавлен в друзья");
    }

    private void handleRemoveFriend(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        if (!FriendRepository.isFriend(name)) {
            logDirect("Такой друг не найден", Formatting.GRAY);
            return;
        }

        FriendRepository.removeFriend(name);
        logDirect("Игрок с именем " + Formatting.WHITE + name + Formatting.GRAY + " успешно удален из друзей");
    }

    private void handleListFriends(IArgConsumer args, String label) throws CommandException {
        args.requireMax(1);
        List<Friend> friends = FriendRepository.getFriends();

        Paginator.paginate(
                args,
                new Paginator<>(friends),
                () -> logDirect("Список друзей:", Formatting.GRAY),
                friend -> {
                    Text nameText = Text.literal(Formatting.GRAY + "- " + Formatting.WHITE + friend.name());
                    Text deleteText = Text.literal(Formatting.RED + " [Удалить]")
                            .styled(style -> style.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FORCE_COMMAND_PREFIX + "friend remove " + friend.name()
                            )).withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Click to delete friend")
                            )));

                    return nameText.copy().append(deleteText);
                },
                FORCE_COMMAND_PREFIX + label
        );
    }

    private void handleClearFriends(IArgConsumer args) throws CommandException {
        args.requireMax(1);
        FriendRepository.clear();
        logDirect("Список друзей очищен", Formatting.GRAY);
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
                return FriendRepository.getFriends().stream()
                        .map(Friend::name)
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
        return "Управление друзьями";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Эта команда позволяет управлять списком друзей.",
                "",
                "Использование:",
                "> friend add <name> - Добавляет игрока в друзья.",
                "> friend remove <name> - Удаляет игрока из друзей.",
                "> friend list - Показывает список всех друзей.",
                "> friend clear - Удаляет всех друзей."
        );
    }
}