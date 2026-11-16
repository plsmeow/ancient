package meow.ancient.module.modules.player

import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.InventoryAutomation
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import sweetie.evaware.flora.api.Commando

object AutoArmorModule : Module("AutoArmor", PLAYER, "Equips better armor without losing the old equipment") {
    private val keepElytra by boolean("Keep Elytra", true)
    private val delay by number("Delay", 250.0, 0.0, 2000.0, 50.0, 0, unit = "ms")
    private var next = 0L
    override fun onDisable() { InventoryAutomation.cancel(this); next = 0L }
    @Commando fun onDisconnect(event: ClientDisconnectEvent) = onDisable()

    private fun score(stack: ItemStack, equipment: EquipmentSlot): Double {
        if (stack.isEmpty || stack.isBroken || stack.get(DataComponents.EQUIPPABLE)?.slot() != equipment) return -1.0
        if (stack.`is`(Items.ELYTRA)) return -1.0
        val modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
        val armor = modifiers.compute(Attributes.ARMOR, 0.0, equipment)
        if (armor <= 0.0) return -1.0
        val protection = mc.level?.registryAccess()?.lookup(Registries.ENCHANTMENT)?.orElse(null)
            ?.get(Enchantments.PROTECTION)?.orElse(null)
        val level = protection?.let { EnchantmentHelper.getItemEnchantmentLevel(it, stack) } ?: 0
        val toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, equipment)
        val durability = if (stack.maxDamage > 0) (stack.maxDamage - stack.damageValue).toDouble() / stack.maxDamage else 1.0
        return armor * 100.0 + level * 60.0 + toughness * 10.0 + durability
    }

    @Commando(priority = 30) fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        if (System.nanoTime() < next || InventoryAutomation.busy() || !InventoryAutomation.available()) return
        val slots = listOf(EquipmentSlot.HEAD to 5, EquipmentSlot.CHEST to 6, EquipmentSlot.LEGS to 7, EquipmentSlot.FEET to 8)
        for ((equipment, menuSlot) in slots) {
            val equipped = player.getItemBySlot(equipment)
            if (equipment == EquipmentSlot.CHEST && keepElytra && (equipped.`is`(Items.ELYTRA) || player.isFallFlying)) continue
            val targetSlot = player.inventoryMenu.getSlot(menuSlot)
            if (!targetSlot.mayPickup(player)) continue
            val held = SilentSwapUtil.debugSnapshot().holdTicks > 0 || PacketMineModule.hasPendingAction()
            val source = (0..35).filter {
                (!held || it != SilentSwapUtil.serverSlot) && targetSlot.mayPlace(player.inventory.getItem(it))
                    && player.inventory.getItem(it).count == 1
                    && score(player.inventory.getItem(it), equipment) > score(equipped, equipment) + 0.01
            }.maxByOrNull { score(player.inventory.getItem(it), equipment) } ?: continue
            val bridge = (8 downTo 0).firstOrNull { !held || it != SilentSwapUtil.serverSlot } ?: return
            val expectedSource = player.inventory.getItem(source).copy()
            val expectedArmor = equipped.copy()
            val expectedBridge = player.inventory.getItem(bridge).copy()
            if (InventoryAutomation.submit(this, {
                    InventoryAutomation.matches(source, expectedSource) && InventoryAutomation.matches(bridge, expectedBridge)
                        && ItemStack.matches(player.getItemBySlot(equipment), expectedArmor)
                        && (source !in 0..8 || InventoryAutomation.hotbarAvailable(source))
                        && (source in 0..8 || InventoryAutomation.hotbarAvailable(bridge))
                        && targetSlot.mayPickup(player) && targetSlot.mayPlace(expectedSource)
                }, { InventoryAutomation.equip(source, menuSlot, bridge) })) next = System.nanoTime() + (delay * 1e6).toLong()
            return
        }
    }
}
