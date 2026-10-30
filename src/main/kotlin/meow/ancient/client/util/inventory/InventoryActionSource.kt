package meow.ancient.client.util.inventory

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.AbstractContainerMenu

enum class InventoryActionSource {
    HOTBAR,
    INVENTORY;

    companion object {
        fun fromInventorySlot(slot: Int): InventoryActionSource =
            if (slot in 0..8) HOTBAR else INVENTORY

        fun fromMenuSlot(
            player: LocalPlayer,
            menu: AbstractContainerMenu,
            menuSlot: Int,
            hotbarButton: Int? = null
        ): InventoryActionSource {
            val slot = menu.slots.getOrNull(menuSlot) ?: return INVENTORY
            if (slot.container === player.inventory && slot.containerSlot in 0..8) return HOTBAR
            if (hotbarButton != null && hotbarButton in 0..8 && slot.container === player.inventory
                && (slot.containerSlot == EquipmentSlot.CHEST.getIndex(36) || slot.containerSlot == 40)) return HOTBAR
            return INVENTORY
        }
    }
}
