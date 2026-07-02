package tech.onetap.util.math;

import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;
import tech.onetap.util.IMinecraft;

@UtilityClass
public class CrystalDamageCalculator implements IMinecraft {
    public float calculateCrystalDamage(Entity crystal, LivingEntity target, double extraResistance) {
        if (mc.player.getOffHandStack().getItem() != Items.PLAYER_HEAD) extraResistance = 0;

        double explosionRadius = 12.0;
        double dx = target.getX() - crystal.getX();
        double dy = target.getEyeY() - crystal.getY();
        double dz = target.getZ() - crystal.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz) / explosionRadius;
        if (distance > 1.0) return 0.0F;

        Vec3d vec = new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
        float exposure = ExplosionImpl.calculateReceivedDamage(vec, target);

        double impact = (1.0 - distance) * exposure;
        float rawDamage = (float) (((impact * impact + impact) * (mc.player.getY() <= crystal.getY() ? 3.7 : 3.35f)) * 7 * 2.0);

        float finalDamage = applyBlastReduction(target, rawDamage, extraResistance);

        return switch (target.getWorld().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> finalDamage * 0.5f;
            case HARD -> finalDamage * 1.5f;
            default -> finalDamage;
        };
    }

    private float applyBlastReduction(LivingEntity entity, double damage, double extraResistance) {
        if (!(entity instanceof PlayerEntity player)) return (float) damage;

        double toughness = player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        double armor = player.getArmor();

        double factor = 2.0 + toughness / 4.0;
        double armorReduction = MathHelper.clamp(armor - damage / factor, armor * 0.2, 20.0);
        damage *= (1.0 - armorReduction / 25.0);

        float protectionAmount = 0;
        for (ItemStack stack : player.getArmorItems()) {
            RegistryEntry<Enchantment> protection = MinecraftClient.getInstance().world.getRegistryManager()
                    .getOptional(RegistryKeys.ENCHANTMENT).get().getEntry(Enchantments.PROTECTION.getValue()).orElseThrow();
            protectionAmount += EnchantmentHelper.getLevel(protection, stack);
        }
        float prot = protectionAmount;

        if (prot > 0) {
            damage *= 1.0 - Math.min(20, prot) / 25.0;
        }

        if (player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int level = player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier();
            damage *= 1.0 - (level + 1) * 0.2;
        }

        damage *= 1.0 - extraResistance;

        return (float) Math.max(damage, 0.0);
    }
}