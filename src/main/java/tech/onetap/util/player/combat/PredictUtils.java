package tech.onetap.util.player.combat;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@UtilityClass
public class PredictUtils {

    private static Vec3d lead(Vec3d base, Entity entity, double factor) {
        Vec3d add = new Vec3d(0, MathHelper.clamp(entity.getY() - entity.getHeight(), 0, entity.getHeight() / 2.0), 0);
        return base.add(add).add(entity.getVelocity().multiply(factor));
    }

    public static Vec3d getPredicted(Entity entity, double factor) {
        return lead(entity.getPos(), entity, factor);
    }

    public static Vec3d getPredictedRender(Entity entity, double factor, float partialTicks) {
        return lead(entity.getLerpedPos(partialTicks), entity, factor);
    }

    public static Vec3d predict(Entity entity, double factor) {
        return getPredicted(entity, factor);
    }
}
