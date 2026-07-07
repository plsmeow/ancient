package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

@ModuleInformation(moduleName = "Auto Cart", moduleCategory = ModuleCategory.COMBAT)
public class AutoCart extends Module {

    final ModeSetting modeSetting = new ModeSetting("Режим", "Pre-Rail", "Pre-Rail", "Insta-Cart");

    enum State {
        IDLE,
        PLACING_RAIL,
        DRAWING_BOW,
        SHOOTING,
        PLACING_MINECART,
        INSTA_DRAWING_BOW,
        INSTA_SHOOTING,
        INSTA_PLACING_RAIL,
        INSTA_PLACING_MINECART,
        DONE
    }

    State currentState = State.IDLE;
    int actionTimer = 0;
    int originalSlot = 0;
    int railSlot = -1;
    int bowSlot = -1;
    int tntMinecartSlot = -1;
    BlockHitResult targetHit = null;
    boolean bowStarted = false;
    float originalPitch = 0;
    float originalYaw = 0;

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player == null || mc.world == null) {
            setEnabled(false);
            return;
        }
        try {
            execute();
        } catch (Exception e) {
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        if (currentState != State.IDLE) {
            processTick();
        }
    }

    private void execute() {
        PlayerInventory inventory = mc.player.getInventory();

        tntMinecartSlot = findTNTMinecart(inventory);
        railSlot = findRail(inventory);
        bowSlot = findFlameBow(inventory);

        if (tntMinecartSlot == -1 || railSlot == -1 || bowSlot == -1) {
            setEnabled(false);
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            setEnabled(false);
            return;
        }

        targetHit = (BlockHitResult) hit;
        originalSlot = mc.player.getInventory().selectedSlot;
        originalPitch = mc.player.getPitch();
        originalYaw = mc.player.getYaw();
        actionTimer = 0;
        bowStarted = false;

        if (modeSetting.is("Pre-Rail")) {
            currentState = State.PLACING_RAIL;
        } else {
            currentState = State.INSTA_DRAWING_BOW;
        }
    }

    private void processTick() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            reset();
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        actionTimer++;

        try {
            if (modeSetting.is("Pre-Rail")) {
                processPreRailMode();
            } else {
                processInstaCartMode();
            }
        } catch (Exception ex) {
            reset();
        }
    }

    private void processPreRailMode() {
        switch (currentState) {
            case PLACING_RAIL:
                if (actionTimer == 1) {
                    selectSlot(railSlot);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, targetHit);
                    currentState = State.DRAWING_BOW;
                    actionTimer = 0;
                }
                break;

            case DRAWING_BOW:
                if (actionTimer == 1) {
                    selectSlot(bowSlot);
                    mc.options.useKey.setPressed(true);
                    bowStarted = true;
                    currentState = State.SHOOTING;
                    actionTimer = 0;
                }
                break;

            case SHOOTING:
                if (actionTimer == 5) {
                    mc.player.setPitch(mc.player.getPitch() - 12.0f);
                } else if (actionTimer == 6) {
                    if (bowStarted) {
                        mc.options.useKey.setPressed(false);
                        bowStarted = false;
                    }
                } else if (actionTimer >= 7) {
                    mc.player.setPitch(originalPitch);
                    mc.player.setYaw(originalYaw);
                    currentState = State.PLACING_MINECART;
                    actionTimer = 0;
                }
                break;

            case PLACING_MINECART:
                if (actionTimer == 1) {
                    selectSlot(tntMinecartSlot);
                    mc.options.useKey.setPressed(true);
                } else if (actionTimer == 2) {
                    mc.options.useKey.setPressed(false);
                    currentState = State.DONE;
                    actionTimer = 0;
                }
                break;

            case DONE:
                if (actionTimer >= 1) {
                    selectSlot(originalSlot);
                    reset();
                    setEnabled(false);
                }
                break;
        }
    }

    private void processInstaCartMode() {
        switch (currentState) {
            case INSTA_DRAWING_BOW:
                if (actionTimer == 1) {
                    selectSlot(bowSlot);
                    mc.options.useKey.setPressed(true);
                    bowStarted = true;
                    currentState = State.INSTA_SHOOTING;
                    actionTimer = 0;
                }
                break;

            case INSTA_SHOOTING:
                if (actionTimer == 5) {
                    mc.player.setPitch(mc.player.getPitch() - 12.0f);
                } else if (actionTimer == 6) {
                    if (bowStarted) {
                        mc.options.useKey.setPressed(false);
                        bowStarted = false;
                    }
                } else if (actionTimer >= 7) {
                    mc.player.setPitch(originalPitch);
                    mc.player.setYaw(originalYaw);
                    currentState = State.INSTA_PLACING_RAIL;
                    actionTimer = 0;
                }
                break;

            case INSTA_PLACING_RAIL:
                if (actionTimer == 1) {
                    selectSlot(railSlot);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, targetHit);
                    currentState = State.INSTA_PLACING_MINECART;
                    actionTimer = 0;
                }
                break;

            case INSTA_PLACING_MINECART:
                if (actionTimer == 1) {
                    selectSlot(tntMinecartSlot);
                    mc.options.useKey.setPressed(true);
                } else if (actionTimer == 2) {
                    mc.options.useKey.setPressed(false);
                    currentState = State.DONE;
                    actionTimer = 0;
                }
                break;

            case DONE:
                if (actionTimer >= 1) {
                    selectSlot(originalSlot);
                    reset();
                    setEnabled(false);
                }
                break;
        }
    }

    private void reset() {
        if (bowStarted && mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        currentState = State.IDLE;
        actionTimer = 0;
        bowStarted = false;
        targetHit = null;
        originalPitch = 0;
        originalYaw = 0;
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        mc.interactionManager.syncSelectedSlot();
    }

    private int findTNTMinecart(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == Items.TNT_MINECART) {
                return i;
            }
        }
        return -1;
    }

    private int findRail(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            if (itemId.contains("rail")) {
                return i;
            }
        }
        return -1;
    }

    private int findFlameBow(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == Items.BOW) {
                ItemEnchantmentsComponent enchantments = stack.getOrDefault(
                        DataComponentTypes.ENCHANTMENTS,
                        ItemEnchantmentsComponent.DEFAULT
                );

                if (!enchantments.isEmpty()) {
                    RegistryEntry<Enchantment> flame = mc.world.getRegistryManager()
                            .getOptional(RegistryKeys.ENCHANTMENT).get()
                            .getEntry(Enchantments.FLAME.getValue()).orElseThrow();

                    if (EnchantmentHelper.getLevel(flame, stack) > 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }
}
