package meow.ancient.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import meow.ancient.event.list.EventKeyInput;
import meow.ancient.event.list.EventPacket;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.settings.BindSetting;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.util.packet.NetworkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInformation(
        moduleName = "WindCharge Abuse",
        moduleDesc = "Задерживает импульсы от порывов ветра и выпускает по биндам",
        moduleCategory = ModuleCategory.MOVEMENT
)
public class WindChargeAbuse extends Module {

    private final BindSetting releaseAllBind = new BindSetting("Спустить все", -1);
    private final BindSetting releaseSingleBind = new BindSetting("Поочередно (1 шт)", -1);
    private final BindSetting clearBind = new BindSetting("Очистить заряды", -1);
    private final BooleanSetting chatFeedback = new BooleanSetting("Вывод в чат", true);

    private final CopyOnWriteArrayList<StoredKnockback> storedKnockbacks = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() {
        super.onEnable();
        storedKnockbacks.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        clearCharges();
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;

        if (e.getType() == EventPacket.Type.RECEIVE) {
            // Сброс при смене мира или респавне
            if (e.getPacket() instanceof PlayerRespawnS2CPacket || e.getPacket() instanceof GameJoinS2CPacket) {
                storedKnockbacks.clear();
                return;
            }

            // Перехват пакета взрыва только от порыва ветра
            if (e.getPacket() instanceof ExplosionS2CPacket packet) {
                if (packet.playerKnockback().isPresent() && isWindChargeExplosion(packet)) {
                    e.cancelEvent();

                    Vec3d kb = packet.playerKnockback().get();
                    StoredKnockback sk = new StoredKnockback(kb);
                    storedKnockbacks.add(sk);

                    if (chatFeedback.getValue()) {
                        logDirect("Задержан порыв ветра! Накоплено: " + storedKnockbacks.size());
                    }

                    // Локальные визуальные и звуковые эффекты
                    mc.execute(() -> {
                        if (mc.world == null) return;
                        try {
                            if (packet.explosionSound() != null && packet.explosionSound().value() != null) {
                                mc.world.playSound(
                                        packet.center().x, packet.center().y, packet.center().z,
                                        packet.explosionSound().value(),
                                        SoundCategory.BLOCKS,
                                        4.0f,
                                        (1.0f + (mc.world.random.nextFloat() - mc.world.random.nextFloat()) * 0.2f) * 0.7f,
                                        false
                                );
                            }
                            if (packet.explosionParticle() != null) {
                                mc.world.addParticle(
                                        packet.explosionParticle(),
                                        packet.center().x, packet.center().y, packet.center().z,
                                        1.0, 0.0, 0.0
                                );
                            }
                        } catch (Exception ignored) {
                        }
                    });
                    return;
                }
            }

            // Буферизация транзакций Grim AC:
            // Пока есть накопленные заряды ветра, пинги буферизируются строго по порядку (FIFO).
            // Никаких самопроизвольных спусков — заряды выпускаются ТОЛЬКО по нажатию бинда!
            if (e.getPacket() instanceof CommonPingS2CPacket ping) {
                if (!storedKnockbacks.isEmpty()) {
                    e.cancelEvent();
                    storedKnockbacks.get(storedKnockbacks.size() - 1).getPongs().add(ping.getParameter());
                }
            }
        }
    }

    @EventHandler
    public void onKey(EventKeyInput e) {
        if (mc.player == null) return;
        if (e.getAction() != 1) return; // 1 = GLFW_PRESS

        if (releaseAllBind.getValue() != -1 && e.getKey() == releaseAllBind.getValue()) {
            releaseAll();
        } else if (releaseSingleBind.getValue() != -1 && e.getKey() == releaseSingleBind.getValue()) {
            releaseSingle();
        } else if (clearBind.getValue() != -1 && e.getKey() == clearBind.getValue()) {
            clearCharges();
        }
    }

