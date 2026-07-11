package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ModuleInformation(moduleName = "TPLoot", moduleDesc = "Автолут предметов с телепортом", moduleCategory = ModuleCategory.PLAYER)
public class TPLoot extends Module {
    private final BooleanSetting autoLeave = new BooleanSetting("Auto Leave", true);
    private final ModeSetting leaveMode = new ModeSetting("Leave Mode", "Hub", "Hub", "Spawn");
    private final ModeSetting tpMode = new ModeSetting("TP Mode", "Bypass", "Pos", "Bypass", "Vault");
    private final SliderSetting packets = new SliderSetting("Bypass Packets", 10, 0, 50, 1).setVisible(() -> tpMode.is("Bypass"));
    private final BooleanSetting bypassOnGround = new BooleanSetting("Bypass OnGround", true).setVisible(() -> tpMode.is("Bypass"));
    private final ModeSetting searchMode = new ModeSetting("Search Mode", "Single", "Single", "Group");
    private final SliderSetting searchRadius = new SliderSetting("Search Radius", 100, 5, 500, 1);
    private final SliderSetting tpDelayMs = new SliderSetting("TP Delay MS", 500, 0, 3000, 50);
    private final SliderSetting vaultDelay = new SliderSetting("Vault Delay MS", 500, 100, 2000, 50).setVisible(() -> tpMode.is("Vault"));

    private final Set<Item> targetItems = new LinkedHashSet<>();
    private final Map<Integer, Long> itemSpawnTime = new HashMap<>();

    private Vec3d startPos;
    private Vec3d deathPos;
    private Integer targetItemId;
    private Vec3d targetPos;
    private long lastTpTime;
    private long lastVaultTime;
    private boolean itemsLoaded;

    public boolean addItem(String name) {
        ensureItemsLoaded();
        String id = normalizeItemName(name);
        for (Item item : Registries.ITEM) {
            if (matchesItemName(item, id)) {
                if (targetItems.add(item)) {
                    logDirect("§a" + Registries.ITEM.getId(item) + " §7добавлен в TPLoot");
                    saveItems();
                    return true;
                }
                logDirect("§c" + Registries.ITEM.getId(item) + " §7уже в списке");
                return false;
            }
        }
        logDirect("§cПредмет §f" + name + " §cне найден");
        return false;
    }

