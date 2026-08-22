package tech.onetap.util.math;

import lombok.experimental.UtilityClass;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import tech.onetap.util.IMinecraft;

@UtilityClass
public class CrystalDamageCalculator implements IMinecraft {
    public final float CRYSTAL_POWER = 6.0f;
    public final float ANCHOR_POWER = 5.0f;
    public final float NO_PROTECTION_OVERRIDE = -1.0f;
    public final float BEST_ARMOR_PROTECTION = 32.0f;

    private final float TERRAIN_BLAST_RESISTANCE = 600.0f;

    private World cachedWorld;
    private RegistryEntry<Enchantment> protectionEntry;
    private RegistryEntry<Enchantment> blastProtectionEntry;

    public float crystalDamage(Vec3d crystalPos, LivingEntity target, boolean ignoreTerrain) {
        if (target == null) return 0.0f;
        return explosionDamage(crystalPos, CRYSTAL_POWER, target, target.getBoundingBox(), ignoreTerrain, NO_PROTECTION_OVERRIDE);
    }

    public float crystalDamage(Vec3d crystalPos, LivingEntity target, Box targetBox, boolean ignoreTerrain, float protectionOverride) {
        return explosionDamage(crystalPos, CRYSTAL_POWER, target, targetBox, ignoreTerrain, protectionOverride);
    }

    public float anchorDamage(Vec3d explosionPos, LivingEntity target, boolean ignoreTerrain) {
        if (target == null) return 0.0f;
        return explosionDamage(explosionPos, ANCHOR_POWER, target, target.getBoundingBox(), ignoreTerrain, NO_PROTECTION_OVERRIDE);
    }

    public float anchorDamage(Vec3d explosionPos, LivingEntity target, Box targetBox, boolean ignoreTerrain, float protectionOverride) {
        return explosionDamage(explosionPos, ANCHOR_POWER, target, targetBox, ignoreTerrain, protectionOverride);
    }

    /**
     * Ванильный расчёт урона от взрыва (1.21.4).
     * Порядок операций повторяет ExplosionImpl#damageEntities -> PlayerEntity#damage ->
     * LivingEntity#applyArmorToDamage -> LivingEntity#modifyAppliedDamage.
     */
    public float explosionDamage(Vec3d explosionPos, float power, LivingEntity target, Box targetBox, boolean ignoreTerrain, float protectionOverride) {
        if (target == null || mc.world == null || targetBox == null) return 0.0f;

        Difficulty difficulty = mc.world.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL) return 0.0f;
        if (target.isInvulnerable()) return 0.0f;
        if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) return 0.0f;

        double radius = power * 2.0;

        double centerX = (targetBox.minX + targetBox.maxX) * 0.5;
        double centerZ = (targetBox.minZ + targetBox.maxZ) * 0.5;
        double dx = centerX - explosionPos.x;
        double dy = targetBox.minY - explosionPos.y;
        double dz = centerZ - explosionPos.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;
        if (distance > 1.0) return 0.0f;

        float exposure = exposure(explosionPos, targetBox, ignoreTerrain);
        if (exposure <= 0.0f) return 0.0f;

        double impact = (1.0 - distance) * exposure;
        float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0);

        damage = switch (difficulty) {
            case EASY -> Math.min(damage / 2.0f + 1.0f, damage);
            case HARD -> damage * 3.0f / 2.0f;
            default -> damage;
        };

        damage = applyArmor(target, damage);
        damage = applyResistance(target, damage);
        if (damage <= 0.0f) return 0.0f;

        float protection = protectionOverride >= 0.0f ? protectionOverride : protectionAmount(target);
        if (protection > 0.0f) damage *= 1.0f - MathHelper.clamp(protection, 0.0f, 20.0f) / 25.0f;

        return Math.max(damage, 0.0f);
    }

    /** DamageUtil#getDamageLeft: на клиенте ветка с getArmorEffectiveness недоступна, эффект брони чистый. */
    private float applyArmor(LivingEntity target, float damage) {
        float armor = target.getArmor();
        float toughness = (float) target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);

        float factor = 2.0f + toughness / 4.0f;
        float reduction = MathHelper.clamp(armor - damage / factor, armor * 0.2f, 20.0f);

        return damage * (1.0f - reduction / 25.0f);
    }

    private float applyResistance(LivingEntity target, float damage) {
        StatusEffectInstance resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance == null) return damage;

        int multiplier = 25 - (resistance.getAmplifier() + 1) * 5;
        return Math.max(damage * multiplier / 25.0f, 0.0f);
    }

    /** EnchantmentHelper#getProtectionAmount для damage_type is_explosion: protection + 2 * blast_protection. */
    public float protectionAmount(LivingEntity target) {
        if (!resolveEnchantments()) return 0.0f;

        float amount = 0.0f;
        for (ItemStack stack : target.getArmorItems()) {
            if (stack.isEmpty()) continue;
            amount += EnchantmentHelper.getLevel(protectionEntry, stack);
            amount += EnchantmentHelper.getLevel(blastProtectionEntry, stack) * 2.0f;
        }

        return Math.min(amount, 20.0f);
    }

    private boolean resolveEnchantments() {
        if (mc.world == null) return false;
        if (cachedWorld == mc.world && protectionEntry != null && blastProtectionEntry != null) return true;

        var registry = mc.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
        if (registry == null) return false;

        var protection = registry.getEntry(Enchantments.PROTECTION.getValue()).orElse(null);
        var blastProtection = registry.getEntry(Enchantments.BLAST_PROTECTION.getValue()).orElse(null);
        if (protection == null || blastProtection == null) return false;

        cachedWorld = mc.world;
        protectionEntry = protection;
        blastProtectionEntry = blastProtection;
        return true;
    }

    /** ExplosionImpl#calculateReceivedDamage: сетка сэмплов по хитбоксу с ванильными смещениями. */
    public float exposure(Vec3d explosionPos, Box box, boolean ignoreTerrain) {
        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) return 0.0f;

        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;

        int misses = 0;
        int total = 0;

        for (double x = 0.0; x <= 1.0; x += stepX) {
            double sampleX = MathHelper.lerp(x, box.minX, box.maxX) + offsetX;
            for (double y = 0.0; y <= 1.0; y += stepY) {
                double sampleY = MathHelper.lerp(y, box.minY, box.maxY);
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    double sampleZ = MathHelper.lerp(z, box.minZ, box.maxZ) + offsetZ;

                    total++;
                    if (raycast(new Vec3d(sampleX, sampleY, sampleZ), explosionPos, ignoreTerrain) == HitResult.Type.MISS) {
                        misses++;
                    }
                }
            }
        }

        return total == 0 ? 0.0f : (float) misses / (float) total;
    }

    /**
     * Луч по коллизиям без аллокации RaycastContext.
     * ignoreTerrain пропускает всё, кроме взрывоустойчивых блоков (обсидиан, бедрок, анкор и т.п.).
     */
    public HitResult.Type raycast(Vec3d start, Vec3d end, boolean ignoreTerrain) {
        if (mc.world == null) return HitResult.Type.MISS;

        return BlockView.raycast(start, end, null, (context, pos) -> {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir()) return null;
            if (ignoreTerrain && state.getBlock().getBlastResistance() < TERRAIN_BLAST_RESISTANCE) return null;

            BlockHitResult hit = state.getCollisionShape(mc.world, pos).raycast(start, end, pos);
            return hit == null ? null : hit.getType();
        }, context -> HitResult.Type.MISS);
    }
}
