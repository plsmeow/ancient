package meow.ancient.client.util.inventory

enum class InventoryActionSource {
    HOTBAR,
    INVENTORY;

    companion object {
        fun fromInventorySlot(slot: Int): InventoryActionSource =
            if (slot in 0..8) HOTBAR else INVENTORY
    }
}
