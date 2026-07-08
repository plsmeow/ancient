package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.mixin.EntityVelocityUpdateS2CPacketAccessor;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "Velocity", moduleCategory = ModuleCategory.COMBAT)
public class Velocity extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Grim", "Custom", "Grim");
    private final BooleanSetting fix = new BooleanSetting("Fix", false).setVisible(() -> mode.is("Grim"));
    private final SliderSetting fixDelay = new SliderSetting("Fix Delay", 100, 0, 1000, 10).setVisible(() -> mode.is("Grim") && fix.getValue());

    private final SliderSetting horizontal = new SliderSetting("Horizontal", 0, 0, 100, 1);
    private final SliderSetting vertical = new SliderSetting("Vertical", 0, 0, 100, 1);

    private long lastVelocityTime = 0;
    private boolean velocityCanceled = false;

    @Subscribe
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

        // --- РЕЖИМ CUSTOM ---
        if (mode.is("Custom")) {
            if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
                int hPct = (int) horizontal.getValue();
                int vPct = (int) vertical.getValue();

                if (hPct == 0 && vPct == 0) {
                    e.cancelEvent();
                } else {
                    EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor) packet;

                    int newX = (int) (packet.getVelocityX() * (hPct / 100.0f));
                    int newY = (int) (packet.getVelocityY() * (vPct / 100.0f));
                    int newZ = (int) (packet.getVelocityZ() * (hPct / 100.0f));

                    accessor.setVelocityX(newX);
                    accessor.setVelocityY(newY);
                    accessor.setVelocityZ(newZ);
                }
            }
        }
    }
}