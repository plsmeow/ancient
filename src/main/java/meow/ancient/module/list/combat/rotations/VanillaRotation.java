package meow.ancient.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.util.math.BestPoint;
import meow.ancient.util.math.RotationUtil;
import meow.ancient.util.rotation.MoveFixMode;
import meow.ancient.util.rotation.Rotation;
import meow.ancient.util.rotation.RotationComponent;

public class VanillaRotation extends RotationMode {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (target == null) return;

        Vec3d targetPoint = ka.resolveMultipoint(target, BestPoint.getNearestPoint(target), ka.distance.getValue());
        var rotation = new Rotation(RotationUtil.calculate(targetPoint));

        RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode());
    }
}