    public boolean removeItem(String name) {
        ensureItemsLoaded();
        String id = normalizeItemName(name);
        Iterator<Item> it = targetItems.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (matchesItemName(item, id)) {
                it.remove();
                logDirect("§a" + Registries.ITEM.getId(item) + " §7удалён из TPLoot");
                saveItems();
                return true;
            }
        }
        logDirect("§cПредмет §f" + name + " §cне найден в списке");
        return false;
    }

    public void clearItems() {
        ensureItemsLoaded();
        targetItems.clear();
        itemSpawnTime.clear();
        saveItems();
        logDirect("§aСписок TPLoot очищен");
    }

    public Set<Item> getTargetItems() {
        ensureItemsLoaded();
        return targetItems;
    }

    public void saveItems() {
        JsonArray arr = new JsonArray();
        for (Item item : targetItems) {
            arr.add(Registries.ITEM.getId(item).toString());
        }

        JsonObject root = new JsonObject();
        root.add("items", arr);

        try {
            Path dir = Paths.get(".options/configs");
            Files.createDirectories(dir);
            Files.write(dir.resolve("tploot.json"), new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadItems() {
        itemsLoaded = true;
        targetItems.clear();

        Path file = Paths.get(".options/configs/tploot.json");
        if (!Files.exists(file)) {
            loadDefaultItems();
            saveItems();
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root == null || !root.has("items")) return;

            JsonArray arr = root.getAsJsonArray("items");
            for (JsonElement el : arr) {
                String name = el.getAsString();
                for (Item item : Registries.ITEM) {
                    if (matchesItemName(item, normalizeItemName(name))) {
                        targetItems.add(item);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ensureItemsLoaded();
        resetState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetState();
        saveItems();
    }

    @Subscribe
    private void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;

        handleDeathReturn();
        findLoot();
        checkTargetPickup();
    }

    private void resetState() {
        startPos = null;
        deathPos = null;
        targetItemId = null;
        targetPos = null;
        itemSpawnTime.clear();
        lastTpTime = 0L;
        lastVaultTime = 0L;
    }

    private void handleDeathReturn() {
        if (mc.player.getHealth() <= 0 && deathPos == null) {
            deathPos = mc.player.getPos();
            logDirect("§7TPLoot: §fсохранена позиция смерти");
        }

        if (deathPos != null && mc.player.isAlive() && mc.player.getHealth() > 0 && canTeleportNow()) {
            teleport(deathPos);
            logDirect("§7TPLoot: §fвозврат на позицию смерти");
            deathPos = null;
        }
    }

    private void findLoot() {
        ensureItemsLoaded();
        if (targetItems.isEmpty()) return;

        Vec3d playerPos = mc.player.getPos();
        double r = searchRadius.getValue();
        Box box = new Box(playerPos.x - r, playerPos.y - r, playerPos.z - r, playerPos.x + r, playerPos.y + r, playerPos.z + r);

        List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class, box, item ->
                item != null && item.isAlive() && targetItems.contains(item.getStack().getItem())
        );

        if (items.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (ItemEntity item : items) {
            itemSpawnTime.putIfAbsent(item.getId(), now);
        }

        ItemEntity targetEntity = searchMode.is("Single")
                ? getNearestItem(items, playerPos)
                : getNearestItem(items, getGroupCenter(items));

        if (targetEntity == null) return;

        Vec3d safeTarget = safePos(targetEntity.getPos());
        if (safeTarget == null) return;

        long spawnTime = itemSpawnTime.getOrDefault(targetEntity.getId(), 0L);
        if (now - spawnTime < tpDelayMs.getIntValue()) return;
        if (mc.player.getPos().distanceTo(safeTarget) < 0.2) return;

        if (tpMode.is("Vault")) {
            if (now - lastVaultTime < vaultDelay.getIntValue()) return;
            lastVaultTime = now;
        }

        if (targetItemId == null) startPos = mc.player.getPos();

        targetItemId = targetEntity.getId();
        targetPos = safeTarget;
        teleport(safeTarget);
    }

    private void checkTargetPickup() {
        if (targetItemId == null || mc.world == null || mc.player == null) return;

        ItemEntity target = null;
        for (ItemEntity item : mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(searchRadius.getValue()), e -> true)) {
            if (item.getId() == targetItemId) {
                target = item;
                break;
            }
        }

        if (target == null || !target.isAlive()) {
            logDirect("§7TPLoot: §fцелевой предмет подобран");
            itemSpawnTime.remove(targetItemId);
            targetItemId = null;
            targetPos = null;

            if (autoLeave.getValue()) {
                leave();
            } else if (startPos != null) {
                teleport(startPos);
                logDirect("§7TPLoot: §fвозврат на стартовую позицию");
                startPos = null;
            }
        }
    }

    private void leave() {
        if (mc.getNetworkHandler() == null) return;

        String cmd = leaveMode.is("Hub") ? "hub" : "spawn";
        mc.getNetworkHandler().sendChatCommand(cmd);
        logDirect("§7TPLoot: §fливнул на /" + cmd);
        toggle();
    }

    private boolean canTeleportNow() {
        return System.currentTimeMillis() - lastTpTime >= tpDelayMs.getIntValue();
    }

    private void teleport(Vec3d pos) {
        if (pos == null || mc.player == null || mc.getNetworkHandler() == null) return;

        lastTpTime = System.currentTimeMillis();

        if (tpMode.is("Pos")) {
            mc.player.setPosition(pos.x, pos.y, pos.z);
            return;
        }

        if (tpMode.is("Bypass")) {
            if (!mc.player.isAlive()) return;
            mc.player.setPosition(pos.x, pos.y, pos.z);

            for (int i = 0; i < packets.getIntValue(); i++) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        pos.x,
                        pos.y,
                        pos.z,
                        bypassOnGround.getValue(),
                        mc.player.horizontalCollision
                ));
            }
            return;
        }

        if (tpMode.is("Vault")) {
            if (!mc.player.isAlive()) return;

            Entity entity = mc.player.hasVehicle() ? mc.player.getVehicle() : mc.player;
            if (entity == null) return;

            Vec3d currentPos = entity.getPos();
            Vec3d upPos = currentPos.add(0, 129.0, 0);
            Vec3d aboveTarget = new Vec3d(pos.x, upPos.y, pos.z);
            Vec3d downPos = new Vec3d(pos.x, pos.y, pos.z);
            Vec3d finalPos = downPos.add(0, 0.01, 0);

            for (int i = 0; i < 13; i++) {
                if (mc.player.hasVehicle()) {
                    mc.getNetworkHandler().sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
                } else {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
                }
            }

            sendVaultMove(entity, upPos);
            sendVaultMove(entity, aboveTarget);
            sendVaultMove(entity, downPos);
            sendVaultMove(entity, finalPos);

            entity.setPosition(finalPos.x, finalPos.y, finalPos.z);
            if (entity != mc.player) {
                mc.player.setPosition(finalPos.x, finalPos.y, finalPos.z);
            }
        }
    }

    private void sendVaultMove(Entity entity, Vec3d pos) {
        if (mc.getNetworkHandler() == null) return;

        if (entity == mc.player) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, false, mc.player.horizontalCollision));
        } else if (mc.player != null && mc.player.getVehicle() != null) {
            mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(pos, mc.player.getVehicle().getYaw(), mc.player.getVehicle().getPitch(), false));
        }
    }

    private ItemEntity getNearestItem(List<ItemEntity> items, Vec3d from) {
        if (items == null || items.isEmpty() || from == null) return null;

        ItemEntity nearest = null;
        double bestDist = Double.MAX_VALUE;

        for (ItemEntity item : items) {
            double dist = item.getPos().distanceTo(from);
            if (dist < bestDist) {
                bestDist = dist;
                nearest = item;
            }
        }

        return nearest;
    }

    private Vec3d getGroupCenter(List<ItemEntity> items) {
        if (items == null || items.isEmpty()) return null;

        double x = 0, y = 0, z = 0;
        for (ItemEntity item : items) {
            x += item.getX();
            y += item.getY();
            z += item.getZ();
        }

        int count = items.size();
        return new Vec3d(x / count, y / count, z / count);
    }

    private boolean canTp(Vec3d pos) {
        if (pos == null || mc.player == null || mc.world == null) return false;

        double w = mc.player.getWidth();
        double h = mc.player.getHeight();
        Box playerBox = new Box(pos.x - w / 2, pos.y, pos.z - w / 2, pos.x + w / 2, pos.y + h, pos.z + w / 2);

        boolean spaceEmpty = mc.world.isSpaceEmpty(playerBox);
        BlockPos below = BlockPos.ofFloored(pos.x, pos.y - 1, pos.z);
        boolean hasGround = !mc.world.getBlockState(below).isAir();

        return spaceEmpty && hasGround;
    }

    private Vec3d safePos(Vec3d pos) {
        if (pos == null) return null;

        List<Vec3d> testPositions = Arrays.asList(
                pos,
                pos.add(0.5, 0, 0),
                pos.add(-0.5, 0, 0),
                pos.add(0, 0, 0.5),
                pos.add(0, 0, -0.5),
                pos.add(0.5, 0, 0.5),
                pos.add(-0.5, 0, -0.5),
                pos.add(0.5, 0, -0.5),
                pos.add(-0.5, 0, 0.5)
        );

        for (Vec3d test : testPositions) {
            if (canTp(test)) return test;
        }

        return null;
    }

    private void ensureItemsLoaded() {
        if (!itemsLoaded) loadItems();
    }

    private void loadDefaultItems() {
        targetItems.addAll(List.of(
                Items.TOTEM_OF_UNDYING,
                Items.NETHERITE_HELMET,
                Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS,
                Items.NETHERITE_SWORD,
                Items.NETHERITE_PICKAXE,
                Items.GOLDEN_APPLE,
                Items.ENCHANTED_GOLDEN_APPLE,
                Items.PLAYER_HEAD,
                Items.SHULKER_BOX,
                Items.NETHERITE_INGOT,
                Items.SPLASH_POTION
        ));
    }

    private String normalizeItemName(String name) {
        return name.toLowerCase(Locale.US);
    }

    private boolean matchesItemName(Item item, String name) {
        var id = Registries.ITEM.getId(item);
        return id.toString().equals(name) || id.getPath().equals(name);
    }
}
