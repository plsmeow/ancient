package tech.onetap.util.commands.manager;

import net.minecraft.util.Pair;
import tech.onetap.Onetap;
import tech.onetap.util.commands.api.ICommand;
import tech.onetap.util.commands.api.argument.ICommandArgument;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.exception.CommandUnhandledException;
import tech.onetap.util.commands.api.exception.ICommandException;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.commands.api.manager.ICommandManager;
import tech.onetap.util.commands.api.registry.Registry;
import tech.onetap.util.commands.argument.ArgConsumer;
import tech.onetap.util.commands.argument.CommandArguments;
import tech.onetap.util.commands.defaults.DefaultCommands;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class CommandRepository implements ICommandManager {

    private final Registry<ICommand> registry = new Registry<>();

    public CommandRepository() {
        DefaultCommands.createAll().forEach(this.registry::register);
    }

    @Override
    public Registry<ICommand> getRegistry() {
        return this.registry;
    }

    @Override
    public ICommand getCommand(String name) {
        for (ICommand command : this.registry.entries) {
            if (command.getNames().contains(name.toLowerCase(Locale.US))) {
                return command;
            }
        }
        return null;
    }

    @Override
    public boolean execute(String string) {
        return this.execute(expand(string));
    }

    @Override
    public boolean execute(Pair<String, List<ICommandArgument>> expanded) {
        ExecutionWrapper execution = this.from(expanded);
        if (execution != null) {
            execution.execute();
        }
        return execution != null;
    }

    @Override
    public Stream<String> tabComplete(Pair<String, List<ICommandArgument>> expanded) {
        ExecutionWrapper execution = this.from(expanded);
        return execution == null ? Stream.empty() : execution.tabComplete();
    }

    @Override
    public Stream<String> tabComplete(String prefix) {
        Pair<String, List<ICommandArgument>> pair = expand(prefix, true);
        String label = pair.getLeft();
        List<ICommandArgument> args = pair.getRight();
        if (args.isEmpty()) {
            return new TabCompleteHelper()
                    .addCommands(Onetap.getInstance().getCommandRepository())
                    .filterPrefix(label)
                    .stream();
        } else {
            return tabComplete(pair);
        }
    }

    private ExecutionWrapper from(Pair<String, List<ICommandArgument>> expanded) {
        String label = expanded.getLeft();
        ArgConsumer args = new ArgConsumer(this, expanded.getRight());

        ICommand command = this.getCommand(label);
        return command == null ? null : new ExecutionWrapper(command, label, args);
    }

    private static Pair<String, List<ICommandArgument>> expand(String string, boolean preserveEmptyLast) {
        String label = string.split("\\s", 2)[0];
        List<ICommandArgument> args = CommandArguments.from(string.substring(label.length()), preserveEmptyLast);
        return new Pair<>(label, args);
    }

    public static Pair<String, List<ICommandArgument>> expand(String string) {
        return expand(string, false);
    }

    private static final class ExecutionWrapper {

        private ICommand command;
        private String label;
        private ArgConsumer args;

        private ExecutionWrapper(ICommand command, String label, ArgConsumer args) {
            this.command = command;
            this.label = label;
            this.args = args;
        }

        private void execute() {
            try {
                this.command.execute(this.label, this.args);
            } catch (Throwable t) {
                ICommandException exception = t instanceof ICommandException
                        ? (ICommandException) t
                        : new CommandUnhandledException(t);

                exception.handle(command, args.getArgs());
            }
        }

        private Stream<String> tabComplete() {
            try {
                return this.command.tabComplete(this.label, this.args);
            } catch (CommandException ignored) {
                // NOP
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return Stream.empty();
        }
    }
}
