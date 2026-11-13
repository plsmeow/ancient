package meow.ancient.module.modules.player

import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.effect.MobEffectUtil
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

internal object PacketMineToolSpeed {
    fun snapshotValue(snapshot: ClientboundUpdateAttributesPacket.AttributeSnapshot): Double {
        val attribute = AttributeInstance(snapshot.attribute()) { }
        attribute.baseValue = snapshot.base()
        snapshot.modifiers().forEach { attribute.addOrUpdateTransientModifier(it) }
        return attribute.value
    }

    fun attributeValue(actual: AttributeInstance, attribute: Holder<Attribute>, held: ItemStack, tool: ItemStack): Double {
        val simulated = AttributeInstance(attribute) { }
        simulated.replaceFrom(actual)
        held.forEachModifier(EquipmentSlot.MAINHAND) { type, modifier ->
            if (type == attribute) simulated.removeModifier(modifier.id())
        }
        tool.forEachModifier(EquipmentSlot.MAINHAND) { type, modifier ->
            if (type == attribute) simulated.addOrUpdateTransientModifier(modifier)
        }
        return simulated.value
    }

    fun score(stack: ItemStack, state: BlockState, efficiency: Double, breakSpeed: Double): Double {
        var speed = stack.getDestroySpeed(state).toDouble()
        if (speed > 1.0) speed += efficiency
        speed *= breakSpeed
        val correct = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state)
        return (speed / if (correct) 30.0 else 100.0).takeIf { it.isFinite() } ?: 0.0
    }

    fun progress(player: LocalPlayer, stack: ItemStack, state: BlockState, hardness: Float,
        serverEfficiency: Double, serverBreakSpeed: Double): Float {
        if (hardness < 0f) return 0f
        if (hardness == 0f) return 1f
        fun attribute(type: Holder<Attribute>): Double {
            val actual = player.getAttribute(type) ?: return type.value().defaultValue
            return attributeValue(actual, type, player.mainHandItem, stack)
        }
        // Player.getDestroySpeed, using the packet-selected stack without changing client equipment.
        var delta = score(stack, state, serverEfficiency, serverBreakSpeed) / hardness
        if (MobEffectUtil.hasDigSpeed(player)) delta *= 1.0 + (MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2
        player.getEffect(MobEffects.MINING_FATIGUE)?.let { delta *= Math.pow(0.3, (it.amplifier + 1).toDouble()) }
        if (player.isEyeInFluid(FluidTags.WATER)) delta *= attribute(Attributes.SUBMERGED_MINING_SPEED)
        if (!player.onGround()) delta /= 5.0
        return delta.toFloat().takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    }
}
