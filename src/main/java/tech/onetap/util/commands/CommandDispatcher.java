/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package tech.onetap.util.commands;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.Pair;
import tech.onetap.Onetap;
import tech.onetap.event.list.ChatEvent;
import tech.onetap.event.list.TabCompleteEvent;
import tech.onetap.module.list.render.Hide;
import tech.onetap.util.commands.api.argument.ICommandArgument;
import tech.onetap.util.commands.api.exception.CommandNotEnoughArgumentsException;
import tech.onetap.util.commands.api.exception.CommandNotFoundException;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.commands.api.manager.ICommandManager;
import tech.onetap.util.commands.argument.ArgConsumer;
import tech.onetap.util.commands.argument.CommandArguments;
import tech.onetap.util.commands.manager.CommandRepository;

import java.util.List;
import java.util.stream.Stream;

import static tech.onetap.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class CommandDispatcher {

    private final ICommandManager manager;

    public CommandDispatcher() {
        this.manager = Onetap.getInstance().getCommandRepository();
        Onetap.getInstance().getEventBus().register(this);
    }

    @Subscribe
    public void onSendChatMessage(ChatEvent event) {
        String msg = event.getMessage();
        String prefix = ".";
        boolean forceRun = msg.startsWith(FORCE_COMMAND_PREFIX);
        if ((msg.startsWith(prefix)) || forceRun) {
            if (Hide.isActive) return;
            event.setCancelled(true);
            String commandStr = msg.substring(forceRun ? FORCE_COMMAND_PREFIX.length() : prefix.length());
            if (!runCommand(commandStr) && !commandStr.trim().isEmpty()) {
                new CommandNotFoundException(CommandRepository.expand(commandStr).getLeft()).handle(null, null);
            }
        }
    }

    public boolean runCommand(String msg) {
        if (msg.isEmpty()) {
            return this.runCommand("help");
        }
        Pair<String, List<ICommandArgument>> pair = CommandRepository.expand(msg);
        return this.manager.execute(pair);
    }

    @Subscribe
    public void onPreTabComplete(TabCompleteEvent event) {
        String prefix = event.getPrefix();
        String commandPrefix = ".";
        if (!prefix.startsWith(commandPrefix)) {
            return;
        }
        if (Hide.isActive) {
            event.setCancelled(true);
            return;
        }
        String msg = prefix.substring(commandPrefix.length());
        List<ICommandArgument> args = CommandArguments.from(msg, true);
        Stream<String> stream = tabComplete(msg);
        if (args.size() == 1) {
            stream = stream.map(x -> commandPrefix + x);
        }
        event.completions = stream.toArray(String[]::new);
    }

    public Stream<String> tabComplete(String msg) {
        try {
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            ArgConsumer argc = new ArgConsumer(this.manager, args);
            if (argc.hasAtMost(2)) {
                if (argc.hasExactly(1)) {
                    return new TabCompleteHelper()
                            .addCommands(this.manager)
                            .filterPrefix(argc.getString())
                            .stream();
                }
          /*      Settings.Setting setting = settings.byLowerName.get(argc.getString().toLowerCase(Locale.US));
                if (setting != null && !setting.isJavaOnly()) {
                    if (setting.getValueClass() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.value) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(argc.getString()).stream();
                    } else {
                        return Stream.of(SettingsUtil.settingValueToString(setting));
                    }
                }*/
            }
            return this.manager.tabComplete(msg);
        } catch (CommandNotEnoughArgumentsException ignored) { // Shouldn't happen, the operation is safe
            return Stream.empty();
        }
    }
}