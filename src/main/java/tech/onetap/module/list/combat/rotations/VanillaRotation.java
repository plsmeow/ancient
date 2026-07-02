package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

public class VanillaRotation extends RotationMode {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (target == null) return;

        Vec3d targetPoint = ka.resolveMultipoint(target, BestPoint.getNearestPoint(target), ka.distance.getValue());
        var rotation = new Rotation(RotationUtil.calculate(targetPoint));

        RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue());
    }
}
