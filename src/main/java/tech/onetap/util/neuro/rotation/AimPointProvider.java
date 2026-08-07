package tech.onetap.util.neuro.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;

/**
 * Интерфейс для провайдера точки прицеливания.
 * Позволяет отделить детерминированный контроллер от потенциальной обучаемой aim-модели.
 */
public interface AimPointProvider {

    /**
     * Обновляет и возвращает aim point для заданной цели.
     * @param ka KillAura для доступа к настройкам
     * @param target цель
     * @param targetChanged true если цель сменилась на этом тике
     * @return позиция точки прицеливания в мире
     */
    Vec3d update(KillAura ka, LivingEntity target, boolean targetChanged);

    /**
     * Сбрасывает внутреннее состояние.
     */
    void reset();
}
