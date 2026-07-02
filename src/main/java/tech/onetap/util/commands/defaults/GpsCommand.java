package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.gps.GpsRenderer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GpsCommand extends Command {
    public GpsCommand() { super("gps"); }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        GpsRenderer gps = GpsRenderer.get();
        if (args.peekString().equalsIgnoreCase("off")) { gps.setEnabled(false); return; }
        double x, z;
        try { x = Double.parseDouble(args.getString()); z = Double.parseDouble(args.getString()); }
        catch (NumberFormatException e) { return; }
        gps.setTarget(x, z); gps.setEnabled(true);
    }

    @Override public String getShortDesc() { return "Устанавливает стрелку на указанные координаты"; }
    @Override public List<String> getLongDesc() {
        return Arrays.asList(
                "С помощью этой команды можно установить стрелку указывающую на указанные координаты",
                "",
                "Использование:",
                "> gps <x> <z> - Ставит стрелку на указанные координаты.",
                "> gps off - Отключает стрелку."
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (!args.hasAny() || (args.hasExactlyOne() && args.getArgs().getFirst().getValue().isEmpty())) {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) return Stream.of("off");
            return Stream.of("off", (int)p.getX()+" "+(int)p.getZ());
        }
        return Stream.empty();
    }
} 