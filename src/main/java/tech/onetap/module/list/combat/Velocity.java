package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.BooleanSetting;

import java.util.Optional;

@ModuleInformation(moduleName = "Velocity", moduleDesc = "Отменяет отталкивание", moduleCategory = ModuleCategory.COMBAT)
public class Velocity extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Grim", "Cancel", "Grim");
    private final BooleanSetting fix = new BooleanSetting("Fix", false).setVisible(() -> mode.is("Grim"));
    private final SliderSetting fixDelay = new SliderSetting("Fix Delay", 100, 0, 1000, 10).setVisible(() -> mode.is("Grim") && fix.getValue());

    private long lastVelocityTime = 0;
    private boolean velocityCanceled = false;

    @EventHandler
    private void onPacket(EventPacket e) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        // --- РЕЖИМ GRIM AC ---
        if (mode.is("Grim")) {

            // Отмена Velocity пакета
            if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
                if (packet.getEntityId() == mc.player.getId()) {
                    e.cancelEvent();
                    velocityCanceled = true;
                    lastVelocityTime = System.currentTimeMillis();
                }
                return;
            }

            // Обработка пинг пакетов
            if (e.getPacket() instanceof CommonPingS2CPacket) {
                if (fix.getValue()) {
                    // Если не было отмены velocity, пропускаем пинг пакет
                    if (!velocityCanceled) {
                        return;
                    }

                    // Проверяем, прошло ли достаточно времени с момента отмены velocity
                    long timeSinceVelocity = System.currentTimeMillis() - lastVelocityTime;
                    long delay = (long) fixDelay.getValue();

                    if (timeSinceVelocity < delay) {
                        // Еще не прошло время задержки, отменяем пинг пакет
                        e.cancelEvent();
                        return;
                    }

                    // Прошло достаточно времени, сбрасываем состояние и пропускаем пинг пакет
                    velocityCanceled = false;
                    return;
                }

                e.cancelEvent();
                return;
            }

            return;
        }

        // --- РЕЖИМ CANCEL ---
        if (mode.is("Cancel")) {
            if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
                if (packet.getEntityId() == mc.player.getId()) {
                    e.cancelEvent();
                }
            }
        }
    }

    public Optional<Vec3d> modifyExplosionKnockback() {
        return Optional.empty();
    }
}
