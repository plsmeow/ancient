package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInformation(moduleName = "Kill Effect", moduleDesc = "Визуальный эффект при убийстве цели", moduleCategory = ModuleCategory.RENDER)
public class KillEffect extends Module {

    private final ModeSetting effect = new ModeSetting("Эффект", "Lightning",
            "Lightning", "Explosion", "Blood Rain", "Disintegrate", "Black Hole", "Shockwave", 
            "Ascension", "Tornado", "Meteor", "Orbital Strike", "Phoenix Rise", "Pig Bomb", 
            "Shatter", "Thunder Clap", "Crown Drop", "Rainbow Burst", "Soul Ascend", "Firework Burst");

    private final List<AnimatedEffect> activeEffects = new ArrayList<>();

    private Entity entity;

    @Subscribe
    private void onAttack(EventAttack e) {
        if (e.getEntity() instanceof PlayerEntity) entity = e.getEntity();
    }

    @Subscribe
    public void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (entity != null && entity.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
            playKillEffect(entity);
            entity = null;
        }

        updateAnimatedEffects();
    }

    private void updateAnimatedEffects() {
        Iterator<AnimatedEffect> iterator = activeEffects.iterator();
        while (iterator.hasNext()) {
            AnimatedEffect fx = iterator.next();
            fx.tick();
            if (fx.isFinished()) {
                iterator.remove();
            }
        }
    }

    private void playKillEffect(Entity entity) {
        if (entity == null) return;

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double centerY = y + entity.getHeight() / 2;
        double height = entity.getHeight();

        switch (effect.getValue()) {
            case "Lightning" -> playLightning(x, y, z);
            case "Explosion" -> playExplosion(x, centerY, z);
            case "Blood Rain" -> activeEffects.add(new BloodRainEffect(x, y, z, height));
            case "Disintegrate" -> activeEffects.add(new DisintegrateEffect(x, y, z, height));
            case "Black Hole" -> activeEffects.add(new BlackHoleEffect(x, y, z, height));
            case "Shockwave" -> activeEffects.add(new ShockwaveEffect(x, y, z));
            case "Ascension" -> activeEffects.add(new AscensionEffect(x, y, z, height));
            case "Tornado" -> activeEffects.add(new TornadoEffect(x, y, z, height));
            case "Meteor" -> activeEffects.add(new MeteorEffect(x, y, z));
            case "Orbital Strike" -> activeEffects.add(new OrbitalStrikeEffect(x, y, z, height));
            case "Phoenix Rise" -> activeEffects.add(new PhoenixRiseEffect(x, y, z, height));
            case "Pig Bomb" -> activeEffects.add(new PigBombEffect(x, y, z));
            case "Shatter" -> activeEffects.add(new ShatterEffect(x, y, z, height));
            case "Thunder Clap" -> activeEffects.add(new ThunderClapEffect(x, y, z, height));
            case "Crown Drop" -> activeEffects.add(new CrownDropEffect(x, y, z, height));
            case "Rainbow Burst" -> activeEffects.add(new RainbowBurstEffect(x, y, z, height));
            case "Soul Ascend" -> activeEffects.add(new SoulAscendEffect(x, y, z, height));
            case "Firework Burst" -> activeEffects.add(new FireworkBurstEffect(x, y, z, height));
        }
    }

    private void playLightning(double x, double y, double z) {
        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
        lightning.refreshPositionAfterTeleport(x, y, z);
        lightning.setCosmetic(true);
        mc.world.addEntity(lightning);
        mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 5.0f, 1.0f);
    }

    private void playExplosion(double x, double y, double z) {
        for (int i = 0; i < 20; i++) {
            double ox = (mc.world.random.nextDouble() - 0.5) * 2;
            double oy = (mc.world.random.nextDouble() - 0.5) * 2;
            double oz = (mc.world.random.nextDouble() - 0.5) * 2;
            mc.world.addParticle(ParticleTypes.EXPLOSION, x + ox, y + oy, z + oz, ox * 0.1, oy * 0.1, oz * 0.1);
        }
        mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 10.0f, 1.0f);
    }

    private abstract class AnimatedEffect {
        protected double x, y, z;
        protected int ticks = 0;
        protected int maxTicks;

        AnimatedEffect(double x, double y, double z, int maxTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxTicks = maxTicks;
        }

        abstract void tick();

        boolean isFinished() {
            return ticks >= maxTicks;
        }
    }

    private class FlameSpiralEffect extends AnimatedEffect {
        private final double height;

        FlameSpiralEffect(double x, double y, double z, double height) {
            super(x, y, z, 30);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 2.0f, 1.0f);
        }

        @Override
        void tick() {
            ticks++;
            double progress = (double) ticks / maxTicks;
            double currentHeight = progress * (height + 2);
            double radius = 0.8 - progress * 0.3;

            for (int spiral = 0; spiral < 2; spiral++) {
                double baseAngle = (ticks * 0.5) + (spiral * Math.PI);
                for (int i = 0; i < 3; i++) {
                    double angle = baseAngle + i * 0.3;
                    double px = x + Math.cos(angle) * radius;
                    double pz = z + Math.sin(angle) * radius;
                    double py = y + currentHeight - i * 0.2;
                    mc.world.addParticle(ParticleTypes.FLAME, px, py, pz, 0, 0.02, 0);
                    if (i == 0) mc.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0, 0.01, 0);
                }
            }
            if (ticks % 3 == 0) {
                double angle = mc.world.random.nextDouble() * Math.PI * 2;
                mc.world.addParticle(ParticleTypes.LAVA, x + Math.cos(angle) * 0.5, y + currentHeight, z + Math.sin(angle) * 0.5, 0, 0, 0);
            }
        }
    }

    private class SoulEffect extends AnimatedEffect {
        private final double height;

        SoulEffect(double x, double y, double z, double height) {
            super(x, y, z, 40);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 2.0f, 0.8f);
        }

        @Override
        void tick() {
            ticks++;
            if (ticks < 25) {
                for (int i = 0; i < 3; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.8;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.8;
                    double oy = mc.world.random.nextDouble() * height;
                    mc.world.addParticle(ParticleTypes.SOUL, x + ox, y + oy, z + oz,
                            (mc.world.random.nextDouble() - 0.5) * 0.02, 0.08, (mc.world.random.nextDouble() - 0.5) * 0.02);
                }
            }
            if (ticks % 2 == 0 && ticks < 30) {
                double angle = mc.world.random.nextDouble() * Math.PI * 2;
                double r = mc.world.random.nextDouble() * 0.6;
                mc.world.addParticle(ParticleTypes.SCULK_SOUL, x + Math.cos(angle) * r, y + height * 0.5, z + Math.sin(angle) * r, 0, 0.1, 0);
            }
            if (ticks == 25) {
                for (int i = 0; i < 10; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    mc.world.addParticle(ParticleTypes.SOUL, x, y + height + 1, z, Math.cos(angle) * 0.1, 0.15, Math.sin(angle) * 0.1);
                }
            }
        }
    }

    private class BloodRainEffect extends AnimatedEffect {
        private final double height;

        BloodRainEffect(double x, double y, double z, double height) {
            super(x, y, z, 50);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 2.0f, 0.5f);
        }

        @Override
        void tick() {
            ticks++;
            if (ticks < 40) {
                for (int i = 0; i < 8; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 3;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 3;
                    double startY = y + height + 3 + mc.world.random.nextDouble() * 2;
                    mc.world.addParticle(ParticleTypes.FALLING_DRIPSTONE_LAVA, x + ox, startY, z + oz, 0, -0.5, 0);
                    if (i % 2 == 0) {
                        mc.world.addParticle(ParticleTypes.DAMAGE_INDICATOR, x + ox, y + mc.world.random.nextDouble() * height, z + oz, 0, 0, 0);
                    }
                }
            }
            if (ticks % 10 == 0 && ticks < 35) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.BLOCKS, 1.0f, 0.5f);
            }
        }
    }

    private class DisintegrateEffect extends AnimatedEffect {
        private final double height;

        DisintegrateEffect(double x, double y, double z, double height) {
            super(x, y, z, 35);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 2.0f, 0.3f);
        }

        @Override
        void tick() {
            ticks++;
            double progress = (double) ticks / maxTicks;
            int particleCount = (int) (20 * (1 - progress * 0.5));

            for (int i = 0; i < particleCount; i++) {
                double ox = (mc.world.random.nextDouble() - 0.5) * 0.6;
                double oy = mc.world.random.nextDouble() * height;
                double oz = (mc.world.random.nextDouble() - 0.5) * 0.6;

                double speed = 0.1 + progress * 0.3;
                double vx = (mc.world.random.nextDouble() - 0.5) * speed;
                double vy = (mc.world.random.nextDouble() - 0.3) * speed;
                double vz = (mc.world.random.nextDouble() - 0.5) * speed;

                if (i % 3 == 0) {
                    mc.world.addParticle(ParticleTypes.ASH, x + ox, y + oy, z + oz, vx, vy, vz);
                } else if (i % 3 == 1) {
                    mc.world.addParticle(ParticleTypes.WHITE_ASH, x + ox, y + oy, z + oz, vx, vy, vz);
                } else {
                    mc.world.addParticle(ParticleTypes.SMOKE, x + ox, y + oy, z + oz, vx, vy, vz);
                }
            }

            if (ticks == maxTicks - 5) {
                for (int i = 0; i < 30; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double speed = 0.3 + mc.world.random.nextDouble() * 0.2;
                    mc.world.addParticle(ParticleTypes.POOF, x, y + height / 2, z,
                            Math.cos(angle) * speed, (mc.world.random.nextDouble() - 0.5) * 0.2, Math.sin(angle) * speed);
                }
            }
        }
    }

    private class BlackHoleEffect extends AnimatedEffect {
        private final double height;

        BlackHoleEffect(double x, double y, double z, double height) {
            super(x, y, z, 50);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 2.0f, 0.3f);
        }

        @Override
        void tick() {
            ticks++;
            double centerY = y + height / 2;

            if (ticks < 35) {
                double radius = 3.0 - (ticks * 0.07);
                for (int i = 0; i < 15; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double r = radius * (0.5 + mc.world.random.nextDouble() * 0.5);
                    double px = x + Math.cos(angle) * r;
                    double pz = z + Math.sin(angle) * r;
                    double py = centerY + (mc.world.random.nextDouble() - 0.5) * 2;

                    double vx = (x - px) * 0.1;
                    double vy = (centerY - py) * 0.1;
                    double vz = (z - pz) * 0.1;

                    if (i % 2 == 0) {
                        mc.world.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, vx, vy, vz);
                    } else {
                        mc.world.addParticle(ParticleTypes.SQUID_INK, px, py, pz, vx, vy, vz);
                    }
                }
                for (int i = 0; i < 5; i++) {
                    mc.world.addParticle(ParticleTypes.DRAGON_BREATH, x + (mc.world.random.nextDouble() - 0.5) * 0.3,
                            centerY + (mc.world.random.nextDouble() - 0.5) * 0.3, z + (mc.world.random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
                }
            }

            if (ticks == 36) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 3.0f, 0.5f);
                for (int i = 0; i < 50; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double pitch = (mc.world.random.nextDouble() - 0.5) * Math.PI;
                    double speed = 0.5 + mc.world.random.nextDouble() * 0.5;
                    double vx = Math.cos(angle) * Math.cos(pitch) * speed;
                    double vy = Math.sin(pitch) * speed;
                    double vz = Math.sin(angle) * Math.cos(pitch) * speed;
                    mc.world.addParticle(ParticleTypes.REVERSE_PORTAL, x, centerY, z, vx, vy, vz);
                    if (i % 3 == 0) {
                        mc.world.addParticle(ParticleTypes.EXPLOSION, x, centerY, z, vx * 0.5, vy * 0.5, vz * 0.5);
                    }
                }
            }
        }
    }

    private class ShockwaveEffect extends AnimatedEffect {
        ShockwaveEffect(double x, double y, double z) {
            super(x, y, z, 25);
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 2.0f, 1.5f);
        }

        @Override
        void tick() {
            ticks++;
            double radius = ticks * 0.4;
            int particles = 20 + ticks * 2;

            for (int i = 0; i < particles; i++) {
                double angle = (Math.PI * 2 / particles) * i;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;

                double vx = Math.cos(angle) * 0.1;
                double vz = Math.sin(angle) * 0.1;

                mc.world.addParticle(ParticleTypes.CLOUD, px, y + 0.1, pz, vx, 0.02, vz);
                if (i % 3 == 0) {
                    mc.world.addParticle(ParticleTypes.SWEEP_ATTACK, px, y + 0.5, pz, 0, 0, 0);
                }
            }

            if (ticks % 5 == 0) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.8f);
            }
        }
    }

    private class AscensionEffect extends AnimatedEffect {
        private final double height;

        AscensionEffect(double x, double y, double z, double height) {
            super(x, y, z, 50);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2.0f, 1.2f);
        }

        @Override
        void tick() {
            ticks++;
            double beamHeight = ticks * 0.5;

            for (int i = 0; i < 10; i++) {
                double py = y + (beamHeight * i / 10.0);
                if (py > y + 15) continue;

                double ox = (mc.world.random.nextDouble() - 0.5) * 0.3;
                double oz = (mc.world.random.nextDouble() - 0.5) * 0.3;
                mc.world.addParticle(ParticleTypes.END_ROD, x + ox, py, z + oz, 0, 0.05, 0);
            }

            if (ticks < 30) {
                double radius = 0.8;
                for (int i = 0; i < 8; i++) {
                    double angle = (Math.PI * 2 / 8) * i + ticks * 0.2;
                    double px = x + Math.cos(angle) * radius;
                    double pz = z + Math.sin(angle) * radius;
                    mc.world.addParticle(ParticleTypes.END_ROD, px, y + 0.1, pz, 0, 0.1, 0);
                }
            }

            if (ticks == 40) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 2.0f, 1.5f);
                for (int i = 0; i < 30; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double speed = 0.2 + mc.world.random.nextDouble() * 0.3;
                    mc.world.addParticle(ParticleTypes.END_ROD, x, y + 10, z,
                            Math.cos(angle) * speed, mc.world.random.nextDouble() * 0.2, Math.sin(angle) * speed);
                }
            }
        }
    }

    private class TornadoEffect extends AnimatedEffect {
        private final double height;

        TornadoEffect(double x, double y, double z, double height) {
            super(x, y, z, 45);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }

        @Override
        void tick() {
            ticks++;
            double tornadoHeight = Math.min(ticks * 0.3, 6);

            for (int layer = 0; layer < 10; layer++) {
                double layerY = y + (tornadoHeight * layer / 10.0);
                if (layerY > y + tornadoHeight) continue;

                double layerProgress = (double) layer / 10.0;
                double radius = 0.3 + layerProgress * 1.2;
                double rotationSpeed = 0.4 - layerProgress * 0.2;

                for (int i = 0; i < 4; i++) {
                    double angle = ticks * rotationSpeed + (Math.PI * 2 / 4) * i + layer * 0.3;
                    double px = x + Math.cos(angle) * radius;
                    double pz = z + Math.sin(angle) * radius;

                    double vx = Math.cos(angle + Math.PI / 2) * 0.1;
                    double vz = Math.sin(angle + Math.PI / 2) * 0.1;

                    if (layer % 2 == 0) {
                        mc.world.addParticle(ParticleTypes.CLOUD, px, layerY, pz, vx, 0.05, vz);
                    } else {
                        mc.world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, layerY, pz, vx, 0.03, vz);
                    }
                }
            }

            if (ticks % 3 == 0) {
                double angle = mc.world.random.nextDouble() * Math.PI * 2;
                mc.world.addParticle(ParticleTypes.CRIT, x + Math.cos(angle) * 1.5, y + 0.2, z + Math.sin(angle) * 1.5,
                        (x - (x + Math.cos(angle) * 1.5)) * 0.1, 0.2, (z - (z + Math.sin(angle) * 1.5)) * 0.1);
            }

            if (ticks % 15 == 0) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
        }
    }

    private class MeteorEffect extends AnimatedEffect {
        private double meteorY;
        private boolean impacted = false;

        MeteorEffect(double x, double y, double z) {
            super(x, y, z, 40);
            this.meteorY = y + 20;
            mc.world.playSound(mc.player, x, y + 10, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.AMBIENT, 2.0f, 0.5f);
        }

        @Override
        void tick() {
            ticks++;

            if (!impacted) {
                meteorY -= 1.2;

                for (int i = 0; i < 8; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.5;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.5;
                    mc.world.addParticle(ParticleTypes.FLAME, x + ox, meteorY + i * 0.3, z + oz, ox * 0.05, 0.1, oz * 0.05);
                    if (i % 2 == 0) {
                        mc.world.addParticle(ParticleTypes.LAVA, x + ox, meteorY + i * 0.3, z + oz, 0, 0, 0);
                    }
                }

                for (int i = 0; i < 3; i++) {
                    mc.world.addParticle(ParticleTypes.LARGE_SMOKE, x, meteorY, z, 0, 0, 0);
                }

                if (meteorY <= y + 1) {
                    impacted = true;
                    mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 5.0f, 0.7f);

                    for (int i = 0; i < 40; i++) {
                        double angle = mc.world.random.nextDouble() * Math.PI * 2;
                        double speed = 0.3 + mc.world.random.nextDouble() * 0.4;
                        double vx = Math.cos(angle) * speed;
                        double vy = 0.2 + mc.world.random.nextDouble() * 0.3;
                        double vz = Math.sin(angle) * speed;

                        mc.world.addParticle(ParticleTypes.FLAME, x, y + 0.5, z, vx, vy, vz);
                        if (i % 2 == 0) {
                            mc.world.addParticle(ParticleTypes.EXPLOSION, x, y + 0.5, z, vx * 0.5, vy * 0.5, vz * 0.5);
                        }
                    }
                }
            } else {
                if (ticks % 2 == 0) {
                    for (int i = 0; i < 5; i++) {
                        double ox = (mc.world.random.nextDouble() - 0.5) * 2;
                        double oz = (mc.world.random.nextDouble() - 0.5) * 2;
                        mc.world.addParticle(ParticleTypes.SMOKE, x + ox, y + 0.3, z + oz, 0, 0.05, 0);
                    }
                }
            }
        }
    }

    private class OrbitalStrikeEffect extends AnimatedEffect {
        private final double height;
        private boolean struck = false;

        OrbitalStrikeEffect(double x, double y, double z, double height) {
            super(x, y, z, 60);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 3.0f, 0.5f);
        }

        @Override
        void tick() {
            ticks++;

            if (ticks < 20) {
                double radius = 1.5;
                for (int i = 0; i < 16; i++) {
                    double angle = (Math.PI * 2 / 16) * i + ticks * 0.1;
                    double px = x + Math.cos(angle) * radius;
                    double pz = z + Math.sin(angle) * radius;
                    mc.world.addParticle(ParticleTypes.END_ROD, px, y + 0.1, pz, 0, 0.02, 0);
                }
                for (int i = 0; i < 8; i++) {
                    double angle = (Math.PI * 2 / 8) * i - ticks * 0.15;
                    double px = x + Math.cos(angle) * 0.7;
                    double pz = z + Math.sin(angle) * 0.7;
                    mc.world.addParticle(ParticleTypes.ELECTRIC_SPARK, px, y + 0.1, pz, 0, 0.01, 0);
                }
            }

            if (ticks >= 15 && ticks < 25) {
                double beamProgress = (ticks - 15) / 10.0;
                double beamTop = y + 25 - beamProgress * 25;

                for (double py = beamTop; py < y + 25; py += 0.5) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.4;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.4;
                    mc.world.addParticle(ParticleTypes.END_ROD, x + ox, py, z + oz, 0, -0.5, 0);
                    if (mc.world.random.nextDouble() < 0.3) {
                        mc.world.addParticle(ParticleTypes.ELECTRIC_SPARK, x + ox, py, z + oz, ox * 0.1, 0, oz * 0.1);
                    }
                }
            }

            if (ticks == 25 && !struck) {
                struck = true;
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 5.0f, 0.7f);
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 4.0f, 1.2f);

                for (int i = 0; i < 30; i++) {
                    mc.world.addParticle(ParticleTypes.FLASH, x, y + height / 2, z, 0, 0, 0);
                }

                for (int i = 0; i < 60; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double pitch = (mc.world.random.nextDouble() - 0.3) * Math.PI * 0.5;
                    double speed = 0.4 + mc.world.random.nextDouble() * 0.5;
                    double vx = Math.cos(angle) * Math.cos(pitch) * speed;
                    double vy = Math.sin(pitch) * speed + 0.1;
                    double vz = Math.sin(angle) * Math.cos(pitch) * speed;

                    mc.world.addParticle(ParticleTypes.END_ROD, x, y + 0.5, z, vx, vy, vz);
                    if (i % 2 == 0) {
                        mc.world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, vx * 1.5, vy * 1.5, vz * 1.5);
                    }
                }
            }

            if (ticks > 25 && ticks < 45) {
                double waveRadius = (ticks - 25) * 0.5;
                int particles = 20 + (ticks - 25) * 2;

                for (int i = 0; i < particles; i++) {
                    double angle = (Math.PI * 2 / particles) * i;
                    double px = x + Math.cos(angle) * waveRadius;
                    double pz = z + Math.sin(angle) * waveRadius;
                    mc.world.addParticle(ParticleTypes.CLOUD, px, y + 0.2, pz, Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05);
                }

                if (ticks % 2 == 0) {
                    for (int i = 0; i < 5; i++) {
                        double ox = (mc.world.random.nextDouble() - 0.5) * 1.5;
                        double oz = (mc.world.random.nextDouble() - 0.5) * 1.5;
                        mc.world.addParticle(ParticleTypes.ELECTRIC_SPARK, x + ox, y + mc.world.random.nextDouble() * 2, z + oz, 0, 0.05, 0);
                    }
                }
            }
        }
    }

    private class PhoenixRiseEffect extends AnimatedEffect {
        private final double height;

        PhoenixRiseEffect(double x, double y, double z, double height) {
            super(x, y, z, 55);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.HOSTILE, 2.0f, 1.5f);
        }

        @Override
        void tick() {
            ticks++;

            double riseHeight = Math.min(ticks * 0.25, 8);
            double phoenixY = y + riseHeight;

            if (ticks < 45) {
                for (int i = 0; i < 8; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.4;
                    double oy = (mc.world.random.nextDouble() - 0.5) * 0.6;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.4;
                    mc.world.addParticle(ParticleTypes.FLAME, x + ox, phoenixY + oy, z + oz, ox * 0.02, 0.1, oz * 0.02);
                    if (i % 2 == 0) {
                        mc.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + ox, phoenixY + oy, z + oz, ox * 0.01, 0.08, oz * 0.01);
                    }
                }

                double wingSpan = 1.0 + Math.sin(ticks * 0.2) * 0.3;
                for (int wing = -1; wing <= 1; wing += 2) {
                    for (int i = 0; i < 5; i++) {
                        double wingX = x + wing * (wingSpan * (0.3 + i * 0.2));
                        double wingY = phoenixY - i * 0.15 + Math.sin(ticks * 0.3 + i) * 0.1;
                        double oy = (mc.world.random.nextDouble() - 0.5) * 0.2;
                        mc.world.addParticle(ParticleTypes.FLAME, wingX, wingY + oy, z, wing * 0.05, 0.02, 0);
                        if (i < 3) {
                            mc.world.addParticle(ParticleTypes.SMALL_FLAME, wingX, wingY + oy, z, wing * 0.03, 0.01, 0);
                        }
                    }
                }

                for (int i = 0; i < 4; i++) {
                    double tailY = phoenixY - 0.5 - i * 0.3;
                    if (tailY > y) {
                        double ox = (mc.world.random.nextDouble() - 0.5) * 0.3;
                        double oz = (mc.world.random.nextDouble() - 0.5) * 0.3;
                        mc.world.addParticle(ParticleTypes.FLAME, x + ox, tailY, z + oz, 0, -0.05, 0);
                        mc.world.addParticle(ParticleTypes.LAVA, x + ox, tailY, z + oz, 0, 0, 0);
                    }
                }
            }

            if (ticks < 35) {
                double trailRadius = 0.8 + ticks * 0.03;
                for (int i = 0; i < 6; i++) {
                    double angle = (Math.PI * 2 / 6) * i + ticks * 0.1;
                    double px = x + Math.cos(angle) * trailRadius;
                    double pz = z + Math.sin(angle) * trailRadius;
                    mc.world.addParticle(ParticleTypes.FLAME, px, y + 0.1, pz, 0, 0.05, 0);
                }
            }

            if (ticks == 45) {
                mc.world.playSound(mc.player, x, phoenixY, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.AMBIENT, 2.0f, 1.2f);
                for (int i = 0; i < 50; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double pitch = (mc.world.random.nextDouble() - 0.5) * Math.PI;
                    double speed = 0.3 + mc.world.random.nextDouble() * 0.4;
                    double vx = Math.cos(angle) * Math.cos(pitch) * speed;
                    double vy = Math.sin(pitch) * speed + 0.2;
                    double vz = Math.sin(angle) * Math.cos(pitch) * speed;

                    mc.world.addParticle(ParticleTypes.FLAME, x, phoenixY, z, vx, vy, vz);
                    if (i % 3 == 0) {
                        mc.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, phoenixY, z, vx * 0.8, vy * 0.8, vz * 0.8);
                    }
                }
            }

            if (ticks > 45 && ticks < 55) {
                for (int i = 0; i < 8; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 4;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 4;
                    double startY = y + 8 + mc.world.random.nextDouble() * 2;
                    mc.world.addParticle(ParticleTypes.FALLING_LAVA, x + ox, startY, z + oz, 0, 0, 0);
                }
            }
        }
    }

    private class PigBombEffect extends AnimatedEffect {
        private PigEntity pig;
        private double pigY;
        private boolean impacted = false;
        private static final double START_HEIGHT = 15.0;
        private static final double FALL_SPEED = 0.8;

        PigBombEffect(double x, double y, double z) {
            super(x, y, z, 60);
            this.pigY = y + START_HEIGHT;
            spawnPig();
            mc.world.playSound(mc.player, x, y + START_HEIGHT, z, SoundEvents.ENTITY_PIG_AMBIENT, SoundCategory.NEUTRAL, 3.0f, 1.5f);
        }

        private void spawnPig() {
            pig = new PigEntity(EntityType.PIG, mc.world);
            pig.refreshPositionAndAngles(x, pigY, z, 0, 0);
            pig.setNoGravity(true);
            pig.setInvulnerable(true);
            mc.world.addEntity(pig);
        }

        @Override
        void tick() {
            ticks++;

            if (!impacted && pig != null) {
                pigY -= FALL_SPEED;
                pig.refreshPositionAndAngles(x, pigY, z, pig.getYaw() + 15, 0);

                for (int i = 0; i < 6; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.6;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.6;
                    double trailY = pigY + 0.5 + i * 0.25;
                    mc.world.addParticle(ParticleTypes.FLAME, x + ox, trailY, z + oz, ox * 0.02, 0.05, oz * 0.02);
                    if (i % 2 == 0) {
                        mc.world.addParticle(ParticleTypes.SMOKE, x + ox, trailY, z + oz, 0, 0.03, 0);
                    }
                }

                if (ticks % 2 == 0) {
                    for (int i = 0; i < 3; i++) {
                        double angle = mc.world.random.nextDouble() * Math.PI * 2;
                        double r = 0.4 + mc.world.random.nextDouble() * 0.3;
                        mc.world.addParticle(ParticleTypes.LAVA, 
                            x + Math.cos(angle) * r, pigY + 0.3, z + Math.sin(angle) * r, 0, 0, 0);
                    }
                }

                if (ticks % 5 == 0) {
                    mc.world.playSound(mc.player, x, pigY, z, SoundEvents.ENTITY_PIG_HURT, SoundCategory.NEUTRAL, 1.5f, 0.5f + ticks * 0.03f);
                }

                if (pigY <= y + 1) {
                    impacted = true;
                    pig.discard();
                    pig = null;
                    playExplosion();
                }
            }

            if (impacted && ticks < maxTicks) {
                if (ticks % 2 == 0) {
                    for (int i = 0; i < 4; i++) {
                        double ox = (mc.world.random.nextDouble() - 0.5) * 2.5;
                        double oz = (mc.world.random.nextDouble() - 0.5) * 2.5;
                        mc.world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + ox, y + 0.3, z + oz, 0, 0.08, 0);
                    }
                }
            }
        }

        private void playExplosion() {
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 5.0f, 0.8f);
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_PIG_DEATH, SoundCategory.NEUTRAL, 3.0f, 0.5f);

            for (int i = 0; i < 50; i++) {
                double angle = mc.world.random.nextDouble() * Math.PI * 2;
                double pitch = (mc.world.random.nextDouble() - 0.5) * Math.PI;
                double speed = 0.4 + mc.world.random.nextDouble() * 0.5;
                double vx = Math.cos(angle) * Math.cos(pitch) * speed;
                double vy = Math.sin(pitch) * speed + 0.2;
                double vz = Math.sin(angle) * Math.cos(pitch) * speed;

                mc.world.addParticle(ParticleTypes.FLAME, x, y + 0.5, z, vx, vy, vz);
                if (i % 2 == 0) {
                    mc.world.addParticle(ParticleTypes.EXPLOSION, x, y + 0.5, z, vx * 0.3, vy * 0.3, vz * 0.3);
                }
                if (i % 4 == 0) {
                    mc.world.addParticle(ParticleTypes.LAVA, x, y + 0.5, z, vx * 0.5, vy * 0.5, vz * 0.5);
                }
            }

            for (int i = 0; i < 25; i++) {
                double angle = mc.world.random.nextDouble() * Math.PI * 2;
                double speed = 0.2 + mc.world.random.nextDouble() * 0.3;
                double vx = Math.cos(angle) * speed;
                double vy = 0.1 + mc.world.random.nextDouble() * 0.3;
                double vz = Math.sin(angle) * speed;
                mc.world.addParticle(ParticleTypes.HEART, x, y + 0.5, z, vx, vy, vz);
            }

            for (int ring = 0; ring < 3; ring++) {
                double radius = 1.0 + ring * 0.8;
                int particles = 16 + ring * 8;
                for (int i = 0; i < particles; i++) {
                    double angle = (Math.PI * 2 / particles) * i;
                    double px = x + Math.cos(angle) * radius;
                    double pz = z + Math.sin(angle) * radius;
                    mc.world.addParticle(ParticleTypes.CLOUD, px, y + 0.2, pz, Math.cos(angle) * 0.1, 0.05, Math.sin(angle) * 0.1);
                }
            }
        }

        @Override
        boolean isFinished() {
            if (pig != null && ticks >= maxTicks) {
                pig.discard();
                pig = null;
            }
            return ticks >= maxTicks;
        }
    }

    private class ShatterEffect extends AnimatedEffect {
        private final double height;
        private final double[][] shards = new double[20][6];

        ShatterEffect(double x, double y, double z, double height) {
            super(x, y, z, 40);
            this.height = height;
            for (int i = 0; i < shards.length; i++) {
                shards[i][0] = x + (mc.world.random.nextDouble() - 0.5) * 0.6;
                shards[i][1] = y + mc.world.random.nextDouble() * height;
                shards[i][2] = z + (mc.world.random.nextDouble() - 0.5) * 0.6;
                double angle = mc.world.random.nextDouble() * Math.PI * 2;
                double speed = 0.15 + mc.world.random.nextDouble() * 0.2;
                shards[i][3] = Math.cos(angle) * speed;
                shards[i][4] = 0.1 + mc.world.random.nextDouble() * 0.15;
                shards[i][5] = Math.sin(angle) * speed;
            }
            mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 2.0f, 0.8f);
        }

        @Override
        void tick() {
            ticks++;
            for (double[] shard : shards) {
                shard[0] += shard[3];
                shard[1] += shard[4];
                shard[2] += shard[5];
                shard[4] -= 0.02;
                mc.world.addParticle(ParticleTypes.CRIT, shard[0], shard[1], shard[2], 0, 0, 0);
                if (mc.world.random.nextDouble() < 0.3) {
                    mc.world.addParticle(ParticleTypes.ENCHANTED_HIT, shard[0], shard[1], shard[2], 0, 0, 0);
                }
            }
            if (ticks % 5 == 0 && ticks < 25) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 0.5f, 1.2f);
            }
        }
    }

    private class ThunderClapEffect extends AnimatedEffect {
        private final double height;
        private boolean clapped = false;

        ThunderClapEffect(double x, double y, double z, double height) {
            super(x, y, z, 35);
            this.height = height;
        }

        @Override
        void tick() {
            ticks++;
            double centerY = y + height / 2;
            if (ticks < 10) {
                for (int i = 0; i < 8; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.5;
                    double oy = (mc.world.random.nextDouble() - 0.5) * height;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.5;
                    mc.world.addParticle(ParticleTypes.ELECTRIC_SPARK, x + ox, centerY + oy, z + oz, (x - (x + ox)) * 0.1, 0, (z - (z + oz)) * 0.1);
                }
            }
            if (ticks == 10 && !clapped) {
                clapped = true;
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 5.0f, 1.5f);
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 3.0f, 1.8f);
                for (int i = 0; i < 40; i++) mc.world.addParticle(ParticleTypes.FLASH, x, centerY, z, 0, 0, 0);
                for (int i = 0; i < 30; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double speed = 0.5 + mc.world.random.nextDouble() * 0.3;
                    mc.world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, centerY, z, Math.cos(angle) * speed, (mc.world.random.nextDouble() - 0.5) * 0.3, Math.sin(angle) * speed);
                }
            }
            if (ticks > 10 && ticks < 30) {
                double waveRadius = (ticks - 10) * 0.6;
                int particles = 24 + (ticks - 10) * 2;
                for (int i = 0; i < particles; i++) {
                    double angle = (Math.PI * 2 / particles) * i;
                    double px = x + Math.cos(angle) * waveRadius;
                    double pz = z + Math.sin(angle) * waveRadius;
                    mc.world.addParticle(ParticleTypes.CLOUD, px, centerY, pz, Math.cos(angle) * 0.08, 0, Math.sin(angle) * 0.08);
                    if (i % 4 == 0) mc.world.addParticle(ParticleTypes.SONIC_BOOM, px, centerY, pz, 0, 0, 0);
                }
            }
        }
    }

    private class CrownDropEffect extends AnimatedEffect {
        private final double height;
        private double crownY;
        private double crownRotation = 0;

        CrownDropEffect(double x, double y, double z, double height) {
            super(x, y, z, 50);
            this.height = height;
            this.crownY = y + height + 5;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 2.0f, 1.2f);
        }

        @Override
        void tick() {
            ticks++;
            crownRotation += 0.15;
            if (ticks < 35) {
                crownY -= 0.15;
                double crownRadius = 0.4;
                for (int i = 0; i < 5; i++) {
                    double angle = crownRotation + (Math.PI * 2 / 5) * i;
                    double px = x + Math.cos(angle) * crownRadius;
                    double pz = z + Math.sin(angle) * crownRadius;
                    mc.world.addParticle(ParticleTypes.END_ROD, px, crownY, pz, 0, 0.02, 0);
                    mc.world.addParticle(ParticleTypes.END_ROD, px, crownY + 0.3, pz, 0, 0.01, 0);
                }
                for (int i = 0; i < 5; i++) {
                    double angle = crownRotation + (Math.PI * 2 / 5) * i + Math.PI / 5;
                    double px = x + Math.cos(angle) * crownRadius * 0.7;
                    double pz = z + Math.sin(angle) * crownRadius * 0.7;
                    mc.world.addParticle(ParticleTypes.GLOW, px, crownY + 0.5, pz, 0, 0.01, 0);
                }
                if (ticks % 3 == 0) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    mc.world.addParticle(ParticleTypes.FIREWORK, x + Math.cos(angle) * 0.3, crownY + 0.2, z + Math.sin(angle) * 0.3, 0, -0.02, 0);
                }
            }
            if (ticks == 35) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 2.0f, 1.0f);
                for (int i = 0; i < 30; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double speed = 0.1 + mc.world.random.nextDouble() * 0.2;
                    mc.world.addParticle(ParticleTypes.END_ROD, x, crownY, z, Math.cos(angle) * speed, 0.1 + mc.world.random.nextDouble() * 0.1, Math.sin(angle) * speed);
                    mc.world.addParticle(ParticleTypes.GLOW, x, crownY, z, Math.cos(angle) * speed * 0.5, 0.05, Math.sin(angle) * speed * 0.5);
                }
            }
            if (ticks > 35 && ticks < 50) {
                for (int i = 0; i < 3; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.5;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.5;
                    mc.world.addParticle(ParticleTypes.END_ROD, x + ox, crownY + mc.world.random.nextDouble() * 0.5, z + oz, 0, 0.03, 0);
                }
            }
        }
    }

    private class RainbowBurstEffect extends AnimatedEffect {
        private final double height;

        RainbowBurstEffect(double x, double y, double z, double height) {
            super(x, y, z, 40);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.AMBIENT, 2.0f, 1.5f);
        }

        @Override
        void tick() {
            ticks++;
            double centerY = y + height / 2;
            if (ticks == 1) {
                for (int i = 0; i < 60; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double pitch = (mc.world.random.nextDouble() - 0.5) * Math.PI;
                    double speed = 0.3 + mc.world.random.nextDouble() * 0.4;
                    double vx = Math.cos(angle) * Math.cos(pitch) * speed;
                    double vy = Math.sin(pitch) * speed + 0.1;
                    double vz = Math.sin(angle) * Math.cos(pitch) * speed;
                    switch (i % 7) {
                        case 0 -> mc.world.addParticle(ParticleTypes.FLAME, x, centerY, z, vx, vy, vz);
                        case 1 -> mc.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, centerY, z, vx, vy, vz);
                        case 2 -> mc.world.addParticle(ParticleTypes.END_ROD, x, centerY, z, vx, vy, vz);
                        case 3 -> mc.world.addParticle(ParticleTypes.GLOW, x, centerY, z, vx, vy, vz);
                        case 4 -> mc.world.addParticle(ParticleTypes.ENCHANTED_HIT, x, centerY, z, vx, vy, vz);
                        case 5 -> mc.world.addParticle(ParticleTypes.CRIT, x, centerY, z, vx, vy, vz);
                        case 6 -> mc.world.addParticle(ParticleTypes.FIREWORK, x, centerY, z, vx, vy, vz);
                    }
                }
            }
            if (ticks < 30) {
                for (int ring = 0; ring < 3; ring++) {
                    double radius = ticks * 0.15 + ring * 0.3;
                    int particles = 12 + ring * 4;
                    double ringY = centerY + (ring - 1) * 0.5;
                    for (int i = 0; i < particles; i++) {
                        double angle = (Math.PI * 2 / particles) * i + ticks * 0.1 * (ring % 2 == 0 ? 1 : -1);
                        double px = x + Math.cos(angle) * radius;
                        double pz = z + Math.sin(angle) * radius;
                        switch ((i + ring + ticks) % 5) {
                            case 0 -> mc.world.addParticle(ParticleTypes.END_ROD, px, ringY, pz, 0, 0.02, 0);
                            case 1 -> mc.world.addParticle(ParticleTypes.GLOW, px, ringY, pz, 0, 0.02, 0);
                            case 2 -> mc.world.addParticle(ParticleTypes.FIREWORK, px, ringY, pz, 0, 0.02, 0);
                            case 3 -> mc.world.addParticle(ParticleTypes.ENCHANTED_HIT, px, ringY, pz, 0, 0.02, 0);
                            case 4 -> mc.world.addParticle(ParticleTypes.CRIT, px, ringY, pz, 0, 0.02, 0);
                        }
                    }
                }
            }
            if (ticks % 10 == 0 && ticks < 30) {
                mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.AMBIENT, 1.0f, 1.0f + ticks * 0.02f);
            }
        }
    }

    private class SoulAscendEffect extends AnimatedEffect {
        private final double height;
        private double soulY;

        SoulAscendEffect(double x, double y, double z, double height) {
            super(x, y, z, 55);
            this.height = height;
            this.soulY = y + height / 2;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 2.0f, 0.6f);
        }

        @Override
        void tick() {
            ticks++;
            if (ticks < 15) {
                for (int i = 0; i < 5; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.8;
                    double oy = mc.world.random.nextDouble() * height;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.8;
                    mc.world.addParticle(ParticleTypes.SOUL, x + ox, y + oy, z + oz, (x - (x + ox)) * 0.05, 0.1, (z - (z + oz)) * 0.05);
                }
            }
            if (ticks >= 15 && ticks < 50) {
                soulY += 0.2;
                double wobble = Math.sin(ticks * 0.3) * 0.2;
                double soulX = x + wobble;
                double soulZ = z + Math.cos(ticks * 0.3) * 0.2;
                for (int i = 0; i < 4; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.3;
                    double oy = (mc.world.random.nextDouble() - 0.5) * 0.5;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.3;
                    mc.world.addParticle(ParticleTypes.SOUL, soulX + ox, soulY + oy, soulZ + oz, 0, 0.05, 0);
                }
                if (ticks % 2 == 0) mc.world.addParticle(ParticleTypes.SCULK_SOUL, soulX, soulY, soulZ, 0, 0.08, 0);
                for (int i = 0; i < 2; i++) {
                    double trailY = soulY - 0.5 - i * 0.3;
                    if (trailY > y) {
                        mc.world.addParticle(ParticleTypes.SOUL, soulX + (mc.world.random.nextDouble() - 0.5) * 0.2, trailY, soulZ + (mc.world.random.nextDouble() - 0.5) * 0.2, 0, -0.02, 0);
                    }
                }
            }
            if (ticks == 50) {
                mc.world.playSound(mc.player, x, soulY, z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 2.0f);
                for (int i = 0; i < 20; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double speed = 0.1 + mc.world.random.nextDouble() * 0.15;
                    mc.world.addParticle(ParticleTypes.SOUL, x, soulY, z, Math.cos(angle) * speed, 0.2 + mc.world.random.nextDouble() * 0.1, Math.sin(angle) * speed);
                }
            }
        }
    }

    private class FireworkBurstEffect extends AnimatedEffect {
        private final double height;
        private int burstCount = 0;

        FireworkBurstEffect(double x, double y, double z, double height) {
            super(x, y, z, 45);
            this.height = height;
            mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.AMBIENT, 2.0f, 1.0f);
        }

        @Override
        void tick() {
            ticks++;
            double centerY = y + height / 2;
            if (ticks < 10) {
                for (int i = 0; i < 5; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 0.3;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 0.3;
                    mc.world.addParticle(ParticleTypes.FIREWORK, x + ox, y + ticks * 0.3, z + oz, 0, 0.1, 0);
                }
            }
            if (ticks == 10 || ticks == 20 || ticks == 30) {
                burstCount++;
                double burstY = centerY + (burstCount - 2) * 1.5;
                mc.world.playSound(mc.player, x, burstY, z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.AMBIENT, 2.0f, 0.8f + burstCount * 0.2f);
                mc.world.playSound(mc.player, x, burstY, z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.AMBIENT, 1.5f, 1.0f);
                for (int i = 0; i < 40; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double pitch = (mc.world.random.nextDouble() - 0.5) * Math.PI;
                    double speed = 0.25 + mc.world.random.nextDouble() * 0.35;
                    double vx = Math.cos(angle) * Math.cos(pitch) * speed;
                    double vy = Math.sin(pitch) * speed;
                    double vz = Math.sin(angle) * Math.cos(pitch) * speed;
                    switch ((i + burstCount) % 6) {
                        case 0 -> mc.world.addParticle(ParticleTypes.FIREWORK, x, burstY, z, vx, vy, vz);
                        case 1 -> mc.world.addParticle(ParticleTypes.END_ROD, x, burstY, z, vx * 0.7, vy * 0.7, vz * 0.7);
                        case 2 -> mc.world.addParticle(ParticleTypes.CRIT, x, burstY, z, vx, vy, vz);
                        case 3 -> mc.world.addParticle(ParticleTypes.ENCHANTED_HIT, x, burstY, z, vx, vy, vz);
                        case 4 -> mc.world.addParticle(ParticleTypes.GLOW, x, burstY, z, vx * 0.5, vy * 0.5, vz * 0.5);
                        case 5 -> mc.world.addParticle(ParticleTypes.FLASH, x, burstY, z, 0, 0, 0);
                    }
                }
            }
            if ((ticks > 10 && ticks < 20) || (ticks > 20 && ticks < 30) || (ticks > 30 && ticks < 40)) {
                for (int i = 0; i < 8; i++) {
                    double ox = (mc.world.random.nextDouble() - 0.5) * 2;
                    double oy = (mc.world.random.nextDouble() - 0.5) * 2;
                    double oz = (mc.world.random.nextDouble() - 0.5) * 2;
                    mc.world.addParticle(ParticleTypes.FIREWORK, x + ox, centerY + oy, z + oz, -ox * 0.02, -oy * 0.02, -oz * 0.02);
                }
            }
            if (ticks == 40) {
                mc.world.playSound(mc.player, x, centerY, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.AMBIENT, 3.0f, 1.0f);
                for (int i = 0; i < 60; i++) {
                    double angle = mc.world.random.nextDouble() * Math.PI * 2;
                    double pitch = (mc.world.random.nextDouble() - 0.5) * Math.PI;
                    double speed = 0.4 + mc.world.random.nextDouble() * 0.4;
                    double vx = Math.cos(angle) * Math.cos(pitch) * speed;
                    double vy = Math.sin(pitch) * speed + 0.1;
                    double vz = Math.sin(angle) * Math.cos(pitch) * speed;
                    mc.world.addParticle(ParticleTypes.FIREWORK, x, centerY, z, vx, vy, vz);
                }
            }
        }
    }

}