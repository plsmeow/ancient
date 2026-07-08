package tech.onetap.util.math;

import lombok.experimental.UtilityClass;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import tech.onetap.util.IMinecraft;

@UtilityClass
public class CrystalDamageCalculator implements IMinecraft {
    private final float EXPLOSION_POWER = 6.0f;
    private final float TERRAIN_IGNORE_BLAST_RESISTANCE = 600.0f;
    private final float ANCHOR_EXPLOSION_POWER = 5.0f;

    public float calculateCrystalDamage(Entity crystal, LivingEntity target, double extraResistance, boolean ignoreTerrain) {
        return calculateExplosionDamage(crystal.getPos(), EXPLOSION_POWER, target, extraResistance, ignoreTerrain);
    }

    public float calculateCrystalDamage(Entity crystal, LivingEntity target, double extraResistance) {
        return calculateCrystalDamage(crystal, target, extraResistance, false);
    }

    public float calculateAnchorDamage(Vec3d explosionPos, LivingEntity target, double extraResistance, boolean ignoreTerrain) {
        return calculateExplosionDamage(explosionPos, ANCHOR_EXPLOSION_POWER, target, extraResistance, ignoreTerrain);
    }

    public float calculateExplosionDamage(Vec3d explosionPos, float explosionPower, LivingEntity target, double extraResistance, boolean ignoreTerrain) {
        if (mc.player.getOffHandStack().getItem() != Items.PLAYER_HEAD) extraResistance = 0;

        double explosionRadius = explosionPower * 2.0f;
        Vec3d targetPos = target.getBoundingBox().getCenter();

        double dx = targetPos.x - explosionPos.x;
        double dy = targetPos.y - explosionPos.y;
        double dz = targetPos.z - explosionPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz) / explosionRadius;
        if (distance > 1.0) return 0.0F;

        float exposure = getExposure(explosionPos, target, ignoreTerrain);
        double impact = (1.0 - distance) * exposure;
        float rawDamage = (float) ((impact * impact + impact) * 3.5 * explosionRadius + 1.0);
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

        float prot = getExplosionProtection(player, entity.getWorld());

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

    private float getExplosionProtection(PlayerEntity player, World world) {
        RegistryEntry<Enchantment> protection = world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).orElseThrow()
                .getEntry(Enchantments.PROTECTION.getValue()).orElseThrow();
        RegistryEntry<Enchantment> blastProtection = world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).orElseThrow()
                .getEntry(Enchantments.BLAST_PROTECTION.getValue()).orElseThrow();

        float protectionAmount = 0;
        for (var stack : player.getArmorItems()) {
            protectionAmount += EnchantmentHelper.getLevel(protection, stack);
            protectionAmount += EnchantmentHelper.getLevel(blastProtection, stack) * 2;
        }

        return Math.min(20, protectionAmount);
    }

    private float getExposure(Vec3d source, Entity entity, boolean ignoreTerrain) {
        Box box = entity.getBoundingBox();
        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);

        if (stepX <= 0.0 || stepY <= 0.0 || stepZ <= 0.0) {
            return 0.0f;
        }

        int totalSamples = 0;
        int visibleSamples = 0;

        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    double sampleX = MathHelper.lerp(x, box.minX, box.maxX);
                    double sampleY = MathHelper.lerp(y, box.minY, box.maxY);
                    double sampleZ = MathHelper.lerp(z, box.minZ, box.maxZ);
                    Vec3d sample = new Vec3d(sampleX, sampleY, sampleZ);

                    totalSamples++;
                    if (isExposureRayOpen(source, sample, entity, ignoreTerrain)) {
                        visibleSamples++;
                    }
                }
            }
        }

        if (totalSamples == 0) return 0.0f;
        return (float) visibleSamples / (float) totalSamples;
    }

    private boolean isExposureRayOpen(Vec3d source, Vec3d target, Entity entity, boolean ignoreTerrain) {
        if (!ignoreTerrain) {
            BlockHitResult hit = entity.getWorld().raycast(new RaycastContext(
                    source,
                    target,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    entity
            ));
            return hit.getType() == HitResult.Type.MISS;
        }

        Vec3d delta = target.subtract(source);
        double length = delta.length();
        if (length <= 1.0E-7) return true;

        Vec3d step = delta.normalize().multiply(0.15);
        Vec3d current = source;
        int steps = Math.max(1, MathHelper.ceil(length / 0.15));

        for (int i = 0; i <= steps; i++) {
            BlockPos pos = BlockPos.ofFloored(current);
            BlockState state = entity.getWorld().getBlockState(pos);

            if (blocksExplosionRay(entity.getWorld(), pos, state, ignoreTerrain)) {
                return false;
            }

            current = current.add(step);
        }

        return true;
    }

    private boolean blocksExplosionRay(World world, BlockPos pos, BlockState state, boolean ignoreTerrain) {
        if (state.isAir()) return false;
        if (ignoreTerrain && isBlastProofBlockingBlock(state)) return true;
        if (state.getCollisionShape(world, pos).isEmpty()) return false;
        if (ignoreTerrain && shouldIgnoreTerrainBlock(state)) return false;
        return true;
    }

    private boolean isBlastProofBlockingBlock(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN)
                || state.isOf(Blocks.CRYING_OBSIDIAN)
                || state.isOf(Blocks.BEDROCK)
                || state.isOf(Blocks.RESPAWN_ANCHOR)
                || state.isOf(Blocks.ENDER_CHEST)
                || state.isOf(Blocks.ENCHANTING_TABLE)
                || state.isOf(Blocks.REINFORCED_DEEPSLATE)
                || state.isOf(Blocks.END_PORTAL_FRAME)
                || state.isOf(Blocks.COMMAND_BLOCK)
                || state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
                || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
                || state.isOf(Blocks.STRUCTURE_BLOCK)
                || state.isOf(Blocks.JIGSAW)
                || state.isOf(Blocks.BARRIER)
                || state.isOf(Blocks.LIGHT);
    }

    private boolean shouldIgnoreTerrainBlock(BlockState state) {
        if (isBlastProofBlockingBlock(state)) {
            return false;
        }

        return state.getBlock().getBlastResistance() < TERRAIN_IGNORE_BLAST_RESISTANCE;
    }
}
