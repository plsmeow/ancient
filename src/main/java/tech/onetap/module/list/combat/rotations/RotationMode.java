package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import tech.onetap.module.list.combat.KillAura;

/**
 * Базовый класс для всех ротаций KillAura.
 * Каждая ротация хранит своё внутреннее состояние и получает доступ
 * к общему состоянию через переданный экземпляр {@link KillAura}.
 */
public abstract class RotationMode {

    public abstract void update(KillAura killAura, LivingEntity target);

    /**
     * Сброс внутреннего состояния ротации (вызывается при потере цели,
     * выключении модуля и т.п.).
     */
    public void reset(KillAura killAura) {
    }
}
