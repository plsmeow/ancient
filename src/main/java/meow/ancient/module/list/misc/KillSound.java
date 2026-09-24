package meow.ancient.module.list.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import meow.ancient.Ancient;
import meow.ancient.event.list.EventAttack;
import meow.ancient.event.list.EventPacket;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.module.settings.SliderSetting;

import java.util.UUID;

@ModuleInformation(moduleName = "KillSound", moduleDesc = "Проигрывает звук при убийстве игрока", moduleCategory = ModuleCategory.MISC)
public class KillSound extends Module {

    private static final SoundEvent KILL_SOUND = SoundEvent.of(Identifier.of("mre", "kill_sound"));

    private final SliderSetting volume = new SliderSetting("Громкость", 1.0, 0.1, 2.0, 0.1);
    private final SliderSetting pitch = new SliderSetting("Тон", 1.0, 0.5, 2.0, 0.1);

    private UUID lastTargetUuid = null;

    @EventHandler
    private void onAttack(EventAttack event) {
        if (mc.player == null || !(event.getEntity() instanceof PlayerEntity player) || player == mc.player) return;
        if (!isKillAuraTarget(player)) return;

        lastTargetUuid = player.getUuid();
    }

    private boolean isKillAuraTarget(PlayerEntity player) {
        KillAura aura = Ancient.getInstance().getModuleStorage().get(KillAura.class);
        return aura != null && aura.isEnabled() && aura.getTarget() != null && aura.getTarget().getUuid().equals(player.getUuid());
    }

    @EventHandler
    private void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null || event.getType() != EventPacket.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet) || packet.getStatus() != 3) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player) || player == mc.player || lastTargetUuid == null) return;
        if (!player.getUuid().equals(lastTargetUuid)) return;

        playKillSound();
        lastTargetUuid = null;
    }

    private void playKillSound() {
        if (mc.getSoundManager() == null) return;

        mc.getSoundManager().play(PositionedSoundInstance.master(KILL_SOUND, pitch.getFloatValue(), volume.getFloatValue()));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastTargetUuid = null;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        lastTargetUuid = null;
    }
}
