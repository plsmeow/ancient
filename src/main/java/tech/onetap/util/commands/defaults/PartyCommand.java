package tech.onetap.util.commands.defaults;

import com.google.gson.JsonObject;
import net.minecraft.util.Formatting;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.exception.CommandNotEnoughArgumentsException;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.party.connection.PartyApiClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class PartyCommand extends Command implements IMinecraft {

    public PartyCommand() {
        super("party");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny()
                ? args.getString().toLowerCase(Locale.ROOT)
                : "help";

        switch (action) {
            case "create" -> handleCreate(args);
            case "invite" -> handleInvite(args);
            case "join" -> handleJoin(args);
            case "leave" -> handleLeave();
            case "disband" -> handleDisband();
            case "list" -> handleList();
            case "kick" -> handleKick(args);
            default -> logDirect("Неизвестная подкоманда");
        }
    }

    private String nick() {
        return mc.player.getNameForScoreboard();
    }

    private void handleCreate(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String name = args.getString();

        JsonObject req = new JsonObject();
        req.addProperty("leader", nick());
        req.addProperty("name", name);

        PartyApiClient.postAsync("/party/create", req, json -> {
            ChatUtil.send(
                    Formatting.GRAY + "Вы создали пати с названием " +
                            Formatting.WHITE + name
            );
        });
    }

    private void handleInvite(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String target = args.getString();

        JsonObject req = new JsonObject();
        req.addProperty("leader", nick());
        req.addProperty("target", target);

        PartyApiClient.postAsync("/party/invite", req, json ->
                ChatUtil.send(Formatting.GRAY + "Вы пригласили игрока " + Formatting.WHITE + target)
        );
    }

    private void handleJoin(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String party = args.getString();

        JsonObject req = new JsonObject();
        req.addProperty("player", nick());
        req.addProperty("party", party);

        PartyApiClient.postAsync("/party/join", req, json ->
                ChatUtil.send(Formatting.GRAY + "Вы вошли в пати " + Formatting.WHITE + party)
        );
    }

    private void handleLeave() {
        JsonObject req = new JsonObject();
        req.addProperty("player", nick());

        PartyApiClient.postAsync("/party/leave", req, json ->
                ChatUtil.send(Formatting.GRAY + "Вы покинули пати")
        );
    }

    private void handleDisband() {
        JsonObject req = new JsonObject();
        req.addProperty("leader", nick());

        PartyApiClient.postAsync("/party/disband", req, json ->
                ChatUtil.send(Formatting.GRAY + "Вы распустили пати")
        );
    }

    private void handleKick(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String target = args.getString();

        JsonObject req = new JsonObject();
        req.addProperty("leader", nick());
        req.addProperty("target", target);

        PartyApiClient.postAsync("/party/kick", req, json ->
                ChatUtil.send(Formatting.GRAY + "Вы кикнули игрока " + Formatting.WHITE + target)
        );
    }

    private void handleList() {
        JsonObject req = new JsonObject();
        req.addProperty("player", nick());

        PartyApiClient.postAsync("/party/list", req, json -> {
            String[] members = json.get("message").getAsString().split(", ");

            ChatUtil.send(Formatting.GRAY + "Игроки в пати:");
            for (String m : members) {
                ChatUtil.send(" " + Formatting.WHITE + m);
            }
        });
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandNotEnoughArgumentsException {
        int size = args.getArgs().size();

        if (size <= 1) {
            String prefix = size == 1 ? args.peekString(0) : "";
            return new TabCompleteHelper()
                    .prepend("create", "invite", "join", "leave", "disband", "list", "kick")
                    .filterPrefix(prefix)
                    .sortAlphabetically()
                    .stream();
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Позволяет видеть игроков из пати на любой дистанции";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Команда для управления пати системой",
                "",
                "Использование:",
                "> party create <name> - Создать пати",
                "> party invite <player> - Пригласить игрока (лидер)",
                "> party join <name> - Войти в пати",
                "> party leave - Выйти из пати",
                "> party disband - Распустить пати (лидер)",
                "> party list - Посмотреть список игроков в пати",
                "> party kick <player> - Кикнуть игрока (лидер)"
        );
    }
}