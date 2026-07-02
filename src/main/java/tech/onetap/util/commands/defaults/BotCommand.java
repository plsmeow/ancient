package tech.onetap.util.commands.defaults;

import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.bot.BotSessionManager;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class BotCommand extends Command {

    public BotCommand() {
        super("bot");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : "list";

        switch (action) {
            case "connect" -> connect(args);
            case "remove" -> remove(args);
            case "return" -> restore();
            case "control" -> control(args);
            case "say" -> say(args);
            case "sayall" -> sayAll(args);
            case "list" -> list();
            default -> logDirect("Неизвестная подкоманда. Используй connect/remove/return/control/say/sayall/list");
        }
    }

    private void connect(IArgConsumer args) throws CommandException {
        args.requireMin(2);
        String name = args.getString();
        String ip = args.getString();
        BotSessionManager.connect(name, ip);
        logDirect("Подключение выполнено: " + name + " -> " + ip + " (Предыдущая сессия заморожена)");
    }

    private void remove(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();
        if (BotSessionManager.remove(name)) {
            logDirect("Бот отключен и удален: " + name);
            return;
        }
        logDirect("Бот не найден: " + name);
    }

    private void control(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();
        if (BotSessionManager.control(name)) {
            logDirect("Переключаюсь на бота: " + name);
            return;
        }
        logDirect("Бот не найден: " + name);
    }

    private void say(IArgConsumer args) throws CommandException {
        args.requireMin(2);
        String name = args.getString();
        StringBuilder message = new StringBuilder();
        while (args.hasAny()) {
            message.append(args.getString()).append(" ");
        }
        if (BotSessionManager.say(name, message.toString().trim())) {
            logDirect("Сообщение от лица " + name + " отправлено.");
            return;
        }
        logDirect("Бот не найден: " + name);
    }

    private void sayAll(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        StringBuilder message = new StringBuilder();
        while (args.hasAny()) {
            message.append(args.getString()).append(" ");
        }
        BotSessionManager.sayAll(message.toString().trim());
        logDirect("Сообщение отправлено от всех ботов.");
    }

    private void restore() {
        if (BotSessionManager.restore()) {
            logDirect("Возвращаю прошлую сессию");
            return;
        }
        logDirect("Нет сохраненной сессии для возврата");
    }

    private void list() {
        List<BotSessionManager.BotConnection> connections = BotSessionManager.getConnections();
        if (connections.isEmpty()) {
            logDirect("Список ботов пуст");
            return;
        }
        logDirect("Подключенные боты:");
        for (BotSessionManager.BotConnection connection : connections) {
            logDirect("- " + connection.name() + " @ " + connection.address());
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .prepend("connect", "remove", "return", "control", "say", "sayall", "list")
                    .filterPrefix(args.getString())
                    .stream();
        }

        if (args.hasExactly(2)) {
            String action = args.peekString(0).toLowerCase(Locale.ROOT);
            if (List.of("remove", "control", "say").contains(action)) {
                String searchName = args.peekString(1).toLowerCase(Locale.ROOT);
                return BotSessionManager.getConnections().stream()
                        .map(BotSessionManager.BotConnection::name)
                        .filter(n -> n.toLowerCase().startsWith(searchName));
            }
            if (action.equals("connect")) return Stream.of("<ник_бота>");
            if (action.equals("sayall")) return Stream.of("<сообщение>");
        }

        if (args.hasExactly(3) && args.peekString(0).equalsIgnoreCase("connect")) {
            return Stream.of("<айпи_сервера>");
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление бот-сессиями";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Управление бот-сессиями и быстрым переключением.",
                "",
                "Использование:",
                "> bot connect <name> <ip>",
                "> bot control <name>",
                "> bot say <name> <message>",
                "> bot sayall <message>",
                "> bot remove <name>",
                "> bot return",
                "> bot list"
        );
    }
}