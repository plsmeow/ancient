package tech.onetap.util.player.other;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.collection.DefaultedList;
import tech.onetap.Onetap;
import tech.onetap.module.list.movement.Sprint;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.base.Instance;
import tech.onetap.util.packet.NetworkUtils;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import static com.mojang.text2speech.Narrator.LOGGER;

@Getter
public class InventoryUtil implements IMinecraft {

    @Getter
    public static final InventoryUtil instance = new InventoryUtil();

    public static int searchItem(Item item) {
        for (int i = 0; i < mc.player.getInventory().getChangeCount(); i++) {
            if (mc.player.getInventory().getStack(i).getItem().equals(item)) {
                return i;
            }
        }
        return -1;
    }

    public static int searchItem(Item item, int start, int end) {
        for (int i = start; i < end; i++) {
            if (mc.player.getInventory().getStack(i).getItem().equals(item)) {
                return i;
            }
        }
        return -1;
    }

    public static int searchItem(List<Item> items) {
        for (var i = 0; i < mc.player.getInventory().getChangeCount(); i++) {
            for (var item : items) {
                if (mc.player.getInventory().getStack(i).getItem().equals(item)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int searchItemHotbar(Item item) {
        for (var i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem().equals(item)) {
                return i;
            }
        }
        return -1;
    }

    public static int searchItemHotbar(List<Item> items) {
        for (var i = 0; i < 9; i++) {
            for (var item : items) {
                if (mc.player.getInventory().getStack(i).getItem().equals(item)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int searchItemStack(Predicate<ItemStack> predicate) {
        for (var i = 0; i < 45; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    public static int searchHotbarStack(Predicate<ItemStack> predicate) {
        for (var i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    public static void swapWithBypassGrim(Runnable runnable) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        try {
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
            if (Onetap.getInstance().getServerManager().isServerSprinting()) {
                mc.player.setSprinting(false);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                if (!Instance.get(Sprint.class).isEnabled()) mc.options.sprintKey.setPressed(false);
            }
            runnable.run();
            NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void swapWithBypassPolar(Runnable runnable) {
        swapWithBypassPolar(runnable, 100);
    }

    public static void swapWithBypassPolar(Runnable runnable, long duration) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        try {
            SlownessManager.addTask(new SlownessManager.SlowTask(duration, () -> {
                if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
                runnable.run();
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void swapAndUseHvH(Item item) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(item))) return;

        var slot = searchItem(item, 9, 45);
        var slotHotbar = searchItem(item, 0, 9);
        var previousSlot = mc.player.getInventory().selectedSlot;

        if (mc.player.getMainHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }

        if (mc.player.getOffHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        if (slotHotbar != -1) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotHotbar));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }

        if (slotHotbar == -1 && slot != -1) {
            var slotCorrectable = -1;
            for (var slotNone = 0; slotNone < 8; slotNone++) {
                var stack = mc.player.getInventory().getStack(slotNone);
                if (stack.isEmpty()) slotCorrectable = slotNone;

                var action = stack.getUseAction();

                if (action == UseAction.NONE) {
                    slotCorrectable = slotNone;
                }
            }
            if (slotCorrectable == -1) {
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                if (Onetap.getInstance().getServerManager().isServerSprinting()) {
                    mc.player.setSprinting(false);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    if (!Instance.get(Sprint.class).isEnabled()) mc.options.sprintKey.setPressed(false);
                }
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(8));
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
            } else {
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                if (Onetap.getInstance().getServerManager().isServerSprinting()) {
                    mc.player.setSprinting(false);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    if (!Instance.get(Sprint.class).isEnabled()) mc.options.sprintKey.setPressed(false);
                }
                mc.interactionManager.clickSlot(0, slot, slotCorrectable, SlotActionType.SWAP, mc.player);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotCorrectable));
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                mc.interactionManager.clickSlot(0, slot, slotCorrectable, SlotActionType.SWAP, mc.player);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
            }
        }
    }

    public static void swapAndUseLegit(Item item) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(item))) return;

        var slot = searchItem(item, 9, 45);
        var slotHotbar = searchItem(item, 0, 9);
        var previousSlot = mc.player.getInventory().selectedSlot;

        if (mc.player.getMainHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }

        if (mc.player.getOffHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        if (slotHotbar != -1) {
            mc.player.getInventory().selectedSlot = slotHotbar;
            mc.interactionManager.syncSelectedSlot();
            mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
            mc.player.getInventory().selectedSlot = previousSlot;
        }

        if (slotHotbar == -1 && slot != -1) {
            var slotCorrectable = -1;
            for (var slotNone = 0; slotNone < 8; slotNone++) {
                var stack = mc.player.getInventory().getStack(slotNone);
                if (stack.isEmpty()) slotCorrectable = slotNone;

                var action = stack.getUseAction();

                if (action == UseAction.NONE) {
                    slotCorrectable = slotNone;
                }
            }
            if (slotCorrectable == -1) {
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.player.getInventory().selectedSlot = slotCorrectable;
                mc.interactionManager.syncSelectedSlot();
                mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.getInventory().selectedSlot = previousSlot;
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
            } else {
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                mc.interactionManager.clickSlot(0, slot, slotCorrectable, SlotActionType.SWAP, mc.player);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.player.getInventory().selectedSlot = slotCorrectable;
                mc.interactionManager.syncSelectedSlot();
                mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.getInventory().selectedSlot = previousSlot;
                mc.interactionManager.clickSlot(0, slot, slotCorrectable, SlotActionType.SWAP, mc.player);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
            }
        }
    }

    public static void clickSlotNoSync(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player) {
        ScreenHandler screenHandler = player.currentScreenHandler;
        if (syncId != screenHandler.syncId) {
            LOGGER.warn("Ignoring click in mismatching container. Click in {}, player has {}.", syncId, screenHandler.syncId);
        } else {
            DefaultedList<Slot> defaultedList = screenHandler.slots;
            int i = defaultedList.size();
            List<ItemStack> list = Lists.newArrayListWithCapacity(i);
            Iterator var10 = defaultedList.iterator();

            while (var10.hasNext()) {
                Slot slot = (Slot) var10.next();
                list.add(slot.getStack().copy());
            }

            screenHandler.onSlotClick(slotId, button, actionType, player);
            Int2ObjectMap<ItemStack> int2ObjectMap = new Int2ObjectOpenHashMap();

            for (int j = 0; j < i; ++j) {
                ItemStack itemStack = (ItemStack) list.get(j);
                ItemStack itemStack2 = ((Slot) defaultedList.get(j)).getStack();
                if (!ItemStack.areEqual(itemStack, itemStack2)) {
                    int2ObjectMap.put(j, itemStack2.copy());
                }
            }

            NetworkUtils.sendSilentPacket(new ClickSlotC2SPacket(syncId, screenHandler.getRevision(), slotId, button, actionType, screenHandler.getCursorStack().copy(), int2ObjectMap));
        }
    }

    public static int findBestElytraSlot() {
        var bestSlot = -1;
        var bestScore = -1.0;

        RegistryEntry<Enchantment> protection = MinecraftClient.getInstance().world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.PROTECTION.getValue()).orElseThrow();
        RegistryEntry<Enchantment> unbreaking = MinecraftClient.getInstance().world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.UNBREAKING.getValue()).orElseThrow();
        RegistryEntry<Enchantment> mending = MinecraftClient.getInstance().world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.MENDING.getValue()).orElseThrow();

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(Items.ELYTRA)) {
                int protLevel = EnchantmentHelper.getLevel(protection, stack);
                int unbLevel = EnchantmentHelper.getLevel(unbreaking, stack);
                int mendLevel = EnchantmentHelper.getLevel(mending, stack);

                int maxDurability = stack.getMaxDamage();
                int currentDamage = stack.getDamage();
                double durabilityRatio = (maxDurability - currentDamage) / (double) maxDurability;

                double score = protLevel * 100 +
                        unbLevel * 10 +
                        (mendLevel > 0 ? 1 : 0) +
                        durabilityRatio * 10;

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }

        return bestSlot;
    }

    public static int findBestChestplateSlot() {
        var bestSlot = -1;
        var bestScore = -1.0;

        var protection = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.PROTECTION.getValue()).orElseThrow();
        var unbreaking = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.UNBREAKING.getValue()).orElseThrow();
        var mending = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.MENDING.getValue()).orElseThrow();

