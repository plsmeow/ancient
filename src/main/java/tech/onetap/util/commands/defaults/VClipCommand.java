package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.List;
import java.util.stream.Stream;

public class VClipCommand extends Command {
    public VClipCommand() {
        super("vclip");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String input = args.getString();

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ClientWorld world = MinecraftClient.getInstance().world;

        double yOffset;
        switch (input.toLowerCase()) {
            case "up" -> yOffset = findOffset(player.getBlockPos(), true, world);
            case "down" -> yOffset = findOffset(player.getBlockPos(), false, world);
            default -> {
                try {
                    yOffset = Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    logDirect(Formatting.RED + input + " не является числом.");
                    return;
                }
            }
        }

        if (yOffset == 0) {
            logDirect(Formatting.RED + "Не удалось выполнить телепортацию.");
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        for (int i = 0; i < 3; i++) {
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(player.isOnGround(), player.horizontalCollision));
        }

        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + yOffset, z, false, player.horizontalCollision));
        player.setPosition(x, y + yOffset, z);

        logDirect("Телепортировано на " + (int) yOffset + " блоков по вертикали");
    }

    private double findOffset(BlockPos pos, boolean toUp, ClientWorld world) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (toUp) {
            for (int i = 3; i < 255; i++) {
                BlockPos base = pos.add(0, i, 0);
                BlockPos head = base.up();
                if (world.getBlockState(base).isAir() && world.getBlockState(head).isAir()) {
                    return base.getY() - player.getY();
                }
            }
        } else {
            for (int i = -1; i > -255; i--) {
                BlockPos solid = pos.add(0, i, 0);
                BlockPos air1 = solid.down();
                BlockPos air2 = air1.down();

                boolean isSolid = !world.getBlockState(solid).isAir();
                boolean isAirBelow1 = world.getBlockState(air1).isAir();
                boolean isAirBelow2 = world.getBlockState(air2).isAir();

                if (isSolid && isAirBelow1 && isAirBelow2) {
                    return air2.getY() - player.getY();
                }
            }
        }

        return 0;
    }


    @Override
    public String getShortDesc() {
        return "Телепорт по вертикали";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Телепортирует игрока вверх или вниз",
                "",
                "> vclip <расстояние> — телепорт на определенное количество блоков",
                "> vclip up — вверх до свободного блока",
                "> vclip down — вниз до свободного блока"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("up", "down");
        }
        return Stream.empty();
    }
}