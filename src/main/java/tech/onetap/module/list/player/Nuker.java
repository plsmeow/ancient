package tech.onetap.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.event.list.EventTick;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@ModuleInformation(moduleName = "Nuker", moduleDesc = "Ломает блоки вокруг игрока", moduleCategory = ModuleCategory.PLAYER)
public class Nuker extends Module {
    private final SliderSetting range = new SliderSetting("Радиус", 4, 1, 16, 1);
    private final ModeSetting mode = new ModeSetting("Режим", "All", "All", "Whitelist");
    private final BooleanSetting rotate = new BooleanSetting("Ротация", true);
    private final BooleanSetting packetInstamine = new BooleanSetting("Packet instamine", false);
    private final Set<Block> whitelist = new LinkedHashSet<>();
    private boolean whitelistLoaded;

    @Override
    public void onEnable() {
        super.onEnable();
        ensureWhitelistLoaded();
    }

    @Override
    public void onDisable() {
        RotationComponent.getInstance().clearMoveFixMode("Nuker");
        RotationComponent.getInstance().stopRotation();
        super.onDisable();
    }

    @EventHandler
    private void onTick(EventTick ignored) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        ensureWhitelistLoaded();
        BlockPos target = findTarget();
        if (target == null) return;
        Direction side = sideFor(target);
        if (rotate.getValue()) {
            RotationComponent.update(new Rotation(RotationUtil.calculate(Vec3d.ofCenter(target))), 180, 180, 180, 0, 2, MoveFixMode.FREE, "Nuker");
        }
        if (packetInstamine.getValue() && isFastBlock(target)) {
            minePacket(target, side);
        } else {
            if (mc.interactionManager.attackBlock(target, side)) {
                mc.player.swingHand(Hand.MAIN_HAND);
            } else {
                mc.interactionManager.updateBlockBreakingProgress(target, side);
            }
        }
    }

    private BlockPos findTarget() {
        int radius = range.getIntValue();
        BlockPos origin = mc.player.getBlockPos();
        ArrayList<BlockPos> candidates = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    mutable.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    var state = mc.world.getBlockState(mutable);
                    if (!state.isAir() && state.getHardness(mc.world, mutable) >= 0 && isAllowed(state.getBlock())) {
                        candidates.add(mutable.toImmutable());
                    }
                }
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)))).orElse(null);
    }

    private boolean isAllowed(Block block) {
        return mode.is("All") || whitelist.contains(block);
    }

    private boolean isFastBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getHardness(mc.world, pos) <= 0.5f;
    }

    private void minePacket(BlockPos pos, Direction side) {
        mc.interactionManager.sendSequencedPacket(mc.world, sequence -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, side, sequence));
        mc.interactionManager.sendSequencedPacket(mc.world, sequence -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, side, sequence));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private Direction sideFor(BlockPos pos) {
        Vec3d delta = Vec3d.ofCenter(pos).subtract(mc.player.getEyePos());
        double ax = Math.abs(delta.x), ay = Math.abs(delta.y), az = Math.abs(delta.z);
        if (ay >= ax && ay >= az) return delta.y > 0 ? Direction.DOWN : Direction.UP;
        if (ax >= az) return delta.x > 0 ? Direction.WEST : Direction.EAST;
        return delta.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public boolean addBlock(String name) { return changeBlock(name, true); }
    public boolean removeBlock(String name) { return changeBlock(name, false); }

    private boolean changeBlock(String name, boolean add) {
        ensureWhitelistLoaded();
        String id = name.toLowerCase(Locale.US);
        for (Block block : Registries.BLOCK) {
            var blockId = Registries.BLOCK.getId(block);
            if (blockId.toString().equals(id) || blockId.getPath().equals(id)) {
                boolean changed = add ? whitelist.add(block) : whitelist.remove(block);
                if (changed) saveWhitelist();
                logDirect(changed ? "§a" + name + (add ? " §7добавлен в Nuker" : " §7удалён из Nuker") : "§c" + name + " §7уже имеет нужное состояние");
                return changed;
            }
        }
        logDirect("§cБлок §f" + name + " §cне найден");
        return false;
    }

    public void clearBlocks() { ensureWhitelistLoaded(); whitelist.clear(); saveWhitelist(); logDirect("§aСписок Nuker очищен"); }
    public Set<Block> getWhitelist() { ensureWhitelistLoaded(); return whitelist; }

    private void ensureWhitelistLoaded() { if (!whitelistLoaded) loadWhitelist(); }

    private void saveWhitelist() {
        try {
            Files.createDirectories(Path.of(".options"));
            var values = new com.google.gson.JsonArray();
            whitelist.stream().map(block -> Registries.BLOCK.getId(block).toString()).forEach(values::add);
            var root = new com.google.gson.JsonObject();
            root.add("blocks", values);
            Files.writeString(Path.of(".options/nuker.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) { }
    }

    private void loadWhitelist() {
        whitelistLoaded = true;
        Path file = Path.of(".options/nuker.json");
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file)) {
            var root = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);
            if (root == null || !root.has("blocks")) return;
            for (var element : root.getAsJsonArray("blocks")) {
                String id = element.getAsString().toLowerCase(Locale.US);
                for (Block block : Registries.BLOCK) {
                    if (Registries.BLOCK.getId(block).toString().equals(id) || Registries.BLOCK.getId(block).getPath().equals(id)) {
                        whitelist.add(block);
                        break;
                    }
                }
            }
        } catch (Exception ignored) { }
    }
}