    public void releaseAll() {
        if (mc.player == null || storedKnockbacks.isEmpty()) return;

        Vec3d total = Vec3d.ZERO;
        int count = storedKnockbacks.size();
        List<Integer> allPongs = new ArrayList<>();

        for (StoredKnockback sk : storedKnockbacks) {
            total = total.add(sk.getVector());
            allPongs.addAll(sk.getPongs());
        }
        storedKnockbacks.clear();

        // 1. Применяем суммарный импульс к игроку
        applyKnockback(total);

        // 2. Отправляем ВСЕ задержанные понг-пакеты строго в порядке их поступления
        for (int param : allPongs) {
            NetworkUtils.sendSilentPacket(new CommonPongC2SPacket(param));
        }

        if (chatFeedback.getValue()) {
            logDirect("Выпущено зарядов: " + count);
        }
    }

    public void releaseSingle() {
        if (mc.player == null || storedKnockbacks.isEmpty()) return;

        StoredKnockback sk = storedKnockbacks.remove(0);

        // 1. Применяем импульс одного порыва ветра
        applyKnockback(sk.getVector());

        // 2. Отправляем транзакции только для этого взрыва
        for (int param : sk.getPongs()) {
            NetworkUtils.sendSilentPacket(new CommonPongC2SPacket(param));
        }

        if (chatFeedback.getValue()) {
            logDirect("Выпущен 1 заряд! (осталось: " + storedKnockbacks.size() + ")");
        }
    }

    public void clearCharges() {
        if (storedKnockbacks.isEmpty()) return;

        int count = storedKnockbacks.size();
        List<Integer> allPongs = new ArrayList<>();
        for (StoredKnockback sk : storedKnockbacks) {
            allPongs.addAll(sk.getPongs());
        }
        storedKnockbacks.clear();

        // Отправляем транзакции, чтобы Grim не выдал TransactionOrder (skipped)
        for (int param : allPongs) {
            NetworkUtils.sendSilentPacket(new CommonPongC2SPacket(param));
        }

        if (chatFeedback.getValue() && count > 0) {
            logDirect("Очищено зарядов: " + count);
        }
    }

    private void applyKnockback(Vec3d kb) {
        if (mc.player == null) return;

        // Применяем вектор от сервера, чтобы симуляция Grim совпала без смещения (offset = 0)
        mc.player.setVelocity(mc.player.getVelocity().add(kb));
        mc.player.velocityModified = true;
        mc.player.fallDistance = 0f;

        if (mc.world != null) {
            try {
                mc.world.playSound(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(),
                        SoundCategory.PLAYERS,
                        1.0f,
                        1.2f,
                        false
                );
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isWindChargeExplosion(ExplosionS2CPacket packet) {
        if (packet.explosionSound() != null) {
            if (packet.explosionSound().matches(SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST)
                    || packet.explosionSound().matches(SoundEvents.ENTITY_BREEZE_WIND_BURST)) {
                return true;
            }

            var optKey = packet.explosionSound().getKey();
            if (optKey.isPresent()) {
                String path = optKey.get().getValue().getPath().toLowerCase();
                if (path.contains("wind_charge") || path.contains("breeze") || path.contains("gust")) {
                    return true;
                }
            }
        }

        if (packet.explosionParticle() != null) {
            var particleType = packet.explosionParticle().getType();
            if (particleType == ParticleTypes.GUST
                    || particleType == ParticleTypes.SMALL_GUST
                    || particleType == ParticleTypes.GUST_EMITTER_LARGE
                    || particleType == ParticleTypes.GUST_EMITTER_SMALL) {
                return true;
            }

            String particleStr = particleType.toString().toLowerCase();
            if (particleStr.contains("gust") || particleStr.contains("wind")) {
                return true;
            }
        }

        return false;
    }

    private static class StoredKnockback {
        private final Vec3d vector;
        private final List<Integer> pongs = new CopyOnWriteArrayList<>();

        public StoredKnockback(Vec3d vector) {
            this.vector = vector;
        }

        public Vec3d getVector() {
            return vector;
        }

        public List<Integer> getPongs() {
            return pongs;
        }
    }
}
