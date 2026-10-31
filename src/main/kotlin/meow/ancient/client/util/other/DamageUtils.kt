package meow.ancient.client.util.other

import net.minecraft.core.component.DataComponents
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.KineticWeapon
import net.minecraft.world.item.component.PiercingWeapon
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.Vec3
import kotlin.math.floor
import kotlin.math.max

object DamageUtils {
    fun getSpearDamage(attacker: Player, target: LivingEntity, spear: ItemStack, movement: Vec3, direction: Vec3, ticksUsed: Int): Float {
        val kinetic = spear.get(DataComponents.KINETIC_WEAPON) ?: return 0f
        if (kinetic.damageConditions().isEmpty || ticksUsed < kinetic.delayTicks()) return 0f
        if (!PiercingWeapon.canHitEntity(attacker, target) || target.isDeadOrDying || target.isBlocking || target.hurtTime > 0) return 0f
        if (target is Player && (target.isCreative || target.isSpectator)) return 0f
        if (attacker.wasRecentlyStabbed(target, kinetic.contactCooldownTicks())) return 0f

        val attackerSpeed = direction.dot(movement) * 20.0
        val relativeSpeed = max(0.0, attackerSpeed - direction.dot(KineticWeapon.getMotion(target)))
        if (!attackerSpeed.isFinite() || !relativeSpeed.isFinite()
            || !kinetic.damageConditions().get().test(ticksUsed - kinetic.delayTicks(), attackerSpeed, relativeSpeed, 1.0)) return 0f

        var damage = (attacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + floor(relativeSpeed * kinetic.damageMultiplier())).toFloat()
        val enchantmentMap = attacker.weaponItem.enchantments
        for (entry in enchantmentMap.entrySet()) {
            val enchantment = entry.key
            val level = entry.intValue
            if (enchantment.`is`(Enchantments.SHARPNESS)) damage += 1f + 0.5f * (level - 1)
            else if (enchantment.`is`(Enchantments.SMITE) && target.typeHolder().`is`(EntityTypeTags.SENSITIVE_TO_SMITE)) damage += 2.5f * level
            else if (enchantment.`is`(Enchantments.BANE_OF_ARTHROPODS) && target.typeHolder().`is`(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) damage += 2.5f * level
            else if (enchantment.`is`(Enchantments.IMPALING) && target.typeHolder().`is`(EntityTypeTags.SENSITIVE_TO_IMPALING)) damage += 2.5f * level
        }

        val armor = floor(target.getAttributeValue(Attributes.ARMOR)).toFloat()
        val toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
        val source = spear.getDamageSource(attacker)
        var reduced = CombatRules.getDamageAfterAbsorb(target, damage, source, armor, toughness)

        val resistance = target.getEffect(MobEffects.RESISTANCE)
        if (resistance != null) {
            val lvl = resistance.amplifier + 1
            reduced *= (1f - lvl * 0.2f)
        }

        val health = max(reduced, 0f)
        return if (health.isFinite()) health else 0f
    }
}