        for (var slot = 0; slot < 36; slot++) {
            var stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() instanceof ArmorItem armor) {
                var protLevel = EnchantmentHelper.getLevel(protection, stack);
                var unbLevel = EnchantmentHelper.getLevel(unbreaking, stack);
                var mendLevel = EnchantmentHelper.getLevel(mending, stack);

                var armorTypePriority = getChestplatePriority(armor);

                var maxDurability = stack.getMaxDamage();
                var currentDamage = stack.getDamage();
                var durabilityRatio = (maxDurability - currentDamage) / (double) maxDurability;

                var score = armorTypePriority * 10000 +
                        protLevel * 100 +
                        unbLevel * 10 +
                        (mendLevel > 0 ? 1 : 0) +
                        durabilityRatio * 10;

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }

        return bestSlot;
    }

    private static int getArmorPriority(Item item) {
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS)
            return 6;
        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS)
            return 5;
        if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS)
            return 4;
        if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS)
            return 3;
        if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS)
            return 2;
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS)
            return 1;
        return 0;
    }

    private static int getChestplatePriority(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 6;
        if (item == Items.DIAMOND_CHESTPLATE) return 5;
        if (item == Items.IRON_CHESTPLATE) return 4;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 3;
        if (item == Items.GOLDEN_CHESTPLATE) return 2;
        if (item == Items.LEATHER_CHESTPLATE) return 1;
        return 0;
    }

    public static int getBestArmorSlot(EquipmentSlot slot) {
        int bestSlot = -1;
        double bestScore = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof ArmorItem)) continue;
            if (getNeededArmorSlot(stack) != slot) continue;

            double score = getArmorScore(stack);

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private static EquipmentSlot getNeededArmorSlot(ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof ArmorItem) {
            if (item == Items.NETHERITE_HELMET || item == Items.DIAMOND_HELMET
                    || item == Items.IRON_HELMET || item == Items.GOLDEN_HELMET
                    || item == Items.CHAINMAIL_HELMET || item == Items.LEATHER_HELMET)
                return EquipmentSlot.HEAD;

            if (item == Items.NETHERITE_CHESTPLATE || item == Items.DIAMOND_CHESTPLATE
                    || item == Items.IRON_CHESTPLATE || item == Items.GOLDEN_CHESTPLATE
                    || item == Items.CHAINMAIL_CHESTPLATE || item == Items.LEATHER_CHESTPLATE)
                return EquipmentSlot.CHEST;

            if (item == Items.NETHERITE_LEGGINGS || item == Items.DIAMOND_LEGGINGS
                    || item == Items.IRON_LEGGINGS || item == Items.GOLDEN_LEGGINGS
                    || item == Items.CHAINMAIL_LEGGINGS || item == Items.LEATHER_LEGGINGS)
                return EquipmentSlot.LEGS;

            if (item == Items.NETHERITE_BOOTS || item == Items.DIAMOND_BOOTS
                    || item == Items.IRON_BOOTS || item == Items.GOLDEN_BOOTS
                    || item == Items.CHAINMAIL_BOOTS || item == Items.LEATHER_BOOTS)
                return EquipmentSlot.FEET;
        }

        return null;
    }

    public static double getArmorScore(ItemStack stack) {
        double score = 0;
        score += getArmorPriority(stack.getItem());

        var protection = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.PROTECTION.getValue()).orElseThrow();
        var unbreaking = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.UNBREAKING.getValue()).orElseThrow();
        var mending = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.MENDING.getValue()).orElseThrow();
        score += EnchantmentHelper.getLevel(protection, stack) * 0.75;
        score += EnchantmentHelper.getLevel(unbreaking, stack) * 0.3;
        score += EnchantmentHelper.getLevel(mending, stack) * 0.1;

        double durabilityFactor = 1.0;

        if (stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int left = max - stack.getDamage();
            durabilityFactor = Math.max(0.05, (double) left / max);
        }

        return score * durabilityFactor;
    }
}
